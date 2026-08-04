package me.rerere.rikkahub.data.study

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfDirectedStudyPlanTest {
    @Test
    fun augustFourthStartsAtChapterFourteenWithoutAnEightChapterHardTarget() {
        val week = StudyPlanCatalog.weekForDate(LocalDate.of(2026, 8, 4))
        val text = week?.tasks.orEmpty().joinToString("\n")

        assertTrue(week?.title.orEmpty().contains("第14章起步"))
        assertTrue(text.contains("第13章已完成，第14章尚未开始"))
        assertTrue(text.contains("不再要求完成第14-21章"))
        assertTrue(text.contains("核心目标为完成第14-15章"))
        assertTrue(text.contains("保留1个完整休息日"))
        assertFalse(text.contains("第11-15章"))
    }

    @Test
    fun followingWeekUsesFourHoursAsTheDailyBaseline() {
        val week = StudyPlanCatalog.weekForDate(LocalDate.of(2026, 8, 11))
        val text = week?.tasks.orEmpty().joinToString("\n")

        assertTrue(week?.title.orEmpty().contains("第17-20章"))
        assertTrue(text.contains("周容量约24小时"))
        assertTrue(text.contains("保留1个完整休息日"))
        assertTrue(text.contains("英语保温约1.5-2小时"))
    }

    @Test
    fun allRegularCoursesHaveAMidOctoberSafetyWindow() {
        val week = StudyPlanCatalog.weekForDate(LocalDate.of(2026, 10, 15))
        val month = StudyPlanCatalog.monthlyPlan("2026-10")
        val monthText = month?.tasks.orEmpty().joinToString("\n")

        assertTrue(week?.title.orEmpty().contains("新课总验收"))
        assertTrue(monthText.contains("10月18日为全部常规新课最晚安全线"))
        assertTrue(monthText.contains("偶尔4.5-5小时"))
    }
}
