package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.ui.CardGroup

@Composable
internal fun DisplayCodeSection(
    displaySetting: DisplaySetting,
    onUpdate: (DisplaySetting) -> Unit,
) {
    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text(stringResource(R.string.setting_page_code_display_settings)) },
    ) {
        item(
            headlineContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_wrap_title)) },
            supportingContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_wrap_desc)) },
            trailingContent = {
                Switch(
                    checked = displaySetting.codeBlockAutoWrap,
                    onCheckedChange = { onUpdate(displaySetting.copy(codeBlockAutoWrap = it)) },
                )
            },
        )
        item(
            headlineContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_collapse_title)) },
            supportingContent = { Text(stringResource(R.string.setting_display_page_code_block_auto_collapse_desc)) },
            trailingContent = {
                Switch(
                    checked = displaySetting.codeBlockAutoCollapse,
                    onCheckedChange = { onUpdate(displaySetting.copy(codeBlockAutoCollapse = it)) },
                )
            },
        )
        item(
            headlineContent = { Text(stringResource(R.string.setting_display_page_show_line_numbers_title)) },
            supportingContent = { Text(stringResource(R.string.setting_display_page_show_line_numbers_desc)) },
            trailingContent = {
                Switch(
                    checked = displaySetting.showLineNumbers,
                    onCheckedChange = { onUpdate(displaySetting.copy(showLineNumbers = it)) },
                )
            },
        )
    }
}

