package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantInteractionGenerationTest {
    @Test
    fun `parser accepts fenced JSON returned by chat models`() {
        val profile = parseAssistantInteractionProfile(
            """
                下面是结果：
                ```json
                {
                  "initiative": "会主动联系并确认用户状态。",
                  "sharingDesire": "会分享自己的发现和日常。",
                  "responsibility": "认真履行照看与提醒。",
                  "followUpStyle": "未回复时温和追问一次。",
                  "passivity": "用户明确忙碌时保持安静。"
                }
                ```
            """.trimIndent(),
        )

        assertEquals("会主动联系并确认用户状态。", profile.initiative)
        assertEquals("未回复时温和追问一次。", profile.followUpStyle)
        assertEquals("用户明确忙碌时保持安静。", profile.passivity)
    }

    @Test
    fun `parser rejects incomplete generated profile instead of silently guessing`() {
        val result = runCatching {
            parseAssistantInteractionProfile(
                """{"initiative":"会主动联系"}""",
            )
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("sharingDesire"))
    }

    @Test
    fun `generation prompt includes full persona and all five required dimensions`() {
        val prompt = buildInteractionProfilePrompt(
            Assistant(
                name = "阿澈",
                systemPrompt = "外冷内热的私人管家，会照顾用户但不说空话。",
                appearancePrompt = "黑发，气质冷静。",
                messageTemplate = "简短、克制。",
            ),
        )

        assertTrue(prompt.contains("外冷内热的私人管家"))
        assertTrue(prompt.contains("initiative"))
        assertTrue(prompt.contains("sharingDesire"))
        assertTrue(prompt.contains("responsibility"))
        assertTrue(prompt.contains("followUpStyle"))
        assertTrue(prompt.contains("passivity"))
    }
}
