package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CompanionNeutralPersistenceTest {
    @Test
    fun durableResponsibilitiesStoreNeutralFactsInsteadOfDefaultPersonaSentences() {
        val draft = CompanionFollowUpDraft(
            assistantId = "role-1",
            category = "wake",
            reason = "明天叫醒",
            sourceText = "以后每天监督我起床",
            dueAt = 1234L,
        )

        val concern = draft.toConcern(nowMillis = 100L)
        val commitment = draft.toCommitment(nowMillis = 100L)
        val stored = listOf(
            concern.event,
            concern.goal,
            commitment.promise,
            commitment.responsibility,
            commitment.actionPlan.userFacingSummary,
        ).joinToString("\n")

        assertEquals("起床监督", concern.event)
        assertEquals("按约定时间执行叫醒并核验完成状态", commitment.responsibility)
        assertFalse(stored.contains("记着"))
        assertFalse(stored.contains("放在心上"))
        assertFalse(stored.contains("你已经醒来"))
    }
}
