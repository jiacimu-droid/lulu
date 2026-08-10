package me.rerere.rikkahub.ui.pages.study

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
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
import me.rerere.rikkahub.plugin.webview.PomodoroTimerService
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
fun StudyPomodoroPage() {
    StudyPomodoroPageContent()
}

@Composable
fun StudyPomodoroFocusPage(
    minutes: Int,
    task: String,
    imageEnabled: Boolean,
    voiceEnabled: Boolean,
    vm: StudyVM = koinViewModel(),
) {
    StudyPomodoroFocusPageContent(
        minutes = minutes,
        task = task,
        imageEnabled = imageEnabled,
        voiceEnabled = voiceEnabled,
        vm = vm,
    )
}

@Composable
internal fun StudyPomodoroPageContent() {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val settingsStore = koinInject<SettingsStore>()
    val scope = rememberCoroutineScope()
    var selectedAssistantId by remember(settings.assistantId) { mutableStateOf(settings.assistantId) }
    val assistant = settings.assistants.firstOrNull { it.id == selectedAssistantId }
        ?: settings.assistants.firstOrNull()
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
                actions = { TextButton(onClick = { showThemePicker = true }) { Text("配色") } },
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
                    assistants = settings.assistants,
                    onAssistantSelected = { selected ->
                        selectedAssistantId = selected.id
                        scope.launch { settingsStore.updateAssistant(selected.id) }
                    },
                    voiceEnabled = voiceEnabled,
                    onVoiceToggle = { enabled ->
                        scope.launch { settingsStore.update { it.copy(pomodoroVoiceEnabled = enabled) } }
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
                        customMinutes.toIntOrNull()?.takeIf { value -> value > 0 }?.let { value -> minutes = value }
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
                    Text(if (settings.assistants.isEmpty()) "开始计时" else "开始陪学")
                }
            }
        }
    }

    if (showThemePicker) {
        PomodoroThemePickerDialog(
            selected = settings.pomodoroTheme,
            onSelect = { theme -> scope.launch { settingsStore.update { it.copy(pomodoroTheme = theme) } } },
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
    @Suppress("UNUSED_PARAMETER") vm: StudyVM = koinViewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val hasAssistant = settings.assistants.isNotEmpty()
    val assistant = settings.getCurrentAssistant()
    val chatService: ChatService = koinInject()
    val conversationRepository = koinInject<ConversationRepository>()
    val settingsStore = koinInject<SettingsStore>()
    val tts = LocalTTSState.current
    val scope = rememberCoroutineScope()
    val requestedTotalSeconds = minutes.coerceAtLeast(1) * 60
    val wasAlreadyRunning = remember { PomodoroTimerService.isRunning() }
    val activeTask = remember(task, wasAlreadyRunning) {
        if (wasAlreadyRunning) PomodoroTimerService.getTask().ifBlank { task } else task
    }
    var totalSeconds by remember {
        mutableIntStateOf(
            if (wasAlreadyRunning) PomodoroTimerService.getTotalSeconds().coerceAtLeast(requestedTotalSeconds)
            else requestedTotalSeconds,
        )
    }
    var remainingSeconds by remember {
        mutableIntStateOf(
            if (wasAlreadyRunning) PomodoroTimerService.getRemainingSeconds().coerceAtLeast(0)
            else requestedTotalSeconds,
        )
    }
    var finished by remember { mutableStateOf(false) }
    var studyConversationId by remember { mutableStateOf<Uuid?>(null) }
    var chatText by remember { mutableStateOf("") }
    var userLine by remember { mutableStateOf("") }
    var coachReply by remember { mutableStateOf("") }
    var waitingReply by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var deliberatelyLeaving by remember { mutableStateOf(false) }
    val focusPalette = focusPalette(settings.pomodoroTheme)
    val studiedSeconds = (totalSeconds - remainingSeconds).coerceIn(0, totalSeconds)

    @Suppress("UNUSED_VARIABLE") val keepImageFlagCompatible = imageEnabled

    fun openFocusAgain() {
        navController.navigate(
            Screen.StudyPomodoroFocus(
                minutes = (PomodoroTimerService.getTotalSeconds().coerceAtLeast(60) + 59) / 60,
                task = PomodoroTimerService.getTask().ifBlank { activeTask },
                imageEnabled = imageEnabled,
                voiceEnabled = voiceEnabled,
            ),
        )
    }

    fun showMiniBar() {
        val host = activity ?: return
        if (!PomodoroTimerService.isRunning()) return
        PomodoroInAppOverlayController.show(
            activity = host,
            task = PomodoroTimerService.getTask().ifBlank { activeTask },
            onOpen = ::openFocusAgain,
            onStop = { PomodoroTimerService.stop(context) },
        )
    }

    LaunchedEffect(Unit) {
        activity?.let(PomodoroInAppOverlayController::hide)
        if (!PomodoroTimerService.isRunning()) {
            PomodoroTimerService.start(
                context = context,
                seconds = requestedTotalSeconds,
                task = activeTask,
                recordStudy = true,
            )
            totalSeconds = requestedTotalSeconds
            remainingSeconds = requestedTotalSeconds
        }
        while (PomodoroTimerService.isRunning()) {
            remainingSeconds = PomodoroTimerService.getRemainingSeconds().coerceAtLeast(0)
            totalSeconds = PomodoroTimerService.getTotalSeconds().coerceAtLeast(1)
            delay(500L)
        }
        remainingSeconds = 0
        finished = true
        coachReply = "这一轮完成了，学习时长已经记进今天的记录。"
    }

    DisposableEffect(activity, activeTask) {
        onDispose {
            if (PomodoroTimerService.isRunning()) showMiniBar()
        }
    }

    LaunchedEffect(wasAlreadyRunning) {
        if (wasAlreadyRunning || !hasAssistant) return@LaunchedEffect
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
        val line = runCatching {
            chatService.sendVoiceCallTurn(
                conversationId = target.id,
                text = buildPomodoroOpeningPrompt(activeTask),
                visibleUserText = "开始番茄钟：${activeTask.ifBlank { "这一轮学习" }}",
            )
        }.getOrNull() ?: buildEncourageLine(activeTask, assistant)
        coachReply = line
        waitingReply = false
        if (voiceEnabled && hasAssistant) {
            tts.speak(
                text = line,
                flushCalled = true,
                providerOverride = settings.getAssistantTTSProvider(assistant.id),
            )
        }
    }

    Box(Modifier.fillMaxSize().background(focusBrush(settings.pomodoroTheme))) {
        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 48.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    deliberatelyLeaving = true
                    showMiniBar()
                    navController.popBackStack()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = focusPalette.primaryText),
            ) { Text("退出，继续计时", fontWeight = FontWeight.SemiBold) }
            TextButton(
                onClick = { showThemePicker = true },
                colors = ButtonDefaults.textButtonColors(contentColor = focusPalette.primaryText),
            ) { Text("配色", fontWeight = FontWeight.SemiBold) }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(focusPalette.topGlow, Color.Transparent, focusPalette.bottomGlow)))
                .padding(horizontal = 22.dp, vertical = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.7f))
            PomodoroTimerCircle(
                timeText = secondsText(remainingSeconds),
                task = activeTask.ifBlank { "专注这一轮" },
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
                onClick = {
                    PomodoroTimerService.stop(context)
                    finished = true
                    coachReply = "这一轮先收住，已学习的整分钟已经记进记录。"
                },
                enabled = !finished,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = focusPalette.primaryText),
            ) {
                Icon(HugeIcons.StopCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("结束番茄钟")
            }
            Spacer(Modifier.height(28.dp))
            if (waitingReply || coachReply.isNotBlank()) {
                Text(
                    if (waitingReply) "正在回复..." else coachReply,
                    style = MaterialTheme.typography.titleMedium,
                    color = focusPalette.primaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (hasAssistant) FocusChatPanel(
                userLine = userLine,
                chatText = chatText,
                assistantName = assistant.name.ifBlank { "当前角色" },
                palette = focusPalette,
                onChatChange = { chatText = it },
                onSend = {
                    val text = chatText.trim()
                    if (text.isBlank()) return@FocusChatPanel
                    userLine = text
                    chatText = ""
                    waitingReply = true
                    scope.launch {
                        val line = studyConversationId?.let { conversationId ->
                            chatService.sendVoiceCallTurn(
                                conversationId = conversationId,
                                text = buildStudyChatPrompt(text, activeTask),
                                visibleUserText = text,
                            )
                        } ?: buildEncourageLine(activeTask, assistant)
                        coachReply = line ?: buildEncourageLine(activeTask, assistant)
                        waitingReply = false
                        if (voiceEnabled && hasAssistant) {
                            tts.speak(
                                text = coachReply,
                                flushCalled = true,
                                providerOverride = settings.getAssistantTTSProvider(assistant.id),
                            )
                        }
                    }
                },
            )
        }
    }

    if (showThemePicker) {
        PomodoroThemePickerDialog(
            selected = settings.pomodoroTheme,
            onSelect = { theme -> scope.launch { settingsStore.update { it.copy(pomodoroTheme = theme) } } },
            onDismiss = { showThemePicker = false },
        )
    }
}

