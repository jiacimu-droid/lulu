package me.rerere.rikkahub.data.study

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Daily chapter ledger for the 2026 summer first-pass course deadlines.
 *
 * Sundays are complete rest days. Course chapters are distributed only across
 * study days, while recitation keeps explicit first/second/third-round goals.
 */
object SummerCourseDeadlinePlan : StudyPlanOverlay {
    private val criminalStart = LocalDate.of(2026, 7, 26)
    private val criminalLastCourseDate = LocalDate.of(2026, 8, 12)
    private val criminalDeadline = LocalDate.of(2026, 8, 15)
    private val civilStart = LocalDate.of(2026, 8, 16)
    private val civilDeadline = LocalDate.of(2026, 9, 15)
    private val constitutionStart = LocalDate.of(2026, 9, 16)
    private val constitutionEnd = LocalDate.of(2026, 9, 22)
    private val transitionBuffer = LocalDate.of(2026, 9, 23)
    private val legalHistoryStart = LocalDate.of(2026, 9, 24)
    private val allCourseDeadline = LocalDate.of(2026, 9, 30)

    override val dailyOverrides: Map<LocalDate, DailyStudyPlan> = buildMap {
        val criminalStudyDates = studyDatesBetween(criminalStart, criminalLastCourseDate)
        criminalStudyDates.forEachIndexed { index, date ->
            val startChapter = chapterBoundary(index, criminalStudyDates.size, 18) + 8
            val endChapter = chapterBoundary(index + 1, criminalStudyDates.size, 18) + 7
            val chapterText = chapterText(startChapter, endChapter)
            put(
                date,
                daily(
                    date,
                    "刑法${chapterText}：按8月15日节点均匀推进",
                    law("听众合法硕刑法${chapterText}课程：按真实课时连续推进；两个短章可同日完成，长章未听完时次日先续本章，不跳章"),
                    review("当天已听完章节闭卷复述目录树、构成关系和关键词；整章听完后完成正式框架图，并集中进入配套题与主错因整理"),
                    review(recitationTask(date, "刑法")),
                    english(ExamStudyPlan.dailyVocabularyTaskTitle),
                    english(defaultEnglishTask(date)),
                    health("今日所有科目从当天总预算内切分；专业课优先，不用熬夜把长章强行清零"),
                ),
            )
        }
        addSundayRestDays(criminalStart, criminalLastCourseDate)

        studyDatesBetween(criminalLastCourseDate.plusDays(1), criminalDeadline).forEach { date ->
            put(
                date,
                daily(
                    date,
                    "刑法一轮收口：课程、框架、题目与错因总验收",
                    review("核对刑法第8-25章课程账本：补完尚未结束的最后课程小节，不再新增其他科目新课"),
                    law("集中完成刑法尚未闭环章节的配套题、唯一主错因和隔日/7-14日回炉日期；优先重点章，不追求把所有二刷一次清零"),
                    review("刑法第一轮背诵验收：闭卷说出重点章目录树、构成要件和关键词；薄弱点登记D1/D3/D7，不要求一字不差"),
                    review(recitationTask(date, "刑法")),
                    english(ExamStudyPlan.dailyVocabularyTaskTitle),
                    english(defaultEnglishTask(date)),
                ),
            )
        }
        addSundayRestDays(criminalLastCourseDate.plusDays(1), criminalDeadline)

        val civilStudyDates = studyDatesBetween(civilStart, civilDeadline)
        civilStudyDates.forEachIndexed { index, date ->
            val startChapter = chapterBoundary(index, civilStudyDates.size, ExamStudyPlan.civilLawChapterCount) + 1
            val endChapter = chapterBoundary(index + 1, civilStudyDates.size, ExamStudyPlan.civilLawChapterCount)
            val chapterText = chapterText(startChapter, endChapter)
            put(
                date,
                daily(
                    date,
                    "民法${chapterText}：按9月15日节点均匀推进",
                    law("听众合法硕民法${chapterText}课程：两个短章可同日连续完成；遇到长章按真实课时占满主块，不用章节数伪造进度"),
                    review("当天已听完章节闭卷复述目录树和关键词，形成最低可用正式框架；整章完成后集中做配套题并记录唯一主错因"),
                    review(recitationTask(date, "民法")),
                    english(ExamStudyPlan.dailyVocabularyTaskTitle),
                    english(defaultEnglishTask(date)),
                    health("民法54章仅在学习日均匀分配；当天章节过长时先保证连续课程输入，闭环缺口进入随后两天，不熬夜清零"),
                ),
            )
        }
        addSundayRestDays(civilStart, civilDeadline)

        val constitutionStudyDates = studyDatesBetween(constitutionStart, constitutionEnd)
        constitutionStudyDates.forEachIndexed { index, date ->
            val startChapter = chapterBoundary(index, constitutionStudyDates.size, ExamStudyPlan.constitutionalLawChapterCount) + 1
            val endChapter = chapterBoundary(index + 1, constitutionStudyDates.size, ExamStudyPlan.constitutionalLawChapterCount)
            val chapterText = chapterText(startChapter, endChapter)
            put(
                date,
                daily(
                    date,
                    "宪法${chapterText}：课程与最低可用闭环",
                    law("听众合法硕宪法${chapterText}课程：完成当天课程主块并记录实际有效分钟"),
                    review("当天章节闭卷目录树、正式框架和配套题入口；写出易混点与一条主观题规范表述"),
                    review(recitationTask(date, "宪法")),
                    english(ExamStudyPlan.dailyVocabularyTaskTitle),
                    english(defaultEnglishTask(date)),
                    politics("政治按当天总预算完成强化课/核心考案小节与1000题启动块，不挤掉专业课主线"),
                ),
            )
        }
        addSundayRestDays(constitutionStart, constitutionEnd)

        put(
            transitionBuffer,
            daily(
                transitionBuffer,
                "宪法收口与法制史切换缓冲",
                review("核对宪法第1-7章课程、正式框架、配套题入口和主错因，只补最大缺口"),
                review("整理宪法总框架与第一轮关键词口述，写清明日法制史第1章入口"),
                review("第二轮规范表述：从法理、刑法或民法较早章节中选一章，闭卷说定义、构成要件和答题骨架"),
                english(ExamStudyPlan.dailyVocabularyTaskTitle),
                english(defaultEnglishTask(transitionBuffer)),
                politics("政治强化课/核心考案小节 + 1000题25-30道 + 主错因"),
            ),
        )

        val historyStudyDates = studyDatesBetween(legalHistoryStart, allCourseDeadline)
        historyStudyDates.forEachIndexed { index, date ->
            val startChapter = chapterBoundary(index, historyStudyDates.size, ExamStudyPlan.legalHistoryChapterCount) + 1
            val endChapter = chapterBoundary(index + 1, historyStudyDates.size, ExamStudyPlan.legalHistoryChapterCount)
            val chapterText = chapterText(startChapter, endChapter)
            put(
                date,
                daily(
                    date,
                    "法制史${chapterText}：9月30日前完成全部新课",
                    law("听众合法硕法制史${chapterText}课程：按朝代、制度和时间线完成当天课程主块"),
                    review("当天章节闭卷时间线、制度比较和正式框架；整章结束后进入配套题并记录唯一主错因"),
                    review(recitationTask(date, "法制史")),
                    english(ExamStudyPlan.dailyVocabularyTaskTitle),
                    english(defaultEnglishTask(date)),
                    politics("政治强化课/核心考案小节 + 1000题25-30道 + 知识点回忆和主错因"),
                    health(if (date == allCourseDeadline) "今日验收四科常规新课全部结束；10月进入第二轮规范表述，11月进入第三轮限时输出" else "新课仍按唯一主线推进，不提前切换到10月二轮任务"),
                ),
            )
        }
        addSundayRestDays(legalHistoryStart, allCourseDeadline)
    }

