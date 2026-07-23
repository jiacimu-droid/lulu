package me.rerere.rikkahub.data.study

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Explicit recovery and accelerated summer course-input policy.
 *
 * The weekly recovery day was used on 2026-07-22. The plan now separates
 * "course input finished" from "the subject is fully closed": all four regular
 * new-course streams must finish by 2026-09-14, while questions, mistake review
 * and later recitation continue as review lines.
 */
object CurrentWeekStudyRecovery {
    val usedRecoveryDate: LocalDate = LocalDate.of(2026, 7, 22)
    val replacementStudyDate: LocalDate = LocalDate.of(2026, 7, 26)

    val executionOrderReference: String = """
        专业课每章或连续单元的第一轮闭环顺序必须固定为：
        1. 听完当前章/连续单元课程，记录原始分钟与实际有效分钟；课程没有结束，不做整章题。
        2. 合上资料口述目录树和构成关系，先画只含主干、层级、构成要件、易混点的闭卷骨架。
        3. 对照考试分析和课程纠正骨架，形成正式框架图；一般控制在30-45分钟，不抄教材。
        4. 正式框架完成后再做听课配套题，再按A/B/C类章节进入额外题源或分科真题。
        5. 每道错题只标一个主错因，把新增易混点或遗漏关系补回原框架，并安排隔日与7-14日回炉。
        6. 为保证9月15日前结束常规新课，切换下一本书的门槛是：本书课程全部听完、各章至少有可用框架、重点章配套题已启动；额外题源、错题二刷和后续背诵作为复线继续，不得用“全部题目都清零”拖延下一本新课。
        7. 框架先于整章题，但不能为了第一次正确率无限画图；题目本身就是检验和修订框架的工具。
    """.trimIndent()

