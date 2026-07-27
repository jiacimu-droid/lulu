package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantInteractionProfile
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.theme.CustomColors

@Composable
internal fun AssistantInteractionSection(
    assistant: Assistant,
    generationState: InteractionGenerationState,
    onUpdate: (Assistant) -> Unit,
    onGenerate: () -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column {
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text("互动") },
                description = {
                    Text("这些规则和人设一起决定角色怎样聊天、是否主动联系、会不会分享、追问和承担照看。可以由 API 根据人设生成，也可以全部自己填写。")
                },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onGenerate,
                        enabled = generationState !is InteractionGenerationState.Running && assistant.systemPrompt.isNotBlank(),
                    ) {
                        Text(
                            when {
                                generationState is InteractionGenerationState.Running -> "生成中…"
                                assistant.interactionProfile.isEmptyForUi() -> "开始生成"
                                else -> "根据人设重新生成"
                            }
                        )
                    }
                    when (generationState) {
                        InteractionGenerationState.Success -> Text(
                            "已根据当前人设生成，你仍可以继续手动修改。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        is InteractionGenerationState.Failed -> Text(
                            generationState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> Unit
                    }
                    if (assistant.systemPrompt.isBlank()) {
                        Text(
                            "先填写上方的角色资料 / 系统人设，才能生成互动设定。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            InteractionField(
                label = "主动意愿",
                description = "角色会不会主动找你、因为什么开口、什么情况下选择不主动。",
                value = assistant.interactionProfile.initiative,
                onValueChange = { value ->
                    onUpdate(assistant.withInteractionProfile { copy(initiative = value) })
                },
            )
            InteractionField(
                label = "分享欲",
                description = "角色会不会主动分享自己的想法、发现、经历和日常，以及怎样分享。",
                value = assistant.interactionProfile.sharingDesire,
                onValueChange = { value ->
                    onUpdate(assistant.withInteractionProfile { copy(sharingDesire = value) })
                },
            )
            InteractionField(
                label = "责任感",
                description = "角色怎样对待承诺、照看、监督、提醒和用户状态。",
                value = assistant.interactionProfile.responsibility,
                onValueChange = { value ->
                    onUpdate(assistant.withInteractionProfile { copy(responsibility = value) })
                },
            )
            InteractionField(
                label = "追问",
                description = "用户没回复、回答含糊或事情没完成时，是否追问、怎样追问、何时停止。",
                value = assistant.interactionProfile.followUpStyle,
                onValueChange = { value ->
                    onUpdate(assistant.withInteractionProfile { copy(followUpStyle = value) })
                },
            )
            InteractionField(
                label = "被动",
                description = "角色在哪些情形会等用户先开口、保持沉默，或把关心藏起来。",
                value = assistant.interactionProfile.passivity,
                onValueChange = { value ->
                    onUpdate(assistant.withInteractionProfile { copy(passivity = value) })
                },
                showDivider = false,
            )
        }
    }
}

@Composable
private fun InteractionField(
    label: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    showDivider: Boolean = true,
) {
    if (showDivider) HorizontalDivider()
    FormItem(
        modifier = Modifier.padding(8.dp),
        label = { Text(label) },
        description = { Text(description) },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
        )
    }
}

private fun Assistant.withInteractionProfile(
    update: AssistantInteractionProfile.() -> AssistantInteractionProfile,
): Assistant = copy(interactionProfile = interactionProfile.update())

private fun AssistantInteractionProfile.isEmptyForUi(): Boolean =
    initiative.isBlank() &&
        sharingDesire.isBlank() &&
        responsibility.isBlank() &&
        followUpStyle.isBlank() &&
        passivity.isBlank()
