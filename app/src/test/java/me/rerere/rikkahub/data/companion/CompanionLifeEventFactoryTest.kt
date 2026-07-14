package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CompanionLifeEventFactoryTest {
    @Test
    fun `same evidence creates the same life event identity`() {
        val first = buildConversationLifeEvent(
            assistantId = "assistant-a",
            assistantText = "我记住了。",
            source = CompanionLifeEventSource.CHAT,
            evidenceReference = "message-1",
            nowMillis = 100L,
        )
        val duplicate = buildConversationLifeEvent(
            assistantId = "assistant-a",
            assistantText = "我记住了。",
            source = CompanionLifeEventSource.CHAT,
            evidenceReference = "message-1",
            nowMillis = 200L,
        )
        val other = buildConversationLifeEvent(
            assistantId = "assistant-a",
            assistantText = "我记住了。",
            source = CompanionLifeEventSource.CHAT,
            evidenceReference = "message-2",
            nowMillis = 200L,
        )

        assertEquals(first?.id, duplicate?.id)
        assertNotEquals(first?.id, other?.id)
    }

    @Test
    fun `failed tool output cannot become a completed life event`() {
        val event = buildToolLifeEvent(
            assistantId = "assistant-a",
            execution = CompanionToolExecution(
                toolCallId = "tool-1",
                toolName = "control_music",
                inputJson = "{}",
                outputText = "操作失败：没有播放权限",
            ),
            source = CompanionLifeEventSource.TOOL,
            nowMillis = 100L,
        )

        assertEquals(CompanionLifeEventStatus.FAILED, event?.status)
        assertEquals(CompanionLifeEventType.MUSIC, event?.type)
    }
}
