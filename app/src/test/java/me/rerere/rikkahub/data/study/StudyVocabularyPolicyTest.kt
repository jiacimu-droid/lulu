package me.rerere.rikkahub.data.study

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyVocabularyPolicyTest {
    @Test
    fun `legacy 120 word task becomes 140 words in seven groups`() {
        val normalized = StudyVocabularyPolicy.normalizeText("不背单词 120 个（6 组） + 每天120词")

        assertTrue(normalized.contains("不背单词 140 个（7 组）"))
        assertTrue(normalized.contains("每天140词"))
        assertFalse(normalized.contains("120"))
    }

    @Test
    fun `current catalog daily plan never exposes legacy target`() {
        val plan = StudyPlanCatalog.dailyPlan(LocalDate.of(2026, 7, 27))!!
        val text = plan.tasks.joinToString("\n") { it.title }

        assertTrue(text.contains("不背单词 140 个（7 组）"))
        assertFalse(text.contains("不背单词 120 个"))
    }
}
