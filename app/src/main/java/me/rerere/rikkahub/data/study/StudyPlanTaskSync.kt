package me.rerere.rikkahub.data.study

import java.time.LocalDate

/**
 * Bridges the composed study-plan catalog to the persisted Today task list.
 *
 * Catalog task ids use a dedicated versioned prefix. Once today's current plan
 * has been installed, repeated screen refreshes preserve the user's edits,
 * including deleting one or all system-planned tasks. Bumping the version only
 * happens when a shipped APK must replace an older built-in plan on the same day.
 */
object StudyPlanTaskSync {
    private const val ID_PREFIX = "catalog-plan-v2"

    fun sync(state: StudyState, date: LocalDate = LocalDate.now()): StudyState {
        val dateText = date.toString()
        val prefix = idPrefix(date)
        val currentPlanTasks = state.tasks.filter { it.source == StudyTaskSource.Plan }

        // Do not let the minute-level screen refresh recreate a system task that
        // the user deliberately deleted today. Empty means all planned tasks may
        // have been removed, which is also a valid user choice.
        if (
            state.today == dateText &&
            state.activePlanDate == dateText &&
            (currentPlanTasks.isEmpty() || currentPlanTasks.all { it.id.startsWith(prefix) })
        ) {
            return state
        }

        // Keep the existing date-rollover, inactivity and migration behavior,
        // then replace its legacy/base-plan tasks with the composed catalog plan.
        val rolled = StudyRules.rolloverToDate(state, date)
        return installCatalogPlan(rolled, date)
    }

    fun visiblePlan(state: StudyState, date: LocalDate = LocalDate.now()): DailyStudyPlan? {
        val catalogPlan = StudyPlanCatalog.dailyPlan(date) ?: return null
        val visibleTitles = state.tasks
            .filter { it.source == StudyTaskSource.Plan }
            .mapTo(mutableSetOf()) { it.title }
        return catalogPlan.copy(
            tasks = catalogPlan.tasks.filter { task ->
                "${task.kind.label}｜${task.title}" in visibleTitles
            },
        )
    }

    private fun installCatalogPlan(state: StudyState, date: LocalDate): StudyState {
        val dateText = date.toString()
        val plan = StudyPlanCatalog.dailyPlan(date)
        val manualTasks = state.tasks.filter { it.source != StudyTaskSource.Plan }
        val previousByTitle = state.tasks
            .filter { it.source == StudyTaskSource.Plan }
            .associateBy { it.title }
        val planTasks = plan?.tasks?.mapIndexed { index, task ->
            val title = "${task.kind.label}｜${task.title}"
            val previous = previousByTitle[title]
            StudyTask(
                id = "${idPrefix(date)}$index",
                title = title,
                done = previous?.done ?: false,
                createdAt = previous?.createdAt ?: System.currentTimeMillis(),
                completedAt = previous?.completedAt,
                completionRewardClaimed = previous?.completionRewardClaimed ?: previous?.done ?: false,
                source = StudyTaskSource.Plan,
            )
        }.orEmpty()

        return state.copy(
            today = dateText,
            tasks = planTasks + manualTasks,
            activePlanDate = dateText,
            superMomentAvailable = false,
            generatedSchedules = state.generatedSchedules - dateText,
        )
    }

    private fun idPrefix(date: LocalDate): String = "$ID_PREFIX-${date}-"
}
