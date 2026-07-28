package me.rerere.rikkahub.ui.pages.setting

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionNotification
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.hooks.rememberSharedPreferenceBoolean
import me.rerere.rikkahub.ui.pages.setting.components.PresetThemeButtonGroup
import me.rerere.rikkahub.ui.theme.CustomColors

@Composable
internal fun DisplayThemeSection(
    dynamicColor: Boolean,
    themeId: String,
    amoledDarkMode: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    onThemeChange: (String) -> Unit,
    onAmoledDarkModeChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.setting_page_theme_setting),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        )
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 4.dp,
                    ),
                ),
            headlineContent = { Text(stringResource(R.string.setting_page_dynamic_color)) },
            supportingContent = { Text(stringResource(R.string.setting_page_dynamic_color_desc)) },
            trailingContent = {
                Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
            },
            colors = CustomColors.listItemColors,
        )
        if (!dynamicColor) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceBright),
            ) {
                PresetThemeButtonGroup(
                    themeId = themeId,
                    modifier = Modifier.fillMaxWidth(),
                    onChangeTheme = onThemeChange,
                )
            }
        }
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 4.dp,
                        bottomStart = 20.dp,
                        bottomEnd = 20.dp,
                    ),
                ),
            headlineContent = { Text(stringResource(R.string.setting_display_page_amoled_dark_mode_title)) },
            supportingContent = { Text(stringResource(R.string.setting_display_page_amoled_dark_mode_desc)) },
            trailingContent = {
                Switch(checked = amoledDarkMode, onCheckedChange = onAmoledDarkModeChange)
            },
            colors = CustomColors.listItemColors,
        )
    }
}

@Composable
internal fun DisplayGeneralSection(
    displaySetting: DisplaySetting,
    onUpdate: (DisplaySetting) -> Unit,
) {
    var createNewConversationOnStart by rememberSharedPreferenceBoolean(
        "create_new_conversation_on_start",
        true,
    )
    val permissionState = rememberPermissionState(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setOf(PermissionNotification)
        } else {
            emptySet()
        },
    )
    PermissionManager(permissionState = permissionState)

    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text(stringResource(R.string.setting_page_general_settings)) },
    ) {
        item(
            headlineContent = { Text(stringResource(R.string.setting_display_page_create_new_conversation_on_start_title)) },
            supportingContent = { Text(stringResource(R.string.setting_display_page_create_new_conversation_on_start_desc)) },
            trailingContent = {
                Switch(
                    checked = createNewConversationOnStart,
                    onCheckedChange = { createNewConversationOnStart = it },
                )
            },
        )
        item(
            headlineContent = { Text(stringResource(R.string.setting_display_page_show_updates_title)) },
            supportingContent = { Text(stringResource(R.string.setting_display_page_show_updates_desc)) },
            trailingContent = {
                Switch(
                    checked = displaySetting.showUpdates,
                    onCheckedChange = { onUpdate(displaySetting.copy(showUpdates = it)) },
                )
            },
        )
        item(
            headlineContent = { Text(stringResource(R.string.setting_display_page_notification_message_generated)) },
            supportingContent = { Text(stringResource(R.string.setting_display_page_notification_message_generated_desc)) },
            trailingContent = {
                Switch(
                    checked = displaySetting.enableNotificationOnMessageGeneration,
                    onCheckedChange = {
                        if (it && !permissionState.allPermissionsGranted) {
                            permissionState.requestPermissions()
                        }
                        onUpdate(displaySetting.copy(enableNotificationOnMessageGeneration = it))
                    },
                )
            },
        )
        if (displaySetting.enableNotificationOnMessageGeneration) {
            item(
                headlineContent = { Text(stringResource(R.string.setting_display_page_live_update_notification)) },
                supportingContent = { Text(stringResource(R.string.setting_display_page_live_update_notification_desc)) },
                trailingContent = {
                    Switch(
                        checked = displaySetting.enableLiveUpdateNotification,
                        onCheckedChange = { onUpdate(displaySetting.copy(enableLiveUpdateNotification = it)) },
                    )
                },
            )
        }
    }
}

