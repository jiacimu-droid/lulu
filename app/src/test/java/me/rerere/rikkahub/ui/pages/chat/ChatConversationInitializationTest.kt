package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class ChatConversationInitializationTest {
    @Test
    fun `edit started from empty placeholder preserves history loaded afterward`() {
        val conversationId = Uuid.random()
        val assistantId = Uuid.random()
        val placeholder = Conversation.ofId(conversationId, assistantId)
        val editedPlaceholder = placeholder.copy(customSystemPrompt = "只在本次聊天使用的设定")
        val loaded = placeholder.copy(
            title = "重要聊天",
            messageNodes = listOf(
                UIMessage.user("旧消息一").toMessageNode(),
                UIMessage(
                    role = MessageRole.ASSISTANT,
                    parts = listOf(UIMessagePart.Text("旧回复一")),
                ).toMessageNode(),
            ),
        )

        val merged = mergeConversationEdit(
            base = placeholder,
            edited = editedPlaceholder,
            current = loaded,
        )

        assertEquals(loaded.messageNodes, merged.messageNodes)
        assertEquals("重要聊天", merged.title)
        assertEquals("只在本次聊天使用的设定", merged.customSystemPrompt)
    }

    @Test
    fun `intentional message mutation after loading is still applied`() {
        val conversationId = Uuid.random()
        val assistantId = Uuid.random()
        val loaded = Conversation.ofId(
            id = conversationId,
            assistantId = assistantId,
            messages = listOf(UIMessage.user("原消息").toMessageNode()),
        )
        val edited = loaded.copy(messageNodes = emptyList())

        val merged = mergeConversationEdit(
            base = loaded,
            edited = edited,
            current = loaded,
        )

        assertEquals(0, merged.messageNodes.size)
        assertNull(merged.customSystemPrompt)
    }
}