@Composable
internal fun DisplayInteractionSection(
    displaySetting: DisplaySetting,
    onUpdate: (DisplaySetting) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CardGroup(
            modifier = Modifier.padding(horizontal = 8.dp),
            title = { Text(stringResource(R.string.setting_page_interaction_notification_settings)) },
        ) {
            item(
                headlineContent = { Text(stringResource(R.string.setting_display_page_send_on_enter_title)) },
                supportingContent = { Text(stringResource(R.string.setting_display_page_send_on_enter_desc)) },
                trailingContent = {
                    Switch(
                        checked = displaySetting.sendOnEnter,
                        onCheckedChange = { onUpdate(displaySetting.copy(sendOnEnter = it)) },
                    )
                },
            )
            item(
                headlineContent = { Text(stringResource(R.string.setting_display_page_show_message_jumper_title)) },
                supportingContent = { Text(stringResource(R.string.setting_display_page_show_message_jumper_desc)) },
                trailingContent = {
                    Switch(
                        checked = displaySetting.showMessageJumper,
                        onCheckedChange = { onUpdate(displaySetting.copy(showMessageJumper = it)) },
                    )
                },
            )
            if (displaySetting.showMessageJumper) {
                item(
                    headlineContent = { Text(stringResource(R.string.setting_display_page_message_jumper_position_title)) },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_message_jumper_position_desc)) },
                    trailingContent = {
                        Switch(
                            checked = displaySetting.messageJumperOnLeft,
                            onCheckedChange = { onUpdate(displaySetting.copy(messageJumperOnLeft = it)) },
                        )
                    },
                )
            }
            item(
                headlineContent = { Text(stringResource(R.string.setting_display_page_enable_auto_scroll_title)) },
                supportingContent = { Text(stringResource(R.string.setting_display_page_enable_auto_scroll_desc)) },
                trailingContent = {
                    Switch(
                        checked = displaySetting.enableAutoScroll,
                        onCheckedChange = { onUpdate(displaySetting.copy(enableAutoScroll = it)) },
                    )
                },
            )
            item(
                headlineContent = { Text(stringResource(R.string.setting_display_page_use_app_icon_style_loading_indicator_title)) },
                supportingContent = { Text(stringResource(R.string.setting_display_page_use_app_icon_style_loading_indicator_desc)) },
                trailingContent = {
                    Switch(
                        checked = displaySetting.useAppIconStyleLoadingIndicator,
                        onCheckedChange = { onUpdate(displaySetting.copy(useAppIconStyleLoadingIndicator = it)) },
                    )
                },
            )
            item(
                headlineContent = { Text(stringResource(R.string.setting_display_page_enable_blur_effect_title)) },
                supportingContent = { Text(stringResource(R.string.setting_display_page_enable_blur_effect_desc)) },
                trailingContent = {
                    Switch(
                        checked = displaySetting.enableBlurEffect,
                        onCheckedChange = { onUpdate(displaySetting.copy(enableBlurEffect = it)) },
                    )
                },
            )
            item(
                headlineContent = { Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_title)) },
                supportingContent = { Text(stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_desc)) },
                trailingContent = {
                    Switch(
                        checked = displaySetting.enableMessageGenerationHapticEffect,
                        onCheckedChange = { onUpdate(displaySetting.copy(enableMessageGenerationHapticEffect = it)) },
                    )
                },
            )
            item(
                headlineContent = { Text(stringResource(R.string.setting_display_page_skip_crop_image_title)) },
                supportingContent = { Text(stringResource(R.string.setting_display_page_skip_crop_image_desc)) },
                trailingContent = {
                    Switch(
                        checked = displaySetting.skipCropImage,
                        onCheckedChange = { onUpdate(displaySetting.copy(skipCropImage = it)) },
                    )
                },
            )
            item(
                headlineContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_as_file_title)) },
                supportingContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_as_file_desc)) },
                trailingContent = {
                    Switch(
                        checked = displaySetting.pasteLongTextAsFile,
                        onCheckedChange = { onUpdate(displaySetting.copy(pasteLongTextAsFile = it)) },
                    )
                },
            )
            if (displaySetting.pasteLongTextAsFile) {
                item(
                    headlineContent = { Text(stringResource(R.string.setting_display_page_paste_long_text_threshold_title)) },
                    supportingContent = {
                        DisplayBehaviorSlider(
                            value = displaySetting.pasteLongTextThreshold.toFloat(),
                            onValueChange = { onUpdate(displaySetting.copy(pasteLongTextThreshold = it.toInt())) },
                            valueRange = 100f..10000f,
                            steps = 98,
                            label = displaySetting.pasteLongTextThreshold.toString(),
                        )
                    },
                )
            }
            item(
                headlineContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_title)) },
                supportingContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_desc)) },
                trailingContent = {
                    Switch(
                        checked = displaySetting.enableVolumeKeyScroll,
                        onCheckedChange = { onUpdate(displaySetting.copy(enableVolumeKeyScroll = it)) },
                    )
                },
            )
            if (displaySetting.enableVolumeKeyScroll) {
                item(
                    headlineContent = { Text(stringResource(R.string.setting_display_page_volume_key_scroll_ratio)) },
                    supportingContent = {
                        DisplayBehaviorSlider(
                            value = displaySetting.volumeKeyScrollRatio,
                            onValueChange = { onUpdate(displaySetting.copy(volumeKeyScrollRatio = it)) },
                            valueRange = 0.25f..1.0f,
                            steps = 2,
                            label = "${(displaySetting.volumeKeyScrollRatio * 100).toInt()}%",
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun DisplayTtsSection(
    displaySetting: DisplaySetting,
    onUpdate: (DisplaySetting) -> Unit,
) {
    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text(stringResource(R.string.setting_page_tts_settings)) },
    ) {
        item(
            headlineContent = { Text(stringResource(R.string.setting_display_page_tts_only_read_quoted_title)) },
            supportingContent = { Text(stringResource(R.string.setting_display_page_tts_only_read_quoted_desc)) },
            trailingContent = {
                Switch(
                    checked = displaySetting.ttsOnlyReadQuoted,
                    onCheckedChange = { onUpdate(displaySetting.copy(ttsOnlyReadQuoted = it)) },
                )
            },
        )
        item(
            headlineContent = { Text(stringResource(R.string.setting_display_page_auto_play_tts_title)) },
            supportingContent = { Text(stringResource(R.string.setting_display_page_auto_play_tts_desc)) },
            trailingContent = {
                Switch(
                    checked = displaySetting.autoPlayTTSAfterGeneration,
                    onCheckedChange = { onUpdate(displaySetting.copy(autoPlayTTSAfterGeneration = it)) },
                )
            },
        )
    }
}

@Composable
private fun DisplayBehaviorSlider(
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
