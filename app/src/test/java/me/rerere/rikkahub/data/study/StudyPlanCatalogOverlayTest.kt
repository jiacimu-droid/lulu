package me.rerere.rikkahub.data.study

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPlanCatalogOverlayTest {
    @Test
    fun readingCatalogDoesNotMutateTheBasePlanCollections() {
        val baseDailyBefore = ExamStudyPlan.dailyPlans.toMap()
        val baseWeeklyBefore = ExamStudyPlan.weeklyPlans.toList()
        val baseMonthlyBefore = ExamStudyPlan.monthlyPlans.toList()

        StudyPlanCatalog.dailyPlans
        StudyPlanCatalog.weeklyPlans
        StudyPlanCatalog.monthlyPlans

        assertEquals(baseDailyBefore, ExamStudyPlan.dailyPlans)
        assertEquals(baseWeeklyBefore, ExamStudyPlan.weeklyPlans)
        assertEquals(baseMonthlyBefore, ExamStudyPlan.monthlyPlans)
    }

    @Test
    fun recoveryOverlayReplacesPlansOnlyAtTheCompositionBoundary() {
        val recoveryDate = LocalDate.of(2026, 7, 23)
        val basePlan = ExamStudyPlan.todayPlan(recoveryDate)
        val composedPlan = StudyPlanCatalog.dailyPlan(recoveryDate)

        assertEquals(CurrentWeekStudyRecovery.planFor(recoveryDate), composedPlan)
        assertNotEquals(basePlan, composedPlan)
        assertTrue(composedPlan?.title.orEmpty().contains("病后补任务"))
    }

    @Test
    fun plannedMinutesAreReadThroughTheOverlayWithoutChangingTheBasePolicy() {
        val recoveryDate = CurrentWeekStudyRecovery.usedRecoveryDate
        val composedMinutes = StudyPlanCatalog.plannedStudyMinutes(recoveryDate)
        val baseMinutes = ExamStudyPlan.plannedStudyMinutes(recoveryDate)

        assertEquals(0, composedMinutes)
        assertEquals(baseMinutes, ExamStudyPlan.plannedStudyMinutes(recoveryDate))
    }
}