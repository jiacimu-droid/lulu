package me.rerere.rikkahub.data.study

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummerCourseMilestonePlanTest {
    @Test
    fun `current week has exact course and recitation endpoints`() {
        val week = SummerCourseDeadlinePlan.weeklyOverrides.getValue("2026-07-w5")

        assertTrue(week.title.contains("刑法听到第13章"))
        assertTrue(week.tasks.any { it.contains("刑法新课从第8章推进到第13章") })
        assertTrue(week.tasks.any { it.contains("法理第一轮闭卷背诵完成第1-2章") })
        assertTrue(week.tasks.any { it.contains("周末验收") })
    }

    @Test
    fun `later weeks keep fixed chapter destinations`() {
        val august = SummerCourseDeadlinePlan.weeklyOverrides.getValue("2026-08-w4")
        val september = SummerCourseDeadlinePlan.weeklyOverrides.getValue("2026-09-w2")

        assertTrue(august.tasks.any { it.contains("民法第11-27章") })
        assertTrue(august.tasks.any { it.contains("法理第一轮背诵完成第11-13章") })
        assertTrue(september.tasks.any { it.contains("民法第41-54章") })
        assertTrue(september.tasks.any { it.contains("民法第一轮背诵完成第33-45章") })
    }

    @Test
    fun `daily plan is flexible instead of assigning a chapter quota`() {
        val plan = SummerCourseDeadlinePlan.dailyOverrides.getValue(LocalDate.of(2026, 7, 27))
        val text = plan.tasks.joinToString("\n") { it.title }

        assertEquals("刑法：围绕本周硬目标自由安排今日进度", plan.title)
        assertTrue(text.contains("本周新课硬目标"))
        assertTrue(text.contains("半章、一章或多个短章"))
        assertTrue(text.contains("今天不设置强制章节终点"))
        assertFalse(text.contains("今天必须完成第"))
    }

    @Test
    fun `monthly plan exposes non negotiable month end checkpoints`() {
        val august = SummerCourseDeadlinePlan.monthlyPlan(
            MonthlyStudyPlan("2026-08", "old", emptyList()),
        )
        val september = SummerCourseDeadlinePlan.monthlyPlan(
            MonthlyStudyPlan("2026-09", "old", emptyList()),
        )

        assertTrue(august.focus.contains("民法听到第27章"))
        assertTrue(august.tasks.any { it.contains("法理第一轮完成第1-13章") })
        assertTrue(september.tasks.any { it.contains("民法听完第54章") })
        assertTrue(september.tasks.any { it.contains("9月30日前法制史听完第7章") })
    }

    @Test
    fun `sunday remains a complete rest day`() {
        val sunday = SummerCourseDeadlinePlan.dailyOverrides.getValue(LocalDate.of(2026, 8, 2))

        assertEquals("每周完整休息日", sunday.title)
        assertTrue(sunday.tasks.isEmpty())
    }
}
