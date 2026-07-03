package me.rerere.rikkahub.data.cihai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CihaiMemoryTest {
    @Test
    fun `journal entry becomes pending vector memory candidate`() {
        val entry = CihaiEntry(
            assistantId = "lulu",
            kind = CihaiEntryKind.INNER_JOURNAL,
            title = "她很久没回我",
            content = "我想再问一句，但她可能正在忙，我先写下来，晚点再判断。",
            emotion = "担心但克制",
            createdAt = 1_700_000_000_000L,
        )

        val memory = entry.toMemoryCandidate().toEntity(
            assistantId = "lulu",
            conversationId = null,
            createdAt = entry.createdAt,
        )

        assertEquals("cihai_journal", memory.memoryKind)
        assertEquals("pending", memory.vectorStatus)
        assertTrue(memory.embeddingText!!.contains("担心但克制"))
        assertTrue(memory.tagsJson!!.contains("辞海"))
    }

    @Test
    fun `reading note keeps source book and reflection in memory text`() {
        val entry = CihaiEntry(
            assistantId = "lulu",
            kind = CihaiEntryKind.READING_NOTE,
            title = "读《亲密关系》第三章",
            content = "看到压力下的人会先冻结，我更明白她卡住时不该只催。",
            sourceTitle = "亲密关系",
            createdAt = 1_700_000_000_000L,
        )

        val candidate = entry.toMemoryCandidate()

        assertEquals("cihai_reading", candidate.type)
        assertTrue(candidate.content.contains("亲密关系"))
        assertTrue(candidate.embeddingText!!.contains("不该只催"))
        assertTrue(candidate.tags.contains("阅读"))
    }
}
