package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryBankContextTest {
    @Test
    fun `build memory context groups memory types for natural recall`() {
        val memories = listOf(
            MemoryBankEntity(content = "今天上午她说论文写不下去了", type = "phase_summary", createdAt = 300L),
            MemoryBankEntity(content = "她最近在准备考研", type = "daily_summary", createdAt = 200L),
            MemoryBankEntity(content = "她不喜欢太硬的打卡提醒", type = "manual", createdAt = 100L),
        )

        val context = buildMemoryRecallContext(memories)

        assertTrue(context.contains("<lulu_memory>"))
        assertTrue(context.contains("阶段回忆"))
        assertTrue(context.contains("长期印象"))
        assertTrue(context.contains("她不喜欢太硬的打卡提醒"))
        assertTrue(context.contains("不要逐条复述"))
    }

    @Test
    fun `build memory context returns blank when there are no memories`() {
        assertEquals("", buildMemoryRecallContext(emptyList()))
    }

    @Test
    fun `build memory context trims long content and limits item count`() {
        val memories = (1..8).map { index ->
            MemoryBankEntity(
                content = "memory-$index " + "x".repeat(180),
                type = "manual",
                createdAt = index.toLong(),
            )
        }

        val context = buildMemoryRecallContext(memories, maxItems = 3, maxContentLength = 24)

        assertTrue(context.contains("memory-8"))
        assertTrue(context.contains("memory-6"))
        assertTrue(!context.contains("memory-5"))
        assertTrue(context.contains("..."))
    }
}
