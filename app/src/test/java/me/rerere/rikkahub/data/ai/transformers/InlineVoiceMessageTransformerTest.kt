package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InlineVoiceMessageTransformerTest {
    @Test
    fun `selected middle sentence becomes one voice bubble without duplicate text`() {
        val parts = listOf(
            UIMessagePart.Tool(
                toolCallId = "voice-1",
                toolName = "text_to_speech",
                input = """{"text":"我爱你宝宝。"}""",
                output = listOf(
                    UIMessagePart.VoiceMessage(
                        url = "/tmp/voice-1.mp3",
                        duration = 2_000,
                        transcript = "我爱你宝宝。",
                    ),
                ),
            ),
            UIMessagePart.Text("今天这么开心呀。我爱你宝宝。好好休息吧。"),
        )

        val visible = parts.promoteMaterializedInlineVoiceParts()

        assertEquals(
            listOf(
                "text:今天这么开心呀。",
                "voice:我爱你宝宝。",
                "text:好好休息吧。",
            ),
            visible.toVisibleLabels(),
        )
        assertFalse(visible.any { part ->
            part is UIMessagePart.Tool && part.toolName == "text_to_speech"
        })
    }

    @Test
    fun `voice remains at tool position when provider does not repeat its transcript`() {
        val parts = listOf(
            UIMessagePart.Text("今天这么开心呀。"),
            UIMessagePart.Tool(
                toolCallId = "voice-2",
                toolName = "text_to_speech",
                input = """{"text":"我爱你宝宝。"}""",
                output = listOf(
                    UIMessagePart.VoiceMessage(
                        url = "/tmp/voice-2.mp3",
                        duration = 2_000,
                        transcript = "我爱你宝宝。",
                    ),
                ),
            ),
            UIMessagePart.Text("好好休息吧。"),
        )

        assertEquals(
            listOf(
                "text:今天这么开心呀。",
                "voice:我爱你宝宝。",
                "text:好好休息吧。",
            ),
            parts.promoteMaterializedInlineVoiceParts().toVisibleLabels(),
        )
    }

    private fun List<UIMessagePart>.toVisibleLabels(): List<String> = mapNotNull { part ->
        when (part) {
            is UIMessagePart.Text -> "text:${part.text}"
            is UIMessagePart.VoiceMessage -> "voice:${part.transcript}"
            else -> null
        }
    }
}
