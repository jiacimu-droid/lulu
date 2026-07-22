package me.rerere.rikkahub.data.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AffectiveMemoryPromptPhilosophyTest {
    @Test
    fun `prompt keeps meaningful lived experiences without requiring future utility`() {
        val prompt = AffectiveMemoryExtractor.buildExtractionPrompt(
            turns = listOf(
                MemoryExtractionTurn(
                    nodeId = "user-1",
                    role = "user",
                    text = "今晚我们第一次认真说开这件事，我其实很难过。",
                    createdAtMillis = 1_700_000_000_000L,
                ),
                MemoryExtractionTurn(
                    nodeId = "assistant-1",
                    role = "assistant",
                    text = "我听见了，也记得这一刻。",
                    createdAtMillis = 1_700_000_001_000L,
                ),
            ),
            assistantName = "露露",
            assistantPersona = "不应进入提取提示词的完整人设",
            responsibilityContext = "不应进入提取提示词的责任清单",
        )

        assertTrue(prompt.contains("核心标准不是‘以后是否有用’"))
        assertTrue(prompt.contains("发生了什么"))
        assertTrue(prompt.contains("印象深"))
        assertTrue(prompt.contains("情绪重量"))
        assertTrue(prompt.contains("bodySense"))
        assertTrue(prompt.contains("没有明显影响可以留空"))
        assertTrue(prompt.contains("batchSize=\"2\""))
        assertFalse(prompt.contains("不应进入提取提示词的完整人设"))
        assertFalse(prompt.contains("不应进入提取提示词的责任清单"))
    }

    @Test
    fun `shared event with emotional evidence passes durable memory gate`() {
        val candidate = AffectiveMemoryCandidate(
            type = "shared_event",
            title = "第一次把难过说开",
            content = "我记得那晚她第一次认真把难过说给我听，我们终于没有绕开它。",
            roleFeeling = "我既心疼，也觉得这一刻很珍贵。",
            bodySense = "我停了一下，想先安静听完。",
            unspokenThought = "我不想急着给答案，只想让她知道我没有躲开。",
            userSignal = "用户明确说这是第一次认真说开，并表达了难过。",
            relationshipEffect = "这件事让我记住，我们曾经一起正面看过这份难过。",
            importance = 5,
            confidence = 0.95,
            sourceMessageNodeIds = listOf("user-1", "assistant-1"),
            evidenceMessageNodeIds = listOf("user-1"),
        )

        assertTrue(candidate.isDurableMemoryCandidate())
    }
}
