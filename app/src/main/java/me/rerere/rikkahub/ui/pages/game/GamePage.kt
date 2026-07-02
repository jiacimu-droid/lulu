package me.rerere.rikkahub.ui.pages.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.asr.ASRStatus
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft02
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Voice
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.theme.CustomColors
import kotlin.math.abs
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHubPage() {
    val navController = LocalNavController.current
    val games = remember {
        listOf(
            GameTile(
                title = "满分男",
                subtitle = "满分的人，离谱的缺点，猜对方会扣几分",
                enabled = true,
                onClick = { navController.navigate(Screen.PerfectManGame) },
            ),
        ) + List(7) { index ->
            GameTile(
                title = "小游戏 ${index + 2}",
                subtitle = "慢慢补充",
                enabled = false,
                onClick = {},
            )
        }
    }
    Scaffold(
        containerColor = GameColors.background,
        topBar = {
            TopAppBar(
                title = { Text("游戏") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft02, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GameHero()
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 18.dp),
            ) {
                items(games, key = { it.title }) { game ->
                    GameTileCard(game)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfectManGamePage() {
    val navController = LocalNavController.current
    val asr = LocalASRState.current
    val tts = LocalTTSState.current
    val asrState by asr.state.collectAsState()
    var round by remember { mutableIntStateOf(1) }
    var targetScore by remember { mutableIntStateOf(Random.nextInt(0, 11)) }
    var flaw by remember { mutableStateOf("") }
    var guessText by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<RoundResult?>(null) }
    var opponentVoiceEnabled by remember { mutableStateOf(true) }
    var openScoreMode by remember { mutableStateOf(false) }
    var scoreRevealed by remember { mutableStateOf(false) }
    var listeningTarget by remember { mutableStateOf<VoiceInputTarget?>(null) }

    val guesser = if (round % 2 == 1) Player.Me else Player.Partner
    val describer = guesser.opposite()
    val isListening = asrState.status != ASRStatus.Idle && asrState.status != ASRStatus.Error

    fun speak(text: String) {
        if (opponentVoiceEnabled && text.isNotBlank()) {
            tts.speak(text)
        }
    }

    fun stopVoiceInput() {
        asr.stop()
        listeningTarget = null
    }

    fun startVoiceInput(target: VoiceInputTarget) {
        if (isListening) {
            stopVoiceInput()
            return
        }
        listeningTarget = target
        asr.start { transcript ->
            val clean = transcript.trim()
            if (clean.isBlank()) return@start
            when (target) {
                VoiceInputTarget.Flaw -> flaw = clean
                VoiceInputTarget.Guess -> guessText = clean.filter { it.isDigit() || it == '.' }
            }
        }
    }

    fun nextRound() {
        tts.stop()
        stopVoiceInput()
        round += 1
        targetScore = Random.nextInt(0, 11)
        flaw = ""
        guessText = ""
        result = null
        scoreRevealed = false
    }

    fun submitGuess() {
        val guess = guessText.trim().toFloatOrNull()?.toInt()?.coerceIn(0, 10) ?: return
        val diff = abs(guess - targetScore)
        val nextResult = RoundResult(guess = guess, score = targetScore, success = diff <= 1, diff = diff)
        result = nextResult
        speak(
            if (nextResult.success) {
                "你们真的太有默契啦。目标分是 $targetScore 分，猜的是 $guess 分。"
            } else {
                "这轮偏差是 $diff 分。输赢不重要，重点是这也太好笑了。"
            },
        )
    }

    Scaffold(
        containerColor = GameColors.background,
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RoundHeader(round = round, describer = describer, guesser = guesser)
            ScoreCard(
                score = targetScore,
                describer = describer,
                openScoreMode = openScoreMode,
                scoreRevealed = scoreRevealed,
                onToggleOpenScore = {
                    openScoreMode = it
                    if (it) scoreRevealed = true
                },
                onToggleScoreReveal = { scoreRevealed = !scoreRevealed },
            )
            VoiceSettingsCard(
                opponentVoiceEnabled = opponentVoiceEnabled,
                onOpponentVoiceEnabledChange = {
                    opponentVoiceEnabled = it
                    if (!it) tts.stop()
                },
            )
            FlawInputCard(
                describer = describer,
                flaw = flaw,
                onFlawChange = { flaw = it },
                onExample = { flaw = PerfectManExamples.random() },
                listening = listeningTarget == VoiceInputTarget.Flaw && isListening,
                onVoice = { startVoiceInput(VoiceInputTarget.Flaw) },
                onSpeak = { speak("这是一个满分男，但是${flaw.ifBlank { "他的缺点还没有写" }}") },
                voiceEnabled = opponentVoiceEnabled,
            )
            GuessCard(
                guesser = guesser,
                guessText = guessText,
                onGuessTextChange = { guessText = it.filter { char -> char.isDigit() || char == '.' }.take(2) },
                listening = listeningTarget == VoiceInputTarget.Guess && isListening,
                onVoice = { startVoiceInput(VoiceInputTarget.Guess) },
                onSubmit = ::submitGuess,
                canSubmit = guessText.trim().toFloatOrNull() != null,
            )
            result?.let {
                ResultCard(result = it, onNextRound = ::nextRound)
            }
            RuleCard()
        }
    }
}

@Composable
private fun GameHero() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GameColors.heroBrush)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.22f), modifier = Modifier.size(54.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(HugeIcons.Puzzle, contentDescription = null, tint = Color.White)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "游戏馆",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text("先放轻量局，后面慢慢补小游戏。", color = Color.White.copy(alpha = 0.84f))
            }
        }
    }
}

