package me.rerere.rikkahub.ui.pages.study

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Image03
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private data class StudyTask(
    val id: Int,
    val text: String,
    val scope: StudyScope,
    val done: Boolean = false,
)

private enum class StudyScope(val label: String) {
    Today("今日计划"),
    Week("本周计划"),
    Month("本月计划"),
}

@Composable
fun StudyPage() {
    val navController = LocalNavController.current
    val tasks = remember {
        mutableStateListOf(
            StudyTask(1, "英语：背单词 30 分钟", StudyScope.Today),
            StudyTask(2, "政治：完成一节强化课", StudyScope.Today),
            StudyTask(3, "专业课：整理错题和框架", StudyScope.Week),
            StudyTask(4, "数学/专业课：完成本月阶段复盘", StudyScope.Month),
        )
    }
    var newTask by remember { mutableStateOf("") }
    var kudos by remember { mutableIntStateOf(0) }
    val daysLeft = remember {
        ChronoUnit.DAYS.between(LocalDate.now(), nextPostgraduateExamDate()).coerceAtLeast(0)
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("考研") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StudyHeader(daysLeft = daysLeft, kudos = kudos) }
            item { LevelCard(kudos = kudos) }
            item {
                StudyPlanCard(
                    tasks = tasks,
                    newTask = newTask,
                    onNewTaskChange = { newTask = it },
                    onToggle = { task ->
                        val index = tasks.indexOfFirst { it.id == task.id }
                        if (index >= 0) {
                            val nextDone = !tasks[index].done
                            tasks[index] = tasks[index].copy(done = nextDone)
                            kudos = (kudos + if (nextDone) 1 else -1).coerceAtLeast(0)
                        }
                    },
                    onAddTask = {
                        val text = newTask.trim()
                        if (text.isNotBlank()) {
                            tasks += StudyTask(
                                id = (tasks.maxOfOrNull { it.id } ?: 0) + 1,
                                text = text,
                                scope = StudyScope.Today,
                            )
                            newTask = ""
                        }
                    },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { navController.navigate(Screen.StudyPomodoro) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(HugeIcons.Clock02, contentDescription = null)
                    Text("进入番茄钟")
                }
            }
        }
    }
}

