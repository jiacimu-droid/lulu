package me.rerere.rikkahub.data.study

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfDirectedStudyPlanTest {
    @Test
    fun augustFourthUsesTheConfirmedChapterFourteenToTwentyOneTaskPool() {
        val week = StudyPlanCatalog.weekForDate(LocalDate.of(2026, 8, 4))
        val text = week?.tasks.orEmpty().joinToString("\n")

        assertTrue(week?.title.orEmpty().contains("第14-21章"))
        assertTrue(text.contains("当前真实起点为刑法第14章"))
        assertTrue(text.contains("不按星期固定分章"))
        assertTrue(text.contains("英语完成1个真题主训练任务"))
        assertFalse(text.contains("第11-15章"))
    }

    @Test
    fun followingWeekClosesChaptersTwentyTwoToTwentyFiveWithoutRepeatingOldWork() {
        val week = StudyPlanCatalog.weekForDate(LocalDate.of(2026, 8, 11))
        val text = week?.tasks.orEmpty().joinToString("\n")

        assertTrue(week?.title.orEmpty().contains("第22-25章"))
        assertTrue(text.contains("先接回上周第14-21章真实欠账"))
        assertTrue(text.contains("不重复已完成章节"))
    }
}