@Composable
internal fun DisplayProfileSection(
    displaySetting: DisplaySetting,
    onUpdate: (DisplaySetting) -> Unit,
) {
    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text("我的资料") },
    ) {
        item(
            headlineContent = { Text("我的昵称") },
            supportingContent = {
                OutlinedTextField(
                    value = displaySetting.userNickname,
                    onValueChange = { onUpdate(displaySetting.copy(userNickname = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
        )
        item(
            headlineContent = { Text("我的个人资料") },
            supportingContent = {
                OutlinedTextField(
                    value = displaySetting.userProfile,
                    onValueChange = { onUpdate(displaySetting.copy(userProfile = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                )
            },
        )
        item(
            headlineContent = { Text("我的外貌 / 互动生图参考") },
            supportingContent = {
                OutlinedTextField(
                    value = displaySetting.userAppearancePrompt,
                    onValueChange = { onUpdate(displaySetting.copy(userAppearancePrompt = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                )
            },
        )
    }
}

@Composable
internal fun DisplayMessageSection(
    displaySetting: DisplaySetting,
    onUpdate: (DisplaySetting) -> Unit,
) {
    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text(stringResource(R.string.setting_page_message_display_settings)) },
    ) {
        DisplayBooleanRow(
            title = stringResource(R.string.setting_display_page_show_user_avatar_title),
            description = stringResource(R.string.setting_display_page_show_user_avatar_desc),
            checked = displaySetting.showUserAvatar,
            onChecked = { onUpdate(displaySetting.copy(showUserAvatar = it)) },
        )
        DisplayBooleanRow(
            title = stringResource(R.string.setting_display_page_show_assistant_bubble_title),
            description = stringResource(R.string.setting_display_page_show_assistant_bubble_desc),
            checked = displaySetting.showAssistantBubble,
            onChecked = { onUpdate(displaySetting.copy(showAssistantBubble = it)) },
        )
        DisplayBooleanRow(
            title = stringResource(R.string.setting_display_page_chat_list_model_icon_title),
            description = stringResource(R.string.setting_display_page_chat_list_model_icon_desc),
            checked = displaySetting.showModelIcon,
            onChecked = { onUpdate(displaySetting.copy(showModelIcon = it)) },
        )
        DisplayBooleanRow(
            title = stringResource(R.string.setting_display_page_show_model_name_title),
            description = stringResource(R.string.setting_display_page_show_model_name_desc),
            checked = displaySetting.showModelName,
            onChecked = { onUpdate(displaySetting.copy(showModelName = it)) },
        )
        DisplayBooleanRow(
            title = stringResource(R.string.setting_display_page_show_date_below_name_title),
            description = stringResource(R.string.setting_display_page_show_date_below_name_desc),
            checked = displaySetting.showDateBelowName,
            onChecked = { onUpdate(displaySetting.copy(showDateBelowName = it)) },
        )
        DisplayBooleanRow(
            title = stringResource(R.string.setting_display_page_show_token_usage_title),
            description = stringResource(R.string.setting_display_page_show_token_usage_desc),
            checked = displaySetting.showTokenUsage,
            onChecked = { onUpdate(displaySetting.copy(showTokenUsage = it)) },
        )
        DisplayBooleanRow(
            title = stringResource(R.string.setting_display_page_show_thinking_content_title),
            description = stringResource(R.string.setting_display_page_show_thinking_content_desc),
            checked = displaySetting.showThinkingContent,
            onChecked = { onUpdate(displaySetting.copy(showThinkingContent = it)) },
        )
        DisplayBooleanRow(
            title = stringResource(R.string.setting_display_page_auto_collapse_thinking_title),
            description = stringResource(R.string.setting_display_page_auto_collapse_thinking_desc),
            checked = displaySetting.autoCloseThinking,
            onChecked = { onUpdate(displaySetting.copy(autoCloseThinking = it)) },
        )
        DisplayBooleanRow(
            title = stringResource(R.string.setting_display_page_enable_latex_rendering_title),
            description = stringResource(R.string.setting_display_page_enable_latex_rendering_desc),
            checked = displaySetting.enableLatexRendering,
            onChecked = { onUpdate(displaySetting.copy(enableLatexRendering = it)) },
        )

        val chatFontFamilyOptions = listOf(
            ChatFontFamily.DEFAULT to stringResource(R.string.setting_display_page_chat_font_family_default),
            ChatFontFamily.SERIF to stringResource(R.string.setting_display_page_chat_font_family_serif),
            ChatFontFamily.MONOSPACE to stringResource(R.string.setting_display_page_chat_font_family_monospace),
        )
        item(
            headlineContent = { Text(stringResource(R.string.setting_display_page_chat_font_family_title)) },
            supportingContent = {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                ) {
                    chatFontFamilyOptions.forEachIndexed { index, (family, label) ->
                        SegmentedButton(
                            selected = displaySetting.chatFontFamily == family,
                            onClick = { onUpdate(displaySetting.copy(chatFontFamily = family)) },
                            shape = SegmentedButtonDefaults.itemShape(index, chatFontFamilyOptions.size),
                        ) {
                            Text(
                                text = label,
                                fontFamily = when (family) {
                                    ChatFontFamily.DEFAULT -> FontFamily.Default
                                    ChatFontFamily.SERIF -> FontFamily.Serif
                                    ChatFontFamily.MONOSPACE -> FontFamily.Monospace
                                    ChatFontFamily.CUSTOM -> FontFamily.Default
                                },
                            )
                        }
                    }
                }
            },
        )
        item(
            headlineContent = { Text(stringResource(R.string.setting_display_page_font_size_title)) },
            supportingContent = {
                Column {
                    DisplaySettingSlider(
                        value = displaySetting.fontSizeRatio,
                        onValueChange = { onUpdate(displaySetting.copy(fontSizeRatio = it)) },
                        valueRange = 0.5f..2f,
                        steps = 11,
                        label = "${(displaySetting.fontSizeRatio * 100).toInt()}%",
                    )
                    MarkdownBlock(
                        content = stringResource(R.string.setting_display_page_font_size_preview),
                        style = LocalTextStyle.current.copy(
                            fontSize = LocalTextStyle.current.fontSize * displaySetting.fontSizeRatio,
                            lineHeight = LocalTextStyle.current.lineHeight * displaySetting.fontSizeRatio,
                            fontFamily = when (displaySetting.chatFontFamily) {
                                ChatFontFamily.DEFAULT -> FontFamily.Default
                                ChatFontFamily.SERIF -> FontFamily.Serif
                                ChatFontFamily.MONOSPACE -> FontFamily.Monospace
                                ChatFontFamily.CUSTOM -> FontFamily.Default
                            },
                        ),
                    )
                }
            },
        )
        item(
            headlineContent = { Text("聊天气泡透明度") },
            supportingContent = {
                DisplaySettingSlider(
                    value = displaySetting.chatBubbleTransparency,
                    onValueChange = { onUpdate(displaySetting.copy(chatBubbleTransparency = it)) },
                    valueRange = 0f..100f,
                    steps = 19,
                    label = "${displaySetting.chatBubbleTransparency.toInt()}%",
                )
            },
        )
        item(
            headlineContent = { Text("思维链透明度") },
            supportingContent = {
                DisplaySettingSlider(
                    value = displaySetting.thinkingChainTransparency,
                    onValueChange = { onUpdate(displaySetting.copy(thinkingChainTransparency = it)) },
                    valueRange = 0f..100f,
                    steps = 19,
                    label = "${displaySetting.thinkingChainTransparency.toInt()}%",
                )
            },
        )
        item(
            headlineContent = { Text("思维链字体大小") },
            supportingContent = {
                DisplaySettingSlider(
                    value = displaySetting.thinkingFontSizeRatio,
                    onValueChange = { onUpdate(displaySetting.copy(thinkingFontSizeRatio = it)) },
                    valueRange = 0.5f..2f,
                    steps = 5,
                    label = "${(displaySetting.thinkingFontSizeRatio * 100).toInt()}%",
                )
            },
        )
    }
}

private fun me.rerere.rikkahub.ui.components.ui.CardGroupScope.DisplayBooleanRow(
    title: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    item(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChecked) },
    )
}

@Composable
private fun DisplaySettingSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
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
            steps = steps,
            modifier = Modifier.weight(1f),
        )
        Text(label)
    }
}
