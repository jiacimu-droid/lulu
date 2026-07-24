package me.rerere.rikkahub.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyAchievementRewardsTest {
    @Test
    fun `early achievements prefer kudos instead of flooding ten draw tickets`() {
        val firstCompanion = StudyRules.achievements.first { it.id == "first_companion" }
        val pomodoroFifty = StudyRules.achievements.first { it.id == "pomodoro_50" }

        assertEquals(200, firstCompanion.reward.kudos)
        assertEquals(0, firstCompanion.reward.tenDrawTickets)
        assertEquals(500, pomodoroFifty.reward.kudos)
        assertEquals(0, pomodoroFifty.reward.tenDrawTickets)
    }

    @Test
    fun `largest lifetime achievement is capped at three ten draw tickets`() {
        val thousandHours = StudyRules.achievements.first { it.id == "study_1000h" }
        val maximumTickets = StudyRules.achievements.maxOf { it.reward.tenDrawTickets }

        assertEquals(3_000, thousandHours.reward.kudos)
        assertEquals(3, thousandHours.reward.tenDrawTickets)
        assertEquals(3, maximumTickets)
    }

    @Test
    fun `claiming an achievement applies the balanced reward`() {
        val state = StudyState(stats = StudyStats(totalPomodoros = 10))
        val result = StudyRules.claimAchievement(state, "first_companion")

        assertEquals(200, result.reward.kudos)
        assertEquals(0, result.reward.tenDrawTickets)
        assertEquals(200, result.state.wallet.kudos)
        assertTrue("first_companion" in result.state.claimedAchievementIds)
    }
}
