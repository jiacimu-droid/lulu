package me.rerere.rikkahub.data.study

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummerCourseMilestonePlanTest {
    @Test
    fun `current week starts from chapter nine and keeps hard endpoints`() {
        val week = SummerCourseDeadlinePlan.weeklyOverrides.getValue("2026-07-w5")

        assertTrue(week.title.contains("刑法听到第13章"))
        assertTrue(week.tasks.any { it.contains("第8章课程和题目全部完成") })
        assertTrue(week.tasks.any { it.contains("本周从第9章推进到第13章") })
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
    fun `daily plan recommends chapter nine without making it mandatory`() {
        val plan = SummerCourseDeadlinePlan.dailyOverrides.getValue(LocalDate.of(2026, 7, 27))
        val text = plan.tasks.joinToString("\n") { it.title }

        assertEquals("刑法｜今日建议：刑法第9章", plan.title)
        assertTrue(text.contains("今日建议进度：刑法第9章"))
        assertTrue(text.contains("第8章课程和题目已经完成"))
        assertTrue(text.contains("你可以不做、少做或多做"))
        assertTrue(text.contains("今日建议背诵：法理第一轮第1章"))
        assertFalse(text.contains("今天必须完成第9章"))
    }

    @Test
    fun `daily chapter suggestions advance through current week`() {
        val monday = SummerCourseDeadlinePlan.dailyOverrides.getValue(LocalDate.of(2026, 7, 27))
        val friday = SummerCourseDeadlinePlan.dailyOverrides.getValue(LocalDate.of(2026, 7, 31))

        assertTrue(monday.tasks.any { it.title.contains("刑法第9章") })
        assertTrue(friday.tasks.any { it.title.contains("刑法第13章") })
        assertTrue(friday.tasks.any { it.title.contains("法理第一轮第2章") })
    }

    @Test
    fun `monthly plan exposes non negotiable month end checkpoints`() {
        val july = SummerCourseDeadlinePlan.monthlyPlan(
            MonthlyStudyPlan("2026-07", "old", emptyList()),
        )
        val august = SummerCourseDeadlinePlan.monthlyPlan(
            MonthlyStudyPlan("2026-08", "old", emptyList()),
        )
        val september = SummerCourseDeadlinePlan.monthlyPlan(
            MonthlyStudyPlan("2026-09", "old", emptyList()),
        )

        assertTrue(july.tasks.any { it.contains("第8章课程和题目已经全部完成") })
        assertTrue(july.tasks.any { it.contains("刑法第9-13章") })
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
