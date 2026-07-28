package me.rerere.rikkahub.ui.pages.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.db.entity.MemoryExtractionBatchEntity
import me.rerere.rikkahub.data.db.entity.MemoryExtractionBatchStatus
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.service.MemoryBankService
import me.rerere.rikkahub.ui.components.ui.Select
import kotlin.uuid.Uuid

@Composable
internal fun MemoryEmbeddingConfigCard(
    enabled: Boolean,
    selectedModelId: Uuid?,
    dimensions: Int?,
    models: List<Model>,
    onEnabledChange: (Boolean) -> Unit,
    onModelSelected: (Uuid?) -> Unit,
    onDimensionsChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("记忆向量化", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "使用 Provider 里的 Embedding 模型处理待向量化记忆",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }

            if (models.isEmpty()) {
                Text(
                    text = "暂无 Embedding 模型。请先在 Provider 设置里新增模型并选择 Embedding 类型。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                val options = listOf<Model?>(null) + models
                val selectedModel = models.firstOrNull { it.id == selectedModelId }
                Select(
                    options = options,
                    selectedOption = selectedModel,
                    onOptionSelected = { model -> onModelSelected(model?.id) },
                    optionToString = { model -> model?.displayName?.ifBlank { model.modelId } ?: "未选择模型" },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = dimensions?.toString().orEmpty(),
                onValueChange = onDimensionsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("向量维度（可选）") },
                singleLine = true,
            )
        }
    }
}

@Composable
internal fun StatsRow(stats: MemoryBankService.MemoryStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard("总计", stats.total, Modifier.weight(1f))
        StatCard("消息", stats.messageCount, Modifier.weight(1f))
        StatCard("固定", stats.manualCount, Modifier.weight(1f))
        StatCard("失效", stats.deprecatedCount, Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard("已向量化", stats.vectorizedCount, Modifier.weight(1f), MaterialTheme.colorScheme.primaryContainer)
        StatCard("待处理", stats.pendingCount, Modifier.weight(1f), MaterialTheme.colorScheme.tertiaryContainer)
        StatCard("失败", stats.failedCount, Modifier.weight(1f), MaterialTheme.colorScheme.errorContainer)
    }
}

@Composable
internal fun MemoryBankLegend() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("按钮：重建向量索引 / 处理待向量化 / 轻量维护合并重复 / 刷新列表", style = MaterialTheme.typography.bodySmall)
            Text("分类：消息来自对话抽取；手动是你或插件主动保存的记忆；失效是被替换或废弃的旧记忆。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(count.toString(), style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AssistantFilterRow(
    selectedAssistantId: String?,
    assistantIds: List<String>,
    assistantLabels: Map<String, String>,
    onAssistantSelected: (String?) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedAssistantId == null,
            onClick = { onAssistantSelected(null) },
            label = { Text("全部角色") },
        )
        assistantIds.forEach { id ->
            FilterChip(
                selected = selectedAssistantId == id,
                onClick = { onAssistantSelected(id) },
                label = { Text(assistantLabels[id] ?: id.shortAssistantId()) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TypeFilterRow(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
) {
    val types = listOf(
        "" to "全部",
        "message" to "消息",
        "manual" to "固定记忆",
        "deprecated" to "失效",
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        types.forEach { (value, label) ->
            FilterChip(
                selected = selectedType == value,
                onClick = { onTypeSelected(value) },
                label = { Text(label) },
            )
        }
    }
}

internal fun buildMemoryAssistantLabels(
    assistantIds: List<String>,
    assistants: List<Assistant>,
): Map<String, String> {
    val assistantNames = assistants.associate { assistant ->
        assistant.id.toString() to assistant.name.trim()
    }
    return assistantIds.associateWith { id ->
        assistantNames[id]?.takeIf { it.isNotBlank() } ?: id.shortAssistantId()
    }
}

internal fun String.shortAssistantId(): String = take(8)

internal fun batchStatusLabel(batch: MemoryExtractionBatchEntity): String = when (batch.status) {
    MemoryExtractionBatchStatus.PENDING.name -> "等待处理"
    MemoryExtractionBatchStatus.PROCESSING.name -> "处理中"
    MemoryExtractionBatchStatus.SUCCESS_WITH_MEMORIES.name -> "已生成记忆"
    MemoryExtractionBatchStatus.SUCCESS_EMPTY.name -> "明确无记忆"
    MemoryExtractionBatchStatus.FAILED_RETRYABLE.name -> "失败，可重试"
    MemoryExtractionBatchStatus.FAILED_MANUAL_REVIEW.name -> "需要手动重试"
    MemoryExtractionBatchStatus.INVALIDATED.name -> "已失效，等待重建"
    else -> "未知状态"
}
