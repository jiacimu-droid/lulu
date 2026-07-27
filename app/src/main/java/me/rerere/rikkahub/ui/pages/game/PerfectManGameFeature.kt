package me.rerere.rikkahub.ui.pages.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.asr.ASRStatus
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft02
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.rikkahub.data.ai.ApiUsageSource
import me.rerere.rikkahub.data.ai.ApiUsageStore
import me.rerere.rikkahub.data.ai.transformers.transformMessages
import me.rerere.rikkahub.data.companion.CompanionLifeEvent
import me.rerere.rikkahub.data.companion.CompanionLifeEventSource
import me.rerere.rikkahub.data.companion.CompanionLifeEventStatus
import me.rerere.rikkahub.data.companion.CompanionLifeEventType
import me.rerere.rikkahub.data.companion.CompanionPerceptionInput
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.companion.CompanionTurnMutation
import me.rerere.rikkahub.data.companion.toPromptContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import kotlin.math.abs
import kotlin.random.Random
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PerfectManGameFeaturePage() {
    val navController = LocalNavController.current
    val asr = LocalASRState.current
    val tts = LocalTTSState.current
    val settings = LocalSettings.current
    val settingsStore = koinInject<SettingsStore>()
    val providerManager = koinInject<ProviderManager>()
    val apiUsageStore = koinInject<ApiUsageStore>()
    val companionRuntime = koinInject<CompanionRuntime>()
    val scope = rememberCoroutineScope()
    val asrState by asr.state.collectAsState()
    var round by remember { mutableIntStateOf(1) }
    var targetScore by remember { mutableIntStateOf(Random.nextInt(0, 11)) }
    var phase by remember { mutableStateOf(PerfectManRoundPhase.UserGuesses) }
    var generatedPrompt by remember { mutableStateOf("") }
    var userDescription by remember { mutableStateOf("") }
    var guessText by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PerfectManRoundResult?>(null) }
    var opponentLine by remember { mutableStateOf(PERFECT_MAN_WAITING_MARKER) }
    var isGenerating by remember { mutableStateOf(false) }
    var opponentVoiceEnabled by remember { mutableStateOf(true) }
    var listeningTarget by remember { mutableStateOf<PerfectManVoiceInputTarget?>(null) }
    var selectedPlayerAssistantId by remember { mutableStateOf<String?>(settings.assistantId.toString()) }
    val selectedPlayer = settings.assistants.firstOrNull { it.id.toString() == selectedPlayerAssistantId }

    val isListening = asrState.status != ASRStatus.Idle && asrState.status != ASRStatus.Error

    fun speak(text: String) {
        if (
            opponentVoiceEnabled &&
            text.isNotBlank() &&
            !text.startsWith("（")
        ) {
            tts.speak(text)
        }
    }

    fun stopVoiceInput() {
        asr.stop()
        listeningTarget = null
    }

    fun startVoiceInput(target: PerfectManVoiceInputTarget) {
        if (isListening) {
            stopVoiceInput()
            return
        }
        listeningTarget = target
        asr.start { transcript ->
            val clean = transcript.trim()
            if (clean.isBlank()) return@start
            when (target) {
                PerfectManVoiceInputTarget.Flaw -> userDescription = clean
                PerfectManVoiceInputTarget.Guess -> guessText = clean.filter { it.isDigit() || it == '.' }
            }
        }
    }

    fun nextRound() {
        tts.stop()
        stopVoiceInput()
        val nextPhase = if (phase == PerfectManRoundPhase.UserGuesses) {
            PerfectManRoundPhase.PartnerGuesses
        } else {
            PerfectManRoundPhase.UserGuesses
        }
        round += 1
        targetScore = Random.nextInt(0, 11)
        phase = nextPhase
        generatedPrompt = ""
        userDescription = ""
        guessText = ""
        result = null
        opponentLine = PERFECT_MAN_WAITING_MARKER
    }

    suspend fun generatePerfectManText(prompt: String, fallback: String): String {
        return runCatching {
            val current = settingsStore.settingsFlow.first()
            val player = selectedPlayerAssistantId
                ?.let { id -> current.assistants.firstOrNull { it.id.toString() == id } }
                ?: return@runCatching fallback
            val model = current.findModelById(player.chatModelId ?: current.chatModelId)
                ?.takeIf { it.type == ModelType.CHAT }
                ?: return@runCatching fallback
            val providerSetting = model.findProvider(current.providers) ?: return@runCatching fallback
            val provider = providerManager.getProviderByType(providerSetting)
            val playerPrompt = buildString {
                appendLine("你现在扮演坐在用户对面一起玩游戏的“${player.name.ifBlank { "玩家" }}”。")
                appendLine("该角色的核心人设、关系类型、世界观、语言习惯与边界是最高约束。游戏场景只能提供事实和轮次目标，不能把角色改写成默认友好、爱吐槽、活泼或亲密的玩家。只输出这个角色当面真正会说出口的话。")
                if (player.systemPrompt.isNotBlank()) {
                    appendLine("角色人设：")
                    appendLine(player.systemPrompt)
                }
                if (player.appearancePrompt.isNotBlank()) {
                    appendLine("角色外貌：")
                    appendLine(player.appearancePrompt)
                }
            }.trim()
            val companionContext = companionRuntime.perception(
                CompanionPerceptionInput(
                    assistantId = player.id.toString(),
                    assistantName = player.name,
                    persona = player.systemPrompt,
                    nowMillis = System.currentTimeMillis(),
                ),
            ).toPromptContext()
            val messages = buildList {
                add(
                    UIMessage.system(
                        "你正在参与“满分男”游戏，不是主持人、裁判或旁白。" +
                            "所选角色的人设与关系边界拥有最高优先级；不得默认友善、吐槽、犹豫、活泼或亲密。" +
                            "只输出该角色会当面说出口的话，不播报后台规则。内容简短、可供猜测，不要色情，不要羞辱真实群体。",
                    ),
                )
                if (playerPrompt.isNotBlank()) add(UIMessage.system(playerPrompt))
                if (companionContext.isNotBlank()) add(UIMessage.system(companionContext))
                add(UIMessage.user(prompt))
            }.let { baseMessages ->
                transformMessages(
                    messages = baseMessages,
                    assistant = player,
                    modeInjections = current.modeInjections,
                    lorebooks = current.lorebooks,
                )
            }
            val chunk = provider.generateText(
                providerSetting = providerSetting,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.8f,
                    topP = 0.9f,
                    maxTokens = 500,
                    reasoningLevel = ReasoningLevel.OFF,
                ),
            )
            chunk.usage?.let { usage ->
                apiUsageStore.record(
                    source = ApiUsageSource.GAME,
                    title = "满分男：${player.name.ifBlank { "当前角色" }}",
                    model = model.displayName.ifBlank { model.modelId },
                    provider = providerSetting.name.ifBlank { providerSetting.id.toString() },
                    usage = usage,
                )
            }
            chunk.choices.firstOrNull()?.message?.toText()?.trim()?.takeIf { it.isNotBlank() } ?: fallback
        }.getOrElse {
            if (it is CancellationException) throw it
            fallback
        }
    }

    suspend fun recordCompletedRound(
        playerAssistantId: String?,
        completedRound: Int,
        completedPhase: PerfectManRoundPhase,
        roundResult: PerfectManRoundResult,
    ) {
        val assistantId = playerAssistantId?.takeIf(String::isNotBlank) ?: return
        val nowMillis = System.currentTimeMillis()
        val phaseLabel = if (completedPhase == PerfectManRoundPhase.UserGuesses) "角色出题" else "角色猜分"
        runCatching {
            companionRuntime.applyTurn(
                CompanionTurnMutation(
                    assistantId = assistantId,
                    lifeEvents = listOf(
                        CompanionLifeEvent(
                            id = "perfect-man:$assistantId:$completedRound:$nowMillis",
                            assistantId = assistantId,
                            type = CompanionLifeEventType.GAME,
                            status = CompanionLifeEventStatus.COMPLETED,
                            title = "一起玩完了一轮满分男",
                            summary = "$phaseLabel，第 $completedRound 轮相差 ${roundResult.diff} 分。",
                            source = CompanionLifeEventSource.CHAT,
                            evidenceReference = "perfect-man-round:$completedRound:$nowMillis",
                            importance = 3,
                            startedAt = nowMillis,
                            endedAt = nowMillis,
                            createdAt = nowMillis,
                        ),
                    ),
                    nowMillis = nowMillis,
                ),
            )
        }
    }

    fun startUserGuessRound() {
        if (isGenerating) return
        isGenerating = true
        opponentLine = PERFECT_MAN_GENERATING_MARKER
        scope.launch {
            val generated = generatePerfectManText(
                prompt = "你现在负责出题。隐藏分数是 $targetScore/10，但绝对不能暴露分数。" +
                    "以当前角色自己的方式描述这个人，让用户猜他实际几分；不要求固定开头、吐槽或友好语气。" +
                    "只输出角色台词，2-4 句。",
                fallback = PERFECT_MAN_REPLY_FAILURE_MARKER,
            )
            generatedPrompt = generated.takeUnless { it == PERFECT_MAN_REPLY_FAILURE_MARKER }.orEmpty()
            opponentLine = generated
            speak(generated)
            isGenerating = false
        }
    }

    fun submitPartnerGuessRound() {
        val description = userDescription.trim()
        if (description.isBlank() || isGenerating) return
        val playerAssistantId = selectedPlayer?.id?.toString()
        val completedRound = round
        val completedPhase = phase
        isGenerating = true
        opponentLine = PERFECT_MAN_GENERATING_MARKER
        scope.launch {
            val reply = generatePerfectManText(
                prompt = "你现在坐在用户对面猜分。真实分数是 $targetScore/10，只用来校准你的猜测。" +
                    "用户给你的描述是：$description\n" +
                    "请以当前角色自己的判断方式回应并给出猜分，不得强制吐槽、犹豫、友好或亲密。" +
                    "最后必须明确写出“我猜：X分”，X 是 0-10 的整数。" +
                    "只输出角色台词，不要说后台校准分。",
                fallback = PERFECT_MAN_REPLY_FAILURE_MARKER,
            )
            if (reply == PERFECT_MAN_REPLY_FAILURE_MARKER) {
                generatedPrompt = ""
                opponentLine = reply
                isGenerating = false
                return@launch
            }
            val guess = Regex("""(\d{1,2})\s*分""").find(reply)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?.coerceIn(0, 10)
            if (guess == null) {
                generatedPrompt = ""
                opponentLine = PERFECT_MAN_REPLY_FORMAT_FAILURE_MARKER
                isGenerating = false
                return@launch
            }
            generatedPrompt = reply
            opponentLine = reply
            val nextResult = PerfectManRoundResult(
                guess = guess,
                score = targetScore,
                success = abs(guess - targetScore) <= 1,
                diff = abs(guess - targetScore),
            )
            result = nextResult
            speak(reply)
            recordCompletedRound(playerAssistantId, completedRound, completedPhase, nextResult)
            isGenerating = false
        }
    }

    fun submitGuess() {
        val guess = guessText.trim().toFloatOrNull()?.toInt()?.coerceIn(0, 10) ?: return
        if (isGenerating || generatedPrompt.isBlank()) return
        val playerAssistantId = selectedPlayer?.id?.toString()
        val completedRound = round
        val completedPhase = phase
        val diff = abs(guess - targetScore)
        isGenerating = true
        opponentLine = PERFECT_MAN_GENERATING_MARKER
        scope.launch {
            val nextResult = PerfectManRoundResult(guess = guess, score = targetScore, success = diff <= 1, diff = diff)
            val reply = generatePerfectManText(
                prompt = "你刚才给用户描述的是：$generatedPrompt\n" +
                    "用户猜这个男的是 $guess 分，真实分数是 $targetScore/10，差值 $diff 分。" +
                    "以当前角色自己的方式回应用户，并明确说出真实分数和差值。" +
                    "是否肯定、讽刺、克制或直接只由角色人设和当前关系决定，不得默认夸奖或轻松吐槽。" +
                    "只输出角色会说出口的话，不要写标题，不要用系统播报口吻。",
                fallback = PERFECT_MAN_REPLY_FAILURE_MARKER,
            )
            result = nextResult
            opponentLine = reply
            speak(reply)
            recordCompletedRound(playerAssistantId, completedRound, completedPhase, nextResult)
            isGenerating = false
        }
    }

    Scaffold(
        containerColor = Color(0xFFF8F4F0),
        topBar = {
            TopAppBar(
                title = { Text("满分男") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft02, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = ::nextRound) {
                        Icon(HugeIcons.Refresh03, contentDescription = "下一轮")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PerfectManRoundHeader(round = round, phase = phase)
            PerfectManPlayerSelector(
                selectedPlayer = selectedPlayer,
                assistants = settings.assistants,
                onSelect = { selectedPlayerAssistantId = it },
            )
            PerfectManOpponentSeatCard(
                line = opponentLine,
                speakingEnabled = opponentVoiceEnabled,
                onSpeak = { speak(opponentLine) },
            )
            PerfectManVoiceSettingsCard(
                opponentVoiceEnabled = opponentVoiceEnabled,
                onOpponentVoiceEnabledChange = {
                    opponentVoiceEnabled = it
                    if (!it) tts.stop()
                },
            )
            PerfectManActionCard(
                phase = phase,
                score = targetScore,
                promptReady = generatedPrompt.isNotBlank(),
                result = result,
                description = userDescription,
                onDescriptionChange = { userDescription = it },
                onExample = { userDescription = PerfectManExampleFlaws.random() },
                guessText = guessText,
                onGuessTextChange = { guessText = it.filter { char -> char.isDigit() || char == '.' }.take(2) },
                listeningTarget = listeningTarget,
                isListening = isListening,
                onVoiceDescription = { startVoiceInput(PerfectManVoiceInputTarget.Flaw) },
                onVoiceGuess = { startVoiceInput(PerfectManVoiceInputTarget.Guess) },
                isGenerating = isGenerating,
                onStartPrompt = ::startUserGuessRound,
                onSubmitDescription = ::submitPartnerGuessRound,
                onSubmitGuess = ::submitGuess,
                onNextRound = ::nextRound,
            )
        }
    }
}
