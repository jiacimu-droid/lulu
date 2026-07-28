package me.rerere.rikkahub.ui.pages.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import me.rerere.rikkahub.data.db.entity.MemoryExtractionBatchStatus
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoryBankPage(
    onBack: () -> Unit,
    onOpenSource: (conversationId: String, nodeId: String?) -> Unit,
) {
    val vm: MemoryBankVM = koinViewModel()
    val memories by vm.memories.collectAsStateWithLifecycle()
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showDeleteDialog by remember { mutableStateOf<MemoryBankEntity?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var editMemory by remember { mutableStateOf<MemoryBankEntity?>(null) }
    var correctionMemory by remember { mutableStateOf<MemoryBankEntity?>(null) }
    var showFullRebuildDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text("记忆库")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(HugeIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
                actions = {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = { vm.rebuildIndex() }) {
                        Icon(HugeIcons.Database02, contentDescription = "重建向量索引")
                    }
                    IconButton(onClick = { vm.processPendingVectors() }) {
                        Icon(HugeIcons.DatabaseSync, contentDescription = "处理待向量化记忆")
                    }
                    IconButton(onClick = { vm.runLightMaintenance() }) {
                        Icon(HugeIcons.Tools, contentDescription = "轻量维护")
                    }
                    IconButton(onClick = { showClearAllDialog = true }) {
                        Icon(HugeIcons.Delete02, contentDescription = "清除长期记忆")
                    }
                    IconButton(onClick = { vm.loadMemories() }) {
                        Icon(HugeIcons.Refresh01, contentDescription = "刷新")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                StatsRow(stats)
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("整理聊天记忆", style = MaterialTheme.typography.titleSmall)
                        Text(
                            selectedAssistant?.let { assistant ->
                                "每批 ${assistant.memoryExtractionInterval} 条，最近 ${assistant.memoryExtractionProtectedRecentCount} 条保持原文；不会跨批次拼接或重复整理。"
                            } ?: "每个角色按自己的批次与最近保护区设置整理；不会跨批次拼接或重复整理。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                enabled = !reorganizationProgress.running,
                                onClick = vm::repairMemoriesFromHistory,
                            ) {
                                Icon(HugeIcons.Refresh01, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("整理最近一批")
                            }
                            TextButton(
                                enabled = !reorganizationProgress.running,
                                onClick = vm::continueHistoricalMemoryRepair,
                            ) {
                                Text("继续补齐旧记录")
                            }
                            TextButton(
                                enabled = !reorganizationProgress.running,
                                onClick = { showFullRebuildDialog = true },
                            ) {
                                Text("完整重建")
                            }
                        }
                        if (reorganizationProgress.message.isNotBlank()) {
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
                        if (reorganizationProgress.running) {
                            Text(
                                "进度：${reorganizationProgress.currentConversation}/${reorganizationProgress.totalConversations} 个对话，已完成 ${reorganizationProgress.completedBatches} 批",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (batchOverviews.isNotEmpty()) {
                item {
                    Text("持久记忆批次", style = MaterialTheme.typography.titleSmall)
                }
                items(batchOverviews, key = { it.conversationId }) { overview ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "对话 ${overview.conversationId.take(8)}…",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                "成功点 ${overview.successfulThrough} · 下一批从 ${overview.nextBatchStart} 开始 · 稳定区到 ${overview.stableRegionEnd} · 剩余 ${overview.remainingMessageCount} 条",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            overview.batches
                                .filter { it.status != MemoryExtractionBatchStatus.SUCCESS_WITH_MEMORIES.name }
                                .takeLast(8)
                                .forEach { batch ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${batch.batchStartSequence}～${batch.batchEndSequence} · ${batchStatusLabel(batch)}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            Text(
                                                "尝试 ${batch.attemptCount} 次${batch.lastError?.let { " · $it" }.orEmpty()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (
                                            batch.status == MemoryExtractionBatchStatus.FAILED_RETRYABLE.name ||
                                            batch.status == MemoryExtractionBatchStatus.FAILED_MANUAL_REVIEW.name
                                        ) {
                                            TextButton(
                                                enabled = !loading && !reorganizationProgress.running,
                                                onClick = { vm.retryExtractionBatch(batch.batchId) },
                                            ) {
                                                Text("重试")
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("查看哪个角色的记忆", style = MaterialTheme.typography.titleSmall)
                    AssistantFilterRow(
                        selectedAssistantId = selectedAssistantId,
                        assistantIds = assistantIds,
                        assistantLabels = assistantLabels,
                        onAssistantSelected = { vm.setSelectedAssistantId(it) },
                    )
                }
            }

            item {
                MemoryBankLegend()
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                TypeFilterRow(
                    selectedType = selectedType,
                    onTypeSelected = { vm.setSelectedType(it) },
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { vm.setSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索记忆...") },
                    leadingIcon = {
                        Icon(HugeIcons.Search01, contentDescription = null)
                    },
                    singleLine = true,
                )
            }

            items(memories, key = { it.id }) { memory ->
                MemoryCard(
                    memory = memory,
                    assistantLabels = assistantLabels,
                    onDelete = { showDeleteDialog = memory },
                    onOpenSource = onOpenSource,
                    onEdit = { editMemory = memory },
                    onTogglePinned = { vm.setPinned(memory, !memory.pinned) },
                    onCorrect = { correctionMemory = memory },
                )
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
                                "系统只保存明确偏好、边界、纠正、承诺和重要共同事件；普通寒暄不会为了凑数写进来。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = vm::repairMemoriesFromHistory) {
                                Icon(HugeIcons.Refresh01, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("整理最近一批")
                            }
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
            text = { Text("确定要删除这条记忆吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMemory(memory.id)
                    showDeleteDialog = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (showFullRebuildDialog) {
        AlertDialog(
            onDismissRequest = { showFullRebuildDialog = false },
            title = { Text("完整重建记忆？") },
            text = {
                Text("这会重新扫描当前筛选角色的全部完整批次，用于修复旧版本的时间和投影错误。已有聊天不会被删除，但可能调用多次模型。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showFullRebuildDialog = false
                    vm.rebuildAllHistoricalMemories()
                }) { Text("开始重建") }
            },
            dismissButton = {
                TextButton(onClick = { showFullRebuildDialog = false }) { Text("取消") }
            },
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
                        "将不可撤销地删除记忆库中的全部长期记忆。角色人设、世界书、聊天记录和考研计划不会受影响。"
                    } else {
                        "将不可撤销地删除“$selectedName”的全部长期记忆。角色人设、世界书、聊天记录和考研计划不会受影响。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearLongTermMemories()
                    showClearAllDialog = false
                }) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("取消")
                }
            },
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
