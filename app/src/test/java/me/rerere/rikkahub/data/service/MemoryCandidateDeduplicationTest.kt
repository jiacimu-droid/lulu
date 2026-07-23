package me.rerere.rikkahub.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryCandidateDeduplicationTest {
    @Test
    fun `paraphrases backed by the same evidence collapse to the strongest memory`() {
        val result = deduplicateMemoryCandidates(
            listOf(
                AffectiveMemoryCandidate(
                    type = "user_preference",
                    content = "我记得她不喜欢自动生成建议回复。",
                    importance = 3,
                    confidence = 0.82,
                    sourceMessageNodeIds = listOf("user-1"),
                ),
                AffectiveMemoryCandidate(
                    type = "user_preference",
                    content = "我知道她不需要系统自动生成对话建议。",
                    roleFeeling = "我需要尊重这个明确选择",
                    importance = 4,
                    confidence = 0.96,
                    sourceMessageNodeIds = listOf("user-1"),
                ),
            )
        )

        assertEquals(1, result.size)
        assertEquals(4, result.single().importance)
        assertTrue(result.single().content.contains("自动生成对话建议"))
    }

    @Test
    fun `different facts in one source message remain separate`() {
        val result = deduplicateMemoryCandidates(
            listOf(
                AffectiveMemoryCandidate(
                    type = "user_preference",
                    content = "我记得她喜欢深色番茄钟界面。",
                    sourceMessageNodeIds = listOf("user-2"),
                ),
                AffectiveMemoryCandidate(
                    type = "user_preference",
                    content = "我记得她希望电话回复更快。",
                    sourceMessageNodeIds = listOf("user-2"),
                ),
            )
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `duplicate evidence ids are merged without duplicating ids`() {
        val result = deduplicateMemoryCandidates(
            listOf(
                AffectiveMemoryCandidate(
                    type = "relationship",
                    content = "我记得她认真认可了我的方案。",
                    importance = 5,
                    confidence = 0.9,
                    sourceMessageNodeIds = listOf("user-3"),
                ),
                AffectiveMemoryCandidate(
                    type = "relationship",
                    content = "我记得她很认真地认可了这套方案。",
                    importance = 4,
                    confidence = 0.8,
                    sourceMessageNodeIds = listOf("user-3", "assistant-3"),
                ),
            )
        )

        assertEquals(1, result.size)
        assertEquals(listOf("user-3", "assistant-3"), result.single().sourceMessageNodeIds)
    }
}
