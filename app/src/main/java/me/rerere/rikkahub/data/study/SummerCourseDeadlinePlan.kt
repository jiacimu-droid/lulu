package me.rerere.rikkahub.data.study

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Hard monthly/weekly milestones with guided but flexible daily execution.
 *
 * Month plans define the non-negotiable month-end destination. Week plans define
 * exact chapter and recitation endpoints. Daily plans still suggest concrete
 * chapters so the learner knows where to start, but those daily suggestions may
 * be skipped, reduced or exceeded according to real course length and condition.
 */
object SummerCourseDeadlinePlan : StudyPlanOverlay {
    private val criminalStart = LocalDate.of(2026, 7, 27)
    private val criminalCourseEnd = LocalDate.of(2026, 8, 12)
    private val criminalDeadline = LocalDate.of(2026, 8, 15)
    private val civilStart = LocalDate.of(2026, 8, 17)
    private val civilDeadline = LocalDate.of(2026, 9, 15)
    private val constitutionStart = LocalDate.of(2026, 9, 16)
    private val constitutionEnd = LocalDate.of(2026, 9, 22)
    private val transitionBuffer = LocalDate.of(2026, 9, 23)
    private val legalHistoryStart = LocalDate.of(2026, 9, 24)
    private val allCourseDeadline = LocalDate.of(2026, 9, 30)

    private val milestones = listOf(
        milestone(
            id = "2026-07-w4",
            title = "刑法第3-7章正式收口与完整休息",
            start = "2026-07-20",
            end = "2026-07-26",
            courseTarget = "7月25日前完成刑法第3-7章合并题、主错因和正式连接框架；7月26日完整休息",
            recitationTarget = "法理第一轮背诵至少完成第1章闭卷验收",
            acceptance = "刑法第3-7章不再残留听课任务；第7章闭环后进入第8章",
        ),
        milestone(
            id = "2026-07-w5",
            title = "刑法听到第13章，法理背到第2章",
            start = "2026-07-27",
            end = "2026-07-31",
            courseTarget = "已确认刑法第8章课程和题目全部完成；本周从第9章推进到第13章，7月31日课程账本必须明确停在第13章",
            recitationTarget = "法理第一轮闭卷背诵完成第1-2章；达到约70%目录树和关键词复述后才能继续",
            acceptance = "刑法第8章保持已完成状态，不再重复安排；第9-13章留下课程记录，至少把第9-10章的配套题入口、正式框架和主错因记入账本",
        ),
        milestone(
            id = "2026-08-w1",
            title = "刑法听到第21章，法理背到第5章",
            start = "2026-08-01",
            end = "2026-08-07",
            courseTarget = "刑法第14-21章课程全部完成，周末课程终点固定为第21章",
            recitationTarget = "法理第一轮背诵完成第3-5章；刑法第9-13章完成第一轮目录树和关键词抽背",
            acceptance = "刑法第14-21章逐章留下课程完成记录；已听完章节完成最低可用框架、配套题入口和唯一主错因",
        ),
        milestone(
            id = "2026-08-w2",
            title = "刑法第25章听完并完成一轮总收口",
            start = "2026-08-08",
            end = "2026-08-14",
            courseTarget = "8月12日前完成刑法第22-25章课程，刑法新课终点固定为第25章",
            recitationTarget = "法理第一轮背诵完成第6-8章；刑法第14-25章完成第一轮关键词背诵登记",
            acceptance = "8月13-14日集中验收刑法第9-25章课程账本、重点章题目、正式框架、主错因和D1/D3/D7回炉日期",
        ),
        milestone(
            id = "2026-08-w3",
            title = "刑法验收完成，民法听到第10章",
            start = "2026-08-15",
            end = "2026-08-21",
            courseTarget = "8月15日完成刑法一轮总验收；8月17日起启动民法并在8月21日前听完第1-10章",
            recitationTarget = "法理第一轮背诵完成第9-10章；民法第一轮目录树和关键词背诵推进到第5章",
            acceptance = "刑法不得继续残留常规新课；民法课程账本明确到第10章，已完成章节留下框架、配套题入口和主错因",
        ),
        milestone(
            id = "2026-08-w4",
            title = "民法听到第27章，法理一轮背完",
            start = "2026-08-22",
            end = "2026-08-31",
            courseTarget = "民法第11-27章课程全部完成，8月月末课程终点固定为第27章",
            recitationTarget = "法理第一轮背诵完成第11-13章并完成全书一轮；民法第一轮背诵完成第6-18章",
            acceptance = "8月31日核对民法课程终点第27章、背诵累计终点第18章以及所有已听完章节的框架、题目和主错因账本",
        ),
        milestone(
            id = "2026-09-w1",
            title = "民法听到第40章，背到第32章",
            start = "2026-09-01",
            end = "2026-09-07",
            courseTarget = "民法第28-40章课程全部完成，周末课程终点固定为第40章",
            recitationTarget = "民法第一轮目录树和关键词背诵完成第19-32章；法理、刑法按D1/D3/D7抽背",
            acceptance = "民法第28-40章课程记录完整；已完成章节至少留下正式框架、配套题入口和唯一主错因",
        ),
        milestone(
            id = "2026-09-w2",
            title = "民法第54章全部听完，背到第45章",
            start = "2026-09-08",
            end = "2026-09-15",
            courseTarget = "9月15日前完成民法第41-54章，民法全部新课终点固定为第54章",
            recitationTarget = "民法第一轮背诵完成第33-45章；较早薄弱章按D1/D3/D7回炉",
            acceptance = "民法第1-54章课程账本完整，最低可用一轮闭环可核对；9月15日起启动政治，不提前挤占专业课",
        ),
        milestone(
            id = "2026-09-w3",
            title = "宪法听到第6章，民法一轮背完",
            start = "2026-09-16",
            end = "2026-09-21",
            courseTarget = "宪法第1-6章课程全部完成，周末课程终点固定为第6章",
            recitationTarget = "民法第一轮背诵完成第46-54章；法理第二轮规范表述推进到第3章",
            acceptance = "民法第一轮背诵全书收口；宪法第1-6章留下目录树、框架、配套题入口和易混点",
        ),
        milestone(
            id = "2026-09-w4",
            title = "宪法与法制史全部新课结束",
            start = "2026-09-22",
            end = "2026-09-30",
            courseTarget = "9月22日完成宪法第7章并收口；9月24-30日完成法制史第1-7章全部课程",
            recitationTarget = "法理第二轮规范表述推进到第6章；刑法第二轮规范表述推进到第5章",
            acceptance = "9月30日验收刑法、民法、宪法、法制史全部常规新课结束；10月不再安排常规新课",
        ),
    )

