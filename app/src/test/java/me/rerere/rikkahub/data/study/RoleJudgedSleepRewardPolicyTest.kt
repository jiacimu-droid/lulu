package me.rerere.rikkahub.data.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RoleJudgedSleepRewardPolicyTest {
    @Test
    fun `normal within-reference approval grants reward`() {
        val result = settleRoleJudgedSleepReward(
            state = StudyState(today = TEST_DATE.toString()),
            habit = StudySleepHabit.EarlySleep,
            evidence = StudySleepHabitEvidence(
                actualTime = LocalTime.of(1, 20),
                referenceTime = LocalTime.of(1, 30),
                recentTrend = "最近三天都在两点前入睡",
            ),
            decision = StudySleepHabitDecision(
                approved = true,
                reason = "一点二十已经达到你的参考目标",
            ),
            nowMillis = testMillis(10, 0),
            zoneId = TEST_ZONE,
            assistantName = "露露",
        )

        assertTrue(result.granted)
        assertEquals(StudyRules.EARLY_SLEEP_KUDOS, result.reward.kudos)
        assertEquals(StudyRules.EARLY_SLEEP_KUDOS, result.state.wallet.kudos)
        assertTrue(result.state.recentEvents.first().detail.contains("批准=true"))
    }

    @Test
    fun `later-than-reference time still grants when role explicitly approves`() {
        val result = settleRoleJudgedSleepReward(
            state = StudyState(today = TEST_DATE.toString()),
            habit = StudySleepHabit.EarlyRise,
            evidence = StudySleepHabitEvidence(
                actualTime = LocalTime.of(10, 0),
                referenceTime = LocalTime.of(9, 30),
                recentTrend = "过去一周通常十一点后才起床",
                specialCircumstances = "昨晚因肠胃不适睡得较晚",
                currentContext = "今天十点已经起床并开始学习",
            ),
            decision = StudySleepHabitDecision(
                approved = true,
                reason = "虽然晚于参考线，但比近期状态明显提前，而且身体正在恢复",
            ),
            nowMillis = testMillis(10, 15),
            zoneId = TEST_ZONE,
            assistantName = "露露",
        )

        assertTrue(result.granted)
        assertEquals(StudyRules.EARLY_RISE_TEN_DRAW_TICKETS, result.reward.tenDrawTickets)
        assertEquals(StudyRules.EARLY_RISE_TEN_DRAW_TICKETS, result.state.wallet.tenDrawTickets)
        assertTrue(result.state.recentEvents.first().detail.contains("实际=10:00"))
        assertTrue(result.state.recentEvents.first().detail.contains("参考=09:30"))
    }

    @Test
    fun `explicit rejection grants nothing and keeps claim available`() {
        val state = StudyState(today = TEST_DATE.toString())
        val result = settleRoleJudgedSleepReward(
            state = state,
            habit = StudySleepHabit.EarlySleep,
            evidence = StudySleepHabitEvidence(
                actualTime = LocalTime.of(4, 0),
                referenceTime = LocalTime.of(1, 30),
                currentContext = "四点仍在持续聊天",
            ),
            decision = StudySleepHabitDecision(
                approved = false,
                reason = "四点仍然清醒，这次不能算早睡",
            ),
            nowMillis = testMillis(10, 0),
            zoneId = TEST_ZONE,
        )

        assertFalse(result.granted)
        assertEquals(0, result.reward.kudos)
        assertEquals(0, result.state.wallet.kudos)
        assertFalse(StudyRules.hasClaimedSleepHabitReward(result.state, StudySleepHabit.EarlySleep, TEST_DATE))
        assertTrue(result.reason.contains("不能算早睡"))
    }

    @Test
    fun `model error grants nothing and does not consume daily claim`() {
        val result = settleRoleJudgedSleepReward(
            state = StudyState(today = TEST_DATE.toString()),
            habit = StudySleepHabit.EarlyRise,
            evidence = StudySleepHabitEvidence(
                actualTime = LocalTime.of(8, 40),
                referenceTime = LocalTime.of(9, 30),
            ),
            decision = StudySleepHabitDecision(
                approved = false,
                reason = "判断模型返回内容无法解析",
                modelError = true,
            ),
            nowMillis = testMillis(9, 0),
            zoneId = TEST_ZONE,
        )

        assertFalse(result.granted)
        assertEquals(0, result.state.wallet.tenDrawTickets)
        assertFalse(StudyRules.hasClaimedSleepHabitReward(result.state, StudySleepHabit.EarlyRise, TEST_DATE))
        assertTrue(result.reason.contains("无法解析"))
    }

    @Test
    fun `approved reward remains idempotent`() {
        val first = settleRoleJudgedSleepReward(
            state = StudyState(today = TEST_DATE.toString()),
            habit = StudySleepHabit.EarlySleep,
            evidence = StudySleepHabitEvidence(LocalTime.of(1, 10), LocalTime.of(1, 30)),
            decision = StudySleepHabitDecision(true, "角色明确批准"),
            nowMillis = testMillis(9, 0),
            zoneId = TEST_ZONE,
        )
        val duplicate = settleRoleJudgedSleepReward(
            state = first.state,
            habit = StudySleepHabit.EarlySleep,
            evidence = StudySleepHabitEvidence(LocalTime.of(1, 10), LocalTime.of(1, 30)),
            decision = StudySleepHabitDecision(true, "再次批准"),
            nowMillis = testMillis(9, 5),
            zoneId = TEST_ZONE,
        )

        assertTrue(first.granted)
        assertFalse(duplicate.granted)
        assertTrue(duplicate.alreadyClaimed)
        assertEquals(StudyRules.EARLY_SLEEP_KUDOS, duplicate.state.wallet.kudos)
    }

    private fun testMillis(hour: Int, minute: Int): Long = ZonedDateTime.of(
        TEST_DATE.year,
        TEST_DATE.monthValue,
        TEST_DATE.dayOfMonth,
        hour,
        minute,
        0,
        0,
        TEST_ZONE,
    ).toInstant().toEpochMilli()

    private companion object {
        val TEST_DATE: LocalDate = LocalDate.of(2026, 7, 30)
        val TEST_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}
