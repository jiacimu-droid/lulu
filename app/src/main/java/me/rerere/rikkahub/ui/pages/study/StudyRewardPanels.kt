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
        Text("每天自动刷新 3 件商品；手动刷新每天最多一次。", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text("奖励系统说明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        StudyGuideBlock(
            title = "每日获取",
            lines = listOf(
                "签到：每天固定 50 夸夸值。",
                "昨晚早睡：按你的作息，约01:30前入睡并经角色认可，可发 ${StudyRules.EARLY_SLEEP_KUDOS} 夸夸值。",
                "今天早起：按你的作息，约09:30前起床并经角色认可，可发十连抽券 x${StudyRules.EARLY_RISE_TEN_DRAW_TICKETS}。",
                "累计学习每满 ${StudyRules.STUDY_REWARD_INTERVAL_MINUTES} 分钟：${StudyRules.STUDY_REWARD_KUDOS} 夸夸值；不足部分跨番茄保留。",
                "完成 1 项待办：50 夸夸值。",
                "今日待办全清：触发超神时刻，固定给十连券 x1。",
                "普通卡池连续 ${StudyRules.NON_NORMAL_PITY_DRAW_COUNT} 抽没有紫/金/彩：第 ${StudyRules.NON_NORMAL_PITY_DRAW_COUNT} 抽直接保底紫色。",
            ),
        )
        StudyGuideBlock(
            title = "抽卡与收藏",
            lines = listOf(
                "单抽 ${StudyRules.SINGLE_DRAW_COST} 夸夸值。",
                "十连 ${StudyRules.TEN_DRAW_COST} 夸夸值。",
                "画卷碎片已经集满后再抽到同名蓝色碎片，仍会在抽卡结果中展示，并标记为“碎片已满”；不会重复计入，也不返夸夸值、抽卡券或其他资源。",
                "蓝色画卷专属碎片 93.8%；紫色 4.5%（游戏局数券 2% / 抖音20分钟 2% / 剧场碎片 0.5%）。",
                "金色 1.5%（游戏120分钟 1% / 视频解锁卡 0.5%）；彩色番剧3小时 0.2%。",
                "硬保底：连续 ${StudyRules.NON_NORMAL_PITY_DRAW_COUNT} 抽没有出现紫/金/彩时，第 ${StudyRules.NON_NORMAL_PITY_DRAW_COUNT} 抽必为紫色。",
                "卡池、等级和神秘商店都不再产出通用碎片；旧存档中的通用碎片仍可使用。",
                "每套画卷需要 10 个专属碎片；通用普通碎片可以补任意一套未满画卷。",
                "娱乐券抽到即拥有；剧场碎片每枚可生成或续写小剧场 1 章。",
            ),
        )
        StudyGuideBlock(
            title = "每日抽数估算",
            lines = listOf(
                "学习2小时约得2400夸夸值，可抽3次十连；学习3小时约得3600夸夸值，可抽4次十连加4次单抽。",
                "超神 5 天给 5 张十连券；等级、成就和商店会追加抽卡券。",
                "按100抽估算：蓝色约94，紫色约4至5，金色约1至2，彩色约0至1。",
                "普通图片专属碎片只来自抽卡；新奖励不再产生通用普通碎片。",
            ),
        )
        StudyGuideBlock(
            title = "惩罚机制",
            lines = listOf(
                "连续 2 天没有番茄钟或待办完成：扣 50 夸夸值。",
                "连续 3 天及以上：每天扣 100 夸夸值。",
                "恢复学习行为后连续未学习计数清零；夸夸值不会变成负数。",
            ),
        )
        StudyGuideBlock(
            title = "陪伴机制",
            lines = listOf(
                "番茄钟开始前可选择语音鼓励。",
                "番茄钟里可以和角色轻声聊天。",
                "番茄钟结束后按实际累计学习时长发放夸夸值。",
                "作息奖励按你的个人基线判断；角色必须知道具体时间，描述含糊会追问，明显矛盾或太晚会拒绝。",
            ),
        )
        StudyGuideBlock(
            title = "当前已落地",
            lines = listOf(
                "签到、作息任务、待办、番茄钟、盲盒、惩罚、抽卡、超神、等级、成就、商店都已接入本地状态。",
                "收藏已按 20 套画卷、每套 10 个专属碎片展示。",
                "旧存档通用普通碎片仍可自动补最佳目标，也可在收藏里指定画卷；新系统不再产出。",
                "娱乐券与剧场碎片均按用途独立保存，卡池内不存在任何通用碎片。",
                "等级与成就只奖励夸夸值和抽卡券；画卷与娱乐收藏通过抽卡获得。",
                "番茄钟已接入角色陪伴、语音鼓励和轻聊天。",
                "更深的角色主动督学、画卷提示词自动带入、星愿馆视频收藏柜可以作为后续增强。",
            ),
        )
    }
}

@Composable
private fun StudyGuideBlock(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        lines.forEach { line ->
            Text("· $line", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
