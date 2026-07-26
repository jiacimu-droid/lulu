package me.rerere.rikkahub.data.study

import java.time.LocalDate

/**
 * July 26 is a one-off study-day correction because the weekly rest day was
 * already taken on July 25. Chapter 8 course input was completed on July 25, so
 * July 26 begins from framework, questions and error analysis instead of replaying
 * finished lessons. Future Sunday rest rules remain unchanged.
 */
object July26StudyDayCorrection : StudyPlanOverlay {
    private val date = LocalDate.of(2026, 7, 26)

    override val dailyOverrides: Map<LocalDate, DailyStudyPlan> = mapOf(
        date to DailyStudyPlan(
            date = date,
            title = "刑法第8章闭环日：课程已完成，今天做框架、题目与错因",
            tasks = listOf(
                StudyPlanTask(
                    title = "刑法第8章闭卷口述目录树、构成关系和关键词；对照课程与考试分析修正后形成正式框架图",
                    kind = StudyPlanTaskKind.Review,
                ),
                StudyPlanTask(
                    title = "完成刑法第8章听课配套题；每道错题只记录一个主错因，并把新增易混点补回原框架",
                    kind = StudyPlanTaskKind.Law,
                ),
                StudyPlanTask(
                    title = "第8章闭环后再开始刑法第9章课程；时间不足时只记录已听进度，不为追进度熬夜",
                    kind = StudyPlanTaskKind.Law,
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
                    title = "昨天已经完成第8章课程并完成本周休息，今天按正常学习日做闭环，不重复听已完成内容",
                    kind = StudyPlanTaskKind.Health,
                ),
            ),
        ),
    )

    override val weeklyOverrides: Map<String, WeeklyStudyPlan> = mapOf(
        "2026-07-w4" to WeeklyStudyPlan(
            id = "2026-07-w4",
            title = "第8章课程已完成，7月26日进入闭环并衔接第9章",
            dateRange = "2026-07-20 至 2026-07-26",
            tasks = listOf(
                "7月25日已经完成刑法第8章课程并作为本周休息日；7月26日不再重复放假",
                "7月26日完成第8章闭卷骨架、正式框架、配套题和唯一主错因",
                "第8章闭环后再开始第9章课程；法理维持第一轮背诵，英语保留120词和真题复盘",
            ),
        ),
    )

    override fun monthlyPlan(base: MonthlyStudyPlan): MonthlyStudyPlan =
        if (base.month == "2026-07") {
            base.copy(
                focus = "刑法第8章课程已完成，7月26日闭环后衔接第9章",
                tasks = listOf(
                    "7月25日已完成刑法第8章课程并完成本周休息；7月26日恢复正常学习",
                    "7月26日先完成第8章闭卷骨架、正式框架、配套题和主错因，再衔接第9章课程",
                    "法理进入第一轮目录树与关键词背诵；英语保留每日120词和真题训练",
                    "本月不启动民法和政治，不用多本新课并行制造进度",
                ),
            )
        } else {
            base
        }
}
