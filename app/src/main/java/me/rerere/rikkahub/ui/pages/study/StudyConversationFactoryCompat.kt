package me.rerere.rikkahub.ui.pages.study

import me.rerere.rikkahub.data.model.Conversation
import kotlin.uuid.Uuid

/** Keeps the Pomodoro call site explicit without treating the Boolean as a message list. */
internal fun Conversation.Companion.ofId(
    id: Uuid,
    assistantId: Uuid,
    newConversation: Boolean,
): Conversation = Conversation.ofId(
    id = id,
    assistantId = assistantId,
    newConversation = newConversation,
)
