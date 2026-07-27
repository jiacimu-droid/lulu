package me.rerere.rikkahub.ui.pages.starwish

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.BookOpen02
import me.rerere.hugeicons.stroke.CircleLock01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Image03
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.rikkahub.data.starwish.StarWishGeneratedImage
import me.rerere.rikkahub.data.starwish.StarWishImageLaunch
import me.rerere.rikkahub.data.starwish.StarWishOutfitPrompts
import me.rerere.rikkahub.data.starwish.StarWishScroll
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog

@Composable
internal fun StarWishHero(section: StarWishSection, onSection: (StarWishSection) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.76f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(StarWishHeroBrush())
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("把学习里抽到的愿望，收进这里。", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("画卷保存套装提示词与生图入口；小剧场保存已解锁剧情和续写资格。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StarWishSection.entries.forEach {
                        FilterChip(selected = section == it, onClick = { onSection(it) }, label = { Text(it.label) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun StarWishListRow(
    title: String,
    subtitle: String,
    unlocked: Boolean,
    progress: Float,
    icon: ImageVector,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = unlocked, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) Color.White.copy(alpha = 0.86f) else StarWishColors.locked,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = if (unlocked) StarWishColors.mistBlue else Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (unlocked) icon else HugeIcons.CircleLock01,
                            contentDescription = null,
                            tint = if (unlocked) StarWishColors.inkBlue else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (unlocked) "进入" else "锁定", style = MaterialTheme.typography.labelMedium, color = if (unlocked) StarWishColors.inkBlue else MaterialTheme.colorScheme.outline)
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(HugeIcons.Delete01, contentDescription = "删除")
                    }
                }
            }
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun TheaterWalletCard(
    rareFragments: Int,
    onAdd: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.82f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = StarWishColors.mistBlue, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(HugeIcons.BookOpen02, null, tint = StarWishColors.inkBlue)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("剧场碎片 $rareFragments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("可用于小剧场章节，也可以在考研 App 兑换一次抖音时间。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onAdd) { Text("添加") }
        }
    }
}

@Composable
internal fun StarWishImageLaunchRow(launch: StarWishImageLaunch, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.86f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = StarWishColors.mistBlue,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = HugeIcons.Image03,
                        contentDescription = null,
                        tint = StarWishColors.inkBlue,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(launch.outfit, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("已发起双图生成 · ${launch.createdAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(HugeIcons.Delete01, contentDescription = "删除")
            }
        }
    }
}

@Composable
internal fun StarWishGeneratedImageRow(image: StarWishGeneratedImage, onDelete: () -> Unit) {
    var showPreview by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.86f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = File(image.filePath),
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .aspectRatio(1f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .clickable { showPreview = true },
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(image.outfit, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (image.fromStarWish) "已同步到画卷 · ${image.createdAt}" else "来自生成图库 · ${image.createdAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(image.prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDelete) {
                Icon(HugeIcons.Delete01, contentDescription = "删除")
            }
        }
    }
    if (showPreview) {
        ImagePreviewDialog(
            images = listOf(image.filePath),
            onDismissRequest = { showPreview = false },
        )
    }
}

@Composable
internal fun StarWishEmptyCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.78f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = StarWishColors.mistBlue,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = StarWishColors.inkBlue, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ScrollDetailDialog(
    scroll: StarWishScroll,
    outfit: String,
    prompts: StarWishOutfitPrompts,
    launches: List<StarWishImageLaunch>,
    onDismiss: () -> Unit,
    onSave: (StarWishOutfitPrompts) -> Unit,
    onCopy: (String) -> Unit,
    onGenerate: (String, Boolean) -> Unit,
) {
    var solo by remember(outfit) { mutableStateOf(prompts.solo) }
    var interaction by remember(outfit) { mutableStateOf(prompts.interaction) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onGenerate(interaction, true) }) {
                    Icon(HugeIcons.Image03, null)
                    Spacer(Modifier.width(6.dp))
                    Text("生成互动")
                }
                Button(onClick = { onGenerate(solo, false) }) {
                    Icon(HugeIcons.AiMagic, null)
                    Spacer(Modifier.width(6.dp))
                    Text("生成独美")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("收起") }
        },
        title = { Text(scroll.title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(520.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { onCopy(solo) }, label = { Text("复制独美") }, leadingIcon = { Icon(HugeIcons.PencilEdit01, null) })
                        AssistChip(onClick = { onCopy(interaction) }, label = { Text("复制互动") }, leadingIcon = { Icon(HugeIcons.Image03, null) })
                    }
                }
                item {
                    OutlinedTextField(
                        value = solo,
                        onValueChange = {
                            solo = it
                            onSave(StarWishOutfitPrompts(solo, interaction))
                        },
                        label = { Text("独美版提示词") },
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = interaction,
                        onValueChange = {
                            interaction = it
                            onSave(StarWishOutfitPrompts(solo, interaction))
                        },
                        label = { Text("互动版提示词") },
                        minLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text("图片记录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (launches.isEmpty()) {
                        Text("还没有从星愿馆发起过生成。点生成独美或生成互动后，会跳到生图页并预填单条提示词。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        launches.take(5).forEach {
                            Text("· ${it.outfit} · ${it.createdAt}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
    )
}
