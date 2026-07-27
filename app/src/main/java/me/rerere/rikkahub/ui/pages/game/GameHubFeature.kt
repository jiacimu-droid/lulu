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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft02
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.companion.CompanionLifeEvent
import me.rerere.rikkahub.data.companion.CompanionLifeEventStatus
import me.rerere.rikkahub.data.companion.CompanionLifeEventType
import me.rerere.rikkahub.data.companion.CompanionStore
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GameHubFeaturePage() {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val companionStore = koinInject<CompanionStore>()
    val companionState by companionStore.state.collectAsState()
    val latestSignalEvent = companionState.snapshots
        .asSequence()
        .flatMap { snapshot -> snapshot.lifeEvents.asSequence() }
        .filter { event ->
            event.type == CompanionLifeEventType.GAME &&
                event.status == CompanionLifeEventStatus.COMPLETED &&
                event.detailsJson.contains("\"game\":\"signal_hunt\"")
        }
        .maxByOrNull { it.endedAt ?: it.startedAt }
    val latestSignalAssistant = latestSignalEvent?.let { event ->
        settings.assistants.firstOrNull { it.id.toString() == event.assistantId }
    }
    val games = remember {
        listOf(
            GameHubTile(
                title = "信号追踪",
                subtitle = "选择一个角色，一起在 3×3 信号网格里找线索",
                enabled = true,
                onClick = { navController.navigate(Screen.SignalHuntGame()) },
            ),
            GameHubTile(
                title = "满分男",
                subtitle = "轮流描述和猜分，角色会按自己的人设参与",
                enabled = true,
                onClick = { navController.navigate(Screen.PerfectManGame) },
            ),
            GameHubTile(
                title = "轻量跑团",
                subtitle = "自由行动、d20 判定，与角色共同探索倒走的钟",
                enabled = true,
                onClick = { navController.navigate(Screen.QuickCompanionGame("roleplay_adventure")) },
            ),
            GameHubTile(
                title = "海龟汤",
                subtitle = "角色主持固定汤底，你自由提问并逐步还原真相",
                enabled = true,
                onClick = { navController.navigate(Screen.QuickCompanionGame("turtle_soup")) },
            ),
            GameHubTile(
                title = "默契问答",
                subtitle = "分别秘密作答，用角色记忆检验彼此有多了解",
                enabled = true,
                onClick = { navController.navigate(Screen.QuickCompanionGame("rapport_quiz")) },
            ),
            GameHubTile(
                title = "一起猜拳",
                subtitle = "真实结果生成后，角色会通过 API 按人设回应",
                enabled = true,
                onClick = { navController.navigate(Screen.QuickCompanionGame("rock_paper_scissors")) },
            ),
            GameHubTile(
                title = "快艇骰子",
                subtitle = "五颗骰子、三次机会、保留骰子并完成整张计分表",
                enabled = true,
                onClick = { navController.navigate(Screen.QuickCompanionGame("yacht_dice")) },
            ),
            GameHubTile(
                title = "五子棋",
                subtitle = "15×15 棋盘，你执黑先手，角色会进攻也会拦截",
                enabled = true,
                onClick = { navController.navigate(Screen.QuickCompanionGame("gomoku")) },
            ),
        )
    }
    Scaffold(
        containerColor = GameHubColors.background,
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 18.dp, top = 4.dp, end = 18.dp, bottom = 24.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                GameHubHero()
            }
            if (latestSignalEvent != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SignalHuntRecordCard(
                        assistantName = latestSignalAssistant?.name?.ifBlank { "某个角色" } ?: "某个角色",
                        event = latestSignalEvent,
                        onClick = { navController.navigate(Screen.SignalHuntGame(latestSignalEvent.id)) },
                    )
                }
            }
            items(games, key = { it.title }) { game ->
                GameHubTileCard(game)
            }
        }
    }
}

@Composable
private fun SignalHuntRecordCard(
    assistantName: String,
    event: CompanionLifeEvent,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        color = GameHubColors.accent.copy(alpha = 0.10f),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${assistantName}刚刚玩过信号追踪", fontWeight = FontWeight.SemiBold, color = GameHubColors.accent)
            Text(event.summary.ifBlank { "点这里观看这一局的完整探测路线。" }, style = MaterialTheme.typography.bodySmall)
            Text("点击观看记录 →", style = MaterialTheme.typography.labelLarge, color = GameHubColors.accent)
        }
    }
}

@Composable
private fun GameHubHero() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GameHubColors.heroBrush)
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
                Text("和角色一起玩，完成的对局会进入真实共同经历。", color = Color.White.copy(alpha = 0.84f))
            }
        }
    }
}

@Composable
private fun GameHubTileCard(game: GameHubTile) {
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
            Surface(shape = CircleShape, color = GameHubColors.accent.copy(alpha = if (game.enabled) 0.18f else 0.08f)) {
                Icon(
                    imageVector = if (game.enabled) HugeIcons.MagicWand01 else HugeIcons.Puzzle,
                    contentDescription = null,
                    tint = if (game.enabled) GameHubColors.accent else MaterialTheme.colorScheme.outline,
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

private data class GameHubTile(
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

private object GameHubColors {
    val background = Color(0xFFF8F4F0)
    val accent = Color(0xFF8B3D5E)
    val heroBrush = Brush.linearGradient(listOf(Color(0xFF8B3D5E), Color(0xFFBD7E64), Color(0xFF4D314E)))
}
