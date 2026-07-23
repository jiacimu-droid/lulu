package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.PauseCircle
import me.rerere.hugeicons.stroke.PlayCircle
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.tts.model.PlaybackStatus
import org.koin.compose.koinInject

private const val TTS_TOOL_NAME = "text_to_speech"
private const val INLINE_TTS_TEXT_KEY = "lulu_inline_tts_text"

private val INLINE_VOICE_SENTENCE_BOUNDARY_REGEX =
    Regex("(?<=[.!?~～。！？])\\s*|(?<=…)(?=\\s|$)\\s*|\\n+")

private val INLINE_VOICE_MATCH_IGNORED_REGEX =
    Regex("[\\s\\p{Punct}，。！？；：、…“”‘’（）【】《》—～]+")

private data class InlineVoiceRequest(
    val partIndex: Int,
    val id: String,
    val rawText: String,
    val bubbleText: String,
)

internal fun List<UIMessagePart>.hasVisibleChatContent(): Boolean =
    !isEmptyUIMessage() || any { part ->
        part is UIMessagePart.Tool && part.inlineTtsRequestText() != null
    }

/**
 * Promotes the character's text_to_speech tool call into the visible content stream.
 *
 * The model can call the tool before it writes its final text. We therefore match the requested
 * voice sentence against the later text bubbles, replace that one sentence in place, and remove
 * the tool from the reasoning chain. If the model did not repeat the sentence, the voice bubble
 * remains at the original tool position as a safe fallback.
 */
internal fun List<UIMessagePart>.withInlineTtsVoiceMessages(): List<UIMessagePart> {
    val requests = mapIndexedNotNull { index, part ->
        val tool = part as? UIMessagePart.Tool ?: return@mapIndexedNotNull null
        val rawText = tool.inlineTtsRequestText() ?: return@mapIndexedNotNull null
        InlineVoiceRequest(
            partIndex = index,
            id = tool.toolCallId.ifBlank { "tts-$index" },
            rawText = rawText,
            bubbleText = rawText.toSingleVoiceBubbleText(),
        )
    }
    if (requests.isEmpty()) return this

    val remaining = requests.toMutableList()
    val matches = mutableMapOf<Pair<Int, Int>, InlineVoiceRequest>()

    forEachIndexed { partIndex, part ->
        if (part !is UIMessagePart.Text) return@forEachIndexed
        part.text.toVoiceCandidateSegments().forEachIndexed { segmentIndex, segment ->
            val requestIndex = remaining.indexOfFirst { request ->
                request.matchesVisibleSegment(segment)
            }
            if (requestIndex >= 0) {
                matches[partIndex to segmentIndex] = remaining.removeAt(requestIndex)
            }
        }
    }

    val matchedIds = matches.values.mapTo(mutableSetOf()) { it.id }
    val requestsByPart = requests.associateBy(InlineVoiceRequest::partIndex)
    val result = mutableListOf<UIMessagePart>()

    forEachIndexed { partIndex, part ->
        when {
            part is UIMessagePart.Tool && part.toolName == TTS_TOOL_NAME -> {
                val request = requestsByPart[partIndex]
                if (request != null && request.id !in matchedIds) {
                    result += inlineVoiceTextPart(
                        text = request.bubbleText,
                        sourceMetadata = part.metadata,
                    )
                }
            }

            part is UIMessagePart.Text -> {
                part.text.toVoiceCandidateSegments().forEachIndexed { segmentIndex, segment ->
                    val request = matches[partIndex to segmentIndex]
                    result += if (request != null) {
                        inlineVoiceTextPart(
                            text = segment.trim(),
                            sourceMetadata = part.metadata,
                        )
                    } else {
                        part.copy(text = segment.trim())
                    }
                }
            }

            else -> result += part
        }
    }

    return result
}

internal fun UIMessagePart.Text.inlineTtsVoiceText(): String? =
    metadata
        ?.get(INLINE_TTS_TEXT_KEY)
        ?.jsonPrimitive
        ?.contentOrNull
        ?.trim()
        ?.takeIf(String::isNotBlank)

