package me.rerere.rikkahub.data.study

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Explicit recovery and summer pacing policy.
 *
 * The learner used the weekly recovery day on 2026-07-22. Healthy days from
 * 2026-07-23 now use four hours as the stable baseline. Criminal-law closure
 * follows course -> framework -> questions -> mistake feedback, rather than
 * asking questions before a usable knowledge structure exists.
 */
object CurrentWeekStudyRecovery {
    val usedRecoveryDate: LocalDate = LocalDate.of(2026, 7, 22)
    val replacementStudyDate: LocalDate = LocalDate.of(2026, 7, 26)

    val executionOrderReference: String = """
        专业课闭环顺序必须固定为：
        1. 听完当前章或当前连续单元课程，记录真实有效分钟；课程没结束不做整章题。
        2. 合上资料口述目录树和构成关系，先画一张只含主干、层级、构成要件、易混点的闭卷骨架。
        3. 对照考试分析和课程纠正骨架，形成正式框架图；框架图控制在30-45分钟，不抄教材。
        4. 正式框架完成后再做听课配套题；随后做独立额外题源。
        5. 每道错题只标一个主错因，并把新增易混点或遗漏关系补回原框架；安排隔日和7-14日回炉。
        6. 题目正确率用于诊断，不为了让第一次正确率好看而无限延长画图时间。
    """.trimIndent()

    val criminalLawTimeline: String =
        "7月31日保底完成到刑法第10章闭环，正常目标进入第11章，状态很好可听到第12章；8月20日前后完成25章课程输入，8月31日前完成刑法全科框架、配套题/额外题源、错题回炉和第一轮关键词闭环。"

