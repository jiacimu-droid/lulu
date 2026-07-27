package me.rerere.rikkahub.ui.pages.study

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.BookOpen02
import me.rerere.hugeicons.stroke.Chart
import me.rerere.hugeicons.stroke.Clapping01
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.study.DailyStudyPlan
import me.rerere.rikkahub.data.study.ExamStudyPlan
import me.rerere.rikkahub.data.study.StudyEvent
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudyScheduleBlock
import me.rerere.rikkahub.data.study.StudySleepHabit
import me.rerere.rikkahub.data.study.StudyState
import me.rerere.rikkahub.data.study.StudyTip
import me.rerere.rikkahub.data.study.StudyTask
import me.rerere.rikkahub.data.study.StudyTaskSource
import java.time.LocalDate

private enum class StudyMainPlanView(val label: String) {
    Weekly("周计划"),
    Monthly("月计划"),
}

private enum class StudyMainDashboardView(val label: String) {
    Tasks("待办"),
    Plan("今日计划"),
    Tomorrow("明日待办"),
    Tips("Tips"),
}

@Composable
internal fun StudyHeroPanel(
    state: StudyState,
    assistant: Assistant,
    assistants: List<Assistant>,
    onSignIn: () -> Unit,
    onOpenLevel: () -> Unit,
    onSelectCompanion: (Assistant) -> Unit,
) {
    val daysLeft = ExamStudyPlan.daysLeft()
    val currentMilestone = ExamStudyPlan.currentMilestone()
    val studyTimeOverview = StudyRules.studyTimeOverview(state)
    val professionalTargetScore =
        ExamStudyPlan.professionalFoundationTargetScore + ExamStudyPlan.professionalComprehensiveTargetScore
    var showCompanionPicker by remember { mutableStateOf(false) }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = StudyMainColors.hero),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(studyHeroBrush())
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    me.rerere.rikkahub.ui.components.ui.UIAvatar(
                        assistant.name,
                        assistant.avatar,
                        Modifier.size(58.dp),
                        onClick = { showCompanionPicker = true },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${assistant.name}陪你备考",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "点头像可以换陪你学习的角色。今天的待办和番茄钟会同步给 TA。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StudyHeroMetric("规划倒计时", "${daysLeft}天", Modifier.weight(1f))
                    StudyHeroMetric("夸夸值", state.wallet.kudos.toString(), Modifier.weight(1f))
                    StudyHeroMetric(
                        "Lv",
                        StudyRules.currentLevel(state).level.toString(),
                        Modifier.weight(1f),
                        onOpenLevel,
                    )
                }
                Text(
                    text = ExamStudyPlan.examDateNotice,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StudyHeroMetric("川大目标", "${ExamStudyPlan.scuSafeTargetScore}分", Modifier.weight(1f))
                    StudyHeroMetric("专业课目标", "$professionalTargetScore/300", Modifier.weight(1f))
                }
                Text(
                    text = currentMilestone,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StudyHeroMetric(
                        "今日学习",
                        studyTimeMetric(studyTimeOverview.todayMinutes, studyTimeOverview.todayPomodoros),
                        Modifier.weight(1f),
                    )
                    StudyHeroMetric(
                        "本周学习",
                        studyTimeMetric(studyTimeOverview.weekMinutes, studyTimeOverview.weekPomodoros),
                        Modifier.weight(1f),
                    )
                }
                FilledTonalButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                    Icon(HugeIcons.Clapping01, null)
                    Spacer(Modifier.width(6.dp))
                    Text("签到")
                }
            }
        }
    }

    if (showCompanionPicker) {
        AlertDialog(
            onDismissRequest = { showCompanionPicker = false },
            title = { Text("选择今天陪你学习的角色") },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(360.dp),
                ) {
                    items(assistants) { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectCompanion(item)
                                    showCompanionPicker = false
                                },
                            color = if (item.id == assistant.id) {
                                Color.White.copy(alpha = 0.92f)
                            } else {
                                Color.White.copy(alpha = 0.62f)
                            },
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                me.rerere.rikkahub.ui.components.ui.UIAvatar(
                                    item.name,
                                    item.avatar,
                                    Modifier.size(42.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(item.name.ifBlank { "未命名角色" }, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (item.id == assistant.id) "正在陪你学习" else "切换为今日陪伴",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCompanionPicker = false }) {
                    Text("收起")
                }
            },
        )
    }
}

