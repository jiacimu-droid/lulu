package me.rerere.rikkahub.data.voicecall

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCallRepositorySummaryTest {
    @Test
    fun `summarizes persisted voice call sessions`() {
        val sessions = listOf(
            VoiceCallSession(
                id = "call-1",
                conversationId = "conversation-1",
                assistantId = "assistant-1",
                assistantName = "露露",
                startedAt = 1L,
                transcript = listOf(
                    VoiceCallLine(role = VoiceCallRole.Assistant, text = "喂"),
                    VoiceCallLine(role = VoiceCallRole.User, text = "在吗"),
                ),
            ),
            VoiceCallSession(
                id = "call-2",
                conversationId = "conversation-1",
                assistantId = "assistant-1",
                assistantName = "露露",
                startedAt = 2L,
                transcript = listOf(
                    VoiceCallLine(role = VoiceCallRole.System, text = "系统记录"),
                ),
            ),
        )

        val summary = summarizeVoiceCallSessions(sessions)

        assertEquals(2, summary.sessionCount)
        assertEquals(2, summary.visibleLineCount)
    }
}
