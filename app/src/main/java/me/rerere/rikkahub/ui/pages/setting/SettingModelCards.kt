package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.pages.assistant.detail.ReasoningButton
import me.rerere.rikkahub.ui.theme.CustomColors
import kotlin.uuid.Uuid

@Composable
internal fun ModelFeatureCard(
    modifier: Modifier = Modifier,
    description: @Composable () -> Unit = {},
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = CustomColors.listItemColors.containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    ProvideTextStyle(MaterialTheme.typography.titleMedium) { title() }
                    ProvideTextStyle(
                        MaterialTheme.typography.bodySmall.copy(
                            color = LocalContentColor.current.copy(alpha = 0.6f),
                        ),
                    ) { description() }
                }
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) { icon() }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
internal fun SimpleModelFeature(
    title: String,
    description: String,
    icon: ImageVector,
    modelId: Uuid?,
    type: ModelType,
    settings: Settings,
    allowClear: Boolean = false,
    onSelect: (Uuid) -> Unit,
) {
    ModelFeatureCard(
        title = { Text(title, maxLines = 1) },
        description = { Text(description) },
        icon = { Icon(icon, contentDescription = null) },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = modelId,
                    type = type,
                    onSelect = { onSelect(it.id) },
                    providers = settings.providers,
                    allowClear = allowClear,
                )
            }
        },
    )
}

@Composable
internal fun PromptModelFeature(
    title: String,
    description: String,
    icon: ImageVector,
    modelId: Uuid?,
    type: ModelType,
    settings: Settings,
    allowClear: Boolean,
    prompt: String,
    promptDescription: String,
    defaultPrompt: String,
    onSelect: (Uuid) -> Unit,
    onPromptChange: (String) -> Unit,
    thinkingBudget: Int? = null,
    onThinkingBudgetChange: ((Int) -> Unit)? = null,
) {
    var showModal by remember { mutableStateOf(false) }
    ModelFeatureCard(
        title = { Text(title, maxLines = 1) },
        description = { Text(description) },
        icon = { Icon(icon, contentDescription = null) },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = modelId,
                    type = type,
                    onSelect = { onSelect(it.id) },
                    providers = settings.providers,
                    allowClear = allowClear,
                )
            }
            IconButton(
                onClick = { showModal = true },
                colors = IconButtonDefaults.filledTonalIconButtonColors(),
            ) {
                Icon(HugeIcons.Tools, contentDescription = null)
            }
        },
    )
    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = { showModal = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (thinkingBudget != null && onThinkingBudgetChange != null) {
                    FormItem(label = { Text(stringResource(R.string.assistant_page_thinking_budget)) }) {
                        ReasoningButton(
                            reasoningLevel = ReasoningLevel.fromBudgetTokens(thinkingBudget),
                            onUpdateReasoningLevel = { onThinkingBudgetChange(it.budgetTokens) },
                        )
                    }
                }
                FormItem(
                    label = { Text(stringResource(R.string.setting_model_page_prompt)) },
                    description = { Text(promptDescription) },
                ) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = onPromptChange,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 10,
                    )
                    TextButton(onClick = { onPromptChange(defaultPrompt) }) {
                        Text(stringResource(R.string.setting_model_page_reset_to_default))
                    }
                }
            }
        }
    }
}
