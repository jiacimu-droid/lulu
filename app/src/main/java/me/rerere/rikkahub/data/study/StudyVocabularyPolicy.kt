package me.rerere.rikkahub.data.study

/**
 * Authoritative vocabulary target layered over the legacy exam-plan catalog.
 *
 * Some historical plan text still contains the old 120-word value. Normalizing
 * at the catalog and AI-prompt boundaries keeps old records readable while every
 * current/future task is presented and scheduled as 140 words in 7 groups.
 */
object StudyVocabularyPolicy {
    const val dailyTarget: Int = 140
    const val dailyGroups: Int = 7
    const val dailyTaskTitle: String = "不背单词 140 个（7 组）"

    fun normalizeText(text: String): String = text
        .replace(Regex("不背单词(?:复习)?\\s*\\d+\\s*个（\\d+\\s*组）"), dailyTaskTitle)
        .replace("每天120词", "每天140词")
        .replace("每日120词", "每日140词")
        .replace("120 个单词", "140 个单词")
        .replace("120个单词", "140个单词")
        .replace("120词", "140词")

    fun normalize(plan: DailyStudyPlan): DailyStudyPlan = plan.copy(
        title = normalizeText(plan.title),
        tasks = plan.tasks.map { task -> task.copy(title = normalizeText(task.title)) },
    )

    fun normalize(plan: WeeklyStudyPlan): WeeklyStudyPlan = plan.copy(
        title = normalizeText(plan.title),
        tasks = plan.tasks.map(::normalizeText),
    )

    fun normalize(plan: MonthlyStudyPlan): MonthlyStudyPlan = plan.copy(
        focus = normalizeText(plan.focus),
        tasks = plan.tasks.map(::normalizeText),
    )
}