    private val dailyCourseSuggestions: Map<LocalDate, String> = buildMap {
        addChapterSuggestions("2026-07-27", "2026-07-31", "刑法", 9, 13)
        addChapterSuggestions("2026-08-01", "2026-08-07", "刑法", 14, 21)
        addChapterSuggestions("2026-08-08", "2026-08-12", "刑法", 22, 25)
        addChapterSuggestions("2026-08-17", "2026-08-21", "民法", 1, 10)
        addChapterSuggestions("2026-08-22", "2026-08-31", "民法", 11, 27)
        addChapterSuggestions("2026-09-01", "2026-09-07", "民法", 28, 40)
        addChapterSuggestions("2026-09-08", "2026-09-15", "民法", 41, 54)
        addChapterSuggestions("2026-09-16", "2026-09-21", "宪法", 1, 6)
        put(LocalDate.of(2026, 9, 22), "宪法第7章")
        addChapterSuggestions("2026-09-24", "2026-09-30", "法制史", 1, 7)
    }

    private val dailyRecitationSuggestions: Map<LocalDate, String> = buildMap {
        addChapterSuggestions("2026-07-27", "2026-07-31", "法理第一轮", 1, 2)
        addChapterSuggestions("2026-08-01", "2026-08-07", "法理第一轮", 3, 5)
        addChapterSuggestions("2026-08-01", "2026-08-07", "刑法第一轮抽背", 9, 13)
        addChapterSuggestions("2026-08-08", "2026-08-14", "法理第一轮", 6, 8)
        addChapterSuggestions("2026-08-08", "2026-08-14", "刑法第一轮抽背", 14, 25)
        addChapterSuggestions("2026-08-15", "2026-08-21", "法理第一轮", 9, 10)
        addChapterSuggestions("2026-08-17", "2026-08-21", "民法第一轮", 1, 5)
        addChapterSuggestions("2026-08-22", "2026-08-31", "法理第一轮", 11, 13)
        addChapterSuggestions("2026-08-22", "2026-08-31", "民法第一轮", 6, 18)
        addChapterSuggestions("2026-09-01", "2026-09-07", "民法第一轮", 19, 32)
        addChapterSuggestions("2026-09-08", "2026-09-15", "民法第一轮", 33, 45)
        addChapterSuggestions("2026-09-16", "2026-09-21", "民法第一轮", 46, 54)
        addChapterSuggestions("2026-09-16", "2026-09-21", "法理第二轮", 1, 3)
        addChapterSuggestions("2026-09-22", "2026-09-30", "法理第二轮", 4, 6)
        addChapterSuggestions("2026-09-22", "2026-09-30", "刑法第二轮", 1, 5)
    }

