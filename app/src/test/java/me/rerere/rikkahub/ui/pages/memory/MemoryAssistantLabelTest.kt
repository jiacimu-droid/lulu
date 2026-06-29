package me.rerere.rikkahub.ui.pages.memory

import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryAssistantLabelTest {
    @Test
    fun `uses assistant name for known memory assistant ids`() {
        val assistantId = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val labels = buildMemoryAssistantLabels(
            assistantIds = listOf(assistantId.toString()),
            assistants = listOf(Assistant(id = assistantId, name = "露露")),
        )

        assertEquals("露露", labels.getValue(assistantId.toString()))
    }

    @Test
    fun `falls back to short id when assistant is missing or unnamed`() {
        val assistantId = "22222222-2222-2222-2222-222222222222"
        val labels = buildMemoryAssistantLabels(
            assistantIds = listOf(assistantId),
            assistants = emptyList(),
        )

        assertEquals("22222222", labels.getValue(assistantId))
    }
}
