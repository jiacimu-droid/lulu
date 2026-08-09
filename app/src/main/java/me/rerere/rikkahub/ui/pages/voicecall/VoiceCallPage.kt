package me.rerere.rikkahub.ui.pages.voicecall

import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.asr.ASRStatus
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft02
import me.rerere.hugeicons.stroke.Call02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Moon02
import me.rerere.hugeicons.stroke.TransactionHistory
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getAssistantTTSProvider
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.voicecall.ProactiveCallManager
import me.rerere.rikkahub.data.voicecall.VoiceCallLine
import me.rerere.rikkahub.data.voicecall.VoiceCallRepository
import me.rerere.rikkahub.data.voicecall.VoiceCallRole
import me.rerere.rikkahub.data.voicecall.VoiceCallSession
import me.rerere.rikkahub.data.voicecall.VoiceCallStatus
import me.rerere.rikkahub.data.voicecall.hasUserFacingContent
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.VoiceCallForegroundService
import me.rerere.rikkahub.ui.components.message.extractSpeakableRoleText
import me.rerere.rikkahub.ui.components.ui.FloatingWindow
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

internal enum class CallStage {
    Idle,
    Ringing,
    Connecting,
    Active,
    Ended,
}

@Composable
fun VoiceCallPage(
    conversationId: String,
    assistantId: String,
    sessionId: String? = null,
    incomingCall: Boolean = false,
    autoStart: Boolean = false,
    incomingReason: String = "",
) {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { VoiceCallRepository(context.applicationContext) }
    val chatService: ChatService = koinInject()
    val tts = LocalTTSState.current
    val asr = LocalASRState.current
    val asrState by asr.state.collectAsState()
    val isSpeaking by tts.isSpeaking.collectAsState()
    val scope = rememberCoroutineScope()
    val assistant = remember(settings, assistantId) {
        runCatching { settings.getAssistantById(Uuid.parse(assistantId)) }.getOrNull()
    }
    val incomingRingtone = remember(context, assistant?.proactiveCallSetting?.ringtoneUri) {
        runCatching {
            val uri = assistant?.proactiveCallSetting?.ringtoneUri
                ?.takeIf(String::isNotBlank)
                ?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            RingtoneManager.getRingtone(context.applicationContext, uri)
        }.getOrNull()
    }
    val ttsProvider = remember(settings, assistant) {
        assistant?.let { settings.getAssistantTTSProvider(it.id) }
    }
    val assistantName = assistant?.name?.ifBlank { "对方" } ?: "对方"
    val isHistoryOnly = sessionId != null
    var session by remember(sessionId, conversationId, assistantId) { mutableStateOf<VoiceCallSession?>(null) }
    var stage by remember(sessionId, incomingCall) {
        mutableStateOf(
            when {
                isHistoryOnly -> CallStage.Ended
                incomingCall -> CallStage.Ringing
                else -> CallStage.Idle
            },
        )
    }
    var showMiniWindow by remember { mutableStateOf(false) }
    var sleepMode by remember { mutableStateOf(false) }
    var sleepMinutes by remember { mutableLongStateOf(20L) }
    var lastTranscript by remember { mutableStateOf("") }
    var assistantTurnInProgress by remember { mutableStateOf(false) }
    var silenceJob by remember { mutableStateOf<Job?>(null) }
    var sleepJob by remember { mutableStateOf<Job?>(null) }
    var assistantGenerationJob by remember { mutableStateOf<Job?>(null) }
    var transcriptRevision by remember { mutableLongStateOf(0L) }
    var userTurnSubmitting by remember { mutableStateOf(false) }
    val latestSession by rememberUpdatedState(session)
    val latestStage by rememberUpdatedState(stage)

    fun saveLine(line: VoiceCallLine, speak: Boolean = false) {
        val current = session ?: return
        val updated = repository.appendLine(current, line)
        session = updated
        if (speak) {
            val speakableText = line.text.cleanRoleLineForUser().extractSpeakableRoleText()
            if (speakableText.isBlank()) {
                assistantTurnInProgress = false
            } else {
                scope.launch {
                    assistantTurnInProgress = true
                    try {
                        speakInSegments(tts, speakableText, ttsProvider)
                    } finally {
                        assistantTurnInProgress = false
                    }
                }
            }
        }
    }

    fun saveAssistantSleepLine(text: String) {
        saveLine(
            VoiceCallLine(
                role = VoiceCallRole.Assistant,
                text = text,
                replayable = true,
            ),
            speak = false,
        )
    }

    fun assistantSay(
        text: String,
        replayable: Boolean = true,
        speak: Boolean = true,
    ) {
        saveLine(
            VoiceCallLine(
                role = VoiceCallRole.Assistant,
                text = text,
                replayable = replayable,
            ),
            speak = speak,
        )
        if (!speak) assistantTurnInProgress = false
    }

    fun createStreamSpeaker(buffer: StringBuilder): suspend (String) -> Unit {
        var firstSegment = true
        return { segment ->
            val speakable = segment.cleanRoleLineForUser().extractSpeakableRoleText()
            if (speakable.isNotBlank()) {
                buffer.append(segment)
                tts.speak(
                    text = speakable,
                    flushCalled = firstSegment,
                    providerOverride = ttsProvider,
                )
                firstSegment = false
            }
        }
    }

    fun startCall() {
        if (isHistoryOnly || stage !in setOf(CallStage.Idle, CallStage.Ringing)) return
        VoiceCallForegroundService.start(context.applicationContext, assistantName)
        stage = CallStage.Connecting
        assistantGenerationJob?.cancel()
        assistantGenerationJob = scope.launch {
            assistantTurnInProgress = true
            val streamedOpening = StringBuilder()
            val streamSpeaker = createStreamSpeaker(streamedOpening)
            val recentOpenings = repository.recentAssistantOpenings(
                conversationId = conversationId,
                assistantId = assistantId,
            )
            val opening = try {
                chatService.sendVoiceCallTurn(
                    conversationId = Uuid.parse(conversationId),
                    text = buildVoiceCallOpeningPrompt(
                        assistantName = assistantName,
                        recentOpenings = recentOpenings,
                        variationSeed = System.currentTimeMillis(),
                        incomingReason = incomingReason.takeIf(String::isNotBlank),
                    ),
                    visibleUserText = null,
                    onPartialReply = streamSpeaker,
                ).takeIf(::isUsableVoiceCallReply)
                    ?: if (streamedOpening.isEmpty()) chatService.sendVoiceCallTurn(
                        conversationId = Uuid.parse(conversationId),
                        text = buildVoiceCallOpeningPrompt(
                            assistantName = assistantName,
                            recentOpenings = recentOpenings,
                            variationSeed = System.currentTimeMillis() + 1L,
                            retry = true,
                            incomingReason = incomingReason.takeIf(String::isNotBlank),
                        ),
                        visibleUserText = null,
                        onPartialReply = streamSpeaker,
                    ).takeIf(::isUsableVoiceCallReply) else null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            stage = CallStage.Active
            if (opening != null) {
                assistantSay(
                    text = opening,
                    replayable = true,
                    speak = streamedOpening.isEmpty(),
                )
            } else if (streamedOpening.isNotEmpty()) {
                assistantSay(
                    text = streamedOpening.toString(),
                    replayable = true,
                    speak = false,
                )
            } else {
                assistantTurnInProgress = false
                saveLine(
                    VoiceCallLine(
                        role = VoiceCallRole.System,
                        text = "开场回复生成失败，已恢复倾听。",
                        replayable = false,
                    ),
                )
            }
        }
    }

    fun startListening() {
        if (isHistoryOnly || sleepMode || stage != CallStage.Active || assistantTurnInProgress || isSpeaking) return
        if (userTurnSubmitting || (asrState.status != ASRStatus.Idle && asrState.status != ASRStatus.Error)) return
        lastTranscript = ""
        transcriptRevision++
        asr.start { transcript ->
            if (userTurnSubmitting || assistantTurnInProgress || stage != CallStage.Active) return@start
            val text = transcript.trim()
            if (text.isBlank() || text == lastTranscript) return@start
            lastTranscript = text
            transcriptRevision++
            val scheduledRevision = transcriptRevision
            silenceJob?.cancel()
            silenceJob = scope.launch {
                delay(voiceCallEndOfSpeechDelayMillis(text))
                if (!shouldCommitVoiceTranscript(
                        scheduledRevision = scheduledRevision,
                        currentRevision = transcriptRevision,
                        userTurnSubmitting = userTurnSubmitting,
                        stageActive = stage == CallStage.Active,
                        transcript = lastTranscript,
                    )
                ) return@launch

                val finalText = lastTranscript.trim()
                userTurnSubmitting = true
                assistantTurnInProgress = true
                asr.stop()
                saveLine(VoiceCallLine(role = VoiceCallRole.User, text = finalText))
                val streamedReply = StringBuilder()
                val streamSpeaker = createStreamSpeaker(streamedReply)
                val reply = try {
                    chatService.sendVoiceCallTurn(
                        conversationId = Uuid.parse(conversationId),
                        text = "${finalText}\n\n$VOICE_CALL_REPLY_PROMPT",
                        visibleUserText = finalText,
                        onPartialReply = streamSpeaker,
                    ).takeIf(::isUsableVoiceCallReply)
                        ?: if (streamedReply.isEmpty()) chatService.sendVoiceCallTurn(
                            conversationId = Uuid.parse(conversationId),
                            text = "${finalText}\n\n$VOICE_CALL_RETRY_PROMPT",
                            visibleUserText = null,
                            onPartialReply = streamSpeaker,
                        ).takeIf(::isUsableVoiceCallReply) else null
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    null
                } finally {
                    userTurnSubmitting = false
                }
                if (reply != null) {
                    assistantSay(text = reply, replayable = true, speak = streamedReply.isEmpty())
                } else if (streamedReply.isNotEmpty()) {
                    assistantSay(text = streamedReply.toString(), replayable = true, speak = false)
                } else {
                    assistantTurnInProgress = false
                    saveLine(
                        VoiceCallLine(
                            role = VoiceCallRole.System,
                            text = "这一轮回复生成失败，已恢复倾听。",
                            replayable = false,
                        ),
                    )
                }
            }
        }
    }

    fun endCall() {
        transcriptRevision++
        userTurnSubmitting = true
        silenceJob?.cancel()
        sleepJob?.cancel()
        assistantGenerationJob?.cancel()
        asr.stop()
        tts.stop()
        val ended = session?.let { repository.endSession(it) }
        session = ended ?: session?.copy(status = VoiceCallStatus.Ended, endedAt = System.currentTimeMillis())
        VoiceCallForegroundService.stop(context.applicationContext)
        stage = CallStage.Idle
        userTurnSubmitting = false
        session = repository.createSession(
            conversationId = conversationId,
            assistantId = assistantId,
            assistantName = assistantName,
            initialLines = emptyList(),
            persistImmediately = false,
        )
    }

    LaunchedEffect(sessionId, conversationId, assistantId) {
        session = sessionId
            ?.let { repository.getSession(it) }
            ?: repository.createSession(
                conversationId = conversationId,
                assistantId = assistantId,
                assistantName = assistantName,
                initialLines = emptyList(),
                persistImmediately = false,
            )
    }

    LaunchedEffect(stage) {
        if (stage == CallStage.Ringing) {
            runCatching {
                incomingRingtone?.play()
            }
        } else {
            runCatching { incomingRingtone?.stop() }
        }
    }

    LaunchedEffect(autoStart, incomingCall, session?.id) {
        if (autoStart && incomingCall && session != null && stage == CallStage.Ringing) {
            ProactiveCallManager.markAnswered(context, assistantId)
            startCall()
        }
    }

    LaunchedEffect(isSpeaking, stage, sleepMode, assistantTurnInProgress, asrState.status) {
        if (shouldStartVoiceCallListening(
                stageActive = stage == CallStage.Active,
                isHistoryOnly = isHistoryOnly,
                sleepMode = sleepMode,
                assistantTurnInProgress = assistantTurnInProgress,
                isSpeaking = isSpeaking,
                asrStatus = asrState.status,
            )
        ) {
            delay(350)
            startListening()
        }
    }

    LaunchedEffect(sleepMode, sleepMinutes, stage) {
        sleepJob?.cancel()
        if (!sleepMode || stage != CallStage.Active) return@LaunchedEffect
        asr.stop()
        val current = session ?: return@LaunchedEffect
        val updated = repository.replaceSession(current.copy(sleepMode = true))
        session = updated
        sleepJob = scope.launch {
            val deadline = System.currentTimeMillis() + sleepMinutes * 60_000
            var index = 0
            while (isActive && System.currentTimeMillis() < deadline) {
                if (stage != CallStage.Active || !sleepMode) return@launch
                val segment = try {
                    chatService.sendVoiceCallTurn(
                        conversationId = Uuid.parse(conversationId),
                        text = buildSleepTalkPrompt(
                            assistantName = assistantName,
                            sequence = index,
                        ),
                        visibleUserText = null,
                    ).takeIf(::isUsableVoiceCallReply)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    null
                }
                if (segment == null) {
                    delay(1_200)
                    continue
                }
                saveAssistantSleepLine(segment)
                segment.cleanRoleLineForUser().extractSpeakableRoleText().takeIf { it.isNotBlank() }?.let {
                    speakInSegments(tts, it, ttsProvider)
                }
                delay(500)
                index++
            }
            if (stage == CallStage.Active && sleepMode) endCall()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { incomingRingtone?.stop() }
            silenceJob?.cancel()
            sleepJob?.cancel()
            if (!isHistoryOnly) asr.stop()
            if (!isHistoryOnly) VoiceCallForegroundService.stop(context.applicationContext)
            if (!isHistoryOnly && latestStage != CallStage.Ended) {
                latestSession?.let {
                    if (it.hasUserFacingContent()) {
                        repository.endSession(it)
                    } else {
                        repository.deleteSession(it.id)
                    }
                }
            }
        }
    }

    if (showMiniWindow && !isHistoryOnly) {
        FloatingWindow(tag = "voice_call_mini", visibility = true) {
            MiniCallWindow(
                assistantName = assistantName,
                stage = stage,
                isSpeaking = isSpeaking || assistantTurnInProgress,
                onOpen = { showMiniWindow = false },
                onEnd = { endCall() },
            )
        }
    }

    val currentSession = session
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isHistoryOnly) "通话记录" else "") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft02, contentDescription = null)
                    }
                },
                actions = {
                    if (!isHistoryOnly) {
                        IconButton(onClick = { showMiniWindow = !showMiniWindow }) {
                            Icon(HugeIcons.Call02, contentDescription = null)
                        }
                    }
                    IconButton(
                        onClick = {
                            navController.navigate(
                                Screen.VoiceCallHistory(
                                    conversationId = conversationId,
                                    assistantId = assistantId,
                                )
                            )
                        }
                    ) {
                        Icon(HugeIcons.TransactionHistory, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFFF8F3),
                    scrolledContainerColor = Color(0xFFF7F0EC),
                    titleContentColor = Color(0xFF303744),
                    navigationIconContentColor = Color(0xFF303744),
                    actionIconContentColor = Color(0xFF303744),
                ),
            )
        },
        containerColor = Color(0xFFFFF8F3),
    ) { padding ->
        if (currentSession == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("准备通话中...")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFF8F3), Color(0xFFF4EFF8), Color(0xFFEAF2F6)),
                    ),
                )
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                UIAvatar(
                    name = assistantName,
                    value = assistant?.avatar ?: Avatar.Dummy,
                    modifier = Modifier.size(96.dp).clip(CircleShape),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = assistantName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF303744),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = statusText(
                            stage = stage,
                            asrStatus = asrState.status,
                            isSpeaking = isSpeaking,
                            assistantTurnInProgress = assistantTurnInProgress,
                            sleepMode = sleepMode,
                            isHistoryOnly = isHistoryOnly,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF625B70),
                    )
                }
            }
            if (stage == CallStage.Ringing) {
                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.76f),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                "语音来电",
                                color = Color(0xFF303744),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            } else if (stage == CallStage.Idle && !isHistoryOnly) {
                IdleCallPanel(
                    assistantName = assistantName,
                    onStartCall = { startCall() },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                CallContentCard(
                    stage = stage,
                    isHistoryOnly = isHistoryOnly,
                    assistantName = assistantName,
                    session = currentSession,
                    onStartCall = { startCall() },
                    onReplay = { line ->
                        line.text.cleanRoleLineForUser().extractSpeakableRoleText().takeIf { it.isNotBlank() }?.let {
                            scope.launch { speakInSegments(tts, it, ttsProvider) }
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }

            if (!isHistoryOnly) {
                if (stage == CallStage.Ringing) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                ProactiveCallManager.markDeclined(context, assistantId)
                                endCall()
                                navController.popBackStack()
                            },
                            modifier = Modifier.weight(1f).height(58.dp),
                        ) {
                            Icon(HugeIcons.Cancel01, contentDescription = null)
                            Text("拒绝")
                        }
                        Button(
                            onClick = {
                                ProactiveCallManager.markAnswered(context, assistantId)
                                startCall()
                            },
                            modifier = Modifier.weight(1f).height(58.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6F91B2),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(HugeIcons.Call02, contentDescription = null)
                            Text("接听")
                        }
                    }
                } else {
                    if (sleepMode) {
                        SleepModePanel(
                            enabled = sleepMode,
                            minutes = sleepMinutes,
                            onEnabledChange = { sleepMode = it },
                            onMinutesChange = { sleepMinutes = it },
                        )
                    } else {
                        FilterChip(
                            selected = false,
                            onClick = { sleepMode = true },
                            label = { Text("哄睡模式") },
                            leadingIcon = {
                                Icon(HugeIcons.Moon02, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val canStartListening = shouldStartVoiceCallListening(
                            stageActive = stage == CallStage.Active,
                            isHistoryOnly = isHistoryOnly,
                            sleepMode = sleepMode,
                            assistantTurnInProgress = assistantTurnInProgress,
                            isSpeaking = isSpeaking,
                            asrStatus = asrState.status,
                        )
                        val canInterruptAssistant =
                            stage in setOf(CallStage.Connecting, CallStage.Active) &&
                                !sleepMode &&
                                (isSpeaking || assistantTurnInProgress)
                        FilledTonalButton(
                            onClick = {
                                if (canInterruptAssistant) {
                                    silenceJob?.cancel()
                                    assistantGenerationJob?.cancel()
                                    tts.stop()
                                    assistantTurnInProgress = false
                                } else if (asrState.status == ASRStatus.Idle || asrState.status == ASRStatus.Error) {
                                    startListening()
                                } else {
                                    asr.stop()
                                }
                            },
                            enabled = asrState.status == ASRStatus.Listening || canStartListening || canInterruptAssistant,
                        ) {
                            Icon(HugeIcons.VolumeHigh, contentDescription = null)
                            Text(
                                when {
                                    canInterruptAssistant -> "打断并说话"
                                    asrState.status == ASRStatus.Listening -> "停止倾听"
                                    else -> "开始倾听"
                                },
                            )
                        }
                        FilledIconButton(
                            onClick = { endCall() },
                            enabled = stage !in setOf(CallStage.Idle, CallStage.Ringing),
                            modifier = Modifier.size(58.dp),
                        ) {
                            Icon(HugeIcons.Cancel01, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}
