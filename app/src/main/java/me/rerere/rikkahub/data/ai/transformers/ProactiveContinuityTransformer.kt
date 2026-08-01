package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

private const val PROACTIVE_CONTEXT_MARKER = "[主动消息上下文]"
private const val MAX_CONTINUITY_TURNS = 8
private const val MAX_TURN_PREVIEW_CHARS = 220

/**
 * Makes a proactive turn behave like the next beat of the same conversation rather than a fresh
 * notification template. The transformer activates only for the internal proactive context request,
 * so ordinary user chat remains untouched.
 */
object ProactiveContinuityTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = injectProactiveContinuity(messages)
}

internal fun injectProactiveContinuity(messages: List<UIMessage>): List<UIMessage> {
    val requestIndex = messages.indexOfLast { message ->
        message.role == MessageRole.USER && message.visibleText().contains(PROACTIVE_CONTEXT_MARKER)
    }
    if (requestIndex < 0) return messages

    val history = messages
        .take(requestIndex)
        .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
        .filter { it.visibleText().isNotBlank() }

    val lastAssistantIndex = history.indexOfLast { it.role == MessageRole.ASSISTANT }
    val userRepliedAfterLastAssistant = lastAssistantIndex >= 0 &&
        history.drop(lastAssistantIndex + 1).any { it.role == MessageRole.USER }
    val lastSpeaker = history.lastOrNull()?.role

    val continuity = UIMessage.system(
        buildString {
            appendLine("<proactive_conversation_continuity>")
            appendLine("This proactive turn is the next beat of the same ongoing relationship and chat, not a fresh greeting or a restarted task.")
            when {
                history.isEmpty() -> appendLine("There is no usable recent dialogue. Start naturally from the current situation and persona without inventing a repeated check-in.")
                lastSpeaker == MessageRole.ASSISTANT && !userRepliedAfterLastAssistant -> {
                    appendLine("The assistant spoke last and the user has not replied yet.")
                    appendLine("A new message is allowed, but it must be a genuine continuation: add new information, a new action, a changed feeling, a playful aside, or a natural topic transition. Do not ask the same question again and do not restate the same attitude in different words.")
                }
                lastSpeaker == MessageRole.USER -> {
                    appendLine("The user spoke last. Respond to that newest user turn and everything that followed from it; do not fall back to an older unfinished topic unless the current context truly requires it.")
                }
                else -> appendLine("Continue from the newest meaningful turn rather than reopening an earlier line.")
            }
            appendLine("Before speaking, silently choose exactly one relationship to the recent conversation: CONTINUE, ADVANCE, NATURAL_TRANSITION, or PASS.")
            appendLine("CONTINUE must directly pick up the latest beat. ADVANCE must change the situation or add something new. NATURAL_TRANSITION must bridge naturally instead of abruptly resetting. PASS is required when there is no new conversational value.")
            appendLine("Never output those labels. Never reproduce an unanswered question merely because another scheduler reason mentions the same topic.")
            appendLine("Recent visible turns, oldest to newest:")
            history.takeLast(MAX_CONTINUITY_TURNS).forEach { message ->
                val speaker = if (message.role == MessageRole.USER) "USER" else "ASSISTANT"
                appendLine("- $speaker: ${message.visibleText().compactPreview()}")
            }
            append("</proactive_conversation_continuity>")
        },
    )

    return messages.take(requestIndex) + continuity + messages.drop(requestIndex)
}

private fun UIMessage.visibleText(): String = parts
    .filterIsInstance<UIMessagePart.Text>()
    .joinToString("\n") { it.text }
    .replace(Regex("<lulu_presence>[\\s\\S]*?</lulu_presence>"), "")
    .trim()

private fun String.compactPreview(): String = lineSequence()
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .joinToString(" ")
    .replace(Regex("\\s+"), " ")
    .take(MAX_TURN_PREVIEW_CHARS)