@Composable
fun StudyPomodoroPage() {
    val settings = LocalSettings.current
    val tts = LocalTTSState.current
    val assistant = settings.getCurrentAssistant()
    var menuExpanded by remember { mutableStateOf(false) }
    var imageEnabled by remember { mutableStateOf(true) }
    var voiceEnabled by remember { mutableStateOf(true) }
    var selectedMinutes by remember { mutableIntStateOf(25) }
    var customMinutes by remember { mutableStateOf("") }
    var taskText by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableIntStateOf(25 * 60) }
    var chatText by remember { mutableStateOf("") }
    var coachReply by remember { mutableStateOf("准备好了就开始，我会陪你把这一轮学完。") }
    var kudos by remember { mutableIntStateOf(0) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(running, selectedMinutes) {
        if (!running) return@LaunchedEffect
        while (running && remainingSeconds > 0) {
            delay(1_000)
            remainingSeconds -= 1
            if (remainingSeconds > 0 && remainingSeconds % (5 * 60) == 0) {
                val line = buildEncourageLine(taskText, assistant)
                coachReply = line
                if (voiceEnabled) tts.speak(line, flushCalled = true)
            }
        }
        if (running && remainingSeconds <= 0) {
            running = false
            kudos += 2
            val line = "这一轮完成了，夸夸值 +2。你刚才没有逃走，这很厉害。"
            coachReply = line
            if (voiceEnabled) tts.speak(line, flushCalled = true)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("督学番茄钟") },
                navigationIcon = { BackButton() },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(HugeIcons.MoreVertical, contentDescription = "开关")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { ToggleRow("AI 生图", imageEnabled) { imageEnabled = it } },
                                onClick = { imageEnabled = !imageEnabled },
                            )
                            DropdownMenuItem(
                                text = { ToggleRow("语音播报", voiceEnabled) { voiceEnabled = it } },
                                onClick = { voiceEnabled = !voiceEnabled },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { CoachCard(assistant = assistant, imageEnabled = imageEnabled, kudos = kudos) }
            item {
                OutlinedTextField(
                    value = taskText,
                    onValueChange = { taskText = it },
                    label = { Text("这一轮要做什么") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
            item {
                DurationPicker(
                    selectedMinutes = selectedMinutes,
                    customMinutes = customMinutes,
                    onSelected = {
                        selectedMinutes = it
                        remainingSeconds = it * 60
                        customMinutes = ""
                    },
                    onCustomChange = {
                        customMinutes = it.filter(Char::isDigit).take(3)
                        customMinutes.toIntOrNull()?.takeIf { value -> value > 0 }?.let { value ->
                            selectedMinutes = value
                            remainingSeconds = value * 60
                        }
                    },
                )
            }
            item {
                TimerCard(
                    selectedMinutes = selectedMinutes,
                    remainingSeconds = remainingSeconds,
                    running = running,
                    onToggle = {
                        if (!running && remainingSeconds <= 0) remainingSeconds = selectedMinutes * 60
                        running = !running
                    },
                    onReset = {
                        running = false
                        remainingSeconds = selectedMinutes * 60
                    },
                )
            }
            item {
                CoachChatCard(
                    assistant = assistant,
                    reply = coachReply,
                    chatText = chatText,
                    onChatChange = { chatText = it },
                    onSend = {
                        val text = chatText.trim()
                        if (text.isNotBlank()) {
                            val line = buildChatReply(text, taskText, assistant)
                            coachReply = line
                            chatText = ""
                            if (voiceEnabled) tts.speak(line, flushCalled = true)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StudyHeader(daysLeft: Long, kudos: Int) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("距离下一次中国考研初试还有", style = MaterialTheme.typography.bodyMedium)
            Text("${daysLeft} 天", style = MaterialTheme.typography.displaySmall)
            Text("夸夸值：$kudos", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun LevelCard(kudos: Int) {
    val reward = rewardFor(kudos)
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("当前等级：Lv.${reward.level} ${reward.title}", style = MaterialTheme.typography.titleMedium)
            Text(reward.unlock, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StudyPlanCard(
    tasks: List<StudyTask>,
    newTask: String,
    onNewTaskChange: (String) -> Unit,
    onToggle: (StudyTask) -> Unit,
    onAddTask: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("学习计划", style = MaterialTheme.typography.titleMedium)
            StudyScope.entries.forEach { scope ->
                Text(scope.label, style = MaterialTheme.typography.labelLarge)
                tasks.filter { it.scope == scope }.forEach { task ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = task.done, onCheckedChange = { onToggle(task) })
                        Text(
                            text = task.text,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (task.done) TextDecoration.LineThrough else null,
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newTask,
                    onValueChange = onNewTaskChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("新增今日任务") },
                    singleLine = true,
                )
                IconButton(onClick = onAddTask) {
                    Icon(HugeIcons.Add01, contentDescription = "添加")
                }
            }
        }
    }
}

@Composable
private fun CoachCard(assistant: Assistant, imageEnabled: Boolean, kudos: Int) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UIAvatar(name = assistant.name, value = assistant.avatar, modifier = Modifier.size(54.dp))
                Column {
                    Text("督学角色", style = MaterialTheme.typography.labelMedium)
                    Text(assistant.name, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (imageEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(HugeIcons.Image03, contentDescription = null, modifier = Modifier.size(30.dp))
                        Text(studyImagePromptHint(kudos), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DurationPicker(
    selectedMinutes: Int,
    customMinutes: String,
    onSelected: (Int) -> Unit,
    onCustomChange: (String) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("时间段", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 40, 50, 90).forEach { minutes ->
                    FilterChip(
                        selected = selectedMinutes == minutes,
                        onClick = { onSelected(minutes) },
                        label = { Text("${minutes}分钟") },
                    )
                }
            }
            OutlinedTextField(
                value = customMinutes,
                onValueChange = onCustomChange,
                label = { Text("自定义分钟") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TimerCard(
    selectedMinutes: Int,
    remainingSeconds: Int,
    running: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit,
) {
    val total = (selectedMinutes * 60).coerceAtLeast(1)
    val progress = 1f - remainingSeconds.coerceIn(0, total).toFloat() / total
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(secondsText(remainingSeconds), style = MaterialTheme.typography.displayMedium)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggle) {
                    Text(if (running) "暂停番茄钟" else "开始番茄钟")
                }
                TextButton(onClick = onReset) {
                    Text("重置")
                }
            }
        }
    }
}

@Composable
private fun CoachChatCard(
    assistant: Assistant,
    reply: String,
    chatText: String,
    onChatChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text("${assistant.name} 的陪伴回复", style = MaterialTheme.typography.titleMedium)
            }
            Text(reply, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = chatText,
                onValueChange = onChatChange,
                label = { Text("学累了可以吐槽一句") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            FilledTonalButton(onClick = onSend, modifier = Modifier.fillMaxWidth()) {
                Icon(HugeIcons.VolumeHigh, contentDescription = null)
                Text("发送并播报")
            }
        }
    }
}

private data class StudyReward(
    val level: Int,
    val title: String,
    val unlock: String,
)

private fun rewardFor(kudos: Int): StudyReward = when {
    kudos >= 12 -> StudyReward(4, "亲密", "解锁：靠在你肩上、10 条新语音")
    kudos >= 7 -> StudyReward(3, "信赖", "解锁：趴桌牵你手、7 条新语音")
    kudos >= 3 -> StudyReward(2, "熟悉", "解锁：托腮看你、5 条新语音")
    else -> StudyReward(1, "初识", "解锁：基础陪伴姿势、3 条语音")
}

private fun nextPostgraduateExamDate(today: LocalDate = LocalDate.now()): LocalDate {
    val currentYearExam = LocalDate.of(today.year, 12, 21)
    return if (today <= currentYearExam) currentYearExam else LocalDate.of(today.year + 1, 12, 21)
}

private fun secondsText(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val rest = safe % 60
    return "%02d:%02d".format(minutes, rest)
}

private fun buildEncourageLine(taskText: String, assistant: Assistant): String {
    val target = taskText.ifBlank { "这一轮任务" }
    return "${assistant.name}在看着你：先别急着跑，我们把“$target”再往前推一点点。"
}

private fun buildChatReply(userText: String, taskText: String, assistant: Assistant): String {
    val target = taskText.ifBlank { "今天的计划" }
    return "${assistant.name}听到了：$userText。先深呼吸一下，我陪你继续做“$target”，只要再坚持几分钟就好。"
}

private fun studyImagePromptHint(kudos: Int): String = when {
    kudos >= 12 -> "生图方向：角色靠在你肩上陪读，画面亲密、安心、有互动感。"
    kudos >= 7 -> "生图方向：角色趴在桌边牵住你的手，鼓励你继续学习。"
    kudos >= 3 -> "生图方向：角色托腮看着屏幕，离你更近，陪你复习。"
    else -> "生图方向：角色坐在你面前半身陪读，桌面干净，氛围温柔。"
}
