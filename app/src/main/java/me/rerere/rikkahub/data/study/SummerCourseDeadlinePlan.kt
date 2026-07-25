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

    override val weeklyOverrides: Map<String, WeeklyStudyPlan> = emptyMap()

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
            "第一轮背诵：法理当前章节闭卷30-40分钟，并抽背$currentSubject已学章节的目录树、关键词和构成关系；每章最多3个主块，达到约70%后推进，薄弱点按D1/D3/D7回炉"
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

    private fun law(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Law)
    private fun review(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Review)
    private fun english(title: String) = StudyPlanTask(title, StudyPlanTaskKind.English)
    private fun politics(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Politics)
    private fun health(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Health)
}
