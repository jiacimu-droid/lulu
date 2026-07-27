package me.rerere.rikkahub.ui.pages.starwish

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.CircleLock01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Play
import me.rerere.rikkahub.data.starwish.StarWishRules
import me.rerere.rikkahub.data.starwish.StarWishVideoItem
import me.rerere.rikkahub.utils.resolveAppVideoUri

@Composable
internal fun VideoRewardCard(
    epicFragments: Int,
    unlocked: Int,
    total: Int,
    onUnlock: () -> Unit,
    onUpload: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.84f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = Color(0xFFFFE7A8), modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(HugeIcons.Play, null, tint = Color(0xFF9B6B10), modifier = Modifier.size(24.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("视频收藏柜", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("视频碎片 $epicFragments 枚 · 已解锁 $unlocked/$total", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(color = StarWishColors.mistBlue.copy(alpha = 0.72f), shape = RoundedCornerShape(14.dp)) {
                Text(
                    "上传 AI 生成的视频后，它会先以灰色锁定状态进入收藏柜。每消耗 1 枚视频碎片，优先随机解锁一个未解锁视频并自动播放；全部解锁后会随机播放已解锁视频。",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = StarWishColors.inkBlue,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onUnlock,
                    enabled = total > 0 && (epicFragments >= StarWishRules.VIDEO_FRAGMENTS_PER_UNLOCK || unlocked >= total),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (unlocked >= total && total > 0) "随机播放" else "解锁下一个")
                }
                TextButton(
                    onClick = onUpload,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("上传视频")
                }
            }
        }
    }
}

@Composable
internal fun StarWishVideoRow(
    video: StarWishVideoItem,
    unlocked: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = if (unlocked) 0.88f else 0.58f)),
        modifier = Modifier.fillMaxWidth().clickable(enabled = unlocked, onClick = onPlay),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (unlocked) StarWishColors.mistBlue else StarWishColors.locked,
                modifier = Modifier.size(54.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (unlocked) HugeIcons.Play else HugeIcons.CircleLock01,
                        contentDescription = null,
                        tint = if (unlocked) StarWishColors.inkBlue else MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(video.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (unlocked) "已解锁 · 可反复播放" else "未解锁 · 使用视频碎片后点亮",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (unlocked) {
                IconButton(onClick = onPlay) {
                    Icon(HugeIcons.Play, contentDescription = "播放")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(HugeIcons.Delete01, contentDescription = "删除")
            }
        }
    }
}

@Composable
internal fun StarWishVideoPlayerDialog(
    video: StarWishVideoItem,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var videoView by remember(video.id) { mutableStateOf<VideoView?>(null) }
    DisposableEffect(video.id) {
        onDispose {
            videoView?.stopPlayback()
            videoView = null
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    VideoView(viewContext).apply {
                        videoView = this
                        val controller = MediaController(context)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoURI(resolveAppVideoUri(context, video.uri))
                        setOnPreparedListener { player ->
                            player.isLooping = true
                            start()
                        }
                    }
                },
                update = { view ->
                    if (videoView !== view) videoView = view
                },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 14.dp)
                    .background(Color.Black.copy(alpha = 0.42f), CircleShape),
            ) {
                Text("×", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
