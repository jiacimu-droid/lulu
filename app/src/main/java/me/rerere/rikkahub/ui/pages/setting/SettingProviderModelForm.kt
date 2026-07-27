package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.registry.ModelRegistry
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomBodies
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomHeaders
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConfigure
import kotlin.uuid.Uuid

@Composable
internal fun SettingProviderModelForm(
    model: Model,
    onModelChange: (Model) -> Unit,
    isEdit: Boolean,
    parentProvider: ProviderSetting? = null,
) {
    val pagerState = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()

    fun setModelId(id: String) {
        val inputModality = ModelRegistry.MODEL_INPUT_MODALITIES.getData(id)
        val outputModality = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(id)
        val abilities = ModelRegistry.MODEL_ABILITIES.getData(id)
        onModelChange(
            model.copy(
                modelId = id,
                displayName = id,
                inputModalities = inputModality,
                outputModalities = outputModality,
                abilities = abilities,
            ),
        )
    }

    Column {
        SecondaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text(stringResource(R.string.setting_provider_page_basic_settings)) },
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text(stringResource(R.string.setting_provider_page_advanced_settings)) },
            )
            Tab(
                selected = pagerState.currentPage == 2,
                onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                text = { Text(stringResource(R.string.setting_page_built_in_tools)) },
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> SettingProviderModelBasicForm(model, onModelChange, isEdit, ::setModelId)
                1 -> SettingProviderModelAdvancedForm(model, onModelChange, parentProvider)
                2 -> SettingProviderBuiltInTools(
                    tools = model.tools,
                    onUpdateTools = { onModelChange(model.copy(tools = it)) },
                )
            }
        }
    }
}

@Composable
private fun SettingProviderModelBasicForm(
    model: Model,
    onModelChange: (Model) -> Unit,
    isEdit: Boolean,
    setModelId: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        OutlinedTextField(
            value = model.modelId,
            onValueChange = { if (!isEdit) setModelId(it.trim()) },
            label = { Text(stringResource(R.string.setting_provider_page_model_id)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                if (!isEdit) Text(stringResource(R.string.setting_provider_page_model_id_placeholder))
            },
            enabled = !isEdit,
        )
        OutlinedTextField(
            value = model.displayName,
            onValueChange = { onModelChange(model.copy(displayName = it.trim())) },
            label = {
                Text(
                    stringResource(
                        if (isEdit) R.string.setting_provider_page_model_name
                        else R.string.setting_provider_page_model_display_name,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                if (!isEdit) Text(stringResource(R.string.setting_provider_page_model_display_name_placeholder))
            },
        )
        SettingProviderModelTypeSelector(
            selectedType = model.type,
            onTypeSelected = { onModelChange(model.copy(type = it)) },
        )
        SettingProviderModelModalitySelector(
            model = model,
            inputModalities = model.inputModalities,
            onUpdateInputModalities = { onModelChange(model.copy(inputModalities = it)) },
            outputModalities = model.outputModalities,
            onUpdateOutputModalities = { onModelChange(model.copy(outputModalities = it)) },
        )
        if (model.type == ModelType.CHAT) {
            ModalAbilitySelector(
                abilities = model.abilities,
                onUpdateAbilities = { onModelChange(model.copy(abilities = it)) },
            )
        }
    }
}

@Composable
private fun SettingProviderModelAdvancedForm(
    model: Model,
    onModelChange: (Model) -> Unit,
    parentProvider: ProviderSetting?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingProviderOverrideSettings(
            providerOverride = model.providerOverwrite,
            onUpdateProviderOverride = { onModelChange(model.copy(providerOverwrite = it)) },
            parentProvider = parentProvider,
        )
        CustomHeaders(
            headers = model.customHeaders,
            onUpdate = { onModelChange(model.copy(customHeaders = it)) },
        )
        CustomBodies(
            customBodies = model.customBodies,
            onUpdate = { onModelChange(model.copy(customBodies = it)) },
        )
    }
}

@Composable
private fun SettingProviderModelTypeSelector(
    selectedType: ModelType,
    onTypeSelected: (ModelType) -> Unit,
) {
    Text(stringResource(R.string.setting_provider_page_model_type), style = MaterialTheme.typography.titleSmall)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ModelType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index, ModelType.entries.size),
                label = {
                    Text(
                        stringResource(
                            when (type) {
                                ModelType.CHAT -> R.string.setting_provider_page_chat_model
                                ModelType.EMBEDDING -> R.string.setting_provider_page_embedding_model
                                ModelType.IMAGE -> R.string.setting_provider_page_image_model
                                ModelType.VIDEO -> R.string.setting_provider_page_video_model
                                ModelType.RERANK -> R.string.setting_provider_page_rerank_model
                            },
                        ),
                    )
                },
                selected = selectedType == type,
                onClick = { onTypeSelected(type) },
            )
        }
    }
}

