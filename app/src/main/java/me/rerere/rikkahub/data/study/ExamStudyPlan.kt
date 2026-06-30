package me.rerere.rikkahub.data.study

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object ExamStudyPlan {
    val examDate: LocalDate = LocalDate.of(2026, 12, 21)

    val monthlyPlans = listOf(
        MonthlyStudyPlan("2026-07", "启动节奏与专业课一轮重启", listOf("法理复活", "刑法与民法入门", "英语单词复习", "长难句起步")),
        MonthlyStudyPlan("2026-08", "专业课一轮主体推进", listOf("刑法、民法完成一轮", "宪法、法制史开始一轮", "配套题跟进", "英语阅读真题入门")),
        MonthlyStudyPlan("2026-09", "专业课二轮与政治轻启动", listOf("五科框架二轮", "错题回炉", "政治每天轻量启动", "英语阅读稳定训练")),
        MonthlyStudyPlan("2026-10", "背诵强化与真题训练", listOf("专业课主观题表达", "历年真题分科训练", "英语作文模板搭建", "政治选择题")),
        MonthlyStudyPlan("2026-11", "冲刺背诵与套卷节奏", listOf("专业课反复背诵", "英语作文定稿", "政治高频题", "每周模拟")),
        MonthlyStudyPlan("2026-12", "保温、模拟与作息校准", listOf("不再开新坑", "错题和高频点保温", "考前模拟", "睡眠节律稳定")),
    )

    val julyWeeks = listOf(
        WeeklyStudyPlan(
            id = "2026-07-w1",
            title = "第1周：轻启动，先把系统跑起来",
            dateRange = "2026-07-01 至 2026-07-07",
            tasks = listOf(
                "完成 6 天单词复习，每天 80-100 个",
                "法理学做一次目录唤醒和核心概念复盘",
                "刑法听课 2 个小节并做配套题",
                "民法听课 2 个小节并做配套题",
                "周末做一次错题与口头复述",
                "至少散步 4 次，每次 15 分钟以上",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-07-w2",
            title = "第2周：刑法建立框架，民法保持手感",
            dateRange = "2026-07-08 至 2026-07-14",
            tasks = listOf(
                "刑法推进犯罪构成、故意过失、违法阻却",
                "民法推进民事法律关系与民事主体",
                "法理学隔日轻复习",
                "英语长难句开始每天 1-2 句",
                "完成本周错题回看",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-07-w3",
            title = "第3周：刑民继续推进，加入英语阅读",
            dateRange = "2026-07-15 至 2026-07-21",
            tasks = listOf(
                "刑法推进未完成形态与共同犯罪",
                "民法推进民事法律行为与代理",
                "英语阅读真题精读 2 篇，不追速度",
                "法理学完成第一轮回看",
                "整理本周最常错 10 个点",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-07-w4",
            title = "第4周：宪法入门，刑民做小结",
            dateRange = "2026-07-22 至 2026-07-28",
            tasks = listOf(
                "宪法听课入门并建立章节框架",
                "刑法做阶段小结",
                "民法做阶段小结",
                "英语阅读真题精读 2-3 篇",
                "周末做一次 7 月复盘",
            ),
        ),
        WeeklyStudyPlan(
            id = "2026-07-w5",
            title = "第5周：7月收尾与8月衔接",
            dateRange = "2026-07-29 至 2026-07-31",
            tasks = listOf(
                "补齐 7 月落下的刑民小节",
                "整理 7 月错题清单",
                "确认 8 月教材与课程安排",
            ),
        ),
    )

    val dailyPlans: Map<LocalDate, DailyStudyPlan> = listOf(
        DailyStudyPlan(
            date = LocalDate.of(2026, 6, 30),
            title = "启动准备日",
            tasks = listOf(
                planTask("确认 7 月备考资料：文运/众合题册、考试分析、网课目录", StudyPlanTaskKind.Foundation),
                planTask("不背单词复习 50 个，只做热身", StudyPlanTaskKind.English),
                planTask("散步 15 分钟，给大脑换气", StudyPlanTaskKind.Health),
            ),
        ),
        DailyStudyPlan(
            date = LocalDate.of(2026, 7, 1),
            title = "法理复活日",
            tasks = listOf(
                planTask("不背单词复习 80 个", StudyPlanTaskKind.English),
                planTask("法理学：翻目录，标出已经学过但陌生的 5 个章节", StudyPlanTaskKind.Law),
                planTask("法理学：做配套题 10 题，错题只标记不深究", StudyPlanTaskKind.Law),
                planTask("用自己的话复述：法的本质/法的作用各 2 句话", StudyPlanTaskKind.Review),
                planTask("散步 15 分钟", StudyPlanTaskKind.Health),
            ),
        ),
        DailyStudyPlan(
            date = LocalDate.of(2026, 7, 2),
            title = "刑法轻启动",
            tasks = listOf(
                planTask("不背单词复习 80-100 个", StudyPlanTaskKind.English),
                planTask("刑法：听课 1 小节，重点抓犯罪构成四要件", StudyPlanTaskKind.Law),
                planTask("刑法：做对应配套题 10-15 题", StudyPlanTaskKind.Law),
                planTask("回看昨天法理错题", StudyPlanTaskKind.Review),
                planTask("睡前写下今天最不懂的 1 个点", StudyPlanTaskKind.Review),
            ),
        ),
        DailyStudyPlan(
            date = LocalDate.of(2026, 7, 3),
            title = "刑法第二步",
            tasks = listOf(
                planTask("不背单词复习 80-100 个", StudyPlanTaskKind.English),
                planTask("刑法：听课 1 小节，继续犯罪构成/故意过失", StudyPlanTaskKind.Law),
                planTask("刑法：做对应配套题 10-15 题", StudyPlanTaskKind.Law),
                planTask("复述昨天刑法内容 3 句话，不要求背原文", StudyPlanTaskKind.Review),
                planTask("散步 15 分钟", StudyPlanTaskKind.Health),
            ),
        ),
        DailyStudyPlan(
            date = LocalDate.of(2026, 7, 4),
            title = "民法轻启动",
            tasks = listOf(
                planTask("不背单词复习 80 个", StudyPlanTaskKind.English),
                planTask("民法：听课 1 小节，抓民事法律关系/民事主体", StudyPlanTaskKind.Law),
                planTask("民法：做对应配套题 10 题", StudyPlanTaskKind.Law),
                planTask("回看刑法错题", StudyPlanTaskKind.Review),
                planTask("整理今天 1 个能讲给别人听的概念", StudyPlanTaskKind.Review),
            ),
        ),
        DailyStudyPlan(
            date = LocalDate.of(2026, 7, 5),
            title = "民法第二步",
            tasks = listOf(
                planTask("不背单词复习 80-100 个", StudyPlanTaskKind.English),
                planTask("民法：听课 1 小节，继续主体/权利能力/行为能力", StudyPlanTaskKind.Law),
                planTask("民法：做对应配套题 10-15 题", StudyPlanTaskKind.Law),
                planTask("复述昨天民法内容 3 句话", StudyPlanTaskKind.Review),
                planTask("散步 15 分钟", StudyPlanTaskKind.Health),
            ),
        ),
        DailyStudyPlan(
            date = LocalDate.of(2026, 7, 6),
            title = "周复盘",
            tasks = listOf(
                planTask("不背单词复习 60-80 个", StudyPlanTaskKind.English),
                planTask("本周法理/刑法/民法错题各挑 3 题回看", StudyPlanTaskKind.Review),
                planTask("用一页纸写本周专业课小结：我听懂了什么、还怕什么", StudyPlanTaskKind.Review),
                planTask("补一个本周漏掉的小任务，只补最小的一块", StudyPlanTaskKind.Foundation),
                planTask("散步或拉伸 20 分钟", StudyPlanTaskKind.Health),
            ),
        ),
        DailyStudyPlan(
            date = LocalDate.of(2026, 7, 7),
            title = "休息容错日",
            tasks = listOf(
                planTask("保底：不背单词复习 50 个", StudyPlanTaskKind.English),
                planTask("如果状态好：回看本周最陌生的 1 个知识点", StudyPlanTaskKind.Review),
                planTask("好好吃一顿饭，散步 20 分钟", StudyPlanTaskKind.Health),
            ),
        ),
    ).associateBy { it.date }

    fun todayPlan(date: LocalDate = LocalDate.now()): DailyStudyPlan? = dailyPlans[date]

    fun daysLeft(date: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(date, examDate).coerceAtLeast(0)

    private fun planTask(title: String, kind: StudyPlanTaskKind): StudyPlanTask =
        StudyPlanTask(title = title, kind = kind)
}

data class MonthlyStudyPlan(
    val month: String,
    val focus: String,
    val tasks: List<String>,
)

data class WeeklyStudyPlan(
    val id: String,
    val title: String,
    val dateRange: String,
    val tasks: List<String>,
)

data class DailyStudyPlan(
    val date: LocalDate,
    val title: String,
    val tasks: List<StudyPlanTask>,
)

data class StudyPlanTask(
    val title: String,
    val kind: StudyPlanTaskKind,
)

enum class StudyPlanTaskKind(val label: String) {
    English("英语"),
    Law("专业课"),
    Review("复盘"),
    Health("身体"),
    Foundation("准备"),
}