    override val weeklyOverrides: Map<String, WeeklyStudyPlan> = listOf(
        weekly(
            "2026-07-w4",
            "刑法第3-7章收口后完整休息",
            "2026-07-20 至 2026-07-26",
            "7月25日已完成刑法第7章题目与框架；7月26日为每周完整休息日，不安排单词或补课",
            "7月27日从刑法第8章进入唯一新课主线，不提前启动民法",
        ),
        weekly(
            "2026-07-w5",
            "刑法第8章起按8月15日节点推进",
            "2026-07-27 至 2026-07-31",
            "专业课只学刑法：课程→闭卷骨架→正式框架→配套题→唯一主错因",
            "法理维持第一轮目录树与关键词背诵；英语保留120词与真题主训练",
            "每天按真实课时推进，长章允许跨日，不用章节数制造虚假完成",
        ),
        weekly(
            "2026-08-w1",
            "刑法课程连续推进与第一轮背诵",
            "2026-08-01 至 2026-08-07",
            "继续刑法第8-25章唯一新课主线，完成章当天留下最低可用正式框架和配套题入口",
            "周日完整休息；法理与已学刑法按D1/D3/D7回炉",
        ),
        weekly(
            "2026-08-w2",
            "刑法课程结束与8月15日总验收",
            "2026-08-08 至 2026-08-14",
            "8月12日前听完刑法新课；随后集中补课程账本、重点章题目、主错因与第一轮背诵",
            "民法不得提前插入，刑法一轮在8月15日前完成总收口",
        ),
        weekly(
            "2026-08-w3",
            "刑法收口后启动民法",
            "2026-08-15 至 2026-08-21",
            "8月15日验收刑法；8月16日起按顺序启动民法第1章，不两本书并行听新课",
            "民法按课程、框架、题目、错因闭环；刑法和法理转为背诵复线",
        ),
        weekly(
            "2026-08-w4",
            "民法唯一新课主线",
            "2026-08-22 至 2026-08-31",
            "按9月15日节点均匀推进民法54章，短章可合并，长章按真实课时跨日",
            "周日完整休息；英语和第一轮背诵从当天总预算中切分",
        ),
        weekly(
            "2026-09-w1",
            "民法后半程连续推进",
            "2026-09-01 至 2026-09-07",
            "继续民法唯一新课主线，完成章留下正式框架、配套题入口和唯一主错因",
            "法理、刑法与已学民法继续第一轮目录树和关键词抽背",
        ),
        weekly(
            "2026-09-w2",
            "民法9月15日节点收口",
            "2026-09-08 至 2026-09-15",
            "9月15日前完成民法全部新课与最低可用一轮闭环",
            "9月15日起启动政治；新增政治时间从当天总预算中切分",
        ),
        weekly(
            "2026-09-w3",
            "宪法新课与第二轮规范表述启动",
            "2026-09-16 至 2026-09-21",
            "9月16日起只听宪法新课，按章节完成目录树、正式框架和配套题入口",
            "第一轮继续收口，同时启动较早章节第二轮定义、构成要件与答题骨架",
        ),
        weekly(
            "2026-09-w4",
            "宪法收口并完成法制史全部新课",
            "2026-09-22 至 2026-09-30",
            "9月22日前完成宪法；9月23日切换缓冲；9月24-30日完成法制史第1-7章",
            "9月30日验收四科常规新课全部结束，10月进入第二轮，11月进入第三轮限时输出",
        ),
    ).associateBy { it.id }

