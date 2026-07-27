package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.registry.ModelRegistry
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ai.ModelAbilityTag
import me.rerere.rikkahub.ui.components.ai.ModelModalityTag
import me.rerere.rikkahub.ui.components.ai.ModelTypeTag
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.hooks.EditState
import me.rerere.rikkahub.ui.hooks.useEditState

@Composable
internal fun SettingProviderModelPickerButton(
    models: List<Model>,
    selectedModels: List<Model>,
    onModelSelected: (Model) -> Unit,
    onModelDeselected: (Model) -> Unit,
    onAllModelSelected: (List<Model>) -> Unit,
    onAllModelDeselected: (List<Model>) -> Unit,
) {
    var showModal by remember { mutableStateOf(false) }
    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = { showModal = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            var filterText by remember { mutableStateOf("") }
            val filterKeywords = filterText.split(" ").filter { it.isNotBlank() }
            val filteredModels = models.fastFilter { model ->
                filterKeywords.isEmpty() || filterKeywords.all { keyword ->
                    model.modelId.contains(keyword, ignoreCase = true) ||
                        model.displayName.contains(keyword, ignoreCase = true)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(8.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_avaliable_models),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val unselectedCount = filteredModels.count { candidate ->
                        selectedModels.none { it.modelId == candidate.modelId }
                    }
                    TextButton(
                        onClick = {
                            if (unselectedCount > 0) onAllModelSelected(filteredModels)
                            else onAllModelDeselected(filteredModels)
                        },
                    ) {
                        Text(
                            if (unselectedCount > 0) {
                                stringResource(R.string.setting_provider_page_select_all, unselectedCount)
                            } else {
                                stringResource(R.string.setting_provider_page_deselect_models)
                            },
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    items(filteredModels) { model ->
                        Card {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                            ) {
                                AutoAIIcon(model.modelId, Modifier.size(32.dp))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(model.modelId, style = MaterialTheme.typography.titleSmall)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        val modelMeta = remember(model) {
                                            model.copy(
                                                inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(model.modelId),
                                                outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(model.modelId),
                                                abilities = ModelRegistry.MODEL_ABILITIES.getData(model.modelId),
                                            )
                                        }
                                        ModelModalityTag(model = modelMeta)
                                        ModelAbilityTag(model = modelMeta)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val selected = selectedModels.firstOrNull { it.modelId == model.modelId }
                                        if (selected != null) onModelDeselected(selected) else onModelSelected(model)
                                    },
                                ) {
                                    Icon(
                                        if (selectedModels.any { it.modelId == model.modelId }) HugeIcons.Cancel01
                                        else HugeIcons.Add01,
                                        null,
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    label = { Text(stringResource(R.string.setting_provider_page_filter_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.setting_provider_page_filter_example)) },
                )
            }
        }
    }
    BadgedBox(
        badge = {
            if (models.isNotEmpty()) {
                Badge { Text(models.size.toString()) }
            }
        },
    ) {
        IconButton(onClick = { showModal = true }) {
            Icon(HugeIcons.Package01, null)
        }
    }
}

@Composable
internal fun SettingProviderModelCard(
    model: Model,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onEdit: (Model) -> Unit,
    parentProvider: ProviderSetting,
) {
    val dialogState = useEditState<Model> { onEdit(it) }
    val swipeState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()

    SettingProviderModelEditSheet(
        dialogState = dialogState,
        parentProvider = parentProvider,
        title = stringResource(R.string.setting_provider_page_edit_model),
        confirmText = stringResource(R.string.confirm),
        isEdit = true,
        canConfirm = { it.displayName.isNotBlank() },
    )

    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = {
            Row(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { scope.launch { swipeState.reset() } }) {
                    Icon(HugeIcons.Cancel01, null)
                }
                FilledIconButton(
                    onClick = {
                        scope.launch {
                            onDelete()
                            swipeState.reset()
                        }
                    },
                ) {
                    Icon(HugeIcons.Delete01, contentDescription = stringResource(R.string.chat_page_delete))
                }
            }
        },
        enableDismissFromStartToEnd = false,
        gesturesEnabled = true,
        modifier = modifier,
    ) {
        OutlinedCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    AutoAIIcon(name = model.modelId, modifier = Modifier.size(36.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (model.providerOverwrite != null) {
                            Tag(type = TagType.INFO) {
                                Text(
                                    model.providerOverwrite?.javaClass?.simpleName
                                        ?: model.providerOverwrite?.name
                                        ?: "ProviderOverwrite",
                                )
                            }
                        }
                        ModelTypeTag(model = model)
                        ModelModalityTag(model = model)
                        ModelAbilityTag(model = model)
                    }
                }
                IconButton(onClick = { dialogState.open(model.copy()) }) {
                    Icon(HugeIcons.Tools, "Edit")
                }
            }
        }
    }
}

@Composable
internal fun SettingProviderModelEditSheet(
    dialogState: EditState<Model>,
    parentProvider: ProviderSetting,
    title: String,
    confirmText: String,
    isEdit: Boolean,
    canConfirm: (Model) -> Boolean,
) {
    if (!dialogState.isEditing) return
    val modelState = dialogState.currentState ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = { dialogState.dismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = if (isEdit) {
            null
        } else {
            {
                IconButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            dialogState.dismiss()
                        }
                    },
                ) { Icon(HugeIcons.Cancel01, null) }
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isEdit) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                dialogState.dismiss()
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) { Icon(HugeIcons.Cancel01, null) }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Center))
                }
            } else {
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                SettingProviderModelForm(
                    model = modelState,
                    onModelChange = { dialogState.currentState = it },
                    isEdit = isEdit,
                    parentProvider = parentProvider,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = { dialogState.dismiss() }) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(
                    onClick = { if (canConfirm(modelState)) dialogState.confirm() },
                ) { Text(confirmText) }
            }
        }
    }
}
