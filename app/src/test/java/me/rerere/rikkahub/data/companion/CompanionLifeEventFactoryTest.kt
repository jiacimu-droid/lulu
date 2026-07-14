package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionLifeEventFactoryTest {
    @Test
    fun `conversation is not duplicated into digital life`() {
        val event = CompanionLifeEvent(
            id = "chat-1",
            assistantId = "assistant-a",
            type = CompanionLifeEventType.CONVERSATION,
            title = "和你聊了一会儿",
            source = CompanionLifeEventSource.CHAT,
            evidenceReference = "message-1",
        )

        assertTrue(!event.isMeaningfulDigitalLifeEvidence())
    }

    @Test
    fun `failed tool output does not enter digital life`() {
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

        assertNull(event)
    }

    @Test
    fun `read only and internal state tools do not enter digital life`() {
        val musicRead = lifeEvent(
            toolName = "control_music",
            input = """{"action":"get_now_playing"}""",
            output = """{"success":true,"title":"song"}""",
        )
        val expressionState = lifeEvent(
            toolName = "record_lulu_expression_state",
            input = """{"mood":"平静"}""",
            output = """{"success":true}""",
        )
        val location = lifeEvent(
            toolName = "get_location",
            input = "{}",
            output = """{"success":true,"address":"某地"}""",
        )

        assertNull(musicRead)
        assertNull(expressionState)
        assertNull(location)
    }

    @Test
    fun `real app action becomes a human readable life event`() {
        val event = lifeEvent(
            toolName = "set_alarm",
            input = """{"hour":7,"minute":30,"label":"起床"}""",
            output = """{"success":true}""",
        )

        assertEquals(CompanionLifeEventType.TOOL_ACTION, event?.type)
        assertEquals("设置了 07:30 的设备提醒：起床", event?.summary)
        assertTrue(event?.isMeaningfulDigitalLifeEvidence() == true)
    }

    private fun lifeEvent(toolName: String, input: String, output: String) = buildToolLifeEvent(
        assistantId = "assistant-a",
        execution = CompanionToolExecution(
            toolCallId = "tool-$toolName",
            toolName = toolName,
            inputJson = input,
            outputText = output,
        ),
        source = CompanionLifeEventSource.TOOL,
        nowMillis = 100L,
    )
}
