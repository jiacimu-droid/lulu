package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

object LuluExpressionOutputTransformer : OutputMessageTransformer {
    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = splitLuluAssistantExpressionMessages(messages)
}

internal fun splitLuluAssistantExpressionMessages(messages: List<UIMessage>): List<UIMessage> {
    val last = messages.lastOrNull() ?: return messages
    if (last.role != MessageRole.ASSISTANT) return messages

    val textPart = last.parts.singleOrNull() as? UIMessagePart.Text ?: return messages
    val segments = splitLuluExpressionBubbles(textPart.text)
    if (segments.size <= 1) return messages

    val splitMessages = segments.mapIndexed { index, segment ->
        last.copy(
            id = if (index == 0) last.id else Uuid.random(),
            parts = listOf(textPart.copy(text = segment)),
            usage = if (index == 0) last.usage else null,
            translation = null,
        )
    }
    return messages.dropLast(1) + splitMessages
}

private fun splitLuluExpressionBubbles(text: String): List<String> {
    val clean = text.trim()
    if (clean.isBlank()) return listOf(clean)
    if (clean.contains("```") || clean.contains("\n- ") || clean.contains("\n1. ")) return listOf(clean)

    val paragraphSegments = clean.split(Regex("\\n\\s*\\n+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val roughSegments = if (paragraphSegments.size > 1) {
        paragraphSegments
    } else {
        clean.split(Regex("(?<=[.!?~\\u3002\\uFF01\\uFF1F\\u2026])\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty {
                clean.split(Regex("(?<=[,\\uFF0C\\u3001\\uFF1B;])\\s*"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
    }
    if (roughSegments.size <= 1) return listOf(clean)

    return roughSegments
        .fold(mutableListOf<String>()) { acc, segment ->
            val last = acc.lastOrNull()
            when {
                last == null -> acc += segment
                segment.length < 5 && last.length + segment.length <= 22 -> acc[acc.lastIndex] = "$last$segment"
                acc.size >= 3 -> acc[acc.lastIndex] = "$last$segment"
                else -> acc += segment
            }
            acc
        }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .takeIf { it.size > 1 }
        ?: listOf(clean)
}