    override val dailyOverrides: Map<LocalDate, DailyStudyPlan> = buildMap {
        studyDatesBetween(criminalStart, criminalCourseEnd).forEach { date ->
            put(
                date,
                guidedCourseDay(
                    date = date,
                    subject = "刑法",
                    courseRule = "从刑法当前真实进度继续听众合法硕刑法课程。第8章课程和题目已经完成，后续不再重复；长章未听完时次日先续本章，不跳章。",
                ),
            )
        }
        addSundayRestDays(criminalStart, criminalCourseEnd)

        datesBetween(criminalCourseEnd.plusDays(1), criminalDeadline).forEach { date ->
            if (date.dayOfWeek == DayOfWeek.SUNDAY) {
                put(date, restDay(date))
            } else {
                val recitationSuggestion = dailyRecitationSuggestions[date]
                put(
                    date,
                    daily(
                        date,
                        "刑法一轮收口：按缺口自由选择验收块",
                        review("本周新课硬目标已经到第25章；今天从课程账本、重点章配套题、正式框架、唯一主错因和D1/D3/D7中选择最大缺口集中完成"),
                        review("今日建议背诵：${recitationSuggestion ?: "按本周剩余背诵章节选择一个块"}。这是推荐落点，可以因状态调整，但本周背诵硬终点不变"),
                        review("刑法第一轮验收按已完成章节进行：闭卷说目录树、构成要件和关键词，薄弱章登记回炉，不要求当天平均背完"),
                        english(ExamStudyPlan.dailyVocabularyTaskTitle),
                        english(defaultEnglishTask(date)),
                    ),
                )
            }
        }

        studyDatesBetween(civilStart, civilDeadline).forEach { date ->
            put(
                date,
                guidedCourseDay(
                    date = date,
                    subject = "民法",
                    courseRule = "从民法当前真实进度继续听众合法硕民法课程。短章可连续完成，长章可以跨日；今日建议只提供起点和方向，不是硬性日验收。",
                ),
            )
        }
        addSundayRestDays(civilStart, civilDeadline)

        studyDatesBetween(constitutionStart, constitutionEnd).forEach { date ->
            put(
                date,
                guidedCourseDay(
                    date = date,
                    subject = "宪法",
                    courseRule = "从宪法当前真实进度继续听众合法硕宪法课程。按真实课时决定今天实际推进多少，不把今日建议当成必须清零的惩罚任务。",
                    includePolitics = true,
                ),
            )
        }
        addSundayRestDays(constitutionStart, constitutionEnd)

        put(
            transitionBuffer,
            daily(
                transitionBuffer,
                "宪法收口与法制史切换缓冲",
                review("核对本月硬目标：宪法第1-7章必须全部完成；只补课程、正式框架、配套题入口和主错因中的最大缺口"),
                review("今日建议背诵：${dailyRecitationSuggestions[transitionBuffer] ?: "法理第二轮与刑法第二轮各一个小块"}；可以按实际状态调整先后"),
                review("写清法制史第1章明日入口；今日只做切换准备，不提前制造新的硬性日章节指标"),
                english(ExamStudyPlan.dailyVocabularyTaskTitle),
                english(defaultEnglishTask(transitionBuffer)),
                politics("政治强化课/核心考案小节 + 1000题25-30道 + 主错因"),
            ),
        )

        studyDatesBetween(legalHistoryStart, allCourseDeadline).forEach { date ->
            put(
                date,
                guidedCourseDay(
                    date = date,
                    subject = "法制史",
                    courseRule = "从法制史当前真实进度继续听众合法硕法制史课程。按朝代和制度连续推进；今日建议可以调整，但9月30日必须到第7章。",
                    includePolitics = true,
                ),
            )
        }
        addSundayRestDays(legalHistoryStart, allCourseDeadline)
    }

