package me.rerere.rikkahub.ui.pages.study

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.Package
import me.rerere.rikkahub.data.study.StudyInventory
import me.rerere.rikkahub.data.study.StudyRules

private enum class StudyCollectionSection {
    Scrolls,
    Theaters,
}

@Composable
internal fun StudyCollectionPanel(
    inventory: StudyInventory,
    onUseUniversalNormalTarget: (String) -> Unit,
    onOpenMysteryBox: (Int) -> Unit,
    onRedeemDouyin: () -> Unit,
    onRedeemGameRoundTicket: () -> Unit,
    onRedeemGame: () -> Unit,
    onRedeemAnime: () -> Unit,
    onOpenStarWish: () -> Unit,
    onOpenImageGen: (String) -> Unit,
) {
    var collectionSection by remember { mutableStateOf(StudyCollectionSection.Scrolls) }
    var selectedOutfit by remember { mutableStateOf<String?>(null) }
    var pendingNormalTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    StudyCollectionCard {
        Text("收藏背包", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (inventory.unopenedMysteryBoxes.isNotEmpty()) {
            Surface(color = StudyCollectionHero.copy(alpha = 0.72f), shape = MaterialTheme.shapes.medium) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(HugeIcons.Package, null, tint = StudyCollectionGold)
                    Column(Modifier.weight(1f)) {
                        Text("未开启盲盒", fontWeight = FontWeight.SemiBold)
                        Text(
                            "还有 ${inventory.unopenedMysteryBoxes.size} 个番茄钟盲盒可以打开",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(onClick = { onOpenMysteryBox(0) }) {
                        Text("开启")
                    }
                }
            }
        }

        Surface(color = StudyCollectionSoftBlue.copy(alpha = 0.92f), shape = MaterialTheme.shapes.medium) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(HugeIcons.Package, null, tint = StudyCollectionBlue)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("通用普通碎片", fontWeight = FontWeight.SemiBold)
                    Text(
                        "点开画卷部件后可指定补 1 片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${inventory.universalNormalFragments}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = StudyCollectionBlue,
                )
            }
        }

        StudyEntertainmentRewardRow(
            label = "抖音时长券 · 20分钟",
            count = inventory.douyinFragments,
            color = StudyCollectionPurple,
            actions = listOf("刷抖音" to onRedeemDouyin),
        )
        StudyEntertainmentRewardRow(
            label = "剧场碎片",
            count = inventory.theaterFragments,
            color = StudyCollectionPurple,
            actions = listOf("小剧场" to onOpenStarWish),
        )
        StudyEntertainmentRewardRow(
            label = "游戏局数券 · 每张4局",
            count = inventory.gameRoundTickets,
            color = StudyCollectionPurple,
            actions = listOf("使用" to onRedeemGameRoundTicket),
        )
        StudyEntertainmentRewardRow(
            label = "游戏畅玩券 · 120分钟",
            count = inventory.gameFragments,
            color = StudyCollectionGold,
            actions = listOf("玩游戏" to onRedeemGame),
        )
        StudyEntertainmentRewardRow(
            label = "视频解锁卡",
            count = inventory.videoFragments,
            color = StudyCollectionGold,
            actions = listOf("视频馆" to onOpenStarWish),
        )
        StudyEntertainmentRewardRow(
            label = "番剧兑换券 · 3小时",
            count = inventory.animeFragments,
            color = Color(0xFF23C8B8),
            actions = listOf("看动漫" to onRedeemAnime),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = collectionSection == StudyCollectionSection.Scrolls,
                onClick = { collectionSection = StudyCollectionSection.Scrolls },
                label = {
                    Text(
                        "已解锁画卷 ${inventory.unlockedOutfits.size}/${StudyRules.outfitNames.size}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = collectionSection == StudyCollectionSection.Theaters,
                onClick = { collectionSection = StudyCollectionSection.Theaters },
                label = {
                    Text(
                        "小剧场 ${StudyRules.theaterNames.size}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }

        StudyCollectionProgressList(
            inventory = inventory,
            section = collectionSection,
            selectedOutfit = selectedOutfit,
            onSelectOutfit = { selectedOutfit = if (selectedOutfit == it) null else it },
            onUseUniversalNormalTarget = { key, label -> pendingNormalTarget = key to label },
            onOpenImageGen = onOpenImageGen,
        )
    }

    pendingNormalTarget?.let { (key, label) ->
        AlertDialog(
            onDismissRequest = { pendingNormalTarget = null },
            title = { Text("使用通用普通碎片？") },
            text = {
                Text(
                    if ((inventory.normalFragments[key] ?: 0) >= StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT) {
                        "$label 已经满 ${StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT} 片，继续使用会转换成100夸夸值。"
                    } else {
                        "要给 $label 增加 1 个碎片吗？"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUseUniversalNormalTarget(key)
                        pendingNormalTarget = null
                    },
                ) {
                    Text("使用")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingNormalTarget = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun StudyEntertainmentRewardRow(
    label: String,
    count: Int,
    color: Color,
    actions: List<Pair<String, () -> Unit>>,
) {
    Surface(color = Color.White.copy(alpha = 0.72f), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold, color = color)
                Text("$count 枚", style = MaterialTheme.typography.bodySmall)
            }
            actions.forEach { (title, action) ->
                TextButton(onClick = action, enabled = count > 0 || title in setOf("小剧场", "AI 视频")) {
                    Text(title)
                }
            }
        }
    }
}

@Composable
private fun StudyCollectionProgressList(
    inventory: StudyInventory,
    section: StudyCollectionSection,
    selectedOutfit: String?,
    onSelectOutfit: (String) -> Unit,
    onUseUniversalNormalTarget: (String, String) -> Unit,
    onOpenImageGen: (String) -> Unit,
) {
    when (section) {
        StudyCollectionSection.Scrolls -> {
            Text("画卷收集进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            StudyRules.outfitNames.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { outfit ->
                        val fragmentCount = inventory.normalOutfitTotal(outfit)
                            .coerceAtMost(StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT)
                        StudyOutfitSummaryTile(
                            outfit = outfit,
                            fragmentCount = fragmentCount,
                            unlocked = outfit in inventory.unlockedOutfits,
                            selected = selectedOutfit == outfit,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectOutfit(outfit) },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            selectedOutfit?.let { outfit ->
                val fragmentCount = inventory.normalOutfitTotal(outfit)
                    .coerceAtMost(StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT)
                val completedParts = if (fragmentCount >= StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT) 1 else 0
                StudyOutfitProgressCard(
                    outfit = outfit,
                    fragmentCount = fragmentCount,
                    completedParts = completedParts,
                    inventory = inventory,
                    onUseUniversalNormalTarget = onUseUniversalNormalTarget,
                    onOpenImageGen = onOpenImageGen,
                )
            }
        }

        StudyCollectionSection.Theaters -> {
            Text("小剧场进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "剧场碎片专门用于小剧场。去星愿馆选择任意小剧场，花 1 枚生成或续写 1 章。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StudyCollectionProgressRow(
                title = "当前剧场碎片",
                detail = "${inventory.theaterFragments} 个 · 每 1 个可兑换 1 章",
                progress = inventory.theaterFragments.coerceAtMost(1).toFloat(),
                unlocked = inventory.theaterFragments >= 1,
            )
            StudyRules.theaterNames.forEach { theater ->
                StudyCollectionProgressRow(
                    title = theater,
                    detail = "候选剧情",
                    progress = 0f,
                    unlocked = inventory.theaterFragments >= 1,
                )
            }
        }
    }
}

private fun StudyInventory.normalOutfitTotal(outfit: String): Int {
    val prefix = "normal:$outfit:"
    return normalFragments.entries.sumOf { (key, count) ->
        if (key.startsWith(prefix)) count else 0
    }
}

@Composable
private fun StudyOutfitSummaryTile(
    outfit: String,
    fragmentCount: Int,
    unlocked: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val progress = (fragmentCount / StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT.toFloat()).coerceIn(0f, 1f)
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = when {
            selected -> StudyCollectionHero.copy(alpha = 0.88f)
            unlocked -> StudyCollectionSoftBlue.copy(alpha = 0.88f)
            else -> Color.White.copy(alpha = 0.58f)
        },
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(42.dp),
                    strokeWidth = 4.dp,
                    color = if (unlocked) StudyCollectionGold else StudyCollectionBlue,
                    trackColor = Color.White.copy(alpha = 0.62f),
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(outfit, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (unlocked) "已解锁" else "$fragmentCount/${StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StudyOutfitProgressCard(
    outfit: String,
    fragmentCount: Int,
    completedParts: Int,
    inventory: StudyInventory,
    onUseUniversalNormalTarget: (String, String) -> Unit,
    onOpenImageGen: (String) -> Unit,
) {
    val unlocked = outfit in inventory.unlockedOutfits
    Surface(
        color = if (unlocked) {
            StudyCollectionHero.copy(alpha = 0.72f)
        } else {
            StudyCollectionSoftBlue.copy(alpha = 0.72f)
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(outfit, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (unlocked) {
                            "完整画卷已解锁"
                        } else {
                            "$completedParts/1 专属碎片 · $fragmentCount/${StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT} 片"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (unlocked) {
                        "已解锁"
                    } else {
                        "${(fragmentCount * 100 / StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT).coerceIn(0, 100)}%"
                    },
                    color = StudyCollectionGold,
                )
            }
            LinearProgressIndicator(
                progress = { fragmentCount / StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            if (unlocked) {
                FilledTonalButton(onClick = { onOpenImageGen(outfit) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(HugeIcons.AiMagic, null)
                    Spacer(Modifier.width(8.dp))
                    Text("用这套造型去生图")
                }
            }
            StudyRules.outfitParts.forEach { part ->
                val key = "normal:$outfit:$part"
                val count = inventory.normalOutfitTotal(outfit)
                    .coerceAtMost(StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT)
                StudyCollectionProgressRow(
                    title = part,
                    detail = "$count/${StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT}",
                    progress = count / StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT.toFloat(),
                    unlocked = count >= StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT,
                    enabled = inventory.universalNormalFragments > 0,
                    onClick = if (inventory.universalNormalFragments > 0) {
                        { onUseUniversalNormalTarget(key, "$outfit · $part") }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun StudyCollectionProgressRow(
    title: String,
    detail: String,
    progress: Float,
    unlocked: Boolean,
    action: (@Composable () -> Unit)? = null,
    enabled: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = if (onClick != null) {
            Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(4.dp)
        } else {
            Modifier
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (unlocked) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (unlocked) StudyCollectionGold else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (action != null) {
                Spacer(Modifier.width(6.dp))
                action()
            } else if (onClick != null && enabled) {
                Spacer(Modifier.width(6.dp))
                Text("点按使用通用", style = MaterialTheme.typography.labelSmall, color = StudyCollectionBlue)
            }
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StudyCollectionCard(content: @Composable ColumnScope.() -> Unit) {
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

private val StudyCollectionHero = Color(0xFFFFE6B8)
private val StudyCollectionSoftBlue = Color(0xFFDCECF4)
private val StudyCollectionBlue = Color(0xFF3D7EA6)
private val StudyCollectionPurple = Color(0xFF8067B7)
private val StudyCollectionGold = Color(0xFF9B6B10)
