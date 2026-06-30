package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class TTSAutoPlayTest {
    @Test
    fun `selects newest assistant message when no spoken marker exists`() {
        val first = assistantMessage("first")
        val second = assistantMessage("second")

        assertEquals(
            second.id,
            findAutoPlayTTSMessage(
                nodes = listOf(MessageNode.of(first), MessageNode.of(second)),
                lastSpokenMessageId = null,
            )?.id,
        )
    }

    @Test
    fun `selects next assistant message after last spoken one`() {
        val first = assistantMessage("first")
        val second = assistantMessage("second")
        val third = assistantMessage("third")

        assertEquals(
            second.id,
            findAutoPlayTTSMessage(
                nodes = listOf(MessageNode.of(first), MessageNode.of(second), MessageNode.of(third)),
                lastSpokenMessageId = first.id,
            )?.id,
        )
    }

    @Test
    fun `does not replay the same assistant message`() {
        val message = assistantMessage("already spoken")

        assertNull(
            findAutoPlayTTSMessage(
                nodes = listOf(MessageNode.of(message)),
                lastSpokenMessageId = message.id,
            ),
        )
    }

    private fun assistantMessage(text: String): UIMessage =
        UIMessage.assistant(text).copy(
            finishedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        )
}
