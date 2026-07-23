package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.scheduleInlineVoiceMessage
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.core.context.GlobalContext
import java.util.concurrent.ConcurrentHashMap

private const val TTS_TOOL_NAME = "text_to_speech"
private const val MAX_SYNTHESIZED_VOICE_CACHE = 64

private val synthesizedVoiceCache = ConcurrentHashMap<String, UIMessagePart.VoiceMessage>()

private val INLINE_VOICE_SENTENCE_BOUNDARY_REGEX =
    Regex("(?<=[.!?~～。！？])\\s*|(?<=…)(?=\\s|$)\\s*|\\n+")

private val INLINE_VOICE_MATCH_IGNORED_REGEX =
    Regex("[\\s\\p{Punct}，。！？；：、…“”‘’（）【】《》—～]+")

private data class InlineVoiceRequest(
    val partIndex: Int,
    val requestedText: String,
    val voiceMessage: UIMessagePart.VoiceMessage,
)

/**
 * Compatibility entry used by the output transformer during visual and final passes.
 * A cache prevents the same completed tool call from scheduling TTS more than once.
 */
internal suspend fun promoteInlineVoiceMessages(messages: List<UIMessage>): List<UIMessage> {
    val context = GlobalContext.get().get<Context>()
    return materializeAndPromoteInlineVoiceMessages(context, messages)
}

internal suspend fun materializeAndPromoteInlineVoiceMessages(
    ctx: TransformerContext,
    messages: List<UIMessage>,
): List<UIMessage> = materializeAndPromoteInlineVoiceMessages(ctx.context, messages)

private fun materializeAndPromoteInlineVoiceMessages(
    context: Context,
    messages: List<UIMessage>,
): List<UIMessage> = messages.map { message ->
    if (message.role == MessageRole.ASSISTANT) {
        message.copy(parts = message.parts.materializeAndPromoteInlineVoiceParts(context))
    } else {
        message
    }
}

internal fun List<UIMessagePart>.materializeAndPromoteInlineVoiceParts(
    context: Context,
): List<UIMessagePart> {
    val materializedParts = map { part ->
        val tool = part as? UIMessagePart.Tool ?: return@map part
        if (tool.toolName != TTS_TOOL_NAME || !tool.isExecuted) return@map part
        if (tool.output.any { outputPart ->
                outputPart is UIMessagePart.VoiceMessage && outputPart.url.isNotBlank()
            }) {
            return@map part
        }
        val requestedText = tool.requestedInlineVoiceText()?.takeIf(String::isNotBlank)
            ?: return@map part
        val cacheKey = "${tool.toolCallId}|$requestedText"
        val scheduled = synthesizedVoiceCache[cacheKey]
            ?: scheduleInlineVoiceMessage(context, requestedText).also { voice ->
                synthesizedVoiceCache[cacheKey] = voice
                pruneSynthesizedVoiceCache()
            }
        tool.copy(output = tool.output + scheduled)
    }
    return materializedParts.promoteMaterializedInlineVoiceParts()
}

/** Pure ordering transformation used by regression tests and persisted-message rendering. */
internal fun List<UIMessagePart>.promoteMaterializedInlineVoiceParts(): List<UIMessagePart> {
    val requests = mapIndexedNotNull { index, part ->
        val tool = part as? UIMessagePart.Tool ?: return@mapIndexedNotNull null
        if (tool.toolName != TTS_TOOL_NAME || !tool.isExecuted) return@mapIndexedNotNull null
        val requestedText = tool.requestedInlineVoiceText()?.takeIf(String::isNotBlank)
            ?: return@mapIndexedNotNull null
        val voiceMessage = tool.output
            .filterIsInstance<UIMessagePart.VoiceMessage>()
            .firstOrNull { voice -> voice.url.isNotBlank() }
            ?: return@mapIndexedNotNull null
        InlineVoiceRequest(
            partIndex = index,
            requestedText = requestedText,
            voiceMessage = voiceMessage.copy(
                transcript = voiceMessage.transcript.ifBlank { requestedText },
            ),
        )
    }
    if (requests.isEmpty()) return this

    val remaining = requests.toMutableList()
    val matches = mutableMapOf<Pair<Int, Int>, InlineVoiceRequest>()

    forEachIndexed { partIndex, part ->
        if (part !is UIMessagePart.Text) return@forEachIndexed
        part.text.toInlineVoiceSegments().forEachIndexed { segmentIndex, segment ->
            val requestIndex = remaining.indexOfFirst { request ->
                request.matchesVisibleSegment(segment)
            }
            if (requestIndex >= 0) {
                matches[partIndex to segmentIndex] = remaining.removeAt(requestIndex)
            }
        }
    }

    val requestPartIndexes = requests.mapTo(mutableSetOf()) { it.partIndex }
    val unmatchedByPart = remaining.associateBy(InlineVoiceRequest::partIndex)
    val result = mutableListOf<UIMessagePart>()

    forEachIndexed { partIndex, part ->
        when {
            part is UIMessagePart.Tool && partIndex in requestPartIndexes -> {
                val unmatched = unmatchedByPart[partIndex]
                if (unmatched != null) {
                    result += unmatched.voiceMessage
                }
                // Completed voice tools are正文 content and must not remain in the reasoning card.
            }

            part is UIMessagePart.Text -> {
                part.text.toInlineVoiceSegments().forEachIndexed { segmentIndex, segment ->
                    val request = matches[partIndex to segmentIndex]
                    result += if (request != null) {
                        request.voiceMessage.copy(transcript = segment.trim())
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

private fun pruneSynthesizedVoiceCache() {
    if (synthesizedVoiceCache.size <= MAX_SYNTHESIZED_VOICE_CACHE) return
    synthesizedVoiceCache.keys
        .take(synthesizedVoiceCache.size - MAX_SYNTHESIZED_VOICE_CACHE)
        .forEach { key -> synthesizedVoiceCache.remove(key) }
}

private fun UIMessagePart.Tool.requestedInlineVoiceText(): String? = runCatching {
    JsonInstant.parseToJsonElement(input)
        .jsonObject["text"]
        ?.jsonPrimitive
        ?.contentOrNull
        ?.trim()
}.getOrNull()

private fun String.toInlineVoiceSegments(): List<String> {
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

private fun InlineVoiceRequest.matchesVisibleSegment(segment: String): Boolean {
    val requestKey = requestedText.inlineVoiceMatchKey()
    val transcriptKey = voiceMessage.transcript.inlineVoiceMatchKey()
    val segmentKey = segment.inlineVoiceMatchKey()
    if (segmentKey.isBlank()) return false
    return segmentKey == requestKey ||
        segmentKey == transcriptKey ||
        (segmentKey.length >= 2 && requestKey.contains(segmentKey))
}

private fun String.inlineVoiceMatchKey(): String =
    lowercase().replace(INLINE_VOICE_MATCH_IGNORED_REGEX, "")
