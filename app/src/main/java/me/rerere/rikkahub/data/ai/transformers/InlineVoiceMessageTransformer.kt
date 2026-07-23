package me.rerere.rikkahub.data.ai.transformers

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
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
 * Move completed text-to-speech results out of the tool/reasoning chain and into the visible reply.
 *
 * A requested spoken sentence replaces its matching text sentence at the same conversational
 * position. If a provider does not repeat the requested sentence in the final text, the voice
 * message remains at the original tool position instead of disappearing.
 */
internal fun promoteInlineVoiceMessages(messages: List<UIMessage>): List<UIMessage> =
    messages.map { message ->
        if (message.role != MessageRole.ASSISTANT) {
            message
        } else {
            message.copy(parts = message.parts.promoteInlineVoiceParts())
        }
    }

internal fun List<UIMessagePart>.promoteInlineVoiceParts(): List<UIMessagePart> {
    val requests = mapIndexedNotNull { index, part ->
        val tool = part as? UIMessagePart.Tool ?: return@mapIndexedNotNull null
        val voiceMessage = tool.completedInlineVoiceMessage() ?: return@mapIndexedNotNull null
        val requestedText = tool.requestedInlineVoiceText()
            ?.takeIf(String::isNotBlank)
            ?: voiceMessage.transcript.trim()
        if (requestedText.isBlank()) return@mapIndexedNotNull null
        InlineVoiceRequest(
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

    val unmatchedByPart = remaining.associateBy(InlineVoiceRequest::partIndex)
    val result = mutableListOf<UIMessagePart>()

    forEachIndexed { partIndex, part ->
        when {
            part is UIMessagePart.Tool && part.toolName == TTS_TOOL_NAME -> {
                val unmatched = unmatchedByPart[partIndex]
                if (unmatched != null) {
                    result += unmatched.voiceMessage
                }
                // A completed TTS tool is visible as正文, never as a reasoning/tool step.
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

private fun UIMessagePart.Tool.completedInlineVoiceMessage(): UIMessagePart.VoiceMessage? {
    if (toolName != TTS_TOOL_NAME || !isExecuted) return null
    return output.filterIsInstance<UIMessagePart.VoiceMessage>().firstOrNull { voice ->
        voice.url.isNotBlank()
    }
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
