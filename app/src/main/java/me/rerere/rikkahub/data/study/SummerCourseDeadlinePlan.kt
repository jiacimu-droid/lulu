package me.rerere.rikkahub.data.study

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Daily chapter ledger for the 2026 summer first-pass course deadlines.
 *
 * The plan follows the user's current real progress: criminal law chapters 1-7
 * are closed, criminal law finishes by August 15, civil law finishes by
 * September 15, and constitutional law plus legal history finish before
 * October. Every date has a concrete next chapter instead of a generic
 * "continue the current course" placeholder.
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
        datesBetween(criminalStart, criminalLastCourseDate).forEachIndexed { index, date ->
            val chapter = 8 + index
            put(
                date,
                daily(
                    date,
                    "刑法第${chapter}章：当天推进并留下闭环入口",
                    law("听众合法硕刑法第${chapter}章课程：按本章真实课时连续推进；长章当天至少完成一个90-120分钟主块，未听完时次日先续本章，不跳章"),
                    review("刑法第${chapter}章课程后闭卷复述目录树、构成关系和关键词；整章听完后完成正式框架图，并集中进入配套题与主错因整理"),
                    review("法理当前章节闭卷背诵30-40分钟：达到约70%目录树和关键词后推进，薄弱点按D1/D3/D7回炉"),
                    english(ExamStudyPlan.dailyVocabularyTaskTitle),
                    english(defaultEnglishTask(date)),
                    health("今日所有科目从当天总预算内切分；专业课优先，不用熬夜把长章强行清零"),
                ),
            )
        }

        datesBetween(criminalLastCourseDate.plusDays(1), criminalDeadline).forEach { date ->
            put(
                date,
                daily(
                    date,
                    "刑法一轮收口：课程、框架、题目与错因总验收",
                    review("核对刑法第8-25章课程账本：补完尚未结束的最后课程小节，不再新增其他科目新课"),
                    law("集中完成刑法尚未闭环章节的配套题、唯一主错因和隔日/7-14日回炉日期；优先重点章，不追求把所有二刷一次清零"),
                    review("修订刑法总框架和第一轮关键词口述痕迹，确认8月16日可以正式切换民法"),
                    review("法理当前章节闭卷背诵30-40分钟"),
                    english(ExamStudyPlan.dailyVocabularyTaskTitle),
                    english(defaultEnglishTask(date)),
                ),
            )
        }

        val civilDates = datesBetween(civilStart, civilDeadline)
        civilDates.forEachIndexed { index, date ->
            val startChapter = civilBoundary(index, civilDates.size) + 1
            val endChapter = civilBoundary(index + 1, civilDates.size)
            val chapterText = if (startChapter == endChapter) {
                "第${startChapter}章"
            } else {
                "第${startChapter}-${endChapter}章"
            }
            put(
                date,
                daily(
                    date,
                    "民法${chapterText}：按9月15日节点均匀推进",
                    law("听众合法硕民法${chapterText}课程：两个短章可同日连续完成；遇到长章按真实课时占满主块，不用章节数伪造进度"),
                    review("当天已听完章节闭卷复述目录树和关键词，形成最低可用正式框架；整章完成后集中做配套题并记录唯一主错因"),
                    review("法理或已学刑法复线30-40分钟：闭卷抽背、错题回炉或框架修订三选一，不开启另一门新书"),
                    english(ExamStudyPlan.dailyVocabularyTaskTitle),
                    english(defaultEnglishTask(date)),
                    health("民法54章按31天均匀分配；当天章节过长时先保证连续课程输入，闭环缺口进入随后两天，不熬夜清零"),
                ),
            )
        }

        datesBetween(constitutionStart, constitutionEnd).forEachIndexed { index, date ->
            val chapter = index + 1
            put(
                date,
                daily(
                    date,
                    "宪法第${chapter}章：课程与最低可用闭环",
                    law("听众合法硕宪法第${chapter}章课程：完成当天课程主块并记录实际有效分钟"),
                    review("宪法第${chapter}章闭卷目录树、正式框架和配套题入口；写出易混点与一条主观题规范表述"),
                    review("刑法/民法旧章抽背或错题回炉30-40分钟"),
                    english(ExamStudyPlan.dailyVocabularyTaskTitle),
                    english(defaultEnglishTask(date)),
                    politics("政治按当天总预算完成强化课/核心考案小节与1000题启动块，不挤掉专业课主线"),
                ),
            )
        }

        put(
            transitionBuffer,
            daily(
                transitionBuffer,
                "宪法收口与法制史切换缓冲",
                review("核对宪法第1-7章课程、正式框架、配套题入口和主错因，只补最大缺口"),
                review("整理宪法总框架与第一轮关键词口述，写清明日法制史第1章入口"),
                review("刑法或民法旧章错题回炉30-40分钟"),
                english(ExamStudyPlan.dailyVocabularyTaskTitle),
                english(defaultEnglishTask(transitionBuffer)),
                politics("政治强化课/核心考案小节 + 1000题25-30道 + 主错因"),
            ),
        )

        datesBetween(legalHistoryStart, allCourseDeadline).forEachIndexed { index, date ->
            val chapter = index + 1
            put(
                date,
                daily(
                    date,
                    "法制史第${chapter}章：9月30日前完成全部新课",
                    law("听众合法硕法制史第${chapter}章课程：按朝代、制度和时间线完成当天课程主块"),
                    review("法制史第${chapter}章闭卷时间线、制度比较和正式框架；整章结束后进入配套题并记录唯一主错因"),
                    review("刑法、民法或宪法旧章抽背/错题回炉30-40分钟"),
                    english(ExamStudyPlan.dailyVocabularyTaskTitle),
                    english(defaultEnglishTask(date)),
                    politics("政治强化课/核心考案小节 + 1000题25-30道 + 知识点回忆和主错因"),
                    health(if (date == allCourseDeadline) "今日验收四科常规新课全部结束；10月起切换二轮背诵、真题和错题主线" else "新课仍按唯一主线推进，不提前切换到10月二轮任务"),
                ),
            )
        }
    }

    override val weeklyOverrides: Map<String, WeeklyStudyPlan> = emptyMap()

    private fun civilBoundary(dayIndex: Int, totalDays: Int): Int =
        (dayIndex * ExamStudyPlan.civilLawChapterCount) / totalDays

    private fun datesBetween(start: LocalDate, endInclusive: LocalDate): List<LocalDate> {
        val count = ChronoUnit.DAYS.between(start, endInclusive).toInt()
        return (0..count).map(start::plusDays)
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
