package me.rerere.rikkahub.data.ai.transformers

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.synthesizeInlineVoiceMessage
import me.rerere.rikkahub.utils.JsonInstant

private const val TTS_TOOL_NAME = "text_to_speech"

private val INLINE_VOICE_SENTENCE_BOUNDARY_REGEX =
    Regex("(?<=[.!?~～。！？])\\s*|(?<=…)(?=\\s|$)\\s*|\\n+")

private val INLINE_VOICE_MATCH_IGNORED_REGEX =
    Regex("[\\s\\p{Punct}，。！？；：、…“”‘’（）【】《》—～]+")

private data class InlineVoiceRequest(
    val partIndex: Int,
    val id: String,
    val requestedText: String,
    val voiceMessage: UIMessagePart.VoiceMessage,
)

/**
 * Materialize completed text-to-speech tools and move them from the reasoning chain into正文.
 *
 * A requested spoken sentence replaces its matching text sentence at the same conversational
 * position. If the provider omits that sentence from final text, the voice message remains at the
 * original tool position instead of disappearing.
 */
internal suspend fun materializeAndPromoteInlineVoiceMessages(
    ctx: TransformerContext,
    messages: List<UIMessage>,
): List<UIMessage> {
    val result = ArrayList<UIMessage>(messages.size)
    for (message in messages) {
        result += if (message.role == MessageRole.ASSISTANT) {
            message.copy(parts = message.parts.materializeAndPromoteInlineVoiceParts(ctx))
        } else {
            message
        }
    }
    return result
}

internal suspend fun List<UIMessagePart>.materializeAndPromoteInlineVoiceParts(
    ctx: TransformerContext,
): List<UIMessagePart> {
    val requests = mutableListOf<InlineVoiceRequest>()
    forEachIndexed { index, part ->
        val tool = part as? UIMessagePart.Tool ?: return@forEachIndexed
        if (tool.toolName != TTS_TOOL_NAME || !tool.isExecuted) return@forEachIndexed
        val requestedText = tool.requestedInlineVoiceText()?.takeIf(String::isNotBlank)
            ?: return@forEachIndexed
        val voiceMessage = tool.output
            .filterIsInstance<UIMessagePart.VoiceMessage>()
            .firstOrNull { voice -> voice.url.isNotBlank() }
            ?: synthesizeInlineVoiceMessage(ctx.context, requestedText).getOrNull()
            ?: return@forEachIndexed
        requests += InlineVoiceRequest(
            partIndex = index,
            id = tool.toolCallId.ifBlank { "tts-$index" },
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