@Composable
private fun GameTileCard(game: GameTile) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .then(if (game.enabled) Modifier.clickable(onClick = game.onClick) else Modifier),
        color = if (game.enabled) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        tonalElevation = if (game.enabled) 4.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(shape = CircleShape, color = GameColors.accent.copy(alpha = if (game.enabled) 0.18f else 0.08f)) {
                Icon(
                    imageVector = if (game.enabled) HugeIcons.MagicWand01 else HugeIcons.Puzzle,
                    contentDescription = null,
                    tint = if (game.enabled) GameColors.accent else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(10.dp).size(24.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(game.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    game.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RoundHeader(round: Int, describer: Player, guesser: Player) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("第 $round 轮", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "${describer.label}看分并描述缺点，${guesser.label}猜最后值几分。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("下一轮会自动交换角色。", style = MaterialTheme.typography.bodySmall, color = GameColors.accent)
        }
    }
}

@Composable
private fun ScoreCard(
    score: Int,
    describer: Player,
    openScoreMode: Boolean,
    scoreRevealed: Boolean,
    onToggleOpenScore: (Boolean) -> Unit,
    onToggleScoreReveal: () -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (openScoreMode) "明拍分数" else "只给${describer.label}看的分数",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "系统随机 roll 0-10 分。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = openScoreMode, onCheckedChange = onToggleOpenScore)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GameColors.scoreBrush, RoundedCornerShape(18.dp))
                    .clickable(enabled = !openScoreMode, onClick = onToggleScoreReveal)
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        if (openScoreMode || scoreRevealed) "$score" else "?",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    Text(
                        when {
                            openScoreMode -> "明拍模式"
                            scoreRevealed -> "${describer.label}看完后点这里隐藏"
                            else -> "${describer.label}点这里偷看分数"
                        },
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceSettingsCard(
    opponentVoiceEnabled: Boolean,
    onOpponentVoiceEnabledChange: (Boolean) -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(HugeIcons.VolumeHigh, contentDescription = null, tint = GameColors.accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("对方语音", fontWeight = FontWeight.SemiBold)
                Text(
                    "关闭后不再播报描述和结果。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = opponentVoiceEnabled, onCheckedChange = onOpponentVoiceEnabledChange)
        }
    }
}

@Composable
private fun FlawInputCard(
    describer: Player,
    flaw: String,
    onFlawChange: (String) -> Unit,
    onExample: () -> Unit,
    listening: Boolean,
    onVoice: () -> Unit,
    onSpeak: () -> Unit,
    voiceEnabled: Boolean,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "${describer.label}描述缺点",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = flaw,
                onValueChange = onFlawChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text("这是一个满分男，但是...") },
                placeholder = { Text("例如：10天不洗脚，也不洗澡。") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = onVoice, modifier = Modifier.weight(1f)) {
                    Icon(HugeIcons.Voice, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (listening) "停止听写" else "语音输入")
                }
                OutlinedButton(onClick = onExample, modifier = Modifier.weight(1f)) {
                    Icon(HugeIcons.Sparkles, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("随机缺点")
                }
            }
            OutlinedButton(
                onClick = onSpeak,
                enabled = voiceEnabled && flaw.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(HugeIcons.VolumeHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("朗读给猜分方")
            }
        }
    }
}

@Composable
private fun GuessCard(
    guesser: Player,
    guessText: String,
    onGuessTextChange: (String) -> Unit,
    listening: Boolean,
    onVoice: () -> Unit,
    onSubmit: () -> Unit,
    canSubmit: Boolean,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "${guesser.label}猜分",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = guessText,
                    onValueChange = onGuessTextChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("0-10 分") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                IconButton(onClick = onVoice) {
                    Icon(HugeIcons.Voice, contentDescription = if (listening) "停止听写" else "语音猜分")
                }
            }
            Button(onClick = onSubmit, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
                Icon(HugeIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("揭晓默契")
            }
        }
    }
}

