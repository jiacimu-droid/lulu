package me.rerere.rikkahub.ui.pages.stats

import me.rerere.rikkahub.data.db.dao.MessageCacheRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StatsPageTest {
    @Test
    fun `visible cache records should keep only the latest fifteen records`() {
        val records = (0 until 20).map { index ->
            MessageCacheRecord(nodeId = "node-$index")
        }

        val visibleRecords = records.visibleCacheRecords()

        assertEquals(15, visibleRecords.size)
        assertEquals("node-0", visibleRecords.first().nodeId)
        assertEquals("node-14", visibleRecords.last().nodeId)
    }

    @Test
    fun `cache record key should distinguish records with duplicate message fallback fields`() {
        val first = MessageCacheRecord(
            conversationId = "conversation-1",
            messageId = "",
            createdAt = "2026-06-29T10:00:00",
            nodeId = "node-1",
            messageIndex = 0,
        )
        val second = first.copy(messageIndex = 1)

        assertNotEquals(first.stableCacheRecordKey(), second.stableCacheRecordKey())
    }

    @Test
    fun `cache record key should distinguish records with duplicate persisted message ids`() {
        val first = MessageCacheRecord(
            conversationId = "conversation-1",
            messageId = "message-1",
            createdAt = "2026-06-29T10:00:00",
            nodeId = "node-1",
            messageIndex = 0,
        )
        val second = first.copy(nodeId = "node-2")

        assertNotEquals(first.stableCacheRecordKey(), second.stableCacheRecordKey())
    }

    @Test
    fun `cache record key should fall back to message id when row identity is unavailable`() {
        val record = MessageCacheRecord(
            conversationId = "conversation-1",
            messageId = "message-1",
            createdAt = "2026-06-29T10:00:00",
        )

        assertEquals("message-1", record.stableCacheRecordKey())
    }
}
