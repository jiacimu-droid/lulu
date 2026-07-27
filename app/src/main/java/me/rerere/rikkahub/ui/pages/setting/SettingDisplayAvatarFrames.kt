package me.rerere.rikkahub.ui.pages.setting

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.ui.CardGroup

@Composable
internal fun DisplayAvatarFramesSection(
    displaySetting: DisplaySetting,
    onUpdate: (DisplaySetting) -> Unit,
) {
    val context = LocalContext.current
    val frameDir = remember { File(context.filesDir, "avatar_frames").apply { mkdirs() } }
    val userFramePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val destFile = File(frameDir, "user_frame_${System.currentTimeMillis()}.png")
            context.contentResolver.openInputStream(it)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            onUpdate(displaySetting.copy(userAvatarFramePath = destFile.absolutePath))
        }
    }
    val aiFramePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val destFile = File(frameDir, "ai_frame_${System.currentTimeMillis()}.png")
            context.contentResolver.openInputStream(it)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            onUpdate(displaySetting.copy(aiAvatarFramePath = destFile.absolutePath))
        }
    }

    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text("头像挂件") },
    ) {
        avatarFrameItems(
            label = "用户头像挂件",
            contentDescription = "用户头像挂件",
            path = displaySetting.userAvatarFramePath,
            offsetX = displaySetting.userAvatarFrameOffsetX,
            offsetY = displaySetting.userAvatarFrameOffsetY,
            scale = displaySetting.userAvatarFrameScale,
            onChoose = { userFramePicker.launch(arrayOf("image/*")) },
            onClear = {
                File(displaySetting.userAvatarFramePath).delete()
                onUpdate(displaySetting.copy(userAvatarFramePath = ""))
            },
            onOffsetX = { onUpdate(displaySetting.copy(userAvatarFrameOffsetX = it)) },
            onOffsetY = { onUpdate(displaySetting.copy(userAvatarFrameOffsetY = it)) },
            onScale = { onUpdate(displaySetting.copy(userAvatarFrameScale = it)) },
        )
        avatarFrameItems(
            label = "AI头像挂件",
            contentDescription = "AI头像挂件",
            path = displaySetting.aiAvatarFramePath,
            offsetX = displaySetting.aiAvatarFrameOffsetX,
            offsetY = displaySetting.aiAvatarFrameOffsetY,
            scale = displaySetting.aiAvatarFrameScale,
            onChoose = { aiFramePicker.launch(arrayOf("image/*")) },
            onClear = {
                File(displaySetting.aiAvatarFramePath).delete()
                onUpdate(displaySetting.copy(aiAvatarFramePath = ""))
            },
            onOffsetX = { onUpdate(displaySetting.copy(aiAvatarFrameOffsetX = it)) },
            onOffsetY = { onUpdate(displaySetting.copy(aiAvatarFrameOffsetY = it)) },
            onScale = { onUpdate(displaySetting.copy(aiAvatarFrameScale = it)) },
        )
    }
}

private fun me.rerere.rikkahub.ui.components.ui.CardGroupScope.avatarFrameItems(
    label: String,
    contentDescription: String,
    path: String,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    onChoose: () -> Unit,
    onClear: () -> Unit,
    onOffsetX: (Float) -> Unit,
    onOffsetY: (Float) -> Unit,
    onScale: (Float) -> Unit,
) {
    item(
        headlineContent = { Text(label) },
        supportingContent = {
            if (path.isBlank() || !File(path).exists()) {
                Text(if (label.startsWith("AI")) "选择一张图片作为AI头像装饰框" else "选择一张图片作为头像装饰框")
            }
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (path.isNotBlank()) {
                    TextButton(onClick = onClear) { Text("清除") }
                }
                TextButton(onClick = onChoose) { Text("选择") }
            }
        },
    )
    if (path.isNotBlank() && File(path).exists()) {
        val frameBitmap = BitmapFactory.decodeFile(path)
        if (frameBitmap != null) {
            item(
                headlineContent = { Text("预览") },
                supportingContent = {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = offsetX.dp, y = offsetY.dp)
                                .size((80 * scale).dp),
                        ) {
                            Image(
                                bitmap = frameBitmap.asImageBitmap(),
                                contentDescription = contentDescription,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                },
            )
            item(
                headlineContent = { Text("偏移 X") },
                supportingContent = {
                    AvatarFrameSlider(
                        value = offsetX,
                        onValueChange = onOffsetX,
                        valueRange = -100f..100f,
                        label = offsetX.toInt().toString(),
                    )
                },
            )
            item(
                headlineContent = { Text("偏移 Y") },
                supportingContent = {
                    AvatarFrameSlider(
                        value = offsetY,
                        onValueChange = onOffsetY,
                        valueRange = -100f..100f,
                        label = offsetY.toInt().toString(),
                    )
                },
            )
            item(
                headlineContent = { Text("缩放") },
                supportingContent = {
                    AvatarFrameSlider(
                        value = scale,
                        onValueChange = onScale,
                        valueRange = 0.5f..2f,
                        label = "${(scale * 100).toInt()}%",
                    )
                },
            )
        }
    }
}

@Composable
private fun AvatarFrameSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
        )
        Text(label)
    }
}
