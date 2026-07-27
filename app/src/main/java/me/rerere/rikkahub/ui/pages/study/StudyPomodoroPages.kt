package me.rerere.rikkahub.ui.pages.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.PomodoroTheme
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getAssistantTTSProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
internal fun StudyPomodoroPageContent() {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val settingsStore = koinInject<SettingsStore>()
    val scope = rememberCoroutineScope()
    val assistant = settings.getCurrentAssistant()
    var minutes by remember { mutableIntStateOf(25) }
    var customMinutes by remember { mutableStateOf("") }
    var taskText by remember { mutableStateOf("") }
    var showThemePicker by remember { mutableStateOf(false) }
    val voiceEnabled = settings.pomodoroVoiceEnabled
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("番茄钟") },
                navigationIcon = { BackButton() },
                actions = {
                    TextButton(onClick = { showThemePicker = true }) { Text("配色") }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = StudyPomodoroPageColor,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                CompanionPrepCard(
                    assistant = assistant,
                    voiceEnabled = voiceEnabled,
                    onVoiceToggle = { enabled ->
                        scope.launch {
                            settingsStore.update { current ->
                                current.copy(pomodoroVoiceEnabled = enabled)
                            }
                        }
                    },
                )
            }
            item {
                OutlinedTextField(
                    value = taskText,
                    onValueChange = { taskText = it },
                    label = { Text("这一轮要完成什么") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                DurationCard(
                    selectedMinutes = minutes,
                    customMinutes = customMinutes,
                    onSelect = {
                        minutes = it
                        customMinutes = ""
                    },
                    onCustom = {
                        customMinutes = it.filter(Char::isDigit).take(3)
                        customMinutes.toIntOrNull()?.takeIf { value -> value > 0 }?.let { minutes = it }
                    },
                )
            }
            item {
                Button(
                    onClick = {
                        navController.navigate(
                            Screen.StudyPomodoroFocus(
                                minutes = minutes.coerceAtLeast(1),
                                task = taskText.trim(),
                                imageEnabled = false,
                                voiceEnabled = voiceEnabled,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(HugeIcons.Play, null)
                    Spacer(Modifier.width(8.dp))
                    Text("开始陪学")
                }
            }
        }
    }

    if (showThemePicker) {
        PomodoroThemePickerDialog(
            selected = settings.pomodoroTheme,
            onSelect = { theme ->
                scope.launch { settingsStore.update { it.copy(pomodoroTheme = theme) } }
            },
            onDismiss = { showThemePicker = false },
        )
    }
}

@Composable
internal fun StudyPomodoroFocusPageContent(
    minutes: Int,
    task: String,
    imageEnabled: Boolean,
    voiceEnabled: Boolean,
    vm: StudyVM = koinViewModel(),
) {
    val settings = LocalSettings.current
    val assistant = settings.getCurrentAssistant()
    val chatService: ChatService = koinInject()
    val conversationRepository = koinInject<ConversationRepository>()
    val settingsStore = koinInject<SettingsStore>()
    val tts = LocalTTSState.current
    val scope = rememberCoroutineScope()
    val safeMinutes = minutes.coerceAtLeast(1)
    val totalSeconds = safeMinutes * 60
    var remainingSeconds by remember(safeMinutes) { mutableIntStateOf(totalSeconds) }
    var finished by remember { mutableStateOf(false) }
    var studyConversationId by remember { mutableStateOf<Uuid?>(null) }
    var chatText by remember { mutableStateOf("") }
    var userLine by remember { mutableStateOf("") }
    var coachReply by remember { mutableStateOf("") }
    var waitingReply by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    val focusPalette = focusPalette(settings.pomodoroTheme)
    val studiedSeconds = (totalSeconds - remainingSeconds).coerceIn(0, totalSeconds)

    @Suppress("UNUSED_VARIABLE")
    val keepImageFlagCompatible = imageEnabled

    fun finishPomodoro(early: Boolean) {
        if (finished) return
        finished = true
        val elapsedSeconds = (totalSeconds - remainingSeconds).coerceIn(0, totalSeconds)
        val recordedMinutes = elapsedSeconds.toRecordedMinutes()
        if (recordedMinutes > 0) {
            vm.completePomodoro(recordedMinutes)
        }
        val line = if (early) {
            if (recordedMinutes > 0) {
                "这一轮先收住，已经学习了 ${studyDurationText(elapsedSeconds)}。奖励按实际学习时长记好了。"
            } else {
                "这一轮还没正式开始计时，先不记奖励。重新开一轮也来得及。"
            }
        } else {
            "这一轮完成了，已经学习了 ${studyDurationText(totalSeconds)}。你真的坐住了，奖励我已经替你收好啦。"
        }
        coachReply = line
        if (voiceEnabled) {
            scope.launch {
                tts.speak(
                    text = line,
                    flushCalled = true,
                    providerOverride = settings.getAssistantTTSProvider(assistant.id),
                )
            }
        }
    }

    LaunchedEffect(safeMinutes) {
        val target = conversationRepository.getRecentConversations(assistant.id, limit = 1)
            .firstOrNull()
            ?: Conversation.ofId(
                id = Uuid.random(),
                assistantId = assistant.id,
                newConversation = true,
            )
        studyConversationId = target.id
        chatService.initializeConversation(target.id)
        waitingReply = true
        scope.launch {
            val line = runCatching {
                chatService.sendVoiceCallTurn(
                    conversationId = target.id,
                    text = buildPomodoroOpeningPrompt(task),
                    visibleUserText = "开始番茄钟：${task.ifBlank { "这一轮学习" }}",
                )
            }.getOrNull() ?: buildEncourageLine(task, assistant)
            coachReply = line
            waitingReply = false
            if (voiceEnabled) {
                tts.speak(
                    text = line,
                    flushCalled = true,
                    providerOverride = settings.getAssistantTTSProvider(assistant.id),
                )
            }
        }
        while (!finished && remainingSeconds > 0) {
            delay(1_000)
            if (!finished) {
                remainingSeconds -= 1
            }
        }
        if (!finished && remainingSeconds <= 0) {
            finishPomodoro(early = false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(focusBrush(settings.pomodoroTheme)),
    ) {
        TextButton(
            onClick = { showThemePicker = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 52.dp, end = 16.dp)
                .height(48.dp)
                .widthIn(min = 92.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = focusPalette.primaryText),
        ) {
            Text("配色", fontWeight = FontWeight.SemiBold)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(focusPalette.topGlow, Color.Transparent, focusPalette.bottomGlow),
                    ),
                )
                .padding(horizontal = 22.dp, vertical = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.7f))
            PomodoroTimerCircle(
                timeText = secondsText(remainingSeconds),
                task = task.ifBlank { "专注这一轮" },
                progress = remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1),
                palette = focusPalette,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "已学习 ${studyDurationText(studiedSeconds)}",
                color = focusPalette.secondaryText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(
                onClick = { finishPomodoro(early = true) },
                enabled = !finished,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = focusPalette.primaryText),
            ) {
                Icon(HugeIcons.StopCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("提前结束")
            }
            Spacer(Modifier.height(34.dp))
            if (waitingReply || coachReply.isNotBlank()) {
                Text(
                    text = if (waitingReply) "正在回复..." else coachReply,
                    style = MaterialTheme.typography.titleMedium,
                    color = focusPalette.primaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            FocusChatPanel(
                userLine = userLine,
                chatText = chatText,
                assistantName = assistant.name.ifBlank { "当前角色" },
                palette = focusPalette,
                onChatChange = { chatText = it },
                onSend = {
                    val text = chatText.trim()
                    if (text.isNotBlank()) {
                        userLine = text
                        chatText = ""
                        waitingReply = true
                        scope.launch {
                            val conversationId = studyConversationId
                            val line = if (conversationId == null) {
                                buildEncourageLine(task, assistant)
                            } else {
                                chatService.sendVoiceCallTurn(
                                    conversationId = conversationId,
                                    text = buildStudyChatPrompt(text, task),
                                    visibleUserText = text,
                                ) ?: buildEncourageLine(task, assistant)
                            }
                            coachReply = line
                            waitingReply = false
                            if (voiceEnabled) {
                                tts.speak(
                                    text = line,
                                    flushCalled = true,
                                    providerOverride = settings.getAssistantTTSProvider(assistant.id),
                                )
                            }
                        }
                    }
                },
            )
        }
    }

    if (showThemePicker) {
        PomodoroThemePickerDialog(
            selected = settings.pomodoroTheme,
            onSelect = { theme ->
                scope.launch { settingsStore.update { it.copy(pomodoroTheme = theme) } }
            },
            onDismiss = { showThemePicker = false },
        )
    }
}

@Composable
private fun CompanionPrepCard(
    assistant: Assistant,
    voiceEnabled: Boolean,
    onVoiceToggle: (Boolean) -> Unit,
) {
    PomodoroCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("开始一轮番茄钟", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${assistant.name}会在倒计时里陪你轻声聊天。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = voiceEnabled,
                onClick = { onVoiceToggle(!voiceEnabled) },
                label = { Text("语音鼓励") },
            )
        }
    }
}

@Composable
private fun DurationCard(
    selectedMinutes: Int,
    customMinutes: String,
    onSelect: (Int) -> Unit,
    onCustom: (String) -> Unit,
) {
    PomodoroCard {
        Text("选择时长", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(25, 40, 50, 90).forEach { minutes ->
                FilterChip(
                    selected = selectedMinutes == minutes,
                    onClick = { onSelect(minutes) },
                    label = { Text("${minutes}分钟") },
                )
            }
        }
        OutlinedTextField(
            value = customMinutes,
            onValueChange = onCustom,
            label = { Text("自定义分钟") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val rewardBlocks = selectedMinutes.coerceAtLeast(0) / StudyRules.STUDY_REWARD_INTERVAL_MINUTES
        Text(
            "本轮预计 +${rewardBlocks * StudyRules.STUDY_REWARD_KUDOS} 夸夸值 · ${rewardBlocks} 次单抽进度",
            color = Color(0xFF8067B7),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PomodoroTimerCircle(
    timeText: String,
    task: String,
    progress: Float,
    palette: PomodoroFocusPalette,
) {
    Box(
        modifier = Modifier
            .size(246.dp)
            .clip(CircleShape)
            .background(palette.timerSurface),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.size(222.dp),
            strokeWidth = 5.dp,
            color = palette.ring,
            trackColor = palette.track,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.displayLarge,
                color = palette.primaryText,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = task,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.secondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 36.dp),
            )
        }
    }
}

@Composable
private fun FocusChatPanel(
    userLine: String,
    chatText: String,
    assistantName: String,
    palette: PomodoroFocusPalette,
    onChatChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (userLine.isNotBlank()) {
            Text(
                "我：$userLine",
                style = MaterialTheme.typography.bodyLarge,
                color = palette.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            color = palette.inputSurface,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = chatText,
                    onValueChange = onChatChange,
                    placeholder = { Text("跟${assistantName}说一句...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = RoundedCornerShape(20.dp),
                )
                Surface(
                    color = if (chatText.isNotBlank()) palette.action else palette.actionMuted,
                    shape = CircleShape,
                ) {
                    androidx.compose.material3.IconButton(onClick = onSend, enabled = chatText.isNotBlank()) {
                        Icon(HugeIcons.ArrowUp02, null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun PomodoroCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

private data class PomodoroFocusPalette(
    val background: List<Color>,
    val topGlow: Color,
    val bottomGlow: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val ring: Color,
    val track: Color,
    val timerSurface: Color,
    val inputSurface: Color,
    val action: Color,
    val actionMuted: Color,
)

private fun focusPalette(theme: PomodoroTheme): PomodoroFocusPalette = when (theme) {
    PomodoroTheme.CLOUD -> PomodoroFocusPalette(
        background = listOf(Color(0xFFF4F6F6), Color(0xFFEEF3F4), Color(0xFFF2F3F1)),
        topGlow = Color.White.copy(alpha = 0.18f),
        bottomGlow = Color(0xFF5C6B7D).copy(alpha = 0.06f),
        primaryText = Color(0xFF35434D),
        secondaryText = Color(0xFF667782),
        ring = Color(0xFF7895A6),
        track = Color.White.copy(alpha = 0.34f),
        timerSurface = Color.White.copy(alpha = 0.24f),
        inputSurface = Color(0xFFFFF8FB).copy(alpha = 0.92f),
        action = Color(0xFF3D7EA6),
        actionMuted = Color(0xFFDCECF4),
    )

    PomodoroTheme.MIDNIGHT -> PomodoroFocusPalette(
        background = listOf(Color(0xFF111827), Color(0xFF172033), Color(0xFF0F172A)),
        topGlow = Color(0xFF88A9C0).copy(alpha = 0.12f),
        bottomGlow = Color.Black.copy(alpha = 0.18f),
        primaryText = Color(0xFFE8EEF5),
        secondaryText = Color(0xFFB2C1CF),
        ring = Color(0xFF88A9C0),
        track = Color.White.copy(alpha = 0.12f),
        timerSurface = Color(0xFF253247).copy(alpha = 0.72f),
        inputSurface = Color(0xFF1C2738).copy(alpha = 0.94f),
        action = Color(0xFF668EAA),
        actionMuted = Color(0xFF34485B),
    )
}

@Composable
private fun PomodoroThemePickerDialog(
    selected: PomodoroTheme,
    onSelect: (PomodoroTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("专注氛围") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    PomodoroTheme.CLOUD to "云雾原版",
                    PomodoroTheme.MIDNIGHT to "深夜墨蓝",
                ).forEach { (theme, label) ->
                    val palette = focusPalette(theme)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                onSelect(theme)
                                onDismiss()
                            },
                        color = palette.background[1],
                        border = BorderStroke(
                            width = if (selected == theme) 2.dp else 1.dp,
                            color = if (selected == theme) palette.ring else palette.track,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(34.dp),
                                shape = CircleShape,
                                color = palette.timerSurface,
                                border = BorderStroke(3.dp, palette.ring),
                            ) {}
                            Text(
                                text = label,
                                color = palette.primaryText,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected == theme) {
                                Text("已选择", color = palette.secondaryText)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun focusBrush(theme: PomodoroTheme): Brush = Brush.verticalGradient(
    focusPalette(theme).background,
)

private fun secondsText(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%02d:%02d".format(safe / 60, safe % 60)
}

private fun Int.toRecordedMinutes(): Int =
    (coerceAtLeast(0) / 60).coerceAtLeast(0)

private fun studyDurationText(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val restSeconds = safe % 60
    return when {
        safe < 60 -> "${restSeconds}秒"
        restSeconds == 0 -> "${minutes}分钟"
        else -> "${minutes}分${restSeconds}秒"
    }
}

private fun buildEncourageLine(taskText: String, assistant: Assistant): String {
    val target = taskText.ifBlank { "这一轮任务" }
    return "${assistant.name.ifBlank { "当前角色" }}：先不想那么远，我们只把“$target”往前推一点点。"
}

private fun buildStudyChatPrompt(userText: String, taskText: String): String {
    val target = taskText.ifBlank { "这一轮学习任务" }
    return "我正在番茄钟学习，任务是“$target”。我想对你说：$userText\n请按你的角色人设自然回复，短一点，并保持角色自己的关系边界和表达方式。只输出你要说出口的话。"
}

private fun buildPomodoroOpeningPrompt(taskText: String): String {
    val target = taskText.ifBlank { "这一轮学习任务" }
    return "用户刚打开番茄钟专注页，准备开始“$target”。请按你的角色人设和关系边界给一句非常短的鼓励，不要预设监督职责或身体距离。不要解释任务，不要输出提示词。"
}

private val StudyPomodoroPageColor = Color(0xFFF7F3EA)