private fun UIMessagePart.Tool.inlineTtsRequestText(): String? {
    if (toolName != TTS_TOOL_NAME) return null
    return runCatching {
        JsonInstant.parseToJsonElement(input)
            .jsonObject["text"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }.getOrNull()
}

private fun inlineVoiceTextPart(
    text: String,
    sourceMetadata: kotlinx.serialization.json.JsonObject?,
): UIMessagePart.Text = UIMessagePart.Text(
    text = "",
    metadata = buildJsonObject {
        sourceMetadata?.forEach { (key, value) -> put(key, value) }
        put(INLINE_TTS_TEXT_KEY, text)
    },
)

private fun String.toVoiceCandidateSegments(): List<String> {
    val clean = trim()
    if (clean.isBlank()) return emptyList()
    if (clean.contains("```") || clean.contains("\n- ") || clean.contains("\n1. ")) {
        return listOf(clean)
    }
    return clean
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split(INLINE_VOICE_SENTENCE_BOUNDARY_REGEX)
        .map(String::trim)
        .filter(String::isNotBlank)
        .ifEmpty { listOf(clean) }
}

private fun String.toSingleVoiceBubbleText(): String =
    toVoiceCandidateSegments().firstOrNull().orEmpty().ifBlank { trim() }

private fun InlineVoiceRequest.matchesVisibleSegment(segment: String): Boolean {
    val rawKey = rawText.inlineVoiceMatchKey()
    val bubbleKey = bubbleText.inlineVoiceMatchKey()
    val segmentKey = segment.inlineVoiceMatchKey()
    if (segmentKey.isBlank()) return false
    return segmentKey == bubbleKey ||
        segmentKey == rawKey ||
        (segmentKey.length >= 2 && rawKey.contains(segmentKey))
}

private fun String.inlineVoiceMatchKey(): String =
    lowercase().replace(INLINE_VOICE_MATCH_IGNORED_REGEX, "")

@Composable
internal fun InlineTtsVoiceMessageBubble(text: String) {
    val ttsState = LocalTTSState.current
    val playbackState by ttsState.playbackState.collectAsState()
    val isSpeaking by ttsState.isSpeaking.collectAsState()
    val eventBus: AppEventBus = koinInject()
    val scope = rememberCoroutineScope()
    var ownsPlayback by remember(text) { mutableStateOf(false) }

    LaunchedEffect(isSpeaking) {
        if (!isSpeaking) ownsPlayback = false
    }

    val isPlaying = ownsPlayback && playbackState.status == PlaybackStatus.Playing
    val progress = if (ownsPlayback && playbackState.durationMs > 0) {
        playbackState.positionMs.toFloat() / playbackState.durationMs
    } else {
        0f
    }
    val estimatedSeconds = (text.length / 5).coerceAtLeast(1)
    val remainingSeconds = if (ownsPlayback && playbackState.durationMs > 0) {
        ((playbackState.durationMs - playbackState.positionMs) / 1000).toInt().coerceAtLeast(0)
    } else {
        estimatedSeconds
    }
    val waveformBars = remember(text) {
        val random = java.util.Random(text.hashCode().toLong())
        List(28) { 0.2f + random.nextFloat() * 0.8f }
    }
    val voiceWidth = (140 + text.length.coerceAtMost(32) * 3).dp

    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 140.dp, max = 240.dp)
                .width(voiceWidth.coerceAtMost(240.dp)),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            onClick = {
                when {
                    isPlaying -> ttsState.pause()
                    ownsPlayback && isSpeaking -> ttsState.resume()
                    else -> {
                        ownsPlayback = true
                        scope.launch { eventBus.emit(AppEvent.Speak(text)) }
                    }
                }
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) HugeIcons.PauseCircle else HugeIcons.PlayCircle,
                        contentDescription = if (isPlaying) "暂停语音" else "播放语音",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp),
                ) {
                    val barWidth = 2.4f
                    val gap = (size.width - barWidth * waveformBars.size) /
                        (waveformBars.size - 1).coerceAtLeast(1)
                    val playedBars = (progress * waveformBars.size).toInt()
                    waveformBars.forEachIndexed { index, ratio ->
                        val barHeight = size.height * ratio.coerceIn(0.2f, 1f)
                        val x = index * (barWidth + gap)
                        val y = (size.height - barHeight) / 2f
                        drawRoundRect(
                            color = if (index < playedBars) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
                            },
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(1.2f, 1.2f),
                        )
                    }
                }

                Text(
                    text = String.format("%d:%02d", remainingSeconds / 60, remainingSeconds % 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.End,
                )
            }
        }

        Text(
            text = text,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
        )
    }
}
