package me.rerere.rikkahub.ui.pages.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.Package
import me.rerere.rikkahub.data.study.StudyAchievement
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudyShopItem
import me.rerere.rikkahub.data.study.StudyState

@Composable
internal fun StudyAchievementPanel(
    state: StudyState,
    onClaim: (String) -> Unit,
) {
    val claimable = StudyRules.claimableAchievements(state).map { it.id }.toSet()
    StudyRewardPanelCard {
        Text("成就墙", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        StudyRules.achievements
            .sortedBy { it.id in state.claimedAchievementIds }
            .forEach { achievement ->
                StudyAchievementRow(
                    achievement = achievement,
                    claimed = achievement.id in state.claimedAchievementIds,
                    claimable = achievement.id in claimable,
                    onClaim = { onClaim(achievement.id) },
                )
            }
    }
}

@Composable
private fun StudyAchievementRow(
    achievement: StudyAchievement,
    claimed: Boolean,
    claimable: Boolean,
    onClaim: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            HugeIcons.Favourite,
            null,
            tint = if (claimed || claimable) StudyRewardGold else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(achievement.title, fontWeight = FontWeight.SemiBold)
            Text("${achievement.condition} · ${achievement.reward.title}", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onClaim, enabled = claimable && !claimed) {
            Text(if (claimed) "已领" else "领取")
        }
    }
}

@Composable
internal fun StudyShopPanel(
    state: StudyState,
    onRefresh: () -> Unit,
    onBuy: (StudyShopItem) -> Unit,
) {
    val canRefresh = state.manualShopRefreshDate != state.today
    StudyRewardPanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "神秘商店",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh, enabled = canRefresh) {
                Text(if (canRefresh) "刷新一次" else "今日已刷新")
            }
        }
        state.shopItems.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(HugeIcons.Package, null, tint = StudyRewardBlue)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    Text("${item.price} 夸夸值", style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.Button(
                    onClick = { onBuy(item) },
                    enabled = item.id !in state.purchasedShopItemIds && state.wallet.kudos >= item.price,
                ) {
                    Text(if (item.id in state.purchasedShopItemIds) "已购" else "购买")
                }
            }
        }
    }
}

@Composable
internal fun StudyRewardGuidePanel() {
    StudyRewardPanelCard {
        Text("奖励说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        StudyGuideBlock(
            title = "获取",
            lines = listOf(
                "签到、作息任务、待办和番茄钟会发放夸夸值或抽卡券。",
                "今日待办全清会触发超神时刻。",
                "普通卡池连续 ${StudyRules.NON_NORMAL_PITY_DRAW_COUNT} 抽没有紫、金或彩色奖励时触发保底。",
            ),
        )
        StudyGuideBlock(
            title = "抽卡与收藏",
            lines = listOf(
                "单抽消耗 ${StudyRules.SINGLE_DRAW_COST} 夸夸值，十连消耗 ${StudyRules.TEN_DRAW_COST} 夸夸值。",
                "画卷由对应专属碎片解锁；娱乐券抽到后直接进入收藏背包。",
                "剧场碎片用于小剧场，视频解锁卡用于视频馆。",
            ),
        )
        StudyGuideBlock(
            title = "陪伴",
            lines = listOf(
                "番茄钟可以开启角色语音鼓励和轻聊天。",
                "学习、作息和奖励记录会同步到考研页面的陪伴记录。",
            ),
        )
    }
}

@Composable
private fun StudyGuideBlock(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        lines.forEach { line -> Text("· $line", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun StudyRewardPanelCard(content: @Composable ColumnScope.() -> Unit) {
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

private val StudyRewardGold = Color(0xFF9B6B10)
private val StudyRewardBlue = Color(0xFF3D7EA6)
