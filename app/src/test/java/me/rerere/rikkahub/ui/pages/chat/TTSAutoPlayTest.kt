package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TTSAutoPlayTest {
    @Test
    fun `selects newest assistant message once`() {
        val first = UIMessage.assistant("第一句")
        val second = UIMessage.assistant("第二句")

        assertEquals(
            second.id,
            findAutoPlayTTSMessage(
                nodes = listOf(MessageNode.of(first), MessageNode.of(second)),
                lastSpokenMessageId = first.id,
            )?.id,
        )
    }

    @Test
    fun `does not replay the same assistant message`() {
        val message = UIMessage.assistant("已经播过")

        assertNull(
            findAutoPlayTTSMessage(
                nodes = listOf(MessageNode.of(message)),
                lastSpokenMessageId = message.id,
            ),
        )
    }
}