@Composable
private fun CompanionPrepCard(
    assistant: Assistant?,
    assistants: List<Assistant>,
    onAssistantSelected: (Assistant) -> Unit,
    voiceEnabled: Boolean,
    onVoiceToggle: (Boolean) -> Unit,
) {
    var showAssistantPicker by remember { mutableStateOf(false) }
    PomodoroCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("陪学角色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    assistant?.name?.ifBlank { "未命名角色" } ?: "未选择角色",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { showAssistantPicker = true }, enabled = assistants.isNotEmpty()) {
                Text(if (assistants.isEmpty()) "暂无角色" else "选择")
            }
        }
        FilterChip(
            selected = voiceEnabled,
            onClick = { onVoiceToggle(!voiceEnabled) },
            enabled = assistants.isNotEmpty(),
            label = { Text("语音鼓励") },
        )
    }
    if (showAssistantPicker) {
        AlertDialog(
            onDismissRequest = { showAssistantPicker = false },
            title = { Text("选择角色") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    assistants.forEach { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onAssistantSelected(item)
                                showAssistantPicker = false
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (item.id == assistant?.id) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        ) {
                            Text(
                                item.name.ifBlank { "未命名角色" },
                                modifier = Modifier.padding(14.dp),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAssistantPicker = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun DurationCard(selectedMinutes: Int, customMinutes: String, onSelect: (Int) -> Unit, onCustom: (String) -> Unit) {
    PomodoroCard {
        Text("选择时长", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(25, 40, 60).forEach { item ->
                FilterChip(selected = selectedMinutes == item, onClick = { onSelect(item) }, label = { Text("${item}分钟") })
            }
        }
        OutlinedTextField(
            value = customMinutes,
            onValueChange = onCustom,
            label = { Text("自定义分钟") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PomodoroTimerCircle(timeText: String, task: String, progress: Float, palette: PomodoroFocusPalette) {
    Box(
        modifier = Modifier.size(246.dp).clip(CircleShape).background(palette.timerSurface),
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
            Text(timeText, style = MaterialTheme.typography.displayLarge, color = palette.primaryText, fontWeight = FontWeight.SemiBold)
            Text(
                task,
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (userLine.isNotBlank()) {
            Text("我：$userLine", color = palette.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Surface(color = palette.inputSurface, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
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
                    shape = RoundedCornerShape(20.dp),
                )
                Surface(color = if (chatText.isNotBlank()) palette.action else palette.actionMuted, shape = CircleShape) {
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
    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
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
        listOf(Color(0xFFF4F6F6), Color(0xFFEEF3F4), Color(0xFFF2F3F1)),
        Color.White.copy(alpha = 0.18f), Color(0xFF5C6B7D).copy(alpha = 0.06f),
        Color(0xFF35434D), Color(0xFF667782), Color(0xFF7895A6),
        Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.24f),
        Color(0xFFFFF8FB).copy(alpha = 0.92f), Color(0xFF3D7EA6), Color(0xFFDCECF4),
    )
    PomodoroTheme.MIDNIGHT -> PomodoroFocusPalette(
        listOf(Color(0xFF111827), Color(0xFF172033), Color(0xFF0F172A)),
        Color(0xFF88A9C0).copy(alpha = 0.12f), Color.Black.copy(alpha = 0.18f),
        Color(0xFFE8EEF5), Color(0xFFB2C1CF), Color(0xFF88A9C0),
        Color.White.copy(alpha = 0.12f), Color(0xFF253247).copy(alpha = 0.72f),
        Color(0xFF1C2738).copy(alpha = 0.94f), Color(0xFF668EAA), Color(0xFF34485B),
    )
}

@Composable
private fun PomodoroThemePickerDialog(selected: PomodoroTheme, onSelect: (PomodoroTheme) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("专注氛围") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(PomodoroTheme.CLOUD to "云雾原版", PomodoroTheme.MIDNIGHT to "深夜墨蓝").forEach { (theme, label) ->
                    val palette = focusPalette(theme)
                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable {
                            onSelect(theme)
                            onDismiss()
                        },
                        color = palette.background[1],
                        border = BorderStroke(if (selected == theme) 2.dp else 1.dp, if (selected == theme) palette.ring else palette.track),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(Modifier.size(34.dp), CircleShape, palette.timerSurface, border = BorderStroke(3.dp, palette.ring)) {}
                            Text(label, color = palette.primaryText, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (selected == theme) Text("已选择", color = palette.secondaryText)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun focusBrush(theme: PomodoroTheme): Brush = Brush.verticalGradient(focusPalette(theme).background)
private fun secondsText(seconds: Int): String = "%02d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)
private fun studyDurationText(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val rest = safe % 60
    return when {
        safe < 60 -> "${rest}秒"
        rest == 0 -> "${minutes}分钟"
        else -> "${minutes}分${rest}秒"
    }
}
private fun buildEncourageLine(taskText: String, assistant: Assistant): String =
    "${assistant.name.ifBlank { "当前角色" }}：先不想那么远，我们只把“${taskText.ifBlank { "这一轮任务" }}”往前推一点点。"
private fun buildStudyChatPrompt(userText: String, taskText: String): String =
    "我正在番茄钟学习，任务是“${taskText.ifBlank { "这一轮学习任务" }}”。我想对你说：$userText\n请按你的角色人设自然回复，短一点，只输出要说出口的话。"
private fun buildPomodoroOpeningPrompt(taskText: String): String =
    "用户刚打开番茄钟专注页，准备开始“${taskText.ifBlank { "这一轮学习任务" }}”。请按角色人设给一句非常短的鼓励，不要预设监督职责或身体距离。"
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
private val StudyPomodoroPageColor = Color(0xFFF7F3EA)
