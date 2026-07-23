package me.rerere.rikkahub.data.study

import java.time.LocalDate

/**
 * Read-only composition root for study plans.
 *
 * Base exam plans remain immutable. Time-bounded adjustments are supplied as
 * explicit overlays and merged on read, so opening a screen can never mutate a
 * process-global list or depend on unsafe MutableList/MutableMap casts.
 */
object StudyPlanCatalog {
    private val overlays: List<StudyPlanOverlay> = listOf(CurrentWeekStudyRecovery)

    val dailyPlans: Map<LocalDate, DailyStudyPlan>
        get() = overlays.fold(ExamStudyPlan.dailyPlans.toMap()) { current, overlay ->
            current + overlay.dailyOverrides
        }

    val weeklyPlans: List<WeeklyStudyPlan>
        get() = mergeByKey(
            base = ExamStudyPlan.weeklyPlans,
            overlays = overlays.flatMap { it.weeklyOverrides.values },
            key = WeeklyStudyPlan::id,
        )

    val monthlyPlans: List<MonthlyStudyPlan>
        get() = ExamStudyPlan.monthlyPlans.map { base ->
            overlays.fold(base) { current, overlay -> overlay.monthlyPlan(current) }
        }

    fun dailyPlan(date: LocalDate): DailyStudyPlan? =
        overlays.asReversed().firstNotNullOfOrNull { it.dailyOverrides[date] }
            ?: ExamStudyPlan.todayPlan(date)

    fun plannedStudyMinutes(date: LocalDate): Int =
        overlays.asReversed().firstNotNullOfOrNull { it.plannedMinutesOverride(date) }
            ?: ExamStudyPlan.plannedStudyMinutes(date)

    fun weeklyPlan(id: String): WeeklyStudyPlan? = weeklyPlans.firstOrNull { it.id == id }

    fun monthlyPlan(month: String): MonthlyStudyPlan? = monthlyPlans.firstOrNull { it.month == month }

    private fun <T, K> mergeByKey(
        base: List<T>,
        overlays: List<T>,
        key: (T) -> K,
    ): List<T> {
        if (overlays.isEmpty()) return base.toList()
        val replacements = overlays.associateBy(key)
        val baseKeys = base.mapTo(mutableSetOf(), key)
        return base.map { replacements[key(it)] ?: it } + overlays.filter { key(it) !in baseKeys }
    }
}

interface StudyPlanOverlay {
    val dailyOverrides: Map<LocalDate, DailyStudyPlan>
    val weeklyOverrides: Map<String, WeeklyStudyPlan>
    fun monthlyPlan(base: MonthlyStudyPlan): MonthlyStudyPlan = base
    fun plannedMinutesOverride(date: LocalDate): Int? = null
}