    val criminalLawTimeline: String =
        "7月31日保底完成刑法第12章闭环，正常进入第13-14章；8月9日前听完刑法25章课程并完成各章最低可用框架，8月10日启动民法；8月16日前补齐刑法重点章配套题第一轮和主要错因；8月27日前听完民法课程，8月28日启动宪法；9月4日前听完宪法，9月5日启动法制史；9月14日前听完法制史，9月15日起不再保留常规新课。"

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
            "五小时增长档：刑法第8章后连续推进",
            law("听众合法硕刑法当前未完成章节课程 150-180 分钟：优先连续输入，记录原始分钟与有效分钟"),
            review("课程结束的章节完成闭卷骨架和正式框架 40-45 分钟；课程未结束就只做小节关系整理"),
            review("法理当前章节闭卷 25 分钟"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english(ExamStudyPlan.dailyEnglishReadingTaskTitle),
            health("今日总预算300分钟；新课输入是主块，但英语和法理不得被完全取消"),
        ),
        daily(
            LocalDate.of(2026, 7, 28),
            "五小时增长档：课程主块 + 框架先行 + 题目入口",
            law("继续刑法课程 140-170 分钟；按真实课时推进，不按章节标题制造进度"),
            review("已结束章节先完成闭卷骨架和正式框架 35-45 分钟"),
            law("仅对正式框架已经完成的章节做配套题 35-45 分钟，并把主错因回填原图"),
            review("法理当前章节闭卷 25 分钟"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english(ExamStudyPlan.dailyEnglishClozeTaskTitle),
        ),
        daily(
            LocalDate.of(2026, 7, 29),
            "五小时增长档：刑法课程连续输入日",
            law("听众合法硕刑法当前未完成章节课程 180-210 分钟；中间休息但不切去另一门新书"),
            review("完成章闭卷骨架与正式框架 35-45 分钟；未听完的章不提前做整章题"),
            review("法理当前章节闭卷 25 分钟"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english("英语一真题新题型 30-35 分钟：记录段落关系、指代和唯一主错因"),
        ),
        daily(
            LocalDate.of(2026, 7, 30),
            "五小时增长档：框架后做题 + 下一章课程",
            law("已完成正式框架的刑法章节做配套题和错因回填 60-75 分钟"),
            law("听众合法硕刑法下一未完成章节课程 130-160 分钟；记录实际有效分钟"),
            review("法理当前章节闭卷 25 分钟"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english(ExamStudyPlan.dailyEnglishReadingTaskTitle),
        ),
        daily(
            LocalDate.of(2026, 7, 31),
            "七月验收：保底刑法12章闭环，正常进入13-14章",
            review("刑法7月验收：按课程有效分钟、正式框架、配套题和错因逐项核对；保底第12章闭环，正常进入第13-14章"),
            law("继续听众合法硕刑法当前未完成章节课程 150-180 分钟；若第12章尚未闭环，先补最大缺口"),
            review("法理当前章节闭卷 25 分钟，并写清8月从哪一章继续"),
            english(ExamStudyPlan.dailyVocabularyTaskTitle),
            english(ExamStudyPlan.dailyEnglishReviewTaskTitle),
            health("今日300分钟；月底目标按真实分钟和闭环痕迹验收，不把只听一部分写成学完"),
        ),
    ).associateBy { it.date }

    private val correctedWeeks: Map<String, WeeklyStudyPlan> = listOf(
        WeeklyStudyPlan(
            id = "2026-07-w4",
            title = "病后恢复周：先框架、后合并题，恢复四小时基线",
            dateRange = "2026-07-20 至 2026-07-26",
            tasks = listOf(
                "负荷：7月22日因身体不适已使用本周恢复日；7月23-25日各按240分钟，7月26日按180分钟轻补。本周不再设置第二个完整休息日",
                "刑法：第3-7章课程已完成，先完成闭卷骨架和正式连接框架，再做合并题；题后把主错因补回原框架并登记隔日/7-14日回炉",
                "法理：第2章闭卷达到约70%后进入第3章；未达到只补最卡结构，不按日期硬跳章",
                "英语：每天120个单词；补完形、翻译、阅读和周复盘，英语从总预算内切分",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-07-w5",
            title = "五小时增长档：刑法课程连续推进",
            dateRange = "2026-07-27 至 2026-07-31",
            tasks = listOf(
                "负荷：每天300分钟有效学习；刑法新课与框架约210-225分钟，法理约25分钟，英语约45-55分钟，其余为错因和超时缓冲",
                "刑法：从第8章继续，按课程→闭卷骨架→正式框架→配套题→错因回填推进；五天新增课程有效输入目标750-900分钟",
                "月底章节目标：保底第12章完成课程、正式框架和配套题闭环，正常进入第13-14章；状态很好可以继续听第15章，但未做框架和题目的章只记课程进度",
                "法理和英语不断线；不得为了追刑法把英语主训练整周取消",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-08-w1",
            title = "刑法课程冲刺周：5.5小时档",
            dateRange = "2026-08-01 至 2026-08-07",
            tasks = listOf(
                "负荷：健康日330分钟，周日休息；新课输入约210-240分钟，框架/重点题约45-60分钟，英语和法理约70-80分钟",
                "刑法：连续推进第13章以后课程，目标到8月7日进入第22-24章范围；每个完成章先留最低可用正式框架，再做重点配套题",
                "框架先于整章题；题后只修订原框架。低频章的额外题源可以进入后续复线，不能阻塞课程输入",
                "英语每天120个单词，完成3篇阅读、2次完形、新题型和翻译各1次；法理安排3次闭卷",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-08-w2",
            title = "刑法听课收口并启动民法",
            dateRange = "2026-08-08 至 2026-08-14",
            tasks = listOf(
                "负荷：健康日360分钟，周日休息；新课输入主块约240分钟，框架/题目复线60-70分钟，英语和法理约70-80分钟",
                "刑法：8月9日前听完第25章并完成每章最低可用框架；8月10日开始民法课程，不等待刑法全部额外题源和错题二刷清零",
                "刑法复线：每天45-60分钟补重点章配套题、主错因和框架修订；8月16日前完成A/B类重点章第一轮验收",
                "民法：8月10日起连续听课并同步闭卷骨架、正式框架；正式框架完成后才做对应整章题",
                "英语和法理继续从360分钟总预算内切分",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-08-w3",
            title = "民法主线加速：六小时稳定档",
            dateRange = "2026-08-15 至 2026-08-20",
            tasks = listOf(
                "负荷：健康日360分钟；民法课程和框架约240-260分钟，刑法复线45分钟，英语和法理55-70分钟",
                "民法：按课程有效分钟推进，8月20日达到整套课程约55%-65%的输入进度；章节数不均，不用每天固定几章制造假进度",
                "已完成民法章节先画闭卷骨架、修成正式框架，再做配套题；刑法仅回炉重点错题和抽背，不重听课程",
                "英语和法理不断线，任何超时不得靠熬夜补齐",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-08-w4",
            title = "民法收口并启动宪法：6.5小时档",
            dateRange = "2026-08-21 至 2026-08-31",
            tasks = listOf(
                "负荷：健康日390分钟，周日休息；只有前一周完成率达到80%、至少4天白天完成且睡眠稳定才使用6.5小时档，否则回退360分钟",
                "民法：8月27日前听完整套课程并完成各章最低可用框架；重点章配套题第一轮同步推进，额外题源和错题二刷进入9月复线",
                "宪法：8月28日启动课程，月底完成约30%-40%输入；继续执行听课→闭卷骨架→正式框架→配套题",
                "刑法和民法的重点错题各保留每周2个回炉块；法理闭卷、英语阅读和小三门不断线",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-09-w1",
            title = "宪法收口并启动法制史",
            dateRange = "2026-09-01 至 2026-09-07",
            tasks = listOf(
                "负荷：健康日390分钟，周日休息；常规新课输入仍是最高优先级",
                "宪法：9月4日前听完课程并完成各章最低可用框架，重点章配套题启动；9月5日开始法制史",
                "法制史：连续听课并按时间轴/制度线画闭卷骨架，修成正式框架后再做配套题",
                "刑法、民法只做重点错题和关键词回炉；英语与法理继续从总预算内切分",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-09-w2",
            title = "全部常规新课收口",
            dateRange = "2026-09-08 至 2026-09-14",
            tasks = listOf(
                "负荷：健康日390分钟，周日休息；本周是常规新课硬收口窗口，不再新增资料",
                "法制史：9月14日前听完课程并完成各章最低可用框架；正式框架后完成重点配套题入口",
                "全科课程账本：核对刑法、民法、宪法、法制史原始分钟、有效分钟、框架状态和题目入口；未闭环题目转入9月下半月复线",
                "9月15日起不再保留常规新课，只进行政治启动、专业课题源/错题/背诵和英语真题",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-09-w3",
            title = "无常规新课：政治启动与专业课输出",
            dateRange = "2026-09-15 至 2026-09-21",
            tasks = listOf(
                "新课状态：刑法、民法、宪法、法制史课程均已结束；不得因个别题目未清零重新把课程主线拖回日程",
                "专业课：按分值和薄弱点补配套题、额外题源、错题回炉、框架修订和主观题输出；较早章节启动第二轮规范表述",
                "政治：按既定入口启动强化课/讲义与1000题，政治时间从每日总预算内切分",
                "英语：继续阅读、完形、新题型、翻译和单词滚动",
            ),
        ),
    ).associateBy { it.id }

    private val monthlyAllocations: Map<YearMonth, StudyMonthAllocation> = mapOf(
        YearMonth.of(2026, 7) to StudyMonthAllocation(2_400, 1_650, 420, 0, 330),
        YearMonth.of(2026, 8) to StudyMonthAllocation(9_330, 7_110, 1_560, 0, 660),
        YearMonth.of(2026, 9) to StudyMonthAllocation(9_780, 6_300, 1_680, 1_320, 480),
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
                            it.startsWith("刑法节点：") ||
                            it.startsWith("课程总节点：")
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
                    if (month in setOf(YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9))) {
                        listOf("课程总节点：$criminalLawTimeline")
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
        date in LocalDate.of(2026, 7, 27)..LocalDate.of(2026, 8, 2) ->
            if (date.dayOfWeek == DayOfWeek.SUNDAY) 0 else 300
        date in LocalDate.of(2026, 8, 3)..LocalDate.of(2026, 8, 9) ->
            if (date.dayOfWeek == DayOfWeek.SUNDAY) 0 else 330
        date in LocalDate.of(2026, 8, 10)..LocalDate.of(2026, 8, 23) ->
            if (date.dayOfWeek == DayOfWeek.SUNDAY) 0 else 360
        date in LocalDate.of(2026, 8, 24)..LocalDate.of(2026, 9, 14) ->
            if (date.dayOfWeek == DayOfWeek.SUNDAY) 0 else 390
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
        val english = coreTasks
            .filter { it.kind == StudyPlanTaskKind.English }
            .joinToString("；") { it.title }

        if (budget <= 180) {
            return listOf(
                StudyScheduleBlock("09:30-11:00", "专业课主块", professional),
                StudyScheduleBlock("14:00-14:25", "法理/框架续段", "完成当前法理闭卷或专业课正式框架；不得跳过框架直接做整章题。"),
                StudyScheduleBlock("15:00-15:35", "单词与英语主训练", english),
                StudyScheduleBlock("16:00-16:30", "闭环续段与缓冲", "继续题目、错因回填或英语复盘；今日有效学习180分钟，到点停止。"),
            )
        }

        if (budget <= 240) {
            return listOf(
                StudyScheduleBlock("09:30-11:30", "专业课主块", professional),
                StudyScheduleBlock("14:00-14:30", "法理/框架续段", "完成当前法理闭卷或专业课正式框架；不得跳过框架直接做整章题。"),
                StudyScheduleBlock("15:00-15:40", "单词与英语主训练", english),
                StudyScheduleBlock("16:00-16:50", "闭环续段与缓冲", "继续题目、错因回填或英语复盘；今日有效学习240分钟，到点停止。"),
            )
        }

        val finalBlockMinutes = (budget - 265).coerceAtLeast(20)
        return listOf(
            StudyScheduleBlock("09:30-11:30", "新课连续输入", "按当前唯一新书连续听课120分钟；记录原始分钟和有效分钟。"),
            StudyScheduleBlock("13:30-14:30", "框架与题目", professional),
            StudyScheduleBlock("14:45-15:15", "法理闭卷", "完成当前法理章节目录树、关键词或规范表述闭卷输出。"),
            StudyScheduleBlock("15:30-16:25", "英语主训练", english),
            StudyScheduleBlock("16:40-${timeAfter("16:40", finalBlockMinutes)}", "新课续段/错题缓冲", "优先继续新课输入；若当前章已听完，则完成闭卷骨架、正式框架或题后错因回填。今日总预算约${budget}分钟。"),
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
            appendLine("暑期课程总节点：$criminalLawTimeline")
            appendLine("日期：$date；当前时间：${currentTime.format(formatter)}。")
            appendLine("7月22日已作为本周恢复日，今天不得再以补病假为由无限叠加任务。")
            appendLine("今日有效学习总预算约${plannedMinutes(date)}分钟，包含新课、框架、题目、背诵和英语。")
            appendLine("今日主题：${presetPlan.title}")
            appendLine("今日预制任务：")
            presetPlan.tasks.forEachIndexed { index, task ->
                appendLine("${index + 1}. ${task.kind.label}｜${task.title}")
            }
            appendLine("当前未完成待办：")
            if (unfinished.isEmpty()) appendLine("- 无") else unfinished.forEach { appendLine("- ${it.title}") }
            appendLine("从当前时间或之后开始排；已经过去的时间块不得补写。")
            appendLine("必须优先保障9月14日前结束刑法、民法、宪法、法制史全部课程输入。")
            appendLine("课程结束后先完成闭卷骨架和正式框架，再做整章题；额外题源与错题二刷可作为复线，不得阻塞下一本课程启动。")
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

    private fun timeAfter(start: String, minutes: Int): String =
        LocalTime.parse(start).plusMinutes(minutes.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))

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
