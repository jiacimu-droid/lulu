package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeReminderTransformerTest {

    private val timeZone = TimeZone.currentSystemDefault()

    private fun instantOf(dateTime: LocalDateTime) = dateTime.toInstant(timeZone)

    private fun userMessage(text: String, createdAt: LocalDateTime) = UIMessage(
        role = MessageRole.USER,
        parts = listOf(UIMessagePart.Text(text)),
        createdAt = createdAt,
    )

    private fun assistantMessage(text: String, createdAt: LocalDateTime) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text(text)),
        createdAt = createdAt,
    )

    private fun getMessageText(msg: UIMessage): String =
        msg.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    @Test
    fun `single user message should inject current request time`() {
        val messages = listOf(userMessage("Hello", LocalDateTime(2026, 2, 22, 10, 0, 0)))
        val result = applyTimeReminder(
            messages = messages,
            requestInstant = instantOf(LocalDateTime(2026, 2, 22, 10, 5, 0))
        )

        assertEquals(2, result.size)
        assertTrue(getMessageText(result[0]).contains("Current real time for this request"))
        assertEquals("Hello", getMessageText(result[1]))
    }

    @Test
    fun `last user message should always receive current request time even with short gap`() {
        val messages = listOf(
            userMessage("Hello", LocalDateTime(2026, 2, 22, 10, 0, 0)),
            assistantMessage("Hi", LocalDateTime(2026, 2, 22, 10, 1, 0)),
            userMessage("World", LocalDateTime(2026, 2, 22, 10, 30, 0)),
        )
        val result = applyTimeReminder(
            messages = messages,
            requestInstant = instantOf(LocalDateTime(2026, 2, 22, 10, 30, 5))
        )

        assertEquals(4, result.size)
        assertTrue(getMessageText(result[2]).contains("Current real time for this request"))
        assertEquals("World", getMessageText(result[3]))
    }

    @Test
    fun `gap exactly 1 hour between user messages should be reported`() {
        val messages = listOf(
            userMessage("One o'clock", LocalDateTime(2026, 2, 22, 1, 0, 0)),
            assistantMessage("Reply", LocalDateTime(2026, 2, 22, 1, 1, 0)),
            userMessage("Two o'clock", LocalDateTime(2026, 2, 22, 2, 0, 0)),
        )
        val result = applyTimeReminder(
            messages = messages,
            requestInstant = instantOf(LocalDateTime(2026, 2, 22, 2, 0, 3))
        )

        val injected = getMessageText(result[2])
        assertTrue(injected.contains("Current real time for this request"))
        assertTrue(injected.contains("1 h since last user message"))
        assertEquals("Two o'clock", getMessageText(result[3]))
    }

    @Test
    fun `historical large gaps should inject reminders before non-last user messages`() {
        val messages = listOf(
            userMessage("Msg 1", LocalDateTime(2026, 2, 20, 10, 0, 0)),
            userMessage("Msg 2", LocalDateTime(2026, 2, 21, 10, 0, 0)),
            userMessage("Msg 3", LocalDateTime(2026, 2, 22, 10, 0, 0)),
        )
        val result = applyTimeReminder(
            messages = messages,
            requestInstant = instantOf(LocalDateTime(2026, 2, 22, 10, 0, 5))
        )

        assertEquals(5, result.size)
        assertEquals("Msg 1", getMessageText(result[0]))
        assertTrue(getMessageText(result[1]).contains("<time_reminder>"))
        assertTrue(getMessageText(result[1]).contains("1 d since last user message"))
        assertEquals("Msg 2", getMessageText(result[2]))
        assertTrue(getMessageText(result[3]).contains("Current real time for this request"))
        assertTrue(getMessageText(result[3]).contains("1 d since last user message"))
        assertEquals("Msg 3", getMessageText(result[4]))
    }

    @Test
    fun `current request time is injected even when historical gaps are disabled`() {
        val messages = listOf(
            userMessage("Msg 1", LocalDateTime(2026, 2, 20, 10, 0, 0)),
            userMessage("Msg 2", LocalDateTime(2026, 2, 22, 10, 0, 0)),
        )
        val result = applyTimeReminder(
            messages = messages,
            requestInstant = instantOf(LocalDateTime(2026, 2, 22, 10, 0, 5)),
            includeHistoricalGaps = false,
        )

        assertEquals(3, result.size)
        assertEquals("Msg 1", getMessageText(result[0]))
        assertTrue(getMessageText(result[1]).contains("Current real time for this request"))
        assertEquals("Msg 2", getMessageText(result[2]))
    }

    @Test
    fun `empty messages should return empty`() {
        val result = applyTimeReminder(
            messages = emptyList(),
            requestInstant = instantOf(LocalDateTime(2026, 2, 22, 10, 0, 0))
        )
        assertEquals(0, result.size)
    }
}
