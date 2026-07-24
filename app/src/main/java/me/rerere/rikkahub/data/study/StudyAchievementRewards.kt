package me.rerere.rikkahub.data.study

/**
 * Achievement economy tuned around the current study loop of roughly forty pulls
 * on a strong study day. Small milestones mainly grant kudos; ten-pull tickets are
 * reserved for meaningful long-term checkpoints so achievements feel rewarding
 * without flooding the wallet.
 *
 * Existing claimed achievements and already-owned resources are never changed.
 */
object StudyAchievementRewards {
    private val balancedRewards = mapOf(
        "warm_start" to kudos(100),
        "first_companion" to kudos(200),
        "pomodoro_20" to kudos(300),
        "pomodoro_50" to kudos(500),
        "pomodoro_100" to mixed(300, 1),
        "task_spark" to kudos(100),
        "todo_slayer" to kudos(300),
        "tasks_50" to kudos(500),
        "tasks_100" to mixed(200, 1),
        "perfect_3" to kudos(200),
        "perfect_7" to kudos(500),
        "perfect_14" to tickets(1),
        "perfect_30" to mixed(500, 1),
        "deep_work_10h" to kudos(200),
        "time_traveler" to kudos(500),
        "study_100h" to mixed(500, 1),
        "study_200h" to mixed(1_000, 1),
        "first_outfit" to kudos(200),
        "outfit_collector" to kudos(500),
        "outfits_5" to tickets(1),
        "outfits_10" to mixed(500, 1),
        "theater_open" to kudos(200),
        "lucky_drawer" to kudos(200),
        "epic_touch" to kudos(300),
        "mcdonalds_arrival" to kudos(300),
        "pomodoro_150" to kudos(800),
        "pomodoro_200" to mixed(500, 1),
        "pomodoro_365" to mixed(1_000, 2),
        "tasks_150" to kudos(800),
        "tasks_200" to mixed(500, 1),
        "tasks_365" to mixed(1_000, 2),
        "perfect_60" to mixed(1_000, 1),
        "perfect_100" to mixed(1_000, 2),
        "study_300h" to mixed(1_500, 1),
        "study_500h" to mixed(1_500, 2),
        "study_1000h" to mixed(3_000, 3),
        "outfits_15" to mixed(1_000, 1),
        "outfits_20" to mixed(1_000, 2),
        "theaters_3" to kudos(500),
        "videos_3" to kudos(500),
    )

    fun rewardFor(id: String, fallback: StudyReward): StudyReward =
        balancedRewards[id] ?: fallback

    private fun kudos(amount: Int) = StudyReward(
        kudos = amount,
        title = "夸夸值 $amount",
    )

    private fun tickets(count: Int) = StudyReward(
        tenDrawTickets = count,
        title = "十连抽券 x$count",
    )

    private fun mixed(kudos: Int, tickets: Int) = StudyReward(
        kudos = kudos,
        tenDrawTickets = tickets,
        title = "夸夸值 $kudos + 十连抽券 x$tickets",
    )
}
