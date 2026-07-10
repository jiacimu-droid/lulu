package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CompanionScheduledToolEventTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 7, 10, 1, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `successful alarm creates a high priority reminder at target`() {
        val draft = buildScheduledToolFollowUp(
            execution = CompanionToolExecution(
                toolCallId = "alarm-call",
                toolName = "set_alarm",
                inputJson = """{"hour":7,"minute":30,"label":"起床"}""",
                outputText = """{"success":true,"alarm_time":"07:30","label":"起床"}""",
            ),
            assistantId = "assistant-a",
            conversationId = "conversation-a",
            sourceMessageId = "user-a",
            nowMillis = now,
            zoneId = zone,
        )

        assertNotNull(draft)
        val target = ZonedDateTime.of(2026, 7, 10, 7, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(target, draft!!.dueAt)
        assertEquals(5, draft.importance)
        assertEquals(CompanionActionType.REMINDER, draft.actionType)
        assertTrue(draft.reason.contains("07:30"))
        assertTrue(draft.reason.contains("起床"))
    }

    @Test
    fun `successful calendar create uses event start and ignores reads`() {
        val start = now + 6 * 60 * 60_000L
        val created = buildScheduledToolFollowUp(
            execution = CompanionToolExecution(
                toolCallId = "calendar-call",
                toolName = "calendar_tool",
                inputJson = """{"action":"create","title":"面试","start_time_ms":$start,"end_time_ms":${start + 3_600_000L}}""",
                outputText = """{"success":true,"action":"create","event_id":12}""",
            ),
            assistantId = "assistant-a",
            conversationId = "conversation-a",
            sourceMessageId = "user-a",
            nowMillis = now,
            zoneId = zone,
        )
        val read = buildScheduledToolFollowUp(
            execution = CompanionToolExecution(
                toolCallId = "calendar-read",
                toolName = "calendar_tool",
                inputJson = """{"action":"read"}""",
                outputText = """{"success":true,"action":"read","events":[]}""",
            ),
            assistantId = "assistant-a",
            conversationId = "conversation-a",
            sourceMessageId = "user-a",
            nowMillis = now,
            zoneId = zone,
        )

        assertEquals(start, created!!.dueAt)
        assertTrue(created.reason.contains("面试"))
        assertNull(read)
    }

    @Test
    fun `failed tool result never creates companion work`() {
        val draft = buildScheduledToolFollowUp(
            execution = CompanionToolExecution(
                toolCallId = "alarm-call",
                toolName = "set_alarm",
                inputJson = """{"hour":7,"minute":30}""",
                outputText = """{"success":false,"error":"no clock"}""",
            ),
            assistantId = "assistant-a",
            conversationId = "conversation-a",
            sourceMessageId = "user-a",
            nowMillis = now,
            zoneId = zone,
        )

        assertNull(draft)
    }
}