    override val weeklyOverrides: Map<String, WeeklyStudyPlan> =
        milestones.associate { item ->
            item.id to WeeklyStudyPlan(
                id = item.id,
                title = item.title,
                dateRange = "${item.start} 至 ${item.end}",
                tasks = listOf(
                    "【本周新课硬终点】${item.courseTarget}",
                    "【本周背诵硬终点】${item.recitationTarget}",
                    "【周末验收】${item.acceptance}",
                    "【每日建议规则】每日待办会给出建议章节，让你知道今天从哪里开始；建议可以不做、少做或多做，但周末硬终点不得变成泛泛的‘继续推进’",
                    "【英语保底】非完整休息日保持120词与真题主训练；英语、背诵和专业课都从当天总预算内切分",
                ),
            )
        }

    override fun monthlyPlan(base: MonthlyStudyPlan): MonthlyStudyPlan = when (base.month) {
        "2026-07" -> base.copy(
            focus = "月末硬目标：刑法听到第13章，法理背到第2章",
            tasks = listOf(
                "【已确认进度】刑法第8章课程和题目已经全部完成，后续计划从第9章开始，不再重复第8章",
                "【新课硬终点】7月31日前完成刑法第9-13章课程，课程账本明确停在第13章",
                "【背诵硬终点】法理第一轮闭卷背诵完成第1-2章",
                "【闭环验收】刑法第9-10章至少留下配套题入口、正式框架和唯一主错因；第11-13章留下课程与目录树记录",
                "【执行方式】月目标和周目标固定；每日给建议章节，但你可以根据真实课时和状态自行选择是否完成",
            ),
        )
        "2026-08" -> base.copy(
            focus = "月末硬目标：刑法全部收口，民法听到第27章",
            tasks = listOf(
                "【新课硬终点】8月12日前听完刑法第25章，8月15日前完成刑法一轮总收口；8月31日前民法课程听到第27章",
                "【背诵硬终点】法理第一轮完成第1-13章；民法第一轮目录树与关键词背诵累计完成第1-18章",
                "【闭环验收】刑法第9-25章课程、重点章题目、正式框架、主错因和回炉日期可核对；民法已学章节留下最低可用闭环",
                "【执行方式】每周写死章节终点，每日给出建议章节；某一天没有完成建议不算整月失败，但必须在周内重新分配",
            ),
        )
        "2026-09" -> base.copy(
            focus = "月末硬目标：四科常规新课全部结束",
            tasks = listOf(
                "【新课硬终点】9月15日前民法听完第54章；9月22日前宪法听完第7章；9月30日前法制史听完第7章",
                "【背诵硬终点】民法第一轮完成第1-54章；法理第二轮规范表述完成第1-6章；刑法第二轮完成第1-5章",
                "【政治硬目标】9月15日起每周完成4-5个政治课题绑定块，每块25-30道1000题并记录主错因",
                "【月末验收】9月30日后不再安排刑法、民法、宪法、法制史常规新课，10月统一进入第二轮、真题与规范表述",
            ),
        )
        else -> base
    }

