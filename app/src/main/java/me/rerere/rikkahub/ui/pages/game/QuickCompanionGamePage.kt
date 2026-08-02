package me.rerere.rikkahub.ui.pages.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft02
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
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCompanionGamePage(gameId: String) {
    val game = remember(gameId) { QuickCompanionGame.fromWireName(gameId) }
    val navController = LocalNavController.current
    val currentSettings = LocalSettings.current
    val settingsStore = koinInject<SettingsStore>()
    val providerManager = koinInject<ProviderManager>()
    val apiUsageStore = koinInject<ApiUsageStore>()
    val companionRuntime = koinInject<CompanionRuntime>()
    val scope = rememberCoroutineScope()
    var selectedAssistantId by remember { mutableStateOf(currentSettings.assistantId.toString()) }
    val selectedAssistant = currentSettings.assistants.firstOrNull { it.id.toString() == selectedAssistantId }
        ?: currentSettings.getCurrentAssistant()
    val assistantName = selectedAssistant.name.ifBlank { "角色" }
    var reactionLine by remember(game.wireName, selectedAssistantId) { mutableStateOf("") }
    var isGeneratingReaction by remember { mutableStateOf(false) }
    var reactionRequestId by remember { mutableIntStateOf(0) }

    suspend fun recordSharedGame(title: String, summary: String, detailsJson: String) {
        val assistantIds = if (game == QuickCompanionGame.ROLEPLAY_ADVENTURE) {
            currentSettings.assistants.map { it.id.toString() }
        } else {
            listOf(selectedAssistant.id.toString())
        }
        val nowMillis = System.currentTimeMillis()
        assistantIds.forEach { assistantId ->
            companionRuntime.applyTurn(
                CompanionTurnMutation(
                    assistantId = assistantId,
                    lifeEvents = listOf(
                        CompanionLifeEvent(
                            id = "shared-game:${game.wireName}:$assistantId:$nowMillis",
                            assistantId = assistantId,
                            type = CompanionLifeEventType.GAME,
                            status = CompanionLifeEventStatus.COMPLETED,
                            title = title,
                            summary = summary,
                            source = CompanionLifeEventSource.CHAT,
                            evidenceReference = "shared-game:${game.wireName}:$nowMillis",
                            detailsJson = detailsJson,
                            importance = if (game == QuickCompanionGame.ROLEPLAY_ADVENTURE) 4 else 3,
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

    suspend fun generateCompanionText(facts: String, instruction: String): String {
        val fallback = ""
        return runCatching {
            val settings = settingsStore.settingsFlow.first()
            val player = settings.assistants.firstOrNull { it.id.toString() == selectedAssistantId }
                ?: return@runCatching fallback
            val model = settings.findModelById(player.chatModelId ?: settings.chatModelId)
                ?.takeIf { it.type == ModelType.CHAT }
                ?: return@runCatching fallback
            val providerSetting = model.findProvider(settings.providers) ?: return@runCatching fallback
            val provider = providerManager.getProviderByType(providerSetting)
            val companionContext = companionRuntime.perception(
                CompanionPerceptionInput(
                    assistantId = player.id.toString(),
                    assistantName = player.name,
                    persona = player.systemPrompt,
                    nowMillis = System.currentTimeMillis(),
                ),
            ).toPromptContext()
            val messages = buildList {
                add(UIMessage.system("你正在以‘${player.name.ifBlank { "角色" }}’的身份和用户一起玩游戏。保持人设和关系连续性，严格接受程序给出的真实结果。"))
                if (player.systemPrompt.isNotBlank()) add(UIMessage.system(player.systemPrompt))
                if (companionContext.isNotBlank()) add(UIMessage.system(companionContext))
                add(UIMessage.system(instruction))
                add(UIMessage.user(facts))
            }.let { baseMessages ->
                transformMessages(
                    messages = baseMessages,
                    assistant = player,
                    modeInjections = settings.modeInjections,
                    lorebooks = settings.lorebooks,
                )
            }
            val chunk = provider.generateText(
                providerSetting = providerSetting,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    temperature = if (game == QuickCompanionGame.ROLEPLAY_ADVENTURE) 0.94f else 0.82f,
                    topP = if (game == QuickCompanionGame.ROLEPLAY_ADVENTURE) 0.96f else 0.9f,
                    maxTokens = if (game == QuickCompanionGame.ROLEPLAY_ADVENTURE) 900 else 260,
                    reasoningLevel = ReasoningLevel.OFF,
                ),
            )
            chunk.usage?.let { usage ->
                apiUsageStore.record(
                    source = ApiUsageSource.GAME,
                    title = "${game.title}：${player.name.ifBlank { "当前角色" }}",
                    model = model.displayName.ifBlank { model.modelId },
                    provider = providerSetting.name.ifBlank { providerSetting.id.toString() },
                    usage = usage,
                )
            }
            chunk.choices.firstOrNull()?.message?.toText()?.trim().orEmpty()
        }.getOrElse {
            if (it is CancellationException) throw it
            fallback
        }
    }

    fun requestCompanionText(facts: String, instruction: String, onResult: (String) -> Unit = {}) {
        reactionRequestId += 1
        val requestId = reactionRequestId
        isGeneratingReaction = true
        reactionLine = ""
        scope.launch {
            val generated = generateCompanionText(facts, instruction)
            if (requestId == reactionRequestId) {
                reactionLine = generated
                isGeneratingReaction = false
                onResult(generated)
            }
        }
    }

    fun saveCheckpoint(title: String, summary: String, detailsJson: String) {
        scope.launch { runCatching { recordSharedGame(title, summary, detailsJson) } }
    }

    fun completeRuleGame(title: String, summary: String, detailsJson: String) {
        saveCheckpoint(title, summary, detailsJson)
        requestCompanionText(
            facts = summary,
            instruction = "只根据真实结果，以角色自己的语气对用户说 1-3 句，不得修改结果。",
        )
    }

    Scaffold(
        containerColor = if (game == QuickCompanionGame.ROLEPLAY_ADVENTURE) androidx.compose.ui.graphics.Color(0xFF151311) else GameColors.background,
        topBar = {
            TopAppBar(
                title = { Text(game.title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft02, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (game == QuickCompanionGame.ROLEPLAY_ADVENTURE) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                FormalRoleplayCampaignGame(
                    request = { facts, instruction, onResult -> requestCompanionText(facts, instruction, onResult) },
                    checkpoint = { title, summary, details -> saveCheckpoint(title, summary, details) },
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(currentSettings.assistants, key = { it.id }) { assistant ->
                                FilterChip(
                                    selected = assistant.id.toString() == selectedAssistantId,
                                    onClick = { selectedAssistantId = assistant.id.toString() },
                                    label = { Text(assistant.name.ifBlank { "未命名角色" }) },
                                )
                            }
                        }
                    }
                }

                if (reactionLine.isNotBlank() || isGeneratingReaction) {
                    CompanionGameReactionCard(
                        assistantName = assistantName,
                        line = reactionLine,
                        isGenerating = isGeneratingReaction,
                    )
                }

                when (game) {
                    QuickCompanionGame.DICE_DUEL -> YachtDiceGame(
                        assistantName = assistantName,
                        onRoundCompleted = { round, userCategory, roleCategory, userScore, roleScore, outcome, userTotal, roleTotal ->
                            val summary = "快艇骰子第 $round 轮：用户将${userCategory}记为$userScore 分，角色将${roleCategory}记为$roleScore 分；$outcome。当前总分用户$userTotal，角色$roleTotal。"
                            completeRuleGame(
                                title = "一起完成一轮快艇骰子",
                                summary = summary,
                                detailsJson = buildJsonObject {
                                    put("game", "yacht_dice")
                                    put("round", round)
                                    put("user_category", userCategory)
                                    put("role_category", roleCategory)
                                    put("user_score", userScore)
                                    put("role_score", roleScore)
                                    put("user_total", userTotal)
                                    put("role_total", roleTotal)
                                    put("outcome", outcome)
                                }.toString(),
                            )
                        },
                    )
                    QuickCompanionGame.TIC_TAC_TOE -> GomokuGame(
                        assistantName = assistantName,
                        onCompleted = { outcome, moves, board ->
                            val summary = "用户执黑、角色执白完成一局 15×15 五子棋，共走 $moves 手，结果：$outcome。"
                            completeRuleGame(
                                title = "一起玩完一局五子棋",
                                summary = summary,
                                detailsJson = buildJsonObject {
                                    put("game", "gomoku")
                                    put("outcome", outcome)
                                    put("moves", moves)
                                    put("board", board.joinToString(","))
                                }.toString(),
                            )
                        },
                    )
                    QuickCompanionGame.TURTLE_SOUP -> TurtleSoupGame(
                        assistantName = assistantName,
                        request = { facts, instruction, onResult -> requestCompanionText(facts, instruction, onResult) },
                        checkpoint = { title, summary, details -> saveCheckpoint(title, summary, details) },
                    )
                    QuickCompanionGame.RAPPORT_QUIZ -> RapportQuizGame(
                        assistantName = assistantName,
                        request = { facts, instruction, onResult -> requestCompanionText(facts, instruction, onResult) },
                        checkpoint = { title, summary, details -> saveCheckpoint(title, summary, details) },
                    )
                    QuickCompanionGame.ROLEPLAY_ADVENTURE -> Unit
                }
            }
        }
    }
}

@Composable
private fun CompanionGameReactionCard(assistantName: String, line: String, isGenerating: Boolean) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("$assistantName 的回应", style = MaterialTheme.typography.labelLarge, color = GameColors.accent)
            if (isGenerating) Text("……", color = MaterialTheme.colorScheme.onSurfaceVariant) else Text(line)
        }
    }
}

@Composable
internal fun GameBody(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
internal fun GameResultText(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Deprecated("井字棋已经替换为五子棋，仅保留给旧测试和历史数据兼容")
internal fun chooseTicTacToeMove(board: List<String?>, roleMark: String): Int {
    val userMark = if (roleMark == "X") "O" else "X"
    val open = board.indices.filter { board[it] == null }
    require(open.isNotEmpty()) { "No open tic-tac-toe cells" }
    return open.firstOrNull { cell ->
        board.toMutableList().also { it[cell] = roleMark }.let(::quickTicTacToeWinner) == roleMark
    } ?: open.firstOrNull { cell ->
        board.toMutableList().also { it[cell] = userMark }.let(::quickTicTacToeWinner) == userMark
    } ?: 4.takeIf { it in open }
    ?: listOf(0, 2, 6, 8).firstOrNull { it in open }
    ?: open.first()
}

@Deprecated("井字棋已经替换为五子棋，仅保留给旧测试和历史数据兼容")
internal fun quickTicTacToeWinner(board: List<String?>): String? =
    QUICK_TIC_TAC_TOE_LINES.firstNotNullOfOrNull { line ->
        board[line[0]]?.takeIf { mark -> line.all { board[it] == mark } }
    }

private val QUICK_TIC_TAC_TOE_LINES = listOf(
    listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
    listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
    listOf(0, 4, 8), listOf(2, 4, 6),
)

internal enum class QuickCompanionGame(
    val wireName: String,
    val title: String,
    val shortTitle: String,
) {
    DICE_DUEL("dice_duel", "快艇骰子", "快艇骰子"),
    TIC_TAC_TOE("tic_tac_toe", "五子棋", "五子棋"),
    TURTLE_SOUP("turtle_soup", "海龟汤", "海龟汤"),
    RAPPORT_QUIZ("rapport_quiz", "默契问答", "默契问答"),
    ROLEPLAY_ADVENTURE("roleplay_adventure", "跑团", "跑团");

    companion object {
        fun fromWireName(value: String): QuickCompanionGame = when (value) {
            "yacht_dice" -> DICE_DUEL
            "gomoku" -> TIC_TAC_TOE
            "turtle_soup" -> TURTLE_SOUP
            "rapport_quiz" -> RAPPORT_QUIZ
            "roleplay_adventure", "trpg" -> ROLEPLAY_ADVENTURE
            else -> TIC_TAC_TOE
        }
    }
}