@Composable
private fun ResultCard(result: RoundResult, onNextRound: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            Button(onClick = onNextRound) {
                Text("下一轮")
            }
        },
        title = { Text(if (result.success) "你们真的太有默契啦" else "这轮偏了 ${result.diff} 分") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.size(96.dp),
                    shape = CircleShape,
                    color = if (result.success) GameColors.success else GameColors.soft,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (result.success) HugeIcons.Sparkles else HugeIcons.Puzzle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(42.dp),
                        )
                    }
                }
                Text(
                    "系统分：${result.score}，猜分：${result.guess}",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (result.success) "差值在 ±1 内，默契通关。" else "失败也没关系，这个游戏主要就是好笑。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun RuleCard() {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("规则", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("满分男基础设定是能想到的地方全满分，但有一个缺点。")
            Text("系统每轮随机 0-10 分。看分方描述缺点，猜分方给出判断。")
            Text("猜分和系统分差值在 ±1 内就算默契通关；输赢不重要，笑出来更重要。")
        }
    }
}

private enum class Player(val label: String) {
    Me("我"),
    Partner("对方");

    fun opposite(): Player = if (this == Me) Partner else Me
}

private enum class VoiceInputTarget {
    Flaw,
    Guess,
}

private data class RoundResult(
    val guess: Int,
    val score: Int,
    val success: Boolean,
    val diff: Int,
)

private data class GameTile(
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

private val PerfectManExamples = listOf(
    "10天不洗脚，也不洗澡。",
    "每次约会都要先讲半小时自己的梦。",
    "微信回复很快，但每句话都带工作总结格式。",
    "长得像建模脸，但是吃饭会把香菜当主菜。",
    "情绪稳定到吵架时会拿白板画流程图。",
    "很会做饭，但所有菜都坚持放薄荷。",
    "记得所有纪念日，但礼物永远买同款保温杯。",
    "声音特别好听，但睡前故事只讲刑法案例。",
)

private object GameColors {
    val background = Color(0xFFF8F4F0)
    val accent = Color(0xFF8B3D5E)
    val success = Color(0xFF2E8B68)
    val soft = Color(0xFF6F6A87)
    val heroBrush = Brush.linearGradient(listOf(Color(0xFF8B3D5E), Color(0xFFBD7E64), Color(0xFF4D314E)))
    val scoreBrush = Brush.linearGradient(listOf(Color(0xFF1F2747), Color(0xFF8B3D5E), Color(0xFFBD7E64)))
}
