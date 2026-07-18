package me.rerere.rikkahub.ui.components.message

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.rikkahub.data.ai.transformers.LULU_PRESENCE_METADATA_TYPE
import org.junit.Assert.assertEquals
import org.junit.Test

class LuluBubbleTimingTest {
    @Test
    fun `fresh assistant text is visible before speech playback finishes`() {
        assertEquals(
            1,
            initialVisibleAssistantBubbleCount(
                segmentCount = 3,
                animate = true,
                isPresegmented = false,
            ),
        )
        assertEquals(
            3,
            initialVisibleAssistantBubbleCount(
                segmentCount = 3,
                animate = true,
                isPresegmented = true,
            ),
        )
    }

    @Test
    fun `pacing metadata still controls later segment intervals`() {
        val slow = listOf(
            UIMessageAnnotation.Metadata(
                type = LULU_PRESENCE_METADATA_TYPE,
                data = buildJsonObject { put("bubble_pacing", "slow") },
            ),
        )

        assertEquals(360L, slow.luluBubblePacingDelayMillis())
        assertEquals(180L, emptyList<UIMessageAnnotation>().luluBubblePacingDelayMillis())
    }
}