    private val plans: Map<LocalDate, DailyStudyPlan> = listOf(
        daily(
            LocalDate.of(2026, 7, 23),
            "病后补任务第1段：先搭刑法3-7章框架，再进入合并题",
            review("刑法第3-7章闭卷骨架 25-30 分钟：先不看资料口述章节关系、主干和易混点；卡住处留空，不抄教材"),
            review("刑法第3-7章正式连接框架 35-40 分钟：对照考试分析和课程修正骨架，写主干、层级、构成关系、选择题陷阱和案例入口"),
            law("刑法第3-7章合并题入口 35-45 分钟：正式框架完成后再做，记录题量；每道错题只标一个主错因"),
            review("法理第2章闭卷回忆 25 分钟：复述目录树和关键词，未达到约70%就保留卡点，不惩罚式加时"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english("英语一真题完形 25-30 分钟：补回7月22日未完成块；限时后抓逻辑线索、词义辨析和3个主要错因"),
            health("今天按240分钟健康基线执行；昨天欠账只拆回主线，不熬夜一次清空"),
        ),
        daily(
            LocalDate.of(2026, 7, 24),
            "补任务第2段：刑法合并题主块 + 错因回填",
            law("刑法第3-7章合并题 70-80 分钟：沿正式框架做题，记录题量、正确率和唯一主错因"),
            review("刑法错因回填 25-30 分钟：把题目暴露的遗漏关系和易混点补回原框架，并登记隔日与7-14日重做日期"),
            review("法理第2章闭卷验收 25 分钟：达到约70%后下一学习日进入第3章；未通过只补最卡结构"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english("英语一真题翻译 30-35 分钟：先限时，再复盘1个语法错点和规范表达"),
            health("今日总预算240分钟；题组或翻译超时只能使用机动时间，不能叠加到夜里"),
        ),
        daily(
            LocalDate.of(2026, 7, 25),
            "补任务第3段：刑法3-7章题组收口 + 第一轮关键词",
            law("刑法第3-7章合并题收口 60-70 分钟：完成配套题当前计划量；未完成就继续题目，不开第8章"),
            review("刑法第3-7章框架二次修订 + 第一轮关键词口述 35-40 分钟：只根据题目修补原图，不重画一张漂亮新图"),
            review("法理第3章第一轮入口 25 分钟；如果第2章仍未达到约70%，则只补第2章最卡结构"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english("英语一真题阅读 35-40 分钟：限时后写题干定位、原文证据、唯一主错因和正确选项理由"),
            health("今天按240分钟执行；新题型只在主线提前完成后加码，不完成不顺延"),
        ),
        daily(
            LocalDate.of(2026, 7, 26),
            "本周已用休息日后的轻补日：刑法周验收，不开第8章",
            review("本周刑法验收 45-50 分钟：核对第3-7章正式框架、合并题题量、主错因、回炉日期和关键词口述，只补一个最大缺口"),
            review("法理当前章节闭卷回忆 25 分钟：处理本周卡点，留下下一章明确入口"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english(ExamStudyPlan.dailyEnglishReviewTaskTitle),
            review("周计划复盘 25-30 分钟：记录真实有效分钟和完成率，确认7月27日刑法第8章入口"),
            health("7月22日已使用本周恢复日；今天安排180分钟轻补，不启动刑法第8章，不补全部历史欠账"),
        ),
        daily(
            LocalDate.of(2026, 7, 27),
            "四小时基线：刑法第8章课程 + 闭卷骨架",
            law("确认第3-7章闭环后，听众合法硕刑法第8章课程 110-130 分钟；记录原始分钟和实际有效分钟"),
            review("第8章课程结束后闭卷口述并画骨架 25-30 分钟；若课程未结束，本块改为整理当前小节关系，不做整章题"),
            review("法理当前章节闭卷 25 分钟"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english(ExamStudyPlan.dailyEnglishReadingTaskTitle),
            health("今日总预算240分钟；课程没有结束就不为了赶章节数提前做整章题"),
        ),
        daily(
            LocalDate.of(2026, 7, 28),
            "四小时基线：当前章收口 + 正式框架 + 配套题",
            law("继续当前刑法章节课程 80-100 分钟，听完后才进入正式框架"),
            review("当前章正式框架 30-40 分钟：在闭卷骨架上修正主干、层级、构成关系和易混点"),
            law("正式框架完成后做当前章配套题 35-45 分钟；课程未听完则本块顺延，不提前刷整章题"),
            review("法理当前章节闭卷 25 分钟"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english(ExamStudyPlan.dailyEnglishClozeTaskTitle),
        ),
        daily(
            LocalDate.of(2026, 7, 29),
            "四小时基线：刑法课程主块 + 框架先行",
            law("听众合法硕刑法当前未完成章节课程 110-130 分钟；按有效分钟推进，不按章节标题制造进度"),
            review("课程完成的章节先画闭卷骨架并校正成正式框架 30-40 分钟，再允许进入题目"),
            review("法理当前章节闭卷 25 分钟"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english("英语一真题新题型 30-35 分钟：记录段落关系、指代和唯一主错因"),
        ),
        daily(
            LocalDate.of(2026, 7, 30),
            "四小时基线：当前章题目闭环 + 下一章课程",
            law("已完成正式框架的刑法章节做配套题和错因回填 55-65 分钟；未画完框架不得跳过"),
            law("听众合法硕刑法下一未完成章节课程 90-110 分钟；记录实际有效分钟"),
            review("法理当前章节闭卷 25 分钟"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english(ExamStudyPlan.dailyEnglishReadingTaskTitle),
        ),
        daily(
            LocalDate.of(2026, 7, 31),
            "七月验收：保底刑法10章闭环，正常进入11章",
            review("刑法7月验收：按课程有效分钟、正式框架、配套题题量和错因逐项核对；保底第10章完整闭环，正常目标进入第11章，状态很好可听到第12章"),
            law("若第10章已闭环，继续听众合法硕刑法第11章或当前未完成章课程 90-120 分钟；未闭环先补最大缺口"),
            review("法理当前章节闭卷 25 分钟，并写清8月从哪一章继续"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english(ExamStudyPlan.dailyEnglishReviewTaskTitle),
            health("今日240分钟；月底目标按真实课程分钟和闭环痕迹验收，不把只听了一部分写成学完"),
        ),
    ).associateBy { it.date }

    private val correctedWeeks: Map<String, WeeklyStudyPlan> = listOf(
        WeeklyStudyPlan(
            id = "2026-07-w4",
            title = "病后恢复周：先框架、后合并题，恢复四小时基线",
            dateRange = "2026-07-20 至 2026-07-26",
            tasks = listOf(
                "负荷：7月22日因身体不适已使用本周恢复日；7月23-25日各按240分钟健康基线，7月26日按180分钟轻补。本周不再设置第二个完整休息日",
                "刑法：第3-7章课程已完成，先完成闭卷骨架和正式连接框架，再做合并题；题后把主错因补回原框架并登记隔日/7-14日回炉。这些未闭环前不启动第8章",
                "法理：第2章闭卷达到约70%后进入第3章；未达到只补最卡结构，不按日期硬跳章",
                "英语：每天120个单词；补完形、翻译、阅读和周复盘，英语从240分钟总预算内切分",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-07-w5",
            title = "四小时基线：刑法第8章后连续推进",
            dateRange = "2026-07-27 至 2026-07-31",
            tasks = listOf(
                "负荷：每天240分钟有效学习，不含吃饭和运动；专业课约150-165分钟，法理背诵25分钟，英语45-55分钟，其余为错因/超时缓冲",
                "刑法：从第8章开始按课程→闭卷骨架→正式框架→配套题→错因回填顺序推进；五天累计新增课程有效输入目标600-750分钟",
                "月底章节目标：保底第10章完成课程、正式框架和配套题闭环；正常进入第11章；若后续章节课时较短且状态稳定，可听到第12章，但未做题的章节不得写成完整闭环",
                "法理和英语：法理当前章节连续闭卷；每天120个单词，完成阅读、完形、新题型和周复盘，不为了追刑法完全取消英语主训练",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-08-w1",
            title = "四小时稳定周：刑法课程与章节闭环交替",
            dateRange = "2026-08-01 至 2026-08-07",
            tasks = listOf(
                "负荷：健康日240分钟，周日休息；先把四小时稳定完成，不直接跳到原计划的五六小时",
                "刑法：课程日完成120-150分钟有效输入并画框架；闭环日完成正式框架、配套题和错因回填。按真实课时争取推进到第14-16章范围",
                "每个章节/连续单元都按课程→框架→配套题→额外题源→错题回炉执行；框架必须在第一轮整章题之前完成，题后只修订原框架",
                "法理安排3次闭卷；英语每天120个单词，完成3篇阅读、2次完形、新题型和翻译各1次",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-08-w2",
            title = "刑法中后段推进：四小时稳定后小幅加量",
            dateRange = "2026-08-08 至 2026-08-14",
            tasks = listOf(
                "负荷：8月8日仍按240分钟；前一周完成率达到80%、至少4天白天完成且睡眠稳定，8月10日起才升到270分钟",
                "刑法：继续课程与闭环交替，按真实课时争取进入第18-20章范围；完成正式框架前不做整章题，做题后把错因回填原图",
                "法理连续章节3次闭卷，刑法已闭环章节抽背2次；英语完成3篇阅读、2次完形、新题型和翻译各1次",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-08-w3",
            title = "刑法课程收口窗口",
            dateRange = "2026-08-15 至 2026-08-20",
            tasks = listOf(
                "负荷：完成率和睡眠继续达标时升到300分钟；否则维持270分钟，不靠熬夜追章数",
                "刑法：目标在8月20日前后听完第25章课程；章节课时不均，因此允许前后浮动3天。课程听完不等于刑法完成",
                "同步补齐各章闭卷骨架、正式框架和配套题；至少完成一次主观题答题骨架输出，并列出尚欠的额外题源与错题回炉",
                "英语和法理不断线，仍从每日总预算内切分",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-08-w4",
            title = "刑法全科闭环与民法条件启动",
            dateRange = "2026-08-21 至 2026-08-31",
            tasks = listOf(
                "负荷：健康日300分钟；连续达标且身体稳定时，8月24日起可使用330分钟档，周日仍休息",
                "刑法：8月31日前完成25章课程账本、正式框架、配套题/独立额外题源、错题回炉日期和第一轮关键词验收；优先补高频与未闭环章，不重听已完成课程",
                "只有刑法课程及最近章节的框架、配套题和主要错题已经收口，次日才启动民法；若刑法仍欠大量闭环，不为日期好看强行双开",
                "法理第1-6章第一轮继续验收；英语每周3-4篇阅读、2次完形、新题型和翻译各1次",
            ),
        ),
    ).associateBy { it.id }

    private val monthlyAllocations: Map<YearMonth, StudyMonthAllocation> = mapOf(
        YearMonth.of(2026, 7) to StudyMonthAllocation(2_100, 1_380, 420, 0, 300),
        YearMonth.of(2026, 8) to StudyMonthAllocation(7_410, 5_220, 1_560, 0, 630),
        YearMonth.of(2026, 9) to StudyMonthAllocation(9_780, 6_000, 1_680, 1_320, 780),
        YearMonth.of(2026, 10) to StudyMonthAllocation(11_790, 6_720, 2_100, 2_100, 870),
        YearMonth.of(2026, 11) to StudyMonthAllocation(12_000, 6_000, 2_400, 2_700, 900),
        YearMonth.of(2026, 12) to StudyMonthAllocation(6_240, 3_360, 840, 1_380, 660),
    )

    init {
        installIntoExamStudyPlan()
    }

    fun installIntoExamStudyPlan() {
        @Suppress("UNCHECKED_CAST")
        (ExamStudyPlan.dailyPlans as? MutableMap<LocalDate, DailyStudyPlan>)?.putAll(plans)

        @Suppress("UNCHECKED_CAST")
        val mutableWeeks = ExamStudyPlan.weeklyPlans as? MutableList<WeeklyStudyPlan>
        mutableWeeks?.let { weeks ->
            weeks.indices.forEach { index ->
                correctedWeeks[weeks[index].id]?.let { corrected -> weeks[index] = corrected }
            }
        }

        @Suppress("UNCHECKED_CAST")
        val mutableMonths = ExamStudyPlan.monthlyPlans as? MutableList<MonthlyStudyPlan>
        mutableMonths?.let { months ->
            months.indices.forEach { index ->
                val plan = months[index]
                val month = runCatching { YearMonth.parse(plan.month) }.getOrNull() ?: return@forEach
                val allocation = monthlyAllocations[month] ?: return@forEach
                val correctedTasks = plan.tasks
                    .filterNot {
                        it.startsWith("容量校准：") ||
                            it.startsWith("总量倒推：") ||
                            it.startsWith("刑法节点：")
                    }
                    .map { task ->
                        if (month == YearMonth.of(2026, 7)) {
                            task.replace(
                                "7 月 26 日完整休息",
                                "7月22日已使用本周恢复日，7月26日改为180分钟轻补",
                            )
                        } else {
                            task
                        }
                    } + allocation.description(month) +
                    if (month in setOf(YearMonth.of(2026, 7), YearMonth.of(2026, 8))) {
                        listOf("刑法节点：$criminalLawTimeline")
                    } else {
                        emptyList()
                    }
                months[index] = plan.copy(tasks = correctedTasks)
            }
        }
    }

    fun planFor(date: LocalDate): DailyStudyPlan? = plans[date]

    fun plannedMinutes(date: LocalDate): Int = when {
        date == usedRecoveryDate -> 0
        date in LocalDate.of(2026, 7, 23)..LocalDate.of(2026, 7, 25) -> 240
        date == replacementStudyDate -> 180
        date in LocalDate.of(2026, 7, 27)..LocalDate.of(2026, 8, 9) ->
            if (date.dayOfWeek == DayOfWeek.SUNDAY) 0 else 240
        date in LocalDate.of(2026, 8, 10)..LocalDate.of(2026, 8, 16) ->
            if (date.dayOfWeek == DayOfWeek.SUNDAY) 0 else 270
        date in LocalDate.of(2026, 8, 17)..LocalDate.of(2026, 8, 23) ->
            if (date.dayOfWeek == DayOfWeek.SUNDAY) 0 else 300
        date in LocalDate.of(2026, 8, 24)..LocalDate.of(2026, 8, 31) ->
            if (date.dayOfWeek == DayOfWeek.SUNDAY) 0 else 330
        else -> ExamStudyPlan.plannedStudyMinutes(date)
    }

    fun applyToState(state: StudyState, date: LocalDate): StudyState {
        installIntoExamStudyPlan()
        val plan = planFor(date) ?: return state
        val dateText = date.toString()
        val manualTasks = state.tasks.filter { it.source != StudyTaskSource.Plan }
        val previousByTitle = state.tasks
            .filter { it.source == StudyTaskSource.Plan }
            .associateBy { it.title }
        val planTasks = plan.tasks.mapIndexed { index, task ->
            val title = "${task.kind.label}｜${task.title}"
            val previous = previousByTitle[title]
            StudyTask(
                id = "recovery-plan-$dateText-$index",
                title = title,
                done = previous?.done ?: false,
                createdAt = previous?.createdAt ?: System.currentTimeMillis(),
                completedAt = previous?.completedAt,
                completionRewardClaimed = previous?.completionRewardClaimed ?: previous?.done ?: false,
                source = StudyTaskSource.Plan,
            )
        }
        val expectedSchedule = scheduleFor(date).orEmpty()
        val currentPlanTitles = state.tasks
            .filter { it.source == StudyTaskSource.Plan }
            .map { it.title }
        val nextPlanTitles = planTasks.map { it.title }
        if (
            state.activePlanDate == dateText &&
            currentPlanTitles == nextPlanTitles &&
            state.generatedSchedules[dateText] == expectedSchedule
        ) return state
        return state.copy(
            today = dateText,
            tasks = planTasks + manualTasks,
            activePlanDate = dateText,
            superMomentAvailable = false,
            generatedSchedules = state.generatedSchedules + (dateText to expectedSchedule),
        )
    }

    fun scheduleFor(date: LocalDate): List<StudyScheduleBlock>? {
        val plan = planFor(date) ?: return null
        val budget = plannedMinutes(date)
        val coreTasks = plan.tasks.filter { it.kind != StudyPlanTaskKind.Health }
        val professional = coreTasks
            .filter { it.kind == StudyPlanTaskKind.Law || it.kind == StudyPlanTaskKind.Review }
            .joinToString("；") { it.title }
        val english = coreTasks.filter { it.kind == StudyPlanTaskKind.English }.joinToString("；") { it.title }
        return listOf(
            StudyScheduleBlock("09:30-09:40", "低阻力启动", "喝水、活动，写下今天刑法当前所处的闭环步骤。"),
            StudyScheduleBlock("10:00-12:00", "刑法主块", professional),
            StudyScheduleBlock("14:00-14:30", "法理/框架续段", "优先完成当前法理闭卷或刑法正式框架；不得跳过框架直接做整章题。"),
            StudyScheduleBlock("15:00-15:40", "单词与英语主训练", english),
            StudyScheduleBlock("16:00-${if (budget >= 240) "16:40" else "16:10"}", "闭环续段与缓冲", "继续题目、错因回填或英语复盘；今日有效学习总预算约${budget}分钟，到点停止。"),
        )
    }

    fun dynamicSchedulePrompt(
        date: LocalDate,
        presetPlan: DailyStudyPlan,
        tasks: List<StudyTask>,
        currentTime: LocalTime,
    ): String? {
        if (planFor(date) == null) return null
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val unfinished = tasks.filterNot { it.done }
        return buildString {
            appendLine(executionOrderReference)
            appendLine()
            appendLine("刑法进度节点：$criminalLawTimeline")
            appendLine("日期：$date；当前时间：${currentTime.format(formatter)}。")
            appendLine("7月22日已作为本周恢复日，今天不得再以补病假为由无限叠加任务。")
            appendLine("今日有效学习总预算约${plannedMinutes(date)}分钟，包含专业课、背诵和英语。")
            appendLine("今日主题：${presetPlan.title}")
            appendLine("今日预制任务：")
            presetPlan.tasks.forEachIndexed { index, task ->
                appendLine("${index + 1}. ${task.kind.label}｜${task.title}")
            }
            appendLine("当前未完成待办：")
            if (unfinished.isEmpty()) appendLine("- 无") else unfinished.forEach { appendLine("- ${it.title}") }
            appendLine("从当前时间或之后开始排；已经过去的时间块不得补写。")
            appendLine("课程未结束时不做整章题；课程结束后必须先完成闭卷骨架和正式框架，再做配套题。")
            appendLine("只输出时间表，每行严格使用：HH:mm-HH:mm｜标题｜具体安排。")
        }
    }

    fun capacityAudit(
        start: LocalDate = LocalDate.of(2026, 7, 23),
        endInclusive: LocalDate = LocalDate.of(2026, 12, 18),
    ): StudyCapacityAudit {
        require(!endInclusive.isBefore(start))
        val minutesByMonth = linkedMapOf<YearMonth, Int>()
        var cursor = start
        while (!cursor.isAfter(endInclusive)) {
            val month = YearMonth.from(cursor)
            minutesByMonth[month] = minutesByMonth.getOrDefault(month, 0) + plannedMinutes(cursor)
            cursor = cursor.plusDays(1)
        }
        val rawCourseMinutes = ExamStudyPlan.criminalLawCourseMinutes +
            ExamStudyPlan.civilLawCourseMinutes +
            ExamStudyPlan.constitutionalLawCourseMinutes +
            ExamStudyPlan.legalHistoryCourseMinutes
        return StudyCapacityAudit(
            start = start,
            endInclusive = endInclusive,
            minutesByMonth = minutesByMonth,
            totalMinutes = minutesByMonth.values.sum(),
            professionalRawCourseMinutes = rawCourseMinutes,
            professionalEffectiveInputFloorMinutes = rawCourseMinutes / 2,
            subjectMinutes = mapOf(
                StudyPlanSubject.Professional to monthlyAllocations.values.sumOf { it.professionalMinutes },
                StudyPlanSubject.English to monthlyAllocations.values.sumOf { it.englishMinutes },
                StudyPlanSubject.Politics to monthlyAllocations.values.sumOf { it.politicsMinutes },
                StudyPlanSubject.Buffer to monthlyAllocations.values.sumOf { it.bufferMinutes },
            ),
        )
    }

    private fun daily(
        date: LocalDate,
        title: String,
        vararg tasks: StudyPlanTask,
    ): DailyStudyPlan = DailyStudyPlan(date = date, title = title, tasks = tasks.toList())

    private fun law(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Law)
    private fun review(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Review)
    private fun english(title: String) = StudyPlanTask(title, StudyPlanTaskKind.English)
    private fun health(title: String) = StudyPlanTask(title, StudyPlanTaskKind.Health)
}

private data class StudyMonthAllocation(
    val totalMinutes: Int,
    val professionalMinutes: Int,
    val englishMinutes: Int,
    val politicsMinutes: Int,
    val bufferMinutes: Int,
) {
    init {
        require(totalMinutes == professionalMinutes + englishMinutes + politicsMinutes + bufferMinutes)
    }

    fun description(month: YearMonth): String =
        "容量校准：${month.monthValue}月总预算约${minutesText(totalMinutes)}；专业课（含听课、框架、题源、错题、背诵和专业课套卷）${minutesText(professionalMinutes)}，英语${minutesText(englishMinutes)}，政治${minutesText(politicsMinutes)}，机动/深复盘${minutesText(bufferMinutes)}。各科必须从总预算内切分，不额外叠加。"

    private fun minutesText(minutes: Int): String {
        val hours = minutes / 60
        val remainder = minutes % 60
        return if (remainder == 0) "${hours}小时" else "${hours}小时${remainder}分钟"
    }
}

enum class StudyPlanSubject {
    Professional,
    English,
    Politics,
    Buffer,
}

data class StudyCapacityAudit(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val minutesByMonth: Map<YearMonth, Int>,
    val totalMinutes: Int,
    val professionalRawCourseMinutes: Int,
    val professionalEffectiveInputFloorMinutes: Int,
    val subjectMinutes: Map<StudyPlanSubject, Int>,
) {
    val totalHours: Double get() = totalMinutes / 60.0
    val professionalRawCourseHours: Double get() = professionalRawCourseMinutes / 60.0
    val professionalEffectiveInputFloorHours: Double get() = professionalEffectiveInputFloorMinutes / 60.0
}
