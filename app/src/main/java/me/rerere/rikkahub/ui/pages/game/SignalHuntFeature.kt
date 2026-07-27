package me.rerere.rikkahub.ui.pages.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft02
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.rikkahub.data.companion.CompanionLifeEvent
import me.rerere.rikkahub.data.companion.CompanionStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignalHuntFeaturePage(recordId: String? = null) {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val companionStore = koinInject<CompanionStore>()
    val companionState by companionStore.state.collectAsState()
    val replayEvent = recordId?.let { id ->
        companionState.snapshots.asSequence()
            .flatMap { snapshot -> snapshot.lifeEvents.asSequence() }
            .firstOrNull { event -> event.id == id }
    }
    val replay = remember(replayEvent?.id, replayEvent?.detailsJson) {
        replayEvent?.let { event ->
            parseSignalHuntFeatureReplay(
                event = event,
                assistantName = settings.assistants.firstOrNull { it.id.toString() == event.assistantId }
                    ?.name
                    ?.ifBlank { "某个角色" }
                    ?: "某个角色",
            )
        }
    }
    val isWatching = replay != null
    var selectedAssistantId by remember { mutableStateOf(settings.assistantId.toString()) }
    val selectedAssistant = settings.assistants.firstOrNull { it.id.toString() == selectedAssistantId }
        ?: settings.getCurrentAssistant()
    var signalCells by remember { mutableStateOf(emptySet<Int>()) }
    var moves by remember { mutableStateOf(emptyList<SignalHuntFeatureMove>()) }
    var started by remember { mutableStateOf(false) }
    var replayStep by remember(replay?.sessionId) { mutableIntStateOf(replay?.moves?.size ?: 0) }
    val visibleReplayMoves = replay?.moves?.take(replayStep).orEmpty()
    val activeMoves = if (isWatching) visibleReplayMoves else moves
    val gameOver = !isWatching && moves.size >= SIGNAL_HUNT_FEATURE_MAX_MOVES

    fun startGame() {
        signalCells = (0..8).shuffled().take(SIGNAL_HUNT_FEATURE_SIGNAL_COUNT).toSet()
        moves = emptyList()
        started = true
    }

    LaunchedEffect(replay?.sessionId) {
        if (isWatching && replay != null) {
            replayStep = 0
            while (replayStep < replay.moves.size) {
                delay(700)
                replayStep += 1
            }
        }
    }

    Scaffold(
        containerColor = SignalHuntFeatureColors.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isWatching) "观看信号追踪" else "一起玩：信号追踪") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft02, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isWatching && replay != null) {
                SignalHuntFeatureReplaySummary(replay = replay, visibleMoves = visibleReplayMoves)
            } else {
                Text("选择陪你玩的角色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(settings.assistants, key = { it.id }) { assistant ->
                        FilterChip(
                            selected = assistant.id.toString() == selectedAssistantId,
                            onClick = { selectedAssistantId = assistant.id.toString() },
                            label = { Text(assistant.name.ifBlank { "未命名角色" }) },
                        )
                    }
                }
                Text(
                    "本局由 ${selectedAssistant.name.ifBlank { "当前角色" }} 陪你一起找信号。每局最多探测 5 格，找到 3 个信号就完成。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SignalHuntFeatureBoard(
                moves = activeMoves,
                signalCells = if (isWatching && replayStep >= (replay?.moves?.size ?: 0)) {
                    replay?.moves.orEmpty().filter { it.foundSignal }.map { it.cell }.toSet()
                } else emptySet(),
                enabled = !isWatching && started && !gameOver,
                onCellClick = { cell ->
                    if (cell !in moves.map { it.cell }) {
                        val found = cell in signalCells
                        val streak = if (found && moves.lastOrNull()?.foundSignal == true) 2 else 1
                        moves = moves + SignalHuntFeatureMove(cell, found, if (found) 20 + (streak - 1) * 5 else 0)
                    }
                },
            )
            if (isWatching && replay != null) {
                Text("${replay.assistantName}的路线已播放完毕。你也可以返回上一页，选择任意角色开启新的一局。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val found = moves.count { it.foundSignal }
                Text("本局进度：找到 $found/$SIGNAL_HUNT_FEATURE_SIGNAL_COUNT 个信号 · 已探测 ${moves.size}/$SIGNAL_HUNT_FEATURE_MAX_MOVES 格")
                Button(onClick = { startGame() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (moves.isEmpty()) HugeIcons.Play else HugeIcons.Refresh03, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (moves.isEmpty()) "开始这一局" else if (gameOver) "再来一局" else "重置本局")
                }
                if (gameOver) {
                    Text(
                        "这一局结束啦。${selectedAssistant.name.ifBlank { "角色" }}会陪你记住这条路线。",
                        color = SignalHuntFeatureColors.success,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalHuntFeatureReplaySummary(
    replay: SignalHuntFeatureReplay,
    visibleMoves: List<SignalHuntFeatureMove>,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${replay.assistantName}的信号追踪记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("得分 ${replay.score}/${replay.maxScore} · 找到 ${visibleMoves.count { it.foundSignal }}/$SIGNAL_HUNT_FEATURE_SIGNAL_COUNT 个信号")
            Text(
                if (visibleMoves.size < replay.moves.size) "正在按角色当时的顺序播放探测路线…" else replay.resultText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SignalHuntFeatureBoard(
    moves: List<SignalHuntFeatureMove>,
    signalCells: Set<Int>,
    enabled: Boolean,
    onCellClick: (Int) -> Unit,
) {
    val moveByCell = moves.associateBy { it.cell }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { column ->
                    val cell = row * 3 + column
                    val move = moveByCell[cell]
                    val revealedSignal = cell in signalCells || move?.foundSignal == true
                    Surface(
                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).then(
                            if (enabled && move == null) Modifier.clickable { onCellClick(cell) } else Modifier,
                        ),
                        color = when {
                            move?.foundSignal == true || cell in signalCells -> Color(0xFFD9F1E5)
                            move != null -> Color(0xFFE8EAF0)
                            else -> Color.White
                        },
                        tonalElevation = 2.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                when {
                                    revealedSignal -> "✦\n信号"
                                    move != null -> "已探测"
                                    enabled -> "?"
                                    else -> "·"
                                },
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = FontWeight.SemiBold,
                                color = if (revealedSignal) SignalHuntFeatureColors.success else SignalHuntFeatureColors.soft,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class SignalHuntFeatureMove(
    val cell: Int,
    val foundSignal: Boolean,
    val points: Int,
)

private data class SignalHuntFeatureReplay(
    val sessionId: String,
    val assistantName: String,
    val score: Int,
    val maxScore: Int,
    val resultText: String,
    val moves: List<SignalHuntFeatureMove>,
)

private fun parseSignalHuntFeatureReplay(
    event: CompanionLifeEvent,
    assistantName: String,
): SignalHuntFeatureReplay? = runCatching {
    val json = JsonInstant.parseToJsonElement(event.detailsJson).jsonObject
    val moves = json["moves"]?.jsonArray.orEmpty().mapNotNull { item ->
        val obj = item.jsonObject
        val cell = obj["cell"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        SignalHuntFeatureMove(
            cell = cell,
            foundSignal = obj["found_signal"]?.jsonPrimitive?.booleanOrNull == true,
            points = obj["points"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }
    if (moves.isEmpty()) return@runCatching null
    SignalHuntFeatureReplay(
        sessionId = json["session_id"]?.jsonPrimitive?.contentOrNull ?: event.id,
        assistantName = assistantName,
        score = json["score"]?.jsonPrimitive?.intOrNull ?: 0,
        maxScore = json["max_score"]?.jsonPrimitive?.intOrNull ?: 75,
        resultText = json["result"]?.jsonPrimitive?.contentOrNull ?: event.summary,
        moves = moves,
    )
}.getOrNull()

private const val SIGNAL_HUNT_FEATURE_SIGNAL_COUNT = 3
private const val SIGNAL_HUNT_FEATURE_MAX_MOVES = 5

private object SignalHuntFeatureColors {
    val background = Color(0xFFF8F4F0)
    val success = Color(0xFF2E8B68)
    val soft = Color(0xFF6F6A87)
}
