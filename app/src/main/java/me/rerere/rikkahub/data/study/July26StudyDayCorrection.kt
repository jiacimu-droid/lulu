package me.rerere.rikkahub.data.study

import java.time.LocalDate

/**
 * July 26 is a one-off study-day correction because the weekly rest day was
 * already taken on July 25. Future Sunday rest rules remain unchanged.
 */
object July26StudyDayCorrection : StudyPlanOverlay {
    private val date = LocalDate.of(2026, 7, 26)

    override val dailyOverrides: Map<LocalDate, DailyStudyPlan> = mapOf(
        date to DailyStudyPlan(
            date = date,
            title = "刑法第8章启动日：昨天已休息，今天恢复正常学习",
            tasks = listOf(
                StudyPlanTask(
                    title = "听众合法硕刑法第8章课程：按真实课时连续推进，未听完就记录进度，明天先续本章",
                    kind = StudyPlanTaskKind.Law,
                ),
                StudyPlanTask(
                    title = "刑法第8章闭卷口述目录树、构成关系和关键词；课程完成后整理最低可用正式框架",
                    kind = StudyPlanTaskKind.Review,
                ),
                StudyPlanTask(
                    title = "法理第一轮背诵30-40分钟，并抽背刑法已学章节目录树；薄弱点登记D1/D3/D7",
                    kind = StudyPlanTaskKind.Review,
                ),
                StudyPlanTask(
                    title = ExamStudyPlan.dailyVocabularyTaskTitle,
                    kind = StudyPlanTaskKind.English,
                ),
                StudyPlanTask(
                    title = ExamStudyPlan.dailyEnglishReviewTaskTitle,
                    kind = StudyPlanTaskKind.English,
                ),
                StudyPlanTask(
                    title = "昨天已经完成本周休息，今天按正常学习日执行；不熬夜强行清零长章",
                    kind = StudyPlanTaskKind.Health,
                ),
            ),
        ),
    )

    override val weeklyOverrides: Map<String, WeeklyStudyPlan> = mapOf(
        "2026-07-w4" to WeeklyStudyPlan(
            id = "2026-07-w4",
            title = "7月25日休息后，7月26日恢复刑法主线",
            dateRange = "2026-07-20 至 2026-07-26",
            tasks = listOf(
                "7月25日已经作为本周完整休息日；7月26日不再重复放假",
                "7月26日启动刑法第8章，按课程→闭卷骨架→正式框架→配套题→主错因推进",
                "法理维持第一轮背诵，英语保留120词和真题复盘",
            ),
        ),
    )

    override fun monthlyPlan(base: MonthlyStudyPlan): MonthlyStudyPlan =
        if (base.month == "2026-07") {
            base.copy(
                focus = "7月25日休息，7月26日起从刑法第8章继续",
                tasks = listOf(
                    "7月25日已经完成本周休息；7月26日恢复正常学习，不再设置空白休息日",
                    "7月26日起只推进刑法第8章及后续章节，按课程→闭卷骨架→正式框架→配套题→主错因执行",
                    "法理进入第一轮目录树与关键词背诵；英语保留每日120词和真题训练",
                    "本月不启动民法和政治，不用多本新课并行制造进度",
                ),
            )
        } else {
            base
        }
}
