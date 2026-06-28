package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LuluStateTest {
    @Test
    fun `missing assistant state returns default companion status`() {
        val assistantId = Uuid.parse("11111111-1111-1111-1111-111111111111")

        val state = emptyList<LuluState>().currentLuluState(assistantId)

        assertEquals(assistantId, state.assistantId)
        assertEquals("在发呆", state.statusText)
        assertEquals("今天也想被好好陪着。", state.innerVoice)
        assertEquals("默认状态", state.reason)
    }

    @Test
    fun `history is filtered by assistant and ordered newest first`() {
        val targetAssistant = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val otherAssistant = Uuid.parse("33333333-3333-3333-3333-333333333333")
        val states = listOf(
            LuluState(
                assistantId = targetAssistant,
                statusText = "刚睡醒",
                updatedAt = 10L,
            ),
            LuluState(
                assistantId = otherAssistant,
                statusText = "不该出现",
                updatedAt = 30L,
            ),
            LuluState(
                assistantId = targetAssistant,
                statusText = "在想你",
                updatedAt = 20L,
            ),
        )

        val history = states.luluStateHistory(targetAssistant)

        assertEquals(listOf("在想你", "刚睡醒"), history.map { it.statusText })
        assertTrue(history.all { it.assistantId == targetAssistant })
        assertEquals("在想你", states.currentLuluState(targetAssistant).statusText)
    }
}
