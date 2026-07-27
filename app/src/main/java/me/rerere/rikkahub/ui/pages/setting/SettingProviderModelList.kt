package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.registry.ModelRegistry
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.rikkahub.R
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun SettingProviderModelList(
    providerSetting: ProviderSetting,
    onUpdateProvider: (ProviderSetting) -> Unit,
) {
    val providerManager = koinInject<ProviderManager>()
    val modelList by produceState(emptyList(), providerSetting) {
        runCatching {
            println("loading models...")
            value = providerManager.getProviderByType(providerSetting)
                .listModels(providerSetting)
                .sortedBy { it.modelId }
                .toList()
        }.onFailure { it.printStackTrace() }
    }
    var expanded by rememberSaveable { mutableStateOf(true) }
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onUpdateProvider(providerSetting.moveMove(from.index, to.index))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .floatingToolbarVerticalNestedScroll(
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false },
                ),
            contentPadding = PaddingValues(16.dp) + PaddingValues(bottom = 128.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = lazyListState,
        ) {
            if (providerSetting.models.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillParentMaxHeight(0.8f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.setting_provider_page_no_models),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.setting_provider_page_add_models_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            } else {
                items(providerSetting.models, key = { it.id }) { item ->
                    ReorderableItem(state = reorderableLazyListState, key = item.id) { isDragging ->
                        SettingProviderModelCard(
                            model = item,
                            onDelete = { onUpdateProvider(providerSetting.delModel(item)) },
                            onEdit = { onUpdateProvider(providerSetting.editModel(it)) },
                            parentProvider = providerSetting,
                            modifier = Modifier
                                .longPressDraggableHandle()
                                .graphicsLayer {
                                    scaleX = if (isDragging) 1.05f else 1f
                                    scaleY = if (isDragging) 1.05f else 1f
                                },
                        )
                    }
                }
            }
        }
        HorizontalFloatingToolbar(
            expanded = expanded,
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = -ScreenOffset),
        ) {
            SettingProviderModelPickerButton(
                models = modelList,
                selectedModels = providerSetting.models,
                onModelSelected = { model ->
                    val modelId = model.modelId
                    onUpdateProvider(
                        providerSetting.addModel(
                            model.copy(
                                inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(modelId),
                                outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(modelId),
                                abilities = ModelRegistry.MODEL_ABILITIES.getData(modelId),
                            ),
                        ),
                    )
                },
                onModelDeselected = { onUpdateProvider(providerSetting.delModel(it)) },
                onAllModelSelected = { models ->
                    onUpdateProvider(
                        providerSetting.copyProvider(
                            models = providerSetting.models + models
                                .filter { candidate -> providerSetting.models.none { it.modelId == candidate.modelId } }
                                .map { candidate ->
                                    candidate.copy(
                                        inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(candidate.modelId),
                                        outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(candidate.modelId),
                                        abilities = ModelRegistry.MODEL_ABILITIES.getData(candidate.modelId),
                                    )
                                },
                        ),
                    )
                },
                onAllModelDeselected = { filteredModels ->
                    onUpdateProvider(
                        providerSetting.copyProvider(
                            models = providerSetting.models.filter { current ->
                                filteredModels.none { it.modelId == current.modelId }
                            },
                        ),
                    )
                },
            )
            SettingProviderAddCustomModelButton(
                expanded = expanded,
                parentProvider = providerSetting,
                onAddModel = { onUpdateProvider(providerSetting.addModel(it)) },
            )
        }
    }
}

@Composable
private fun SettingProviderAddCustomModelButton(
    expanded: Boolean,
    parentProvider: ProviderSetting,
    onAddModel: (Model) -> Unit,
) {
    val dialogState = me.rerere.rikkahub.ui.hooks.useEditState<Model> { onAddModel(it) }
    Button(onClick = { dialogState.open(Model()) }) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(HugeIcons.Add01, contentDescription = stringResource(R.string.setting_provider_page_add_model))
            AnimatedVisibility(expanded) {
                Row {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.setting_provider_page_add_new_model),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
    SettingProviderModelEditSheet(
        dialogState = dialogState,
        parentProvider = parentProvider,
        title = stringResource(R.string.setting_provider_page_add_model),
        confirmText = stringResource(R.string.setting_provider_page_add),
        isEdit = false,
        canConfirm = { it.modelId.isNotBlank() && it.displayName.isNotBlank() },
    )
}
