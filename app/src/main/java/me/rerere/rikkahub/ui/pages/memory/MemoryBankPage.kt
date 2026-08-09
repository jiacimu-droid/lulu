package me.rerere.rikkahub.ui.pages.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.DatabaseSync
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import me.rerere.rikkahub.data.db.entity.MemoryExtractionBatchEntity
import me.rerere.rikkahub.data.db.entity.MemoryExtractionBatchStatus
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryBankPage(
    onBack: () -> Unit,
    onOpenSource: (conversationId: String, nodeId: String?) -> Unit,
) {
    val vm: MemoryBankVM = koinViewModel()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val archiveSources by vm.archiveSources.collectAsStateWithLifecycle()
    val rawTimeline by vm.rawTimeline.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val selectedType by vm.selectedType.collectAsStateWithLifecycle()
    val selectedAssistantId by vm.selectedAssistantId.collectAsStateWithLifecycle()
    val assistantIds by vm.assistantIds.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val maintenanceMessage by vm.maintenanceMessage.collectAsStateWithLifecycle()
    val reorganizationProgress by vm.reorganizationProgress.collectAsStateWithLifecycle()
    val batchOverviews by vm.batchOverviews.collectAsStateWithLifecycle()
    val embeddingModels = remember(settings.providers) { vm.embeddingModels(settings) }
    val assistantLabels = remember(assistantIds, settings.assistants) {
        buildMemoryAssistantLabels(assistantIds, settings.assistants)
    }
    val selectedAssistant = remember(selectedAssistantId, settings.assistants) {
        selectedAssistantId?.let { id -> settings.assistants.firstOrNull { it.id.toString() == id } }
    }
    val batchSize = selectedAssistant?.memoryExtractionInterval?.coerceAtLeast(1) ?: 20

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showDeleteDialog by remember { mutableStateOf<MemoryBankEntity?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var editMemory by remember { mutableStateOf<MemoryBankEntity?>(null) }
    var correctionMemory by remember { mutableStateOf<MemoryBankEntity?>(null) }
    var expandedArchiveIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showRawTimeline by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("记忆库") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(HugeIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
                actions = {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = vm::rebuildIndex) {
                        Icon(HugeIcons.Database02, contentDescription = "重建向量索引")
                    }
                    IconButton(onClick = vm::processPendingVectors) {
                        Icon(HugeIcons.DatabaseSync, contentDescription = "处理待向量化记忆")
                    }
                    IconButton(onClick = vm::runLightMaintenance) {
                        Icon(HugeIcons.Tools, contentDescription = "合并重复记忆")
                    }
                    IconButton(onClick = { showClearAllDialog = true }) {
                        Icon(HugeIcons.Delete02, contentDescription = "清除长期记忆")
                    }
                    IconButton(onClick = vm::loadMemories) {
                        Icon(HugeIcons.Refresh01, contentDescription = "刷新")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { StatsRow(stats) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showRawTimeline,
                        onClick = { showRawTimeline = false },
                        label = { Text("长期记忆") },
                    )
                    FilterChip(
                        selected = showRawTimeline,
                        onClick = { showRawTimeline = true },
                        label = { Text("原始时间线") },
                    )
                }
            }

            if (showRawTimeline) {
                items(rawTimeline, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenSource(entry.conversationId, entry.nodeId) },
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = when (entry.role) {
                                    "USER" -> "用户"
                                    "ASSISTANT" -> "角色"
                                    else -> entry.role
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(entry.content, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                entry.createdAt,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (rawTimeline.isEmpty()) {
                    item { Spacer(Modifier.height(1.dp)) }
                }
                return@LazyColumn
            }

            maintenanceMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            item {
                MemoryEmbeddingConfigCard(
                    enabled = settings.memoryEmbeddingConfig.enabled,
                    selectedModelId = settings.memoryEmbeddingConfig.modelId,
                    dimensions = settings.memoryEmbeddingConfig.dimensions,
                    models = embeddingModels,
                    onEnabledChange = vm::setMemoryEmbeddingEnabled,
                    onModelSelected = vm::setMemoryEmbeddingModel,
                    onDimensionsChange = vm::setMemoryEmbeddingDimensions,
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("查看哪个角色的记忆", style = MaterialTheme.typography.titleSmall)
                    AssistantFilterRow(
                        selectedAssistantId = selectedAssistantId,
                        assistantIds = assistantIds,
                        assistantLabels = assistantLabels,
                        onAssistantSelected = vm::setSelectedAssistantId,
                    )
                }
            }

            if (batchOverviews.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("持久记忆批次", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "按每批 $batchSize 条固定分段。每一条消息只属于一个批次；旧版滑动区间不再显示。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(batchOverviews, key = { it.conversationId }) { overview ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("对话 ${overview.conversationId.take(8)}…", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "可整理到 ${overview.stableRegionEnd}；最近保护区不会进入批次。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            buildAlignedBatchRows(overview, batchSize).forEach { row ->
                                AlignedMemoryBatchRow(
                                    row = row,
                                    busy = loading || reorganizationProgress.running,
                                    onProcess = vm::continueHistoricalMemoryRepair,
                                    onRetry = vm::retryExtractionBatch,
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "当前还没有达到一个完整的 $batchSize 条稳定批次。",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (reorganizationProgress.message.isNotBlank()) {
                item {
                    Text(
                        text = reorganizationProgress.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (reorganizationProgress.failedBatches > 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }

            item { MemoryBankLegend() }
            item {
                TypeFilterRow(selectedType = selectedType, onTypeSelected = vm::setSelectedType)
            }
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = vm::setSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索记忆...") },
                    leadingIcon = { Icon(HugeIcons.Search01, contentDescription = null) },
                    singleLine = true,
                )
            }

            items(memories, key = { it.id }) { memory ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MemoryCard(
                        memory = memory,
                        assistantLabels = assistantLabels,
                        onDelete = { showDeleteDialog = memory },
                        onOpenSource = onOpenSource,
                        onEdit = { editMemory = memory },
                        onTogglePinned = { vm.setPinned(memory, !memory.pinned) },
                        onCorrect = { correctionMemory = memory },
                    )
                    val sources = archiveSources[memory.id].orEmpty()
                    if (sources.isNotEmpty()) {
                        val expanded = memory.id in expandedArchiveIds
                        TextButton(
                            onClick = {
                                expandedArchiveIds = if (expanded) {
                                    expandedArchiveIds - memory.id
                                } else {
                                    expandedArchiveIds + memory.id
                                }
                            },
                        ) {
                            Text(if (expanded) "收起原始记忆" else "查看原始记忆（${sources.size}）")
                        }
                        if (expanded) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "这些原子记忆只作为证据回查，不再和每日总结平级参与普通召回。",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    sources.forEach { source ->
                                        MemoryCard(
                                            memory = source,
                                            assistantLabels = assistantLabels,
                                            onDelete = { showDeleteDialog = source },
                                            onOpenSource = onOpenSource,
                                            onEdit = { editMemory = source },
                                            onTogglePinned = { vm.setPinned(source, !source.pinned) },
                                            onCorrect = { correctionMemory = source },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (memories.isEmpty() && !loading) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("这个角色还没有长期记忆", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "请直接在上面的标准批次中选择“整理”或“重试”，不再使用含义不清楚的一键补齐入口。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { memory ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除记忆") },
            text = {
                Text(
                    if (memory.type == "daily_summary" || memory.memoryKind == "daily_archive") {
                        "删除这条每日总结后，它下面归档的原始记忆会恢复为独立记忆。"
                    } else {
                        "确定要删除这条记忆吗？此操作不可撤销。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMemory(memory.id)
                    showDeleteDialog = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("取消") } },
        )
    }

    if (showClearAllDialog) {
        val clearingAll = selectedAssistantId == null
        val selectedName = selectedAssistantId?.let { assistantLabels[it] } ?: "当前角色"
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(if (clearingAll) "清除全部长期记忆" else "清除角色长期记忆") },
            text = {
                Text(
                    if (clearingAll) {
                        "将不可撤销地删除记忆库中的全部长期记忆。聊天记录不会受影响。"
                    } else {
                        "将不可撤销地删除“$selectedName”的全部长期记忆。聊天记录不会受影响。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearLongTermMemories()
                    showClearAllDialog = false
                }) { Text("清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearAllDialog = false }) { Text("取消") } },
        )
    }

    editMemory?.let { memory ->
        EditMemoryDialog(
            memory = memory,
            onDismiss = { editMemory = null },
            onConfirm = {
                vm.updateMemory(it)
                editMemory = null
            },
        )
    }

    correctionMemory?.let { memory ->
        CorrectMemoryDialog(
            memory = memory,
            onDismiss = { correctionMemory = null },
            onConfirm = { reason, supersededBy ->
                vm.markDeprecated(memory, reason, supersededBy)
                correctionMemory = null
            },
        )
    }
}

private data class AlignedBatchRow(
    val start: Int,
    val end: Int,
    val batch: MemoryExtractionBatchEntity?,
    val isNextPending: Boolean,
)

private fun buildAlignedBatchRows(
    overview: MemoryBatchOverview,
    batchSize: Int,
): List<AlignedBatchRow> {
    if (overview.stableRegionEnd <= 0) return emptyList()
    return generateSequence(1) { previous -> previous + batchSize }
        .takeWhile { start -> start <= overview.stableRegionEnd }
        .map { start ->
            val end = (start + batchSize - 1).coerceAtMost(overview.stableRegionEnd)
            val exact = overview.batches
                .filter { it.batchStartSequence == start && it.batchEndSequence == end }
                .maxByOrNull { it.updatedAt }
            AlignedBatchRow(
                start = start,
                end = end,
                batch = exact,
                isNextPending = start == overview.nextBatchStart,
            )
        }
        .toList()
}

@Composable
private fun AlignedMemoryBatchRow(
    row: AlignedBatchRow,
    busy: Boolean,
    onProcess: () -> Unit,
    onRetry: (String) -> Unit,
) {
    val batch = row.batch
    val status = when (batch?.status) {
        MemoryExtractionBatchStatus.SUCCESS_WITH_MEMORIES.name -> "成功"
        MemoryExtractionBatchStatus.SUCCESS_EMPTY.name -> "成功 · 无需长期保存"
        MemoryExtractionBatchStatus.PROCESSING.name -> "整理中"
        MemoryExtractionBatchStatus.PENDING.name -> "待整理"
        MemoryExtractionBatchStatus.FAILED_RETRYABLE.name -> "失败 · 可重试"
        MemoryExtractionBatchStatus.FAILED_MANUAL_REVIEW.name -> "失败 · 可重试"
        MemoryExtractionBatchStatus.INVALIDATED.name -> "已失效 · 可重新整理"
        null -> if (row.isNextPending) "待整理" else "等待前序批次"
        else -> "状态未知"
    }
    val retryable = batch != null && batch.status in setOf(
        MemoryExtractionBatchStatus.FAILED_RETRYABLE.name,
        MemoryExtractionBatchStatus.FAILED_MANUAL_REVIEW.name,
        MemoryExtractionBatchStatus.INVALIDATED.name,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${row.start}～${row.end} · $status", style = MaterialTheme.typography.bodySmall)
            batch?.lastError?.takeIf(String::isNotBlank)?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                )
            }
        }
        when {
            retryable -> TextButton(enabled = !busy, onClick = { onRetry(batch!!.batchId) }) { Text("重试") }
            batch == null && row.isNextPending -> TextButton(enabled = !busy, onClick = onProcess) { Text("整理") }
        }
    }
}
