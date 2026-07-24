package me.rerere.rikkahub.ui.pages.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
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
    var reactionLine by remember(game.wireName, selectedAssistantId) {
        mutableStateOf("（开始游戏后，角色会根据真实对局和自己的记忆回应你）")
    }
    var isGeneratingReaction by remember { mutableStateOf(false) }
    var reactionRequestId by remember { mutableIntStateOf(0) }

    suspend fun recordSharedGame(
        title: String,
        summary: String,
        detailsJson: String,
    ) {
        val assistantId = selectedAssistant.id.toString()
        val nowMillis = System.currentTimeMillis()
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

    suspend fun generateCompanionText(facts: String, instruction: String): String {
        val fallback = "（角色回应生成失败，但游戏状态和结果仍然有效）"
        return runCatching {
            val settings = settingsStore.settingsFlow.first()
            val player = settings.assistants.firstOrNull { it.id.toString() == selectedAssistantId }
                ?: return@runCatching fallback
            val model = settings.findModelById(player.chatModelId ?: settings.chatModelId)
                ?.takeIf { it.type == ModelType.CHAT }
                ?: return@runCatching fallback
            val providerSetting = model.findProvider(settings.providers) ?: return@runCatching fallback
            val provider = providerManager.getProviderByType(providerSetting)
            val personaPrompt = buildString {
                appendLine("你正在以‘${player.name.ifBlank { "角色" }}’的身份和用户一起玩游戏。")
                appendLine("程序提供的题目真相、掷骰、棋局和分数都是不可修改的事实。")
                appendLine("必须保持角色人设和关系连续性，不要默认活泼、亲密、温柔或吐槽。")
                if (player.systemPrompt.isNotBlank()) {
                    appendLine("角色人设：")
                    appendLine(player.systemPrompt)
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
                add(UIMessage.system("你正在进行角色陪伴游戏。严格遵守玩法指令和不可变事实。"))
                add(UIMessage.system(personaPrompt))
                if (companionContext.isNotBlank()) add(UIMessage.system(companionContext))
                add(UIMessage.system("本次输出要求：$instruction"))
                add(UIMessage.user("游戏事实：\n$facts"))
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
                    temperature = 0.82f,
                    topP = 0.9f,
                    maxTokens = if (game == QuickCompanionGame.ROLEPLAY_ADVENTURE) 520 else 260,
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
            chunk.choices.firstOrNull()?.message?.toText()?.trim()?.takeIf { it.isNotBlank() } ?: fallback
        }.getOrElse {
            if (it is CancellationException) throw it
            fallback
        }
    }

    fun requestCompanionText(
        facts: String,
        instruction: String,
        onResult: (String) -> Unit = {},
    ) {
        reactionRequestId += 1
        val requestId = reactionRequestId
        isGeneratingReaction = true
        reactionLine = "（$assistantName 正在回应你）"
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

    fun completeRuleGame(
        title: String,
        summary: String,
        detailsJson: String,
    ) {
        saveCheckpoint(title, summary, detailsJson)
        requestCompanionText(
            facts = summary,
            instruction = "只根据真实对局结果，以角色自己的语气当面对用户说 1-3 句。不得修改结果、虚构动作或使用系统播报口吻。",
        )
    }

    Scaffold(
        containerColor = GameColors.background,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("选择一起玩的角色", fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(currentSettings.assistants, key = { it.id }) { assistant ->
                            FilterChip(
                                selected = assistant.id.toString() == selectedAssistantId,
                                onClick = { selectedAssistantId = assistant.id.toString() },
                                label = { Text(assistant.name.ifBlank { "未命名角色" }) },
                            )
                        }
                    }
                    Text(
                        when (game) {
                            QuickCompanionGame.TURTLE_SOUP -> "这是海龟汤独立页面：题面和汤底由程序锁定，角色只负责主持与回应。"
                            QuickCompanionGame.RAPPORT_QUIZ -> "这是默契问答独立页面：角色会读取人设、记忆和对你的印象后独立作答。"
                            QuickCompanionGame.ROLEPLAY_ADVENTURE -> "这是跑团独立页面：程序负责 d20 判定和状态，角色负责同伴演出与剧情主持。"
                            else -> "游戏引擎负责真实规则和结果，角色模型根据这些事实按人设回应。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            CompanionGameReactionCard(
                assistantName = assistantName,
                line = reactionLine,
                isGenerating = isGeneratingReaction,
            )

            when (game) {
                QuickCompanionGame.ROCK_PAPER_SCISSORS -> RockPaperScissorsGame(
                    assistantName = assistantName,
                    onCompleted = { userMove, roleMove, outcome ->
                        val summary = "用户选择$userMove，角色选择$roleMove，结果：$outcome。"
                        completeRuleGame(
                            title = "一起玩完一轮猜拳",
                            summary = summary,
                            detailsJson = buildJsonObject {
                                put("game", game.wireName)
                                put("user_move", userMove)
                                put("role_move", roleMove)
                                put("outcome", outcome)
                            }.toString(),
                        )
                    },
                )

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

                QuickCompanionGame.ROLEPLAY_ADVENTURE -> RoleplayAdventureGame(
                    assistantName = assistantName,
                    request = { facts, instruction, onResult -> requestCompanionText(facts, instruction, onResult) },
                    checkpoint = { title, summary, details -> saveCheckpoint(title, summary, details) },
                )
            }
        }
    }
}

@Composable
private fun CompanionGameReactionCard(
    assistantName: String,
    line: String,
    isGenerating: Boolean,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("$assistantName 的游戏回应", style = MaterialTheme.typography.labelLarge, color = GameColors.accent)
            Text(line, color = if (isGenerating) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun RockPaperScissorsGame(
    assistantName: String,
    onCompleted: (String, String, String) -> Unit,
) {
    val moves = listOf("石头", "剪刀", "布")
    var roleMove by remember { mutableStateOf<String?>(null) }
    var userMove by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf<String?>(null) }

    fun play(move: String) {
        val role = moves.random()
        val result = compareRockPaperScissors(move, role)
        userMove = move
        roleMove = role
        outcome = result
        onCompleted(move, role, result)
    }

    GameBody(title = "猜拳", subtitle = "你先出手，$assistantName 同一轮出手。每轮结果都会让角色通过 API 真正回应。") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            moves.forEach { move ->
                OutlinedButton(onClick = { play(move) }, modifier = Modifier.weight(1f)) {
                    Text(move)
                }
            }
        }
        if (outcome != null) {
            GameResultText("你出$userMove · $assistantName 出$roleMove · $outcome")
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
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

internal fun compareRockPaperScissors(userMove: String, roleMove: String): String = when {
    userMove == roleMove -> "平局"
    (userMove == "石头" && roleMove == "剪刀") ||
        (userMove == "剪刀" && roleMove == "布") ||
        (userMove == "布" && roleMove == "石头") -> "用户胜"
    else -> "角色胜"
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
    listOf(0, 1, 2),
    listOf(3, 4, 5),
    listOf(6, 7, 8),
    listOf(0, 3, 6),
    listOf(1, 4, 7),
    listOf(2, 5, 8),
    listOf(0, 4, 8),
    listOf(2, 4, 6),
)

internal enum class QuickCompanionGame(
    val wireName: String,
    val title: String,
    val shortTitle: String,
) {
    ROCK_PAPER_SCISSORS("rock_paper_scissors", "一起玩：猜拳", "猜拳"),
    DICE_DUEL("dice_duel", "一起玩：快艇骰子", "快艇骰子"),
    TIC_TAC_TOE("tic_tac_toe", "一起玩：五子棋", "五子棋"),
    TURTLE_SOUP("turtle_soup", "一起玩：海龟汤", "海龟汤"),
    RAPPORT_QUIZ("rapport_quiz", "一起玩：默契问答", "默契问答"),
    ROLEPLAY_ADVENTURE("roleplay_adventure", "一起玩：轻量跑团", "跑团");

    companion object {
        fun fromWireName(value: String): QuickCompanionGame = when (value) {
            "yacht_dice" -> DICE_DUEL
            "gomoku" -> TIC_TAC_TOE
            "turtle_soup" -> TURTLE_SOUP
            "rapport_quiz" -> RAPPORT_QUIZ
            "roleplay_adventure", "trpg" -> ROLEPLAY_ADVENTURE
            else -> entries.firstOrNull { it.wireName == value } ?: ROCK_PAPER_SCISSORS
        }
    }
}