    override fun monthlyPlan(base: MonthlyStudyPlan): MonthlyStudyPlan = when (base.month) {
        "2026-07" -> base.copy(
            focus = "刑法第3-7章收口，7月27日起从第8章继续",
            tasks = listOf(
                "7月25日已完成刑法第7章题目与框架；7月26日完整休息，不安排任何学习任务",
                "7月27日起只推进刑法第8章及后续章节，按课程→闭卷骨架→正式框架→配套题→主错因执行",
                "法理进入第一轮目录树与关键词背诵；英语保留每日120词和真题训练",
                "本月不启动民法和政治，不用多本新课并行制造进度",
            ),
        )
        "2026-08" -> base.copy(
            focus = "8月15日前完成刑法，随后启动民法",
            tasks = listOf(
                "8月12日前完成刑法第8-25章课程，8月13-15日集中验收框架、配套题、主错因和第一轮背诵",
                "8月16日起按顺序启动民法第1章；刑法未收口前不得提前听民法新课",
                "每周星期日完整休息；法理与已学专业课按D1/D3/D7持续抽背",
                "英语每天120词并轮换阅读、完形、翻译和新题型，全部计入当天总预算",
            ),
        )
        "2026-09" -> base.copy(
            focus = "9月15日前完成民法，9月30日前完成宪法与法制史",
            tasks = listOf(
                "9月15日前完成民法54章全部新课与最低可用一轮闭环",
                "9月16-22日完成宪法第1-7章；9月23日为收口与切换缓冲",
                "9月24-30日完成法制史第1-7章，9月30日验收四科常规新课全部结束",
                "9月15日起启动政治；第一轮背诵收口并在9月下半月启动第二轮规范表述",
            ),
        )
        else -> base
    }

