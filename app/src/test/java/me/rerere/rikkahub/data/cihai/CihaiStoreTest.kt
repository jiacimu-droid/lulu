package me.rerere.rikkahub.data.cihai

import kotlinx.serialization.decodeFromString
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CihaiStoreTest {
    @Test
    fun `formal diary stays in Cihai without entering a memory queue`() {
        val updated = CihaiState().addDiaryEntry(
            diary(id = "diary-1", assistantId = "assistant-a"),
        )

        assertEquals(listOf("diary-1"), updated.entries.map { it.id })
    }

    @Test
    fun `legacy entry kinds are removed during normalization`() {
        val normalized = CihaiState(
            entries = listOf(
                diary(id = "diary", assistantId = "assistant-a"),
                CihaiEntry(
                    id = "reflection",
                    assistantId = "assistant-a",
                    kind = CihaiEntryKind.REFLECTION,
                    title = "旧沉淀",
                    content = "旧版本内容",
                ),
            ),
        ).normalizedCihaiState()

        assertEquals(listOf("diary"), normalized.entries.map { it.id })
    }

    @Test
    fun `legacy settlement fields are ignored when decoding persisted state`() {
        val state = JsonInstant.decodeFromString<CihaiState>(
            """
            {
              "selectedAssistantId": "assistant-a",
              "entries": [{
                "id": "diary",
                "assistantId": "assistant-a",
                "kind": "diary",
                "title": "日记",
                "content": "我记得这一刻。",
                "memoryDisposition": "saved",
                "memorySaved": true
              }],
              "memoryQueue": [{
                "entryId": "diary",
                "assistantId": "assistant-a",
                "enqueuedAt": 1
              }]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("diary"), state.normalizedCihaiState().entries.map { it.id })
    }

    @Test
    fun `non diary entry cannot be added`() {
        val updated = CihaiState().addDiaryEntry(
            CihaiEntry(
                id = "action",
                assistantId = "assistant-a",
                kind = CihaiEntryKind.ACTION_LOG,
                title = "行动",
                content = "不应保存",
            ),
        )

        assertTrue(updated.entries.isEmpty())
    }

    @Test
    fun `clearing one assistant leaves other diaries intact`() {
        val state = CihaiState(
            entries = listOf(
                diary(id = "a", assistantId = "assistant-a"),
                diary(id = "b", assistantId = "assistant-b"),
            ),
        )

        val cleared = state.withoutAssistantRecords("assistant-a")

        assertEquals(listOf("b"), cleared.entries.map { it.id })
    }

    private fun diary(id: String, assistantId: String): CihaiEntry = CihaiEntry(
        id = id,
        assistantId = assistantId,
        kind = CihaiEntryKind.DIARY,
        title = "日记",
        content = "我记下这一刻真实发生的感受。",
        createdAt = 1L,
    )
}
