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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.BookOpen02
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.study.DailyStudyPlan
import me.rerere.rikkahub.data.study.StudyPlanCatalog
import me.rerere.rikkahub.data.study.StudyScheduleBlock
import me.rerere.rikkahub.data.study.StudyState
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
                    "选择任务后开始一轮专注",
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
                Text("$done/$total", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        if (state.superMomentAvailable) {
            Button(onClick = onClaimNormal, modifier = Modifier.fillMaxWidth()) {
                Text("领取十连券 ×1")
            }
        }
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
    val todayPlan = StudyPlanCatalog.dailyPlan(today)
    val tomorrowPlan = StudyPlanCatalog.dailyPlan(today.plusDays(1))
    val schedule = generatedSchedule ?: todayPlan?.tasks.orEmpty().map { task ->
        StudyScheduleBlock("自定", task.kind.label, task.title)
    }
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
            StudyMainDashboardView.Tips -> StudyTipsContent()
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
        Text("待办", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                value = newTask,
                onValueChange = onNewTask,
                label = { Text("新增学习任务") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(onClick = onAdd) { Icon(HugeIcons.Add01, "添加") }
        }
        if (tasks.isEmpty()) {
            Text(
                "写下今天最重要的事情，${assistantName}会看到你的任务和完成情况。",
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
                IconButton(onClick = { onDelete(task.id) }) { Icon(HugeIcons.Delete01, "删除") }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(HugeIcons.Clock02, null, tint = StudyMainColors.blue)
            Column(Modifier.weight(1f)) {
                Text("今日计划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (generatedByAi) "已生成短计划" else todayPlan?.title ?: "今天由你自行安排",
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
                Text(if (isGeneratingSchedule) "生成中" else "生成")
            }
        }
        schedule.forEach { block ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(block.time, style = MaterialTheme.typography.labelMedium, color = StudyMainColors.blue, modifier = Modifier.width(72.dp))
                Column(Modifier.weight(1f)) {
                    Text(block.title, fontWeight = FontWeight.SemiBold)
                    if (block.detail.isNotBlank()) {
                        Text(block.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
            Text("明日待办", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (tomorrowPlan == null) {
            Text("暂无预制计划", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            tomorrowPlan.tasks.forEach { task ->
                Text("· ${task.title}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StudyTipsContent() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tips", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("先做最重要的一项，再决定下一步。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("没做完的任务回到任务池，不用熬夜补。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("英语训练不要只剩单词，记得安排真题。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StudyPlanOverviewPanel() {
    var planView by remember { mutableStateOf(StudyMainPlanView.Weekly) }
    val today = LocalDate.now()
    val week = StudyPlanCatalog.weekForDate(today) ?: StudyPlanCatalog.weeklyPlans.firstOrNull()

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
                    it.tasks.forEach { task -> Text("· $task", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } ?: Text("本周计划待更新", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StudyMainPlanView.Monthly -> {
                Text("7-12月滚动计划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                StudyPlanCatalog.monthlyPlans.forEach { month ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${month.month} · ${month.focus}", fontWeight = FontWeight.SemiBold)
                        month.tasks.forEach { task -> Text("· $task", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
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
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
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
            Column(verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("超神时刻", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color.White)
                Text("今日全清", style = MaterialTheme.typography.headlineMedium, color = Color.White.copy(alpha = 0.92f))
                Text(
                    "${assistant.name}看见你完成了今天的全部任务。",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onClaimNormal, modifier = Modifier.fillMaxWidth()) { Text("领取十连券 ×1") }
                TextButton(onClick = onDismissRequest, modifier = Modifier.fillMaxWidth()) { Text("先等等", color = Color.White) }
            }
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
    val blue = Color(0xFF3D7EA6)
    val purple = Color(0xFF8067B7)
    val gold = Color(0xFF9B6B10)
}

private fun studySuperMomentBrush(): Brush = Brush.linearGradient(
    listOf(Color(0xFFFFC857), Color(0xFFFF7AA2), Color(0xFF7C6BFF)),
)
