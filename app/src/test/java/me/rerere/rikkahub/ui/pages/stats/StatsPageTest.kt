package me.rerere.rikkahub.ui.pages.stats

import me.rerere.rikkahub.data.ai.ApiUsageRecord
import me.rerere.rikkahub.data.ai.ApiUsageSource
import me.rerere.rikkahub.data.ai.summarizeApiUsage
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsPageTest {
    @Test
    fun `visible cache records should keep only the latest fifteen records`() {
        val records = (0 until 20).map { index ->
            ApiUsageRecord(id = "record-$index", source = ApiUsageSource.CHAT)
        }

        val visibleRecords = records.visibleCacheRecords()

        assertEquals(15, visibleRecords.size)
        assertEquals("record-0", visibleRecords.first().id)
        assertEquals("record-14", visibleRecords.last().id)
    }

    @Test
    fun `cache record key should use persisted api usage id`() {
        val record = ApiUsageRecord(id = "usage-1", source = ApiUsageSource.GAME)

        assertEquals("usage-1", record.stableCacheRecordKey())
    }

    @Test
    fun `api usage summary should keep chat phone and game separate`() {
        val summaries = listOf(
            ApiUsageRecord(source = ApiUsageSource.CHAT, promptTokens = 100L, cachedTokens = 20L),
            ApiUsageRecord(source = ApiUsageSource.PHONE, promptTokens = 50L, cachedTokens = 10L),
            ApiUsageRecord(source = ApiUsageSource.GAME, promptTokens = 30L, cachedTokens = 3L),
            ApiUsageRecord(source = ApiUsageSource.CHAT, promptTokens = 100L, cachedTokens = 40L),
        ).summarizeApiUsage()

        assertEquals(3, summaries.size)
        assertEquals(2, summaries.first { it.source == ApiUsageSource.CHAT }.callCount)
        assertEquals(60L, summaries.first { it.source == ApiUsageSource.CHAT }.cachedTokens)
        assertEquals(10L, summaries.first { it.source == ApiUsageSource.PHONE }.cachedTokens)
        assertEquals(3L, summaries.first { it.source == ApiUsageSource.GAME }.cachedTokens)
    }
}
