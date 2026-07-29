package me.rerere.rikkahub.data.study

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Evidence shown to the selected companion before it decides a sleep reward. */
data class StudySleepHabitEvidence(
    val actualTime: LocalTime?,
    val referenceTime: LocalTime,
    val recentTrend: String = "近期趋势未知",
    val specialCircumstances: String = "无已知特殊情况",
    val currentContext: String = "",
)

/** Structured decision returned by the selected companion/model. */
data class StudySleepHabitDecision(
    val approved: Boolean,
    val reason: String,
    val modelError: Boolean = false,
)

/**
 * Settles a companion-owned sleep decision.
 *
 * Clock thresholds are evidence only. Once the selected companion explicitly
 * approves with an actual time and reason, the system cannot reject merely
 * because that time is later than the reference baseline. The settlement layer
 * still enforces idempotency and non-negative balances.
 */
fun settleRoleJudgedSleepReward(
    state: StudyState,
    habit: StudySleepHabit,
    evidence: StudySleepHabitEvidence,
    decision: StudySleepHabitDecision,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    assistantName: String = "学习陪伴角色",
): StudySleepRewardResult {
    val date = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val current = if (state.today == date.toString()) state else StudyRules.rolloverToDate(state, date)
    val claimKey = "${date}:${habit.name}"
    if (claimKey in current.sleepHabitRewardClaims) {
        return StudySleepRewardResult(
            state = current,
            granted = false,
            alreadyClaimed = true,
            reason = "今天这项作息奖励已经发过了。",
        )
    }

    val cleanReason = decision.reason.trim()
    if (decision.modelError) {
        return StudySleepRewardResult(
            state = current,
            granted = false,
            reason = cleanReason.ifBlank { "角色判断模型暂时失败，本次不发奖，也不会占用今天的领取机会。" },
        )
    }
    if (evidence.actualTime == null) {
        return StudySleepRewardResult(
            state = current,
            granted = false,
            reason = "还不知道具体几点睡或几点起；角色需要先问清实际时间。",
        )
    }
    if (cleanReason.isBlank()) {
        return StudySleepRewardResult(
            state = current,
            granted = false,
            reason = "角色需要给出明确的批准或拒绝理由，不能机械发奖。",
        )
    }
    if (!decision.approved) {
        return StudySleepRewardResult(
            state = current,
            granted = false,
            reason = cleanReason,
        )
    }

    val reward = when (habit) {
        StudySleepHabit.EarlySleep -> StudyReward(
            kudos = StudyRules.EARLY_SLEEP_KUDOS.coerceAtLeast(0),
            title = "早睡奖励 +${StudyRules.EARLY_SLEEP_KUDOS.coerceAtLeast(0)} 夸夸值",
        )
        StudySleepHabit.EarlyRise -> StudyReward(
            tenDrawTickets = StudyRules.EARLY_RISE_TEN_DRAW_TICKETS.coerceAtLeast(0),
            title = "早起奖励 十连抽券 x${StudyRules.EARLY_RISE_TEN_DRAW_TICKETS.coerceAtLeast(0)}",
        )
    }
    val nextWallet = current.wallet.copy(
        kudos = (current.wallet.kudos + reward.kudos).coerceAtLeast(0),
        totalKudosEarned = current.wallet.totalKudosEarned + reward.kudos.coerceAtLeast(0),
        singleDrawTickets = (current.wallet.singleDrawTickets + reward.singleDrawTickets).coerceAtLeast(0),
        tenDrawTickets = (current.wallet.tenDrawTickets + reward.tenDrawTickets).coerceAtLeast(0),
        purpleDrawTickets = (current.wallet.purpleDrawTickets + reward.purpleDrawTickets).coerceAtLeast(0),
    )
    val roleName = assistantName.trim().ifBlank { "学习陪伴角色" }
    val habitName = when (habit) {
        StudySleepHabit.EarlySleep -> "早睡"
        StudySleepHabit.EarlyRise -> "早起"
    }
    val event = StudyEvent(
        id = "event-${System.currentTimeMillis()}-${current.recentEvents.size}",
        type = StudyEventType.Habit,
        title = "${habitName}被角色认可",
        detail = buildString {
            append("批准=true")
            append(" · 实际=${evidence.actualTime}")
            append(" · 参考=${evidence.referenceTime}")
            append(" · 近期=${evidence.recentTrend.ifBlank { "未知" }}")
            append(" · 特殊情况=${evidence.specialCircumstances.ifBlank { "无" }}")
            append(" · 角色=$roleName")
            append(" · 理由=$cleanReason")
            append(" · 奖励=${reward.title}")
        },
        createdAt = System.currentTimeMillis(),
    )
    return StudySleepRewardResult(
        state = current.copy(
            wallet = nextWallet,
            sleepHabitRewardClaims = current.sleepHabitRewardClaims + claimKey,
            recentEvents = (listOf(event) + current.recentEvents).take(40),
        ),
        granted = true,
        reason = cleanReason,
        reward = reward,
    )
}

fun referenceTimeFor(habit: StudySleepHabit): LocalTime = when (habit) {
    StudySleepHabit.EarlySleep -> LocalTime.of(
        StudyRules.EARLY_SLEEP_CUTOFF_HOUR,
        StudyRules.EARLY_SLEEP_CUTOFF_MINUTE,
    )
    StudySleepHabit.EarlyRise -> LocalTime.of(
        StudyRules.EARLY_RISE_CUTOFF_HOUR,
        StudyRules.EARLY_RISE_CUTOFF_MINUTE,
    )
}