@Composable
private fun StudyHeroMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = Color.White.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun StudySectionChips(
    labels: List<String>,
    selectedLabel: String,
    onSelected: (String) -> Unit,
    gacha: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        labels.forEach { label ->
            FilterChip(
                selected = selectedLabel == label,
                onClick = { onSelected(label) },
                label = { Text(label, maxLines = 1) },
                colors = if (gacha) {
                    FilterChipDefaults.filterChipColors(
                        containerColor = Color.White.copy(alpha = 0.72f),
                        labelColor = Color(0xFF815B60),
                        selectedContainerColor = Color(0xFFFFCFA6),
                        selectedLabelColor = Color(0xFF684018),
                    )
                } else {
                    FilterChipDefaults.filterChipColors()
                },
            )
        }
    }
}

@Composable
internal fun StudyTodayPomodoroLaunchCard(onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFF1F5FF)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = Color(0xFFDCE7FF),
                contentColor = Color(0xFF385788),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(HugeIcons.Clock02, contentDescription = null, modifier = Modifier.size(26.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text("开始番茄钟", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "从今天的任务顺手开始一轮专注",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(HugeIcons.ArrowRight01, contentDescription = null, tint = Color(0xFF5874A3))
        }
    }
}

@Composable
internal fun StudyTodayProgressCard(
    state: StudyState,
    onClaimNormal: () -> Unit,
) {
    val total = state.tasks.size
    val done = state.tasks.count { it.done }
    val progress = if (total == 0) 0f else done.toFloat() / total
    val progressPercent = (progress * 100).toInt().coerceIn(0, 100)
    StudyMainCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("今日进度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("计划下面单独看进度：$done/$total", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(color = StudyMainColors.hero.copy(alpha = 0.78f), shape = CircleShape) {
                Text(
                    "$progressPercent%",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = StudyMainColors.gold,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (state.superMomentAvailable) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onClaimNormal, modifier = Modifier.fillMaxWidth()) {
                    Text("领取十连券 x1")
                }
            }
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun StudyDailyDashboard(
    tasks: List<StudyTask>,
    assistantName: String,
    generatedSchedule: List<StudyScheduleBlock>?,
    isGeneratingSchedule: Boolean,
    newTask: String,
    onNewTask: (String) -> Unit,
    onAdd: () -> Unit,
    onGenerateSchedule: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    val today = LocalDate.now()
    val todayPlan = ExamStudyPlan.todayPlan(today)
    val tomorrowPlan = ExamStudyPlan.todayPlan(today.plusDays(1))
    val schedule = generatedSchedule ?: ExamStudyPlan.todaySchedule(today)
    val tips = ExamStudyPlan.todayTips(today)
    var dashboardView by remember { mutableStateOf(StudyMainDashboardView.Tasks) }

    StudyMainCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            StudyMainDashboardView.entries.forEach { view ->
                FilterChip(
                    selected = dashboardView == view,
                    onClick = { dashboardView = view },
                    label = { Text(view.label, maxLines = 1) },
                )
            }
        }
        when (dashboardView) {
            StudyMainDashboardView.Tasks -> StudyTaskContent(
                tasks = tasks,
                assistantName = assistantName,
                newTask = newTask,
                onNewTask = onNewTask,
                onAdd = onAdd,
                onToggle = onToggle,
                onDelete = onDelete,
            )
            StudyMainDashboardView.Plan -> StudyTodayPlanContent(
                todayPlan = todayPlan,
                schedule = schedule,
                generatedByAi = generatedSchedule != null,
                isGeneratingSchedule = isGeneratingSchedule,
                onGenerateSchedule = onGenerateSchedule,
            )
            StudyMainDashboardView.Tomorrow -> StudyTomorrowPlanContent(tomorrowPlan)
            StudyMainDashboardView.Tips -> StudyTipsContent(tips)
        }
    }
}

@Composable
private fun StudyTaskContent(
    tasks: List<StudyTask>,
    assistantName: String,
    newTask: String,
    onNewTask: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("待办", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                value = newTask,
                onValueChange = onNewTask,
                label = { Text("新增学习任务") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(onClick = onAdd) {
                Icon(HugeIcons.Add01, "添加")
            }
        }
        if (tasks.isEmpty()) {
            Text(
                "先写下今天最重要的 3-5 件事。${assistantName}会按人设和约定陪你保持节奏。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        tasks.forEach { task ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = task.done, onCheckedChange = { onToggle(task.id, it) })
                Column(Modifier.weight(1f)) {
                    if (task.source == StudyTaskSource.Plan) {
                        Text("计划", style = MaterialTheme.typography.labelSmall, color = StudyMainColors.blue)
                    }
                    Text(
                        text = task.title,
                        textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    )
                }
                IconButton(onClick = { onDelete(task.id) }) {
                    Icon(HugeIcons.Delete01, "删除")
                }
            }
        }
    }
}

