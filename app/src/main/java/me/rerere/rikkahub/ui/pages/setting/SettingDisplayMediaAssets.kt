package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.io.File
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.ui.CardGroup

@Composable
internal fun DisplayMediaAssetsSection(
    displaySetting: DisplaySetting,
    onUpdate: (DisplaySetting) -> Unit,
) {
    val context = LocalContext.current
    val fontDir = remember { File(context.filesDir, "custom_fonts").apply { mkdirs() } }
    val bgDir = remember { File(context.filesDir, "input_backgrounds").apply { mkdirs() } }
    val drawerBgDir = remember { File(context.filesDir, "drawer_backgrounds").apply { mkdirs() } }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            val destFile = File(fontDir, "custom_font_${System.currentTimeMillis()}.ttf")
            context.contentResolver.openInputStream(it)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            onUpdate(
                displaySetting.copy(
                    chatFontFamily = ChatFontFamily.CUSTOM,
                    customFontPath = destFile.absolutePath,
                ),
            )
        }
    }

    val bgPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            val destFile = File(bgDir, "input_bg_${System.currentTimeMillis()}.png")
            context.contentResolver.openInputStream(it)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            onUpdate(displaySetting.copy(inputBackgroundPath = destFile.absolutePath))
        }
    }

    val drawerBgPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            val destFile = File(drawerBgDir, "drawer_bg_${System.currentTimeMillis()}.png")
            context.contentResolver.openInputStream(it)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            onUpdate(displaySetting.copy(drawerBackgroundPath = destFile.absolutePath))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CardGroup(
            modifier = Modifier.padding(horizontal = 8.dp),
            title = { Text("自定义字体") },
        ) {
            item(
                headlineContent = { Text("导入自定义字体") },
                supportingContent = {
                    Text(
                        if (displaySetting.customFontPath.isNotBlank() && File(displaySetting.customFontPath).exists()) {
                            "当前字体: ${File(displaySetting.customFontPath).name}"
                        } else {
                            "支持 .ttf / .otf 字体文件"
                        },
                    )
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (displaySetting.customFontPath.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    File(displaySetting.customFontPath).delete()
                                    onUpdate(
                                        displaySetting.copy(
                                            customFontPath = "",
                                            chatFontFamily = ChatFontFamily.DEFAULT,
                                        ),
                                    )
                                },
                            ) { Text("清除") }
                        }
                        TextButton(onClick = { fontPickerLauncher.launch(arrayOf("*/*")) }) {
                            Text("选择字体")
                        }
                    }
                },
            )
            if (displaySetting.customFontPath.isNotBlank() && File(displaySetting.customFontPath).exists()) {
                item(
                    headlineContent = { Text("字体预览") },
                    supportingContent = {
                        val customFont = remember(displaySetting.customFontPath) {
                            runCatching { FontFamily(Font(File(displaySetting.customFontPath))) }
                                .getOrDefault(FontFamily.Default)
                        }
                        Text(
                            text = "The quick brown fox jumps over the lazy dog. 你好世界！1234567890",
                            fontFamily = customFont,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                )
            }
        }

        CardGroup(
            modifier = Modifier.padding(horizontal = 8.dp),
            title = { Text("输入框背景") },
        ) {
            item(
                headlineContent = { Text("自定义输入框背景图") },
                supportingContent = {
                    Text(
                        if (displaySetting.inputBackgroundPath.isNotBlank() && File(displaySetting.inputBackgroundPath).exists()) {
                            "当前背景: ${File(displaySetting.inputBackgroundPath).name}"
                        } else {
                            "选择一张图片作为输入框区域背景"
                        },
                    )
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (displaySetting.inputBackgroundPath.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    File(displaySetting.inputBackgroundPath).delete()
                                    onUpdate(displaySetting.copy(inputBackgroundPath = ""))
                                },
                            ) { Text("清除") }
                        }
                        TextButton(onClick = { bgPickerLauncher.launch(arrayOf("image/*")) }) {
                            Text("选择图片")
                        }
                    }
                },
            )
        }

        CardGroup(
            modifier = Modifier.padding(horizontal = 8.dp),
            title = { Text("侧边栏背景") },
        ) {
            item(
                headlineContent = { Text("自定义侧边栏背景图") },
                supportingContent = {
                    Text(
                        if (displaySetting.drawerBackgroundPath.isNotBlank() && File(displaySetting.drawerBackgroundPath).exists()) {
                            "当前背景: ${File(displaySetting.drawerBackgroundPath).name}"
                        } else {
                            "选择一张图片作为侧边栏背景"
                        },
                    )
                },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (displaySetting.drawerBackgroundPath.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    File(displaySetting.drawerBackgroundPath).delete()
                                    onUpdate(displaySetting.copy(drawerBackgroundPath = ""))
                                },
                            ) { Text("清除") }
                        }
                        TextButton(onClick = { drawerBgPickerLauncher.launch(arrayOf("image/*")) }) {
                            Text("选择图片")
                        }
                    }
                },
            )
            item(
                headlineContent = { Text("侧边栏元素透明度") },
                supportingContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Slider(
                            value = displaySetting.drawerItemAlpha,
                            onValueChange = { onUpdate(displaySetting.copy(drawerItemAlpha = it)) },
                            valueRange = 0f..1f,
                            steps = 19,
                            modifier = Modifier.weight(1f),
                        )
                        Text(text = "${(displaySetting.drawerItemAlpha * 100).toInt()}%")
                    }
                },
            )
        }
    }
}
