package me.rerere.rikkahub.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivingIntentReturnClassifierTest {
    @Test
    fun `ordinary held silence completes when user returns`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我先忙一下",
            assistantText = "好，我等你。",
            nowMillis = NOW,
        ).copy(silentEvaluationCount = 1)

        assertTrue(
            LivingIntentReturnClassifier.shouldCompleteOnUserReturn(
                intent = intent,
                userText = "我回来了",
                nowMillis = NOW + 30 * MINUTE,
            )
        )
    }

    @Test
    fun `deadline intent closes when user says the task is submitted or finished`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "晚上 6 点前交任务",
            assistantText = "我会盯一下。",
            nowMillis = NOW,
            deadlineAtMillis = NOW + 60 * MINUTE,
        ).copy(silentEvaluationCount = 2)

        assertTrue(
            LivingIntentReturnClassifier.shouldCompleteOnUserReturn(
                intent = intent,
                userText = "我提交了，已经做完了",
                nowMillis = NOW + 30 * MINUTE,
            )
        )
    }

    @Test
    fun `deadline intent can close when deadline passed and user returns`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "晚上 6 点前交任务",
            assistantText = "我会盯一下。",
            nowMillis = NOW,
            deadlineAtMillis = NOW + 60 * MINUTE,
        ).copy(silentEvaluationCount = 2)

        assertTrue(
            LivingIntentReturnClassifier.shouldCompleteOnUserReturn(
                intent = intent,
                userText = "我刚才在忙，现在回来了",
                nowMillis = NOW + 80 * MINUTE,
            )
        )
    }

    @Test
    fun `deadline intent does not close on vague return before deadline`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "晚上 6 点前交任务",
            assistantText = "我会盯一下。",
            nowMillis = NOW,
            deadlineAtMillis = NOW + 60 * MINUTE,
        ).copy(silentEvaluationCount = 1)

        assertFalse(
            LivingIntentReturnClassifier.shouldCompleteOnUserReturn(
                intent = intent,
                userText = "我刚才在忙，现在回来了",
                nowMillis = NOW + 30 * MINUTE,
            )
        )
    }

    @Test
    fun `health intent needs explicit safety feedback before closing`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我肚子好疼",
            assistantText = "我有点担心你。",
            nowMillis = NOW,
        ).copy(silentEvaluationCount = 2)

        assertFalse(
            LivingIntentReturnClassifier.shouldCompleteOnUserReturn(
                intent = intent,
                userText = "嗯嗯",
                nowMillis = NOW + 20 * MINUTE,
            )
        )
        assertTrue(
            LivingIntentReturnClassifier.shouldCompleteOnUserReturn(
                intent = intent,
                userText = "没事了，好多了，已经吃药了",
                nowMillis = NOW + 20 * MINUTE,
            )
        )
    }

    @Test
    fun `wake intent closes when user says they woke up`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "九点叫我起床",
            assistantText = "好，到点我叫你。",
            nowMillis = NOW,
            targetAtMillis = NOW + 60 * MINUTE,
        ).copy(spokenCount = 1)

        assertTrue(
            LivingIntentReturnClassifier.shouldCompleteOnUserReturn(
                intent = intent,
                userText = "我醒了，已经起床了",
                nowMillis = NOW + 62 * MINUTE,
            )
        )
    }

    @Test
    fun `study intent closes when user says study block started or finished`() {
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = "露露",
            userText = "我去学习三小时",
            assistantText = "我守着你的专注。",
            nowMillis = NOW,
        ).copy(silentEvaluationCount = 1)

        assertTrue(
            LivingIntentReturnClassifier.shouldCompleteOnUserReturn(
                intent = intent,
                userText = "我开始学了，先不聊",
                nowMillis = NOW + 40 * MINUTE,
            )
        )
        assertTrue(
            LivingIntentReturnClassifier.shouldCompleteOnUserReturn(
                intent = intent,
                userText = "番茄结束了，学完了",
                nowMillis = NOW + 90 * MINUTE,
            )
        )
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val MINUTE = 60_000L
    }
}