@Composable
private fun StudyTodayPlanContent(
    todayPlan: DailyStudyPlan?,
    schedule: List<StudyScheduleBlock>,
    generatedByAi: Boolean,
    isGeneratingSchedule: Boolean,
    onGenerateSchedule: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(HugeIcons.Clock02, null, tint = StudyMainColors.blue)
            Column(Modifier.weight(1f)) {
                Text("今日计划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (generatedByAi) "已按今日待办重新生成" else todayPlan?.title ?: "今天先守住最小学习闭环",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onGenerateSchedule, enabled = !isGeneratingSchedule) {
                if (isGeneratingSchedule) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(HugeIcons.AiMagic, null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(if (isGeneratingSchedule) "生成中" else "生成计划表")
            }
        }
        schedule.forEach { block ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    block.time,
                    style = MaterialTheme.typography.labelMedium,
                    color = StudyMainColors.blue,
                    modifier = Modifier.width(82.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(block.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        block.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyTomorrowPlanContent(tomorrowPlan: DailyStudyPlan?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(HugeIcons.BookOpen02, null, tint = StudyMainColors.purple)
            Column(Modifier.weight(1f)) {
                Text("明日待办", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    tomorrowPlan?.title ?: "明天先保留弹性，不提前制造压力",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (tomorrowPlan == null) {
            Text("还没有明天的预制计划。今晚收尾时只写明天第一步。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            tomorrowPlan.tasks.forEachIndexed { index, task ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Surface(shape = CircleShape, color = StudyMainColors.softBlue, modifier = Modifier.size(26.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = StudyMainColors.blue)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(task.kind.label, style = MaterialTheme.typography.labelSmall, color = StudyMainColors.purple)
                        Text(task.title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text(
                "明日待办只是预览；今晚收尾时再决定明天第一步，不把焦虑提前搬到今天。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StudyTipsContent(tips: List<StudyTip>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(HugeIcons.AiMagic, null, tint = StudyMainColors.gold)
            Column(Modifier.weight(1f)) {
                Text("tips", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("按今天任务给你提效，不照搬经验帖强度。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        tips.forEach { tip ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tip.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(tip.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun StudyPlanOverviewPanel() {
    var planView by remember { mutableStateOf(StudyMainPlanView.Weekly) }
    val today = LocalDate.now()
    val week = ExamStudyPlan.weekForDate(today) ?: ExamStudyPlan.weeklyPlans.firstOrNull()

    StudyMainCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StudyMainPlanView.entries.forEach { view ->
                FilterChip(
                    selected = planView == view,
                    onClick = { planView = view },
                    label = { Text(view.label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        when (planView) {
            StudyMainPlanView.Weekly -> {
                week?.let {
                    Text(it.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(it.dateRange, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    it.tasks.forEach { task ->
                        Text("· $task", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } ?: Text("本周计划待生成", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StudyMainPlanView.Monthly -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("7-12月总计划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    ExamStudyPlan.monthlyPlans.forEach { month ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${month.month} · ${month.focus}", fontWeight = FontWeight.SemiBold)
                            month.tasks.forEach { task ->
                                Text("· $task", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StudyLevelDialog(
    state: StudyState,
    onClaimLevel: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val level = StudyRules.currentLevel(state)
    val next = StudyRules.levels.firstOrNull { it.level == level.level + 1 }
    val claimable = StudyRules.claimableLevels(state)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { TextButton(onClick = onDismissRequest) { Text("收起") } },
        title = { Text("等级进度") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(HugeIcons.Chart, null, tint = StudyMainColors.gold)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Lv${level.level} ${level.title}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("累计夸夸值 ${state.wallet.totalKudosEarned}")
                    }
                }
                next?.let {
                    val span = (it.threshold - level.threshold).coerceAtLeast(1)
                    val current = (state.wallet.totalKudosEarned - level.threshold).coerceIn(0, span)
                    LinearProgressIndicator(progress = { current.toFloat() / span }, modifier = Modifier.fillMaxWidth())
                    Text("距离 Lv${it.level} 还差 ${(it.threshold - state.wallet.totalKudosEarned).coerceAtLeast(0)} 累计夸夸值")
                } ?: Text("你已经抵达星穹彼岸")

                Text("可领取奖励", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (claimable.isEmpty()) {
                    Text("暂时没有新的等级奖励。继续完成待办和番茄钟吧。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                claimable.take(5).forEach {
                    AssistChip(onClick = { onClaimLevel(it.level) }, label = { Text("领取 Lv${it.level}：${it.reward.title}") })
                }

                Text("等级奖励表", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                StudyRules.levels.forEach {
                    Text("Lv${it.level} ${it.title} · ${it.threshold} · ${it.reward.title}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )
}

@Composable
internal fun StudySuperMomentCelebration(
    assistant: Assistant,
    onDismissRequest: () -> Unit,
    onClaimNormal: () -> Unit,
) {
    val pulse by rememberInfiniteTransition(label = "super-moment").animateFloat(
        initialValue = 0.88f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "super-moment-pulse",
    )
    Box(
        modifier = Modifier.fillMaxSize().background(studySuperMomentBrush()).padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(7) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.30f + it * 0.06f),
                        modifier = Modifier.size(((12 + it * 3) * pulse).dp),
                    ) {}
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("超神时刻", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color.White)
                Text("今日全清", style = MaterialTheme.typography.headlineMedium, color = Color.White.copy(alpha = 0.92f))
                Text(
                    "${assistant.name}看见你把今天全部拿下了。奖励固定发放十连券 x1。",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(12) {
                        Surface(
                            shape = CircleShape,
                            color = listOf(Color.White, StudyMainColors.gold, StudyMainColors.purple)[it % 3]
                                .copy(alpha = 0.78f),
                            modifier = Modifier.size(((10 + it % 4 * 5) * pulse).dp),
                        ) {}
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onClaimNormal, modifier = Modifier.fillMaxWidth()) {
                    Text("领取十连券 x1")
                }
                TextButton(onClick = onDismissRequest, modifier = Modifier.fillMaxWidth()) {
                    Text("先等等", color = Color.White)
                }
            }
        }
    }
}

@Composable
internal fun StudySleepHabitRewardCard(
    state: StudyState,
    assistantName: String,
) {
    val today = LocalDate.now()
    val earlySleepClaimed = StudyRules.hasClaimedSleepHabitReward(
        state = state,
        habit = StudySleepHabit.EarlySleep,
        date = today,
    )
    val earlyRiseClaimed = StudyRules.hasClaimedSleepHabitReward(
        state = state,
        habit = StudySleepHabit.EarlyRise,
        date = today,
    )
    StudyMainCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("作息任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "个人标准：约01:30前睡、09:30前起。告诉 $assistantName 具体时间，由 TA 结合对话判断。每天每项一次。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        StudySleepHabitRewardRow(
            title = "昨晚早睡",
            reward = "+${StudyRules.EARLY_SLEEP_KUDOS} 夸夸值",
            claimed = earlySleepClaimed,
        )
        StudySleepHabitRewardRow(
            title = "今天早起",
            reward = "十连抽券 ×${StudyRules.EARLY_RISE_TEN_DRAW_TICKETS}",
            claimed = earlyRiseClaimed,
        )
    }
}

@Composable
private fun StudySleepHabitRewardRow(
    title: String,
    reward: String,
    claimed: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (claimed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(reward, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (claimed) "今天已领取" else "等你告诉 TA",
                style = MaterialTheme.typography.labelLarge,
                color = if (claimed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun StudyRecentEventsCard(events: List<StudyEvent>) {
    StudyMainCard {
        Text("奖励记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (events.isEmpty()) {
            Text("完成一个待办或番茄钟后，这里会亮起来。")
        }
        events.take(6).forEach { event ->
            Text("· ${event.title} ${event.detail}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StudyMainCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

private object StudyMainColors {
    val hero = Color(0xFFFFE6B8)
    val softBlue = Color(0xFFDCECF4)
    val blue = Color(0xFF3D7EA6)
    val purple = Color(0xFF8067B7)
    val gold = Color(0xFF9B6B10)
}

private fun studyHeroBrush(): Brush = Brush.linearGradient(
    listOf(Color(0xFFFFE5AE), Color(0xFFE2F0F7), Color(0xFFFFF8D8)),
)

private fun studySuperMomentBrush(): Brush = Brush.linearGradient(
    listOf(Color(0xFFFFC857), Color(0xFFFF7AA2), Color(0xFF7C6BFF)),
)

private fun studyTimeMetric(minutes: Int, pomodoros: Int): String =
    "${minutes}分 · ${pomodoros}个"
