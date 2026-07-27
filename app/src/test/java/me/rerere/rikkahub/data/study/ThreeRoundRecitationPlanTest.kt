package me.rerere.rikkahub.data.study

import org.junit.Assert.assertTrue
import org.junit.Test

class ThreeRoundRecitationPlanTest {
    @Test
    fun `first round is complete by October 14`() {
        val week = ThreeRoundRecitationPlan.weeklyOverrides.getValue("2026-10-w2")

        assertTrue(week.tasks.any { it.contains("10月14日前") && it.contains("第一轮") })
        assertTrue(week.tasks.any { it.contains("法理13章") && it.contains("民法54章") })
    }

    @Test
    fun `second round is complete by October 31`() {
        val week = ThreeRoundRecitationPlan.weeklyOverrides.getValue("2026-10-w4")

        assertTrue(week.title.contains("第二轮全部完成"))
        assertTrue(week.tasks.any { it.contains("10月31日前五科第二轮全部结束") })
    }

    @Test
    fun `third round is complete by November 30`() {
        val week = ThreeRoundRecitationPlan.weeklyOverrides.getValue("2026-11-w4")
        val december = ThreeRoundRecitationPlan.monthlyPlan(
            MonthlyStudyPlan("2026-12", "old", emptyList()),
        )

        assertTrue(week.tasks.any { it.contains("五科第三轮全部结束") })
        assertTrue(december.tasks.any { it.contains("11月30日前全部完成") })
        assertTrue(december.tasks.any { it.contains("12月不承担第三轮主体任务") })
    }

    @Test
    fun `legal history course is no longer compressed into September`() {
        val september = ThreeRoundRecitationPlan.monthlyPlan(
            MonthlyStudyPlan("2026-09", "old", emptyList()),
        )

        assertTrue(september.tasks.any { it.contains("法制史听到第4章") })
        assertTrue(september.tasks.any { it.contains("全部新课终点调整为10月7日") })
    }
}