    /**
     * Older composables still read ExamStudyPlan directly. Remove every overlapping
     * legacy entry first, then install the current plan.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun installIntoLegacyExamPlanViews() {
        val dailyPlans = ExamStudyPlan.dailyPlans as MutableMap<LocalDate, DailyStudyPlan>
        dailyPlans.keys.removeAll { date -> !date.isBefore(criminalStart) && !date.isAfter(allCourseDeadline) }
        dailyPlans.putAll(dailyOverrides)

        val weeklyPlans = ExamStudyPlan.weeklyPlans as MutableList<WeeklyStudyPlan>
        val firstReplacementIndex = weeklyPlans
            .indexOfFirst { it.id in weeklyOverrides.keys }
            .let { index -> if (index >= 0) index else weeklyPlans.size }
        weeklyPlans.removeAll { it.id in weeklyOverrides.keys }
        weeklyPlans.addAll(firstReplacementIndex.coerceAtMost(weeklyPlans.size), weeklyOverrides.values)

        val monthlyPlans = ExamStudyPlan.monthlyPlans as MutableList<MonthlyStudyPlan>
        monthlyPlans.indices.forEach { index ->
            monthlyPlans[index] = monthlyPlan(monthlyPlans[index])
        }
    }

    private fun guidedCourseDay(
        date: LocalDate,
        subject: String,
        courseRule: String,
        includePolitics: Boolean = false,
    ): DailyStudyPlan {
        val target = milestoneFor(date)
        val courseSuggestion = dailyCourseSuggestions[date]
        val recitationSuggestion = dailyRecitationSuggestions[date]
        val tasks = buildList {
            add(
                law(
                    "今日建议进度：${courseSuggestion ?: "从当前未完成章节继续"}。这是今日导航，不是硬性日终点；你可以不做、少做或多做。" +
                        " 本周新课硬目标：${target?.courseTarget ?: "按当前月计划继续"}。$courseRule",
                ),
            )
            add(review("今天只闭环实际完成的章节：整章听完后再集中做正式框架、配套题和唯一主错因；未听完的长章次日先续，不跳章"))
            add(
                review(
                    "今日建议背诵：${recitationSuggestion ?: "从本周剩余背诵章节中选择一个块"}。这是建议落点，可以按状态调整；" +
                        "本周背诵硬目标：${target?.recitationTarget ?: "按当前轮次继续"}。完成后留下闭卷复述痕迹",
                ),
            )
            add(english(ExamStudyPlan.dailyVocabularyTaskTitle))
            add(english(defaultEnglishTask(date)))
            if (includePolitics) {
                add(politics("政治按本周固定题量完成当天一个课题绑定块；从当天总预算中切分，不挤掉专业课硬目标"))
            }
            add(health("今日章节只是建议，不完成可以顺延或重新分配；状态好可多推进，长章或不适时可少推进，但周末必须按硬终点验收"))
        }
        return DailyStudyPlan(
            date = date,
            title = "$subject｜今日建议：${courseSuggestion ?: "从当前章节继续"}",
            tasks = tasks,
        )
    }

    private fun milestoneFor(date: LocalDate): WeeklyMilestone? =
        milestones.firstOrNull { date >= it.start && date <= it.end }

    private fun MutableMap<LocalDate, String>.addChapterSuggestions(
        start: String,
        end: String,
        subject: String,
        startChapter: Int,
        endChapter: Int,
    ) {
        val dates = studyDatesBetween(LocalDate.parse(start), LocalDate.parse(end))
        if (dates.isEmpty() || endChapter < startChapter) return
        val chapterCount = endChapter - startChapter + 1
        dates.forEachIndexed { index, date ->
            val startOffset = index * chapterCount / dates.size
            val endOffset = (((index + 1) * chapterCount / dates.size) - 1).coerceAtLeast(startOffset)
            val suggestedStart = startChapter + startOffset
            val suggestedEnd = (startChapter + endOffset).coerceAtMost(endChapter)
            val chapterText = if (suggestedStart == suggestedEnd) {
                "第${suggestedStart}章"
            } else {
                "第${suggestedStart}-${suggestedEnd}章"
            }
            val suggestion = "$subject$chapterText"
            this[date] = listOfNotNull(this[date], suggestion).joinToString("；")
        }
    }

    private fun MutableMap<LocalDate, DailyStudyPlan>.addSundayRestDays(
        start: LocalDate,
        endInclusive: LocalDate,
    ) {
        datesBetween(start, endInclusive)
            .filter { it.dayOfWeek == DayOfWeek.SUNDAY }
            .forEach { date -> put(date, restDay(date)) }
    }

    private fun restDay(date: LocalDate): DailyStudyPlan =
        DailyStudyPlan(date = date, title = "每周完整休息日", tasks = emptyList())

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

    private fun milestone(
        id: String,
        title: String,
        start: String,
        end: String,
        courseTarget: String,
        recitationTarget: String,
        acceptance: String,
    ): WeeklyMilestone = WeeklyMilestone(
        id = id,
        title = title,
        start = LocalDate.parse(start),
        end = LocalDate.parse(end),
        courseTarget = courseTarget,
        recitationTarget = recitationTarget,
        acceptance = acceptance,
    )

    private fun law(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Law)
    private fun review(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Review)
    private fun english(title: String) = StudyPlanTask(title, StudyPlanTaskKind.English)
    private fun politics(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Politics)
    private fun health(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Health)

    private data class WeeklyMilestone(
        val id: String,
        val title: String,
        val start: LocalDate,
        val end: LocalDate,
        val courseTarget: String,
        val recitationTarget: String,
        val acceptance: String,
    )
}
