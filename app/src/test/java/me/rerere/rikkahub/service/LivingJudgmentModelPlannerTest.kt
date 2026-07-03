package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivingJudgmentModelPlannerTest {
    @Test
    fun `prompt asks model to choose next evaluation delay`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我先忙一下",
            assistantText = "好，我会自己判断下次什么时候想你。",
            nowMillis = NOW,
        )
        val prompt = LivingJudgmentModelPlanner.buildPrompt(
            LivingJudgmentModelInput(
                assistantName = "露露",
                persona = "小管家。",
                intent = intent,
                observation = LivingObservation(summary = "No risk signals.", createdAt = NOW),
                recentConversation = emptyList(),
            )
        )

        assertTrue(prompt.contains("nextEvaluateDelayMinutes"))
        assertTrue(prompt.contains("不要照抄固定表"))
        assertTrue(prompt.contains("不等于多久后发消息"))
    }

    @Test
    fun `parse trace extracts fenced json judgment`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我肚子疼，先不回你",
            assistantText = "我会先看一下情况。",
            nowMillis = NOW,
        )
        val observation = LivingObservation(
            summary = "Runtime observation before judgment.",
            signals = listOf("missing_tool=get_gadgetbridge_data"),
            createdAt = NOW,
        )
        val input = LivingJudgmentModelInput(
            assistantName = "露露",
            persona = "小管家，关心用户但会克制。",
            intent = intent,
            observation = observation,
            recentConversation = emptyList(),
        )

        val trace = LivingJudgmentModelPlanner.parseTrace(
            rawText = """
                ```json
                {
                  "belief": "用户身体不舒服，而且暂时没有回复。",
                  "desire": "确认安全，同时不要制造恐慌。",
                  "intention": "先观察工具线索，再决定是否轻轻确认。",
                  "thought": "我没有足够健康线索，所以不能假装知道。",
                  "action": "TOOL_CHECK, MESSAGE, SCHEDULE_NEXT_TICK",
                  "observation": "get_gadgetbridge_data is missing.",
                  "decision": "可以准备轻声确认，但允许后续生成时 PASS。",
                  "nextEvaluateDelayMinutes": 17
                }
                ```
            """.trimIndent(),
            input = input,
        )

        assertEquals(LivingJudgmentSource.MAIN_API_STRUCTURED_JUDGMENT, trace?.source)
        assertEquals("TOOL_CHECK, MESSAGE, SCHEDULE_NEXT_TICK", trace?.action)
        assertEquals(17, trace?.nextEvaluateDelayMinutes)
        assertTrue(trace?.thought?.contains("不能假装知道") == true)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
