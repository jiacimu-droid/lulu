package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Mortarboard01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.theme.CustomColors
import kotlin.uuid.Uuid

internal enum class ModelSettingSection {
    CHAT,
    MEMORY_EMBEDDING,
    MEMORY_RERANK,
    MEMORY_EXTRACTION,
    LULU_INTENT,
    THEATER,
    IMAGE_GENERATION,
    VIDEO_GENERATION,
    TRANSLATION,
    OCR,
    COMPRESS,
    TITLE_SUMMARY,
}

internal fun defaultModelSettingSections(): List<ModelSettingSection> = listOf(
    ModelSettingSection.CHAT,
    ModelSettingSection.MEMORY_EMBEDDING,
    ModelSettingSection.MEMORY_RERANK,
    ModelSettingSection.MEMORY_EXTRACTION,
    ModelSettingSection.LULU_INTENT,
    ModelSettingSection.THEATER,
    ModelSettingSection.IMAGE_GENERATION,
    ModelSettingSection.VIDEO_GENERATION,
    ModelSettingSection.TRANSLATION,
    ModelSettingSection.OCR,
    ModelSettingSection.COMPRESS,
)

@Composable
internal fun DefaultMemoryEmbeddingModelSetting(settings: Settings, vm: SettingVM) {
    var showModal by remember { mutableStateOf(false) }
    val config = settings.memoryEmbeddingConfig
    ModelFeatureCard(
        title = {
            Text(stringResource(R.string.setting_model_page_memory_embedding_model), maxLines = 1)
        },
        description = {
            Text(stringResource(R.string.setting_model_page_memory_embedding_model_desc))
        },
        icon = { Icon(HugeIcons.Database02, null) },
        actions = {
            Switch(
                checked = config.enabled,
                onCheckedChange = { enabled ->
                    vm.updateSettings(
                        settings.copy(memoryEmbeddingConfig = config.copy(enabled = enabled)),
                    )
                },
            )
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = config.modelId,
                    type = ModelType.EMBEDDING,
                    onSelect = { model ->
                        val selectedModelId = model.id.takeUnless { model.modelId.isBlank() }
                        vm.updateSettings(
                            settings.copy(
                                memoryEmbeddingConfig = config.copy(
                                    modelId = selectedModelId,
                                    enabled = selectedModelId != null,
                                ),
                            ),
                        )
                    },
                    providers = settings.providers,
                    allowClear = true,
                    modifier = Modifier.wrapContentWidth(),
                )
            }
            IconButton(
                onClick = { showModal = true },
                colors = IconButtonDefaults.filledTonalIconButtonColors(),
            ) {
                Icon(HugeIcons.Tools, null)
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
                FormItem(
                    label = { Text(stringResource(R.string.setting_model_page_memory_embedding_enabled)) },
                    description = { Text(stringResource(R.string.setting_model_page_memory_embedding_enabled_desc)) },
                    tail = {
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = { enabled ->
                                vm.updateSettings(
                                    settings.copy(memoryEmbeddingConfig = config.copy(enabled = enabled)),
                                )
                            },
                        )
                    },
                )
                FormItem(
                    label = { Text(stringResource(R.string.setting_model_page_memory_rerank_candidates)) },
                    description = { Text(stringResource(R.string.setting_model_page_memory_rerank_candidates_desc)) },
                ) {
                    OutlinedTextField(
                        value = config.rerankCandidateCount.toString(),
                        onValueChange = { value ->
                            vm.updateSettings(
                                settings.copy(
                                    memoryEmbeddingConfig = config.copy(
                                        rerankCandidateCount = parseMemoryRerankCandidateCountInput(value),
                                    ),
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
                FormItem(
                    label = { Text(stringResource(R.string.setting_model_page_memory_embedding_dimensions)) },
                    description = { Text(stringResource(R.string.setting_model_page_memory_embedding_dimensions_desc)) },
                ) {
                    OutlinedTextField(
                        value = config.dimensions?.toString().orEmpty(),
                        onValueChange = { value ->
                            vm.updateSettings(
                                settings.copy(
                                    memoryEmbeddingConfig = config.copy(
                                        dimensions = parseMemoryEmbeddingDimensionsInput(value),
                                    ),
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
                FormItem(
                    label = { Text(stringResource(R.string.setting_model_page_memory_embedding_batch_size)) },
                    description = { Text(stringResource(R.string.setting_model_page_memory_embedding_batch_size_desc)) },
                ) {
                    OutlinedTextField(
                        value = config.batchSize.toString(),
                        onValueChange = { value ->
                            vm.updateSettings(
                                settings.copy(
                                    memoryEmbeddingConfig = config.copy(
                                        batchSize = parseMemoryEmbeddingBatchSizeInput(value),
                                    ),
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
                MemoryEngineDiagnostics(
                    buildMemoryEngineDiagnostics(
                        enabled = config.enabled,
                        embeddingModel = settings.memoryModelName(config.modelId),
                        rerankModel = settings.memoryModelName(config.rerankModelId),
                        extractionModel = settings.memoryModelName(config.extractionModelId),
                        candidateCount = config.rerankCandidateCount,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun DefaultMemoryRerankModelSetting(settings: Settings, vm: SettingVM) {
    val config = settings.memoryEmbeddingConfig
    SimpleModelFeature(
        title = stringResource(R.string.setting_model_page_memory_rerank_model),
        description = stringResource(R.string.setting_model_page_memory_rerank_model_desc),
        icon = HugeIcons.Database02,
        modelId = config.rerankModelId,
        type = ModelType.RERANK,
        settings = settings,
        allowClear = true,
        onSelect = { model ->
            vm.updateSettings(
                settings.copy(
                    memoryEmbeddingConfig = config.copy(
                        rerankModelId = model.id.takeUnless { model.modelId.isBlank() },
                    ),
                ),
            )
        },
    )
}

@Composable
internal fun MemoryExtractionModelSetting(settings: Settings, vm: SettingVM) {
    val config = settings.memoryEmbeddingConfig
    SimpleModelFeature(
        title = stringResource(R.string.setting_model_page_memory_extraction_model),
        description = stringResource(R.string.setting_model_page_memory_extraction_model_desc),
        icon = HugeIcons.Mortarboard01,
        modelId = config.extractionModelId,
        type = ModelType.CHAT,
        settings = settings,
        allowClear = true,
        onSelect = { model ->
            vm.updateSettings(
                settings.copy(
                    memoryEmbeddingConfig = config.copy(
                        extractionModelId = model.id.takeUnless { model.modelId.isBlank() },
                    ),
                ),
            )
        },
    )
}

private fun Settings.memoryModelName(modelId: Uuid?): String? = modelId
    ?.let { id -> findModelById(id) }
    ?.let { model -> model.displayName.takeIf { it.isNotBlank() } ?: model.modelId }

@Composable
private fun MemoryEngineDiagnostics(lines: List<String>) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = CustomColors.listItemColors.containerColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_model_page_memory_engine_diagnostics),
                style = MaterialTheme.typography.titleSmall,
            )
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.82f),
                )
            }
        }
    }
}

internal fun parseMemoryEmbeddingDimensionsInput(value: String): Int? =
    value.trim().toIntOrNull()?.takeIf { it > 0 }

internal fun parseMemoryEmbeddingBatchSizeInput(value: String): Int =
    (value.trim().toIntOrNull() ?: 1).coerceIn(1, 64)

internal fun parseMemoryRerankCandidateCountInput(value: String): Int =
    (value.trim().toIntOrNull() ?: 5).coerceIn(5, 60)

internal fun buildMemoryEngineDiagnostics(
    enabled: Boolean,
    embeddingModel: String?,
    rerankModel: String?,
    extractionModel: String?,
    candidateCount: Int,
): List<String> {
    val mode = if (enabled) "本地向量库：已启用" else "本地向量库：未启用"
    val embedding = "Embedding：${embeddingModel?.takeIf { it.isNotBlank() } ?: "未配置"}"
    val rerank = "Reranker：${rerankModel?.takeIf { it.isNotBlank() } ?: "未配置，将使用本地混合排序"}"
    val extraction = "记忆抽取：${extractionModel?.takeIf { it.isNotBlank() } ?: "未单独配置，将使用当前聊天模型"}"
    val candidates = "重排序候选：${candidateCount.coerceIn(5, 60)} 条"
    val backend = "Backend / Vector Index：未接入，当前使用设备本地 Room 向量字段"
    return listOf(mode, embedding, rerank, extraction, candidates, backend)
}
