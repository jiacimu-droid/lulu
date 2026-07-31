package me.rerere.rikkahub.ui.pages.study

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clapping01
import me.rerere.hugeicons.stroke.Package
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.study.StudyEvent
import me.rerere.rikkahub.data.study.StudyInventory
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudySleepHabit
import me.rerere.rikkahub.data.study.StudyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun StudyCompactHeroPanel(
    state: StudyState,
    assistant: Assistant,
    assistants: List<Assistant>,
    onSignIn: () -> Unit,
    onSelectCompanion: (Assistant) -> Unit,
) {
    val overview = StudyRules.studyTimeOverview(state)
    var showCompanionPicker by remember { mutableStateOf(false) }
    var showKudosHistory by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE6B8)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    me.rerere.rikkahub.ui.components.ui.UIAvatar(
                        assistant.name,
                        assistant.avatar,
                        Modifier.size(58.dp),
                        onClick = { showCompanionPicker = true },
                    )
                    Text(
                        "${assistant.name}陪你备考",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactMetric(
                        label = "夸夸值",
                        value = state.wallet.kudos.toString(),
                        modifier = Modifier.weight(1f),
                        onClick = { showKudosHistory = true },
                    )
                    CompactMetric(
                        label = "今日学习",
                        value = compactStudyMetric(overview.todayMinutes, overview.todayPomodoros),
                        modifier = Modifier.weight(1f),
                    )
                }
                CompactMetric(
                    label = "本周学习",
                    value = compactStudyMetric(overview.weekMinutes, overview.weekPomodoros),
                    modifier = Modifier.fillMaxWidth(),
                )

                FilledTonalButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                    Icon(HugeIcons.Clapping01, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("签到")
                }
            }
        }
    }

    if (showCompanionPicker) {
        AlertDialog(
            onDismissRequest = { showCompanionPicker = false },
            title = { Text("选择陪伴角色") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(assistants, key = { it.id }) { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectCompanion(item)
                                    showCompanionPicker = false
                                },
                            color = if (item.id == assistant.id) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                me.rerere.rikkahub.ui.components.ui.UIAvatar(
                                    item.name,
                                    item.avatar,
                                    Modifier.size(42.dp),
                                )
                                Text(
                                    item.name.ifBlank { "未命名角色" },
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCompanionPicker = false }) { Text("收起") }
            },
        )
    }

    if (showKudosHistory) {
        AlertDialog(
            onDismissRequest = { showKudosHistory = false },
            title = { Text("夸夸值") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompactKudosLine("当前可用", state.wallet.kudos)
                    CompactKudosLine("历史累计", state.wallet.totalKudosEarned)
                }
            },
            confirmButton = {
                TextButton(onClick = { showKudosHistory = false }) { Text("知道啦") }
            },
        )
    }
}