    /**
     * Some older composables still read ExamStudyPlan directly. Install this
     * overlay into those already-existing collections so the screen cannot show
     * the retired recovery plan while persistence and scheduling use the catalog.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun installIntoLegacyExamPlanViews() {
        (ExamStudyPlan.dailyPlans as? MutableMap<LocalDate, DailyStudyPlan>)
            ?.putAll(dailyOverrides)

        (ExamStudyPlan.weeklyPlans as? MutableList<WeeklyStudyPlan>)?.let { plans ->
            weeklyOverrides.values.forEach { replacement ->
                val index = plans.indexOfFirst { it.id == replacement.id }
                if (index >= 0) plans[index] = replacement
            }
        }

        (ExamStudyPlan.monthlyPlans as? MutableList<MonthlyStudyPlan>)?.let { plans ->
            plans.indices.forEach { index ->
                plans[index] = monthlyPlan(plans[index])
            }
        }
    }

    private fun MutableMap<LocalDate, DailyStudyPlan>.addSundayRestDays(
        start: LocalDate,
        endInclusive: LocalDate,
    ) {
        datesBetween(start, endInclusive)
            .filter { it.dayOfWeek == DayOfWeek.SUNDAY }
            .forEach { date ->
                put(date, DailyStudyPlan(date = date, title = "每周完整休息日", tasks = emptyList()))
            }
    }

    private fun recitationTask(date: LocalDate, currentSubject: String): String = when {
        date < LocalDate.of(2026, 9, 16) ->
            "第一轮背诵：法理当前章节闭卷30-40分钟，并抽背${currentSubject}已学章节的目录树、关键词和构成关系；每章最多3个主块，达到约70%后推进，薄弱点按D1/D3/D7回炉"
        date < LocalDate.of(2026, 10, 1) ->
            "第一轮收口 + 第二轮启动：当前科目做目录树和关键词闭卷；法理、刑法、民法较早章节轮流背定义、构成要件与规范表述40-60分钟"
        date < LocalDate.of(2026, 11, 1) ->
            "第二轮规范表述：按题型闭卷输出定义、构成要件、简答骨架和案例分析步骤，薄弱章隔日回炉"
        else ->
            "第三轮限时输出：默写框架、口头答题和高频点抽背，按考场时间限制完成"
    }

    private fun chapterBoundary(dayIndex: Int, totalDays: Int, chapterCount: Int): Int =
        (dayIndex * chapterCount) / totalDays

    private fun chapterText(startChapter: Int, endChapter: Int): String =
        if (startChapter == endChapter) "第${startChapter}章" else "第${startChapter}-${endChapter}章"

    private fun studyDatesBetween(start: LocalDate, endInclusive: LocalDate): List<LocalDate> =
        datesBetween(start, endInclusive).filter { it.dayOfWeek != DayOfWeek.SUNDAY }

    private fun datesBetween(start: LocalDate, endInclusive: LocalDate): List<LocalDate> {
        val count = ChronoUnit.DAYS.between(start, endInclusive).toInt()
        return (0..count).map { offset -> start.plusDays(offset.toLong()) }
    }

    private fun defaultEnglishTask(date: LocalDate): String = when (date.dayOfWeek.value) {
        1, 4 -> ExamStudyPlan.dailyEnglishReadingTaskTitle
        2, 5 -> ExamStudyPlan.dailyEnglishClozeTaskTitle
        3 -> "英语一真题翻译：限时作答后完成语法还原、译文修订和一个主错因"
        6 -> "英语一真题新题型：限时作答后记录衔接词、指代和段落逻辑"
        else -> ExamStudyPlan.dailyEnglishReviewTaskTitle
    }

    private fun daily(
        date: LocalDate,
        title: String,
        vararg tasks: StudyPlanTask,
    ): DailyStudyPlan = DailyStudyPlan(date = date, title = title, tasks = tasks.toList())

    private fun weekly(
        id: String,
        title: String,
        dateRange: String,
        vararg tasks: String,
    ): WeeklyStudyPlan = WeeklyStudyPlan(id = id, title = title, dateRange = dateRange, tasks = tasks.toList())

    private fun law(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Law)
    private fun review(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Review)
    private fun english(title: String) = StudyPlanTask(title, StudyPlanTaskKind.English)
    private fun politics(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Politics)
    private fun health(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Health)
}