@Composable
private fun SettingProviderModelModalitySelector(
    model: Model,
    inputModalities: List<Modality>,
    onUpdateInputModalities: (List<Modality>) -> Unit,
    outputModalities: List<Modality>,
    onUpdateOutputModalities: (List<Modality>) -> Unit,
) {
    if (model.type != ModelType.CHAT) return
    Text(stringResource(R.string.setting_provider_page_input_modality), style = MaterialTheme.typography.titleSmall)
    MultiChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Modality.entries.forEachIndexed { index, modality ->
            SegmentedButton(
                checked = modality in inputModalities,
                shape = SegmentedButtonDefaults.itemShape(index, Modality.entries.size),
                onCheckedChange = {
                    onUpdateInputModalities(if (it) inputModalities + modality else inputModalities - modality)
                },
            ) {
                Text(
                    stringResource(
                        when (modality) {
                            Modality.TEXT -> R.string.setting_provider_page_text
                            Modality.IMAGE -> R.string.setting_provider_page_image
                        },
                    ),
                )
            }
        }
    }
    Text(stringResource(R.string.setting_provider_page_output_modality), style = MaterialTheme.typography.titleSmall)
    MultiChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Modality.entries.forEachIndexed { index, modality ->
            SegmentedButton(
                checked = modality in outputModalities,
                shape = SegmentedButtonDefaults.itemShape(index, Modality.entries.size),
                onCheckedChange = {
                    onUpdateOutputModalities(if (it) outputModalities + modality else outputModalities - modality)
                },
            ) {
                Text(
                    stringResource(
                        when (modality) {
                            Modality.TEXT -> R.string.setting_provider_page_text
                            Modality.IMAGE -> R.string.setting_provider_page_image
                        },
                    ),
                )
            }
        }
    }
}

@Composable
fun ModalAbilitySelector(
    abilities: List<ModelAbility>,
    onUpdateAbilities: (List<ModelAbility>) -> Unit,
) {
    Text(stringResource(R.string.setting_provider_page_abilities), style = MaterialTheme.typography.titleSmall)
    MultiChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ModelAbility.entries.forEachIndexed { index, ability ->
            SegmentedButton(
                checked = ability in abilities,
                shape = SegmentedButtonDefaults.itemShape(index, ModelAbility.entries.size),
                onCheckedChange = {
                    onUpdateAbilities(if (it) abilities + ability else abilities - ability)
                },
                label = {
                    Text(
                        stringResource(
                            when (ability) {
                                ModelAbility.TOOL -> R.string.setting_provider_page_tool
                                ModelAbility.REASONING -> R.string.setting_provider_page_reasoning
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun SettingProviderBuiltInTools(
    tools: Set<BuiltInTools>,
    onUpdateTools: (Set<BuiltInTools>) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.setting_page_built_in_tools), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.setting_page_built_in_tools_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val availableTools = listOf(
            BuiltInTools.Search to (
                stringResource(R.string.setting_page_built_in_tools_search) to
                    stringResource(R.string.setting_page_built_in_tools_search_desc)
                ),
            BuiltInTools.UrlContext to (
                stringResource(R.string.setting_page_built_in_tools_url_context) to
                    stringResource(R.string.setting_page_built_in_tools_url_context_desc)
                ),
            BuiltInTools.ImageGeneration to (
                stringResource(R.string.setting_page_built_in_tools_image_generation) to
                    stringResource(R.string.setting_page_built_in_tools_image_generation_desc)
                ),
        )
        availableTools.forEach { (tool, info) ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(info.first, style = MaterialTheme.typography.titleSmall)
                        Text(
                            info.second,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = tool in tools,
                        onCheckedChange = { onUpdateTools(if (it) tools + tool else tools - tool) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingProviderOverrideSettings(
    providerOverride: ProviderSetting?,
    onUpdateProviderOverride: (ProviderSetting?) -> Unit,
    parentProvider: ProviderSetting?,
) {
    var showProviderConfig by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ProviderSetting?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.setting_provider_page_provider_override), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.setting_provider_page_provider_override_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (providerOverride != null) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AutoAIIcon(providerOverride.name, modifier = Modifier.size(24.dp))
                        Text(
                            text = "${providerOverride.name} (Override)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                editingProvider = providerOverride
                                showProviderConfig = true
                            },
                        ) {
                            Icon(HugeIcons.Tools, contentDescription = "Edit override")
                        }
                        IconButton(onClick = { onUpdateProviderOverride(null) }) {
                            Icon(HugeIcons.Cancel01, contentDescription = "Remove override")
                        }
                    }
                }
            }
        } else {
            Button(
                onClick = {
                    editingProvider = parentProvider?.copyProvider(
                        id = Uuid.random(),
                        builtIn = false,
                        models = emptyList(),
                        description = {},
                    )
                    showProviderConfig = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(HugeIcons.Add01, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.setting_provider_page_add_provider_override))
            }
        }

        if (showProviderConfig && editingProvider != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    showProviderConfig = false
                    editingProvider = null
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                var internalProvider by remember(editingProvider) { mutableStateOf(editingProvider!!) }
                Column(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        stringResource(R.string.setting_provider_page_configure_provider_override),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ProviderConfigure(provider = internalProvider, onEdit = { internalProvider = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(
                            onClick = {
                                showProviderConfig = false
                                editingProvider = null
                            },
                        ) { Text(stringResource(R.string.cancel)) }
                        TextButton(
                            onClick = {
                                onUpdateProviderOverride(internalProvider)
                                showProviderConfig = false
                                editingProvider = null
                            },
                        ) { Text(stringResource(R.string.setting_provider_page_save)) }
                    }
                }
            }
        }
    }
}