@Composable
private fun CompactMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        color = Color.White.copy(alpha = 0.46f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompactKudosLine(label: String, value: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.toString(), fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun StudyCompactSleepHabitCard(state: StudyState) {
    val earlySleepClaimed = StudyRules.hasClaimedSleepHabitReward(
        state = state,
        habit = StudySleepHabit.EarlySleep,
        date = java.time.LocalDate.now(),
    )
    val earlyRiseClaimed = StudyRules.hasClaimedSleepHabitReward(
        state = state,
        habit = StudySleepHabit.EarlyRise,
        date = java.time.LocalDate.now(),
    )
    CompactStudyCard {
        Text("作息任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        CompactHabitRow("昨晚早睡", "+${StudyRules.EARLY_SLEEP_KUDOS} 夸夸值", earlySleepClaimed)
        CompactHabitRow("今天早起", "十连抽券 ×${StudyRules.EARLY_RISE_TEN_DRAW_TICKETS}", earlyRiseClaimed)
    }
}

@Composable
private fun CompactHabitRow(title: String, reward: String, claimed: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (claimed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(reward, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (claimed) "已领取" else "待完成", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun StudyCompactCollectionPanel(
    inventory: StudyInventory,
    onOpenMysteryBox: (Int) -> Unit,
    onRedeemDouyin: () -> Unit,
    onRedeemGameRoundTicket: () -> Unit,
    onRedeemGame: () -> Unit,
    onRedeemAnime: () -> Unit,
    onOpenStarWish: () -> Unit,
    onOpenImageGen: (String) -> Unit,
) {
    var showAllOutfits by remember { mutableStateOf(false) }
    val outfits = if (showAllOutfits) StudyRules.outfitNames else StudyRules.outfitNames.take(4)

    CompactStudyCard {
        Text("收藏背包", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        if (inventory.unopenedMysteryBoxes.isNotEmpty()) {
            CompactRewardRow(
                title = "未开启盲盒",
                count = inventory.unopenedMysteryBoxes.size,
                actionLabel = "开启",
                onAction = { onOpenMysteryBox(0) },
            )
        }

        CompactRewardRow("抖音时长券 · 20分钟", inventory.douyinFragments, "使用", onRedeemDouyin)
        CompactNavigationRow("小剧场", inventory.theaterFragments, "进入", onOpenStarWish)
        CompactRewardRow("游戏局数券 · 每张4局", inventory.gameRoundTickets, "使用", onRedeemGameRoundTicket)
        CompactRewardRow("游戏畅玩券 · 120分钟", inventory.gameFragments, "使用", onRedeemGame)
        CompactNavigationRow("视频馆", inventory.videoFragments, "进入", onOpenStarWish)
        CompactRewardRow("番剧兑换券 · 3小时", inventory.animeFragments, "使用", onRedeemAnime)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("画卷收藏", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (StudyRules.outfitNames.size > 4) {
                TextButton(onClick = { showAllOutfits = !showAllOutfits }) {
                    Text(if (showAllOutfits) "收起" else "查看全部")
                }
            }
        }

        outfits.forEach { outfit ->
            val count = inventory.normalOutfitTotalCompact(outfit)
                .coerceAtMost(StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT)
            val unlocked = outfit in inventory.unlockedOutfits
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (unlocked) Color(0xFFE4F2F6) else MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(outfit, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$count/${StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT}")
                    }
                    LinearProgressIndicator(
                        progress = { count / StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (unlocked) {
                        TextButton(onClick = { onOpenImageGen(outfit) }) { Text("生成画卷") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactRewardRow(
    title: String,
    count: Int,
    actionLabel: String,
    onAction: () -> Unit,
) {
    CompactInventoryRow(title, count, actionLabel, onAction, enabled = count > 0, navigation = false)
}

@Composable
private fun CompactNavigationRow(
    title: String,
    count: Int,
    actionLabel: String,
    onAction: () -> Unit,
) {
    CompactInventoryRow(title, count, actionLabel, onAction, enabled = true, navigation = true)
}

@Composable
private fun CompactInventoryRow(
    title: String,
    count: Int,
    actionLabel: String,
    onAction: () -> Unit,
    enabled: Boolean,
    navigation: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (navigation) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.72f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(HugeIcons.Package, contentDescription = null, tint = if (navigation) MaterialTheme.colorScheme.secondary else Color(0xFF8067B7))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text("$count 枚", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onAction, enabled = enabled) { Text(actionLabel) }
        }
    }
}

@Composable
internal fun StudyCompactRecentEventsCard(events: List<StudyEvent>) {
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    CompactStudyCard {
        Text("奖励记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (events.isEmpty()) {
            Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            events.take(10).forEach { event ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(event.title, fontWeight = FontWeight.SemiBold)
                            if (event.detail.isNotBlank()) {
                                Text(event.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (event.createdAt > 0L) {
                            Text(
                                formatter.format(Date(event.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactStudyCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

private fun StudyInventory.normalOutfitTotalCompact(outfit: String): Int {
    val prefix = "normal:$outfit:"
    return normalFragments.entries.sumOf { (key, count) -> if (key.startsWith(prefix)) count else 0 }
}

private fun compactStudyMetric(minutes: Int, pomodoros: Int): String = "$minutes 分 · $pomodoros 个"
