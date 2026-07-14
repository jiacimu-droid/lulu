package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.data.companion.CompanionState
import org.junit.Assert.assertEquals
import org.junit.Test

class LuluStatusDialogTest {
    @Test
    fun `current action and unspoken thought render as separate sections`() {
        val sections = buildCurrentStatusSections(
            CompanionState(
                selfScene = "偏着头看向屏幕，嘴角轻轻扬了一下。",
                innerThought = "她肯好好吃饭就行，我先不念她。",
            ),
        )

        assertEquals(
            listOf(
                "此刻" to "偏着头看向屏幕，嘴角轻轻扬了一下。",
                "没说出口" to "她肯好好吃饭就行，我先不念她。",
            ),
            sections,
        )
    }
}
