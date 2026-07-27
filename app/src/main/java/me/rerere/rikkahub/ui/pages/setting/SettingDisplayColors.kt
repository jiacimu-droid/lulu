package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ColorPickerDialog
import me.rerere.rikkahub.ui.components.ui.toComposeColor

@Composable
internal fun DisplayColorSection(
    displaySetting: DisplaySetting,
    onUpdate: (DisplaySetting) -> Unit,
) {
    var picker by remember { mutableStateOf<DisplayColorTarget?>(null) }

    picker?.let { target ->
        ColorPickerDialog(
            initialColor = target.current(displaySetting),
            defaultColor = target.defaultColor(),
            onConfirm = {
                onUpdate(target.apply(displaySetting, it))
                picker = null
            },
            onDismiss = { picker = null },
        )
    }

    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text("颜色自定义") },
    ) {
        DisplayColorTarget.entries.forEach { target ->
            item(
                headlineContent = { Text(target.title) },
                supportingContent = if (target.description != null) {
                    { Text(target.description) }
                } else {
                    null
                },
                trailingContent = {
                    DisplayColorActions(
                        color = target.current(displaySetting)?.toComposeColor() ?: target.defaultColor(),
                        customized = target.current(displaySetting) != null,
                        onCustomize = { picker = target },
                        onReset = { onUpdate(target.apply(displaySetting, null)) },
                    )
                },
            )
        }
    }
}

@Composable
private fun DisplayColorActions(
    color: Color,
    customized: Boolean,
    onCustomize: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, CircleShape),
        )
        TextButton(onClick = onCustomize) { Text("自定义") }
        if (customized) {
            TextButton(onClick = onReset) { Text("重置") }
        }
    }
}

private enum class DisplayColorTarget(
    val title: String,
    val description: String? = null,
) {
    ChatText("聊天正文颜色"),
    GlobalText("全局字体颜色"),
    UserBubble("用户气泡颜色", "自定义用户消息气泡背景色"),
    AssistantBubble("AI气泡颜色", "自定义AI消息气泡背景色"),
    ThinkingBubble("思维链气泡颜色"),
    ChatBackground("聊天背景色", "有背景图时图片优先"),
    Primary("主色调（按钮/链接）"),
    InputField("输入框背景颜色", "有背景图时图片优先");

    @Composable
    fun defaultColor(): Color = when (this) {
        ChatText -> MaterialTheme.colorScheme.onSurface
        GlobalText -> MaterialTheme.colorScheme.onBackground
        UserBubble -> MaterialTheme.colorScheme.secondaryContainer
        AssistantBubble -> MaterialTheme.colorScheme.surfaceContainerHigh
        ThinkingBubble -> MaterialTheme.colorScheme.surfaceContainerHigh
        ChatBackground -> MaterialTheme.colorScheme.background
        Primary -> MaterialTheme.colorScheme.primary
        InputField -> MaterialTheme.colorScheme.surfaceContainerLowest
    }

    fun current(setting: DisplaySetting): Long? = when (this) {
        ChatText -> setting.chatTextColor
        GlobalText -> setting.globalTextColor
        UserBubble -> setting.userBubbleColor
        AssistantBubble -> setting.assistantBubbleColor
        ThinkingBubble -> setting.thinkingBubbleColor
        ChatBackground -> setting.chatBackgroundColor
        Primary -> setting.primaryColor
        InputField -> setting.inputFieldColor
    }

    fun apply(setting: DisplaySetting, value: Long?): DisplaySetting = when (this) {
        ChatText -> setting.copy(chatTextColor = value)
        GlobalText -> setting.copy(globalTextColor = value)
        UserBubble -> setting.copy(userBubbleColor = value)
        AssistantBubble -> setting.copy(assistantBubbleColor = value)
        ThinkingBubble -> setting.copy(thinkingBubbleColor = value)
        ChatBackground -> setting.copy(chatBackgroundColor = value)
        Primary -> setting.copy(primaryColor = value)
        InputField -> setting.copy(inputFieldColor = value)
    }
}
