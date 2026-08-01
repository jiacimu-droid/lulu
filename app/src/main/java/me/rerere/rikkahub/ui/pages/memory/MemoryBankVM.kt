package me.rerere.rikkahub.ui.pages.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.companion.CompanionTurnMutation
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import me.rerere.rikkahub.data.db.entity.MemoryExtractionBatchEntity
import me.rerere.rikkahub.data.db.entity.MemoryExtractionBatchStatus
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.service.MemoryBankService
import me.rerere.rikkahub.data.service.buildCompanionPrivateImpression
import me.rerere.rikkahub.data.service.buildDeterministicMemoryCandidatesFromNodes
import me.rerere.rikkahub.data.service.buildRelationshipEventsFromMemoryCandidates
import me.rerere.rikkahub.data.service.buildSelectedConversationBranchId
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.MemoryReorganizationMode
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

data class MemoryBatchOverview(
    val conversationId: String,
    val successfulThrough: Int,
    val nextBatchStart: Int,
    val stableRegionEnd: Int,
    val remainingMessageCount: Int,
    val batches: List<MemoryExtractionBatchEntity>,
)

class MemoryBankVM(
    private val memoryBankService: MemoryBankService,
    private val settingsStore: SettingsStore,
    private val conversationRepository: ConversationRepository,
    private val companionRuntime: CompanionRuntime,
    private val chatService: ChatService,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    private val _memories = MutableStateFlow<List<MemoryBankEntity>>(emptyList())
    val memories: StateFlow<List<MemoryBankEntity>> = _memories.asStateFlow()

    private val _archiveSources = MutableStateFlow<Map<Int, List<MemoryBankEntity>>>(emptyMap())
    val archiveSources: StateFlow<Map<Int, List<MemoryBankEntity>>> = _archiveSources.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedType = MutableStateFlow("")
    val selectedType: StateFlow<String> = _selectedType.asStateFlow()

    private val _selectedAssistantId = MutableStateFlow<String?>(null)
    val selectedAssistantId: StateFlow<String?> = _selectedAssistantId.asStateFlow()

    private val _assistantIds = MutableStateFlow<List<String>>(emptyList())
    val assistantIds: StateFlow<List<String>> = _assistantIds.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _stats = MutableStateFlow(MemoryBankService.MemoryStats())
    val stats: StateFlow<MemoryBankService.MemoryStats> = _stats.asStateFlow()

    private val _batchOverviews = MutableStateFlow<List<MemoryBatchOverview>>(emptyList())
    val batchOverviews: StateFlow<List<MemoryBatchOverview>> = _batchOverviews.asStateFlow()

    private val _maintenanceMessage = MutableStateFlow<String?>(null)
    val maintenanceMessage: StateFlow<String?> = _maintenanceMessage.asStateFlow()
    val reorganizationProgress = chatService.memoryReorganizationProgress
    private var attemptedAutomaticHistoryRepair = false
    private var initializedAssistantFilter = false

    init {
        loadMemories()
    }

    fun loadMemories() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val currentSettings = settingsStore.settingsFlow.first()
                refreshMemoryData(currentSettings)
                if (
                    _memories.value.isEmpty() &&
                    _searchQuery.value.isBlank() &&
                    _selectedType.value.isBlank() &&
                    !attemptedAutomaticHistoryRepair
                ) {
                    attemptedAutomaticHistoryRepair = true
                    val recovered = recoverMemoriesFromHistory(currentSettings, _selectedAssistantId.value)
                    if (recovered > 0) {
                        _maintenanceMessage.value = "已从已有聊天补整理 $recovered 条长期记忆"
                        refreshMemoryData(currentSettings)
                    }
                }
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun refreshMemoryData(currentSettings: Settings) {
        if (!initializedAssistantFilter) {
            _selectedAssistantId.value = currentSettings.getCurrentAssistant().id.toString()
            initializedAssistantFilter = true
        }
        val configuredAssistantIds = currentSettings.assistants.map { it.id.toString() }
        val storedAssistantIds = memoryBankService.getAssistantIds()
        _assistantIds.value = (configuredAssistantIds + storedAssistantIds).distinct()
        val selectedAssistantId = _selectedAssistantId.value
        if (selectedAssistantId != null && selectedAssistantId !in _assistantIds.value) {
            _selectedAssistantId.value = null
        }
        val assistantId = _selectedAssistantId.value
        _stats.value = memoryBankService.getStats(assistantId)
        val visible = memoryBankService.searchMemories(
            keyword = _searchQuery.value,
            type = _selectedType.value,
            limit = 100,
            assistantId = assistantId,
        )
        _memories.value = collapseArchiveSources(visible)
        _archiveSources.value = loadArchiveSources(assistantId, visible)
        _batchOverviews.value = buildBatchOverviews(currentSettings, assistantId)
    }

    private suspend fun loadArchiveSources(
        assistantId: String?,
        visible: List<MemoryBankEntity>,
    ): Map<Int, List<MemoryBankEntity>> {
        val archives = visible.filter(MemoryBankEntity::isDailyArchive)
        if (archives.isEmpty()) return emptyMap()
        val archivedRows = memoryBankService.searchMemories(
            type = "deprecated",
            limit = 1000,
            assistantId = assistantId,
        )
        val activeRows = memoryBankService.searchMemories(
            limit = 1000,
            assistantId = assistantId,
        )
        val allById = (activeRows + archivedRows).associateBy { it.id }
        return archives.associate { archive ->
            archive.id to archive.sourceMemoryIds().mapNotNull(allById::get)
        }
    }

    private suspend fun buildBatchOverviews(
        currentSettings: Settings,
        assistantId: String?,
    ): List<MemoryBatchOverview> {
        val assistant = currentSettings.assistants.firstOrNull { it.id.toString() == assistantId }
            ?: return emptyList()
        val batchSize = assistant.memoryExtractionInterval.coerceAtLeast(1)
        val protectedRecent = assistant.memoryExtractionProtectedRecentCount.coerceAtLeast(0)
        val batches = memoryBankService.getExtractionBatchesForAssistant(assistant.id.toString())
        return conversationRepository.getRecentConversations(assistant.id, limit = 1000)
            .mapNotNull { conversation ->
                val conversationId = conversation.id.toString()
                val conversationBatches = batches.filter { it.conversationId == conversationId }
                val stableRegionEnd = (
                    (conversation.messageNodes.size - protectedRecent).coerceAtLeast(0) / batchSize
                    ) * batchSize
                val successfulThrough = conversationBatches
                    .filter {
                        (
                            it.status == MemoryExtractionBatchStatus.SUCCESS_WITH_MEMORIES.name ||
                                it.status == MemoryExtractionBatchStatus.SUCCESS_EMPTY.name
                            ) &&
                            it.branchId == buildSelectedConversationBranchId(
                                conversation.messageNodes,
                                it.batchEndSequence,
                            )
                    }
                    .maxOfOrNull { it.batchEndSequence }
                    ?.coerceAtMost(stableRegionEnd)
                    ?: 0
                if (stableRegionEnd == 0 && conversationBatches.isEmpty()) {
                    null
                } else {
                    MemoryBatchOverview(
                        conversationId = conversationId,
                        successfulThrough = successfulThrough,
                        nextBatchStart = successfulThrough + 1,
                        stableRegionEnd = stableRegionEnd,
                        remainingMessageCount = (stableRegionEnd - successfulThrough).coerceAtLeast(0),
                        batches = conversationBatches.sortedBy { it.batchStartSequence },
                    )
                }
            }
    }

    fun repairMemoriesFromHistory() = reorganizeMemories(MemoryReorganizationMode.RECENT_BATCH)

    fun continueHistoricalMemoryRepair() = reorganizeMemories(MemoryReorganizationMode.CONTINUE_HISTORY)

    fun rebuildAllHistoricalMemories() = reorganizeMemories(MemoryReorganizationMode.FULL_REBUILD)

    fun retryExtractionBatch(batchId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                memoryBankService.resetExtractionBatchForManualRetry(batchId)
                chatService.retryHistoricalMemoryExtraction(
                    assistantId = _selectedAssistantId.value,
                    mode = MemoryReorganizationMode.CONTINUE_HISTORY,
                ).join()
                _maintenanceMessage.value = chatService.memoryReorganizationProgress.value.message
                refreshMemoryData(settingsStore.settingsFlow.first())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _maintenanceMessage.value = "重试失败，请稍后再试"
            } finally {
                _loading.value = false
            }
        }
    }

    private fun reorganizeMemories(mode: MemoryReorganizationMode) {
        viewModelScope.launch {
            _loading.value = true
            try {
                chatService.retryHistoricalMemoryExtraction(
                    assistantId = _selectedAssistantId.value,
                    mode = mode,
                ).join()
                memoryBankService.processPendingVectors()
                _maintenanceMessage.value = chatService.memoryReorganizationProgress.value.message
                attemptedAutomaticHistoryRepair = true
                refreshMemoryData(settingsStore.settingsFlow.first())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _maintenanceMessage.value = error.message?.let { "整理失败：$it" } ?: "整理失败，请重试"
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun recoverMemoriesFromHistory(
        currentSettings: Settings,
        assistantId: String?,
    ): Int {
        val assistants = currentSettings.assistants.filter { assistant ->
            assistantId == null || assistant.id.toString() == assistantId
        }
        var recoveredCount = 0
        for (assistant in assistants) {
            for (conversation in conversationRepository.getRecentConversations(assistant.id, limit = 8)) {
                val candidates = buildDeterministicMemoryCandidatesFromNodes(conversation.messageNodes, limit = 6)
                if (candidates.isEmpty()) continue
                val nowMillis = System.currentTimeMillis()
                val saved = memoryBankService.saveExtractedMemories(
                    candidates = candidates,
                    assistantId = assistant.id.toString(),
                    conversationId = conversation.id.toString(),
                    createdAt = nowMillis,
                )
                recoveredCount += saved.size
                if (saved.isNotEmpty()) {
                    val snapshot = companionRuntime.snapshot(assistant.id.toString())
                    companionRuntime.applyTurn(
                        CompanionTurnMutation(
                            assistantId = assistant.id.toString(),
                            privateImpression = buildCompanionPrivateImpression(
                                previous = snapshot.privateImpression,
                                candidates = candidates,
                                nowMillis = nowMillis,
                            ),
                            relationshipEvents = buildRelationshipEventsFromMemoryCandidates(
                                candidates = candidates,
                                assistantId = assistant.id.toString(),
                                conversationId = conversation.id.toString(),
                                createdAt = nowMillis,
                            ),
                            nowMillis = nowMillis,
                        ),
                    )
                }
            }
        }
        return recoveredCount
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        loadMemories()
    }

    fun setSelectedType(type: String) {
        _selectedType.value = type
        loadMemories()
    }

    fun setSelectedAssistantId(assistantId: String?) {
        _selectedAssistantId.value = assistantId
        loadMemories()
    }

    fun deleteMemory(id: Int) {
        viewModelScope.launch {
            val archive = _memories.value.firstOrNull { it.id == id && it.isDailyArchive() }
            archive?.sourceMemoryIds()?.let { sourceIds ->
                val archivedRows = memoryBankService.searchMemories(
                    type = "deprecated",
                    limit = 1000,
                    assistantId = archive.assistantId,
                ).associateBy { it.id }
                sourceIds.mapNotNull(archivedRows::get)
                    .filter { it.deprecatedReason == "archived_into_daily:$id" }
                    .forEach { source ->
                        memoryBankService.updateMemory(
                            source.copy(
                                deprecated = false,
                                deprecatedReason = null,
                                supersededByMemoryId = null,
                                correctedAt = null,
                            ),
                        )
                    }
            }
            memoryBankService.deleteMemory(id)
            loadMemories()
        }
    }

    fun clearLongTermMemories() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val assistantId = _selectedAssistantId.value
                if (assistantId == null) {
                    memoryBankService.deleteAllMemories()
                    _maintenanceMessage.value = "已清除全部长期记忆"
                } else {
                    memoryBankService.deleteMemoriesByAssistant(assistantId)
                    _maintenanceMessage.value = "已清除当前角色的长期记忆"
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _maintenanceMessage.value = error.message?.let { "清除失败：$it" } ?: "清除失败，请重试"
            } finally {
                _loading.value = false
                loadMemories()
            }
        }
    }

    fun updateMemory(memory: MemoryBankEntity) {
        viewModelScope.launch {
            memoryBankService.updateMemory(memory)
            loadMemories()
        }
    }

    fun setPinned(memory: MemoryBankEntity, pinned: Boolean) {
        viewModelScope.launch {
            memoryBankService.setPinned(memory, pinned)
            loadMemories()
        }
    }

    fun markDeprecated(memory: MemoryBankEntity, reason: String, supersededByMemoryId: String?) {
        viewModelScope.launch {
            memoryBankService.markMemoryDeprecated(memory, reason, supersededByMemoryId)
            loadMemories()
        }
    }

    fun rebuildIndex() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val blocker = memoryVectorBlocker(settingsStore.settingsFlow.first())
                if (blocker != null) {
                    _maintenanceMessage.value = blocker
                    return@launch
                }
                memoryBankService.rebuildIndex()
                _maintenanceMessage.value = "向量索引重建完成"
                loadMemories()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _maintenanceMessage.value = error.message?.let { "向量索引重建失败：$it" }
                    ?: "向量索引重建失败"
            } finally {
                _loading.value = false
            }
        }
    }

    fun processPendingVectors() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val currentSettings = settingsStore.settingsFlow.first()
                val blocker = memoryVectorBlocker(currentSettings)
                if (blocker != null) {
                    _maintenanceMessage.value = blocker
                    refreshMemoryData(currentSettings)
                    return@launch
                }

                val before = memoryBankService.getStats(_selectedAssistantId.value)
                memoryBankService.processPendingVectors()
                val after = memoryBankService.getStats(_selectedAssistantId.value)
                val completed = (after.vectorizedCount - before.vectorizedCount).coerceAtLeast(0)
                val newlyFailed = (after.failedCount - before.failedCount).coerceAtLeast(0)
                _maintenanceMessage.value = when {
                    before.pendingCount == 0 -> "没有待向量化记忆；记忆抽取和向量化是两条独立链路"
                    completed > 0 && after.pendingCount > 0 ->
                        "已向量化 $completed 条，仍有 ${after.pendingCount} 条等待下一批处理"
                    completed > 0 -> "已完成 $completed 条记忆向量化"
                    newlyFailed > 0 -> "有 $newlyFailed 条向量化失败；请检查 Embedding 模型接口后重建索引"
                    else -> "没有完成新的向量化；请检查 Embedding 模型是否支持批量向量接口"
                }
                refreshMemoryData(currentSettings)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _maintenanceMessage.value = error.message?.let { "向量化失败：$it" } ?: "向量化失败"
            } finally {
                _loading.value = false
            }
        }
    }

    fun runLightMaintenance() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val result = memoryBankService.runLightMaintenance()
                val archivedCount = archiveDailySourceMemories()
                _maintenanceMessage.value =
                    "轻量维护完成：合并 ${result.deprecatedDuplicateCount} 条重复记忆，归档 $archivedCount 条原子记忆"
                refreshMemoryData(settingsStore.settingsFlow.first())
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun archiveDailySourceMemories(): Int {
        val assistantId = _selectedAssistantId.value
        val active = memoryBankService.searchMemories(limit = 1000, assistantId = assistantId)
        val byId = active.associateBy { it.id }
        var archivedCount = 0
        active.filter(MemoryBankEntity::isDailyArchive).forEach { archive ->
            archive.sourceMemoryIds()
                .mapNotNull(byId::get)
                .filter(MemoryBankEntity::canArchiveUnderDailySummary)
                .forEach { source ->
                    memoryBankService.markMemoryDeprecated(
                        memory = source,
                        reason = "archived_into_daily:${archive.id}",
                        supersededByMemoryId = archive.id.toString(),
                    )
                    archivedCount += 1
                }
        }
        return archivedCount
    }

    fun setMemoryEmbeddingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsStore.settingsFlow.first()
            settingsStore.update { settings ->
                settings.copy(memoryEmbeddingConfig = settings.memoryEmbeddingConfig.copy(enabled = enabled))
            }
            _maintenanceMessage.value = when {
                !enabled -> "本地向量库已关闭；长期记忆仍会继续抽取和保存"
                current.memoryEmbeddingConfig.modelId == null -> "向量库已开启，但还需要选择 Embedding 模型"
                else -> "本地向量库已开启"
            }
            if (enabled && current.memoryEmbeddingConfig.modelId != null) {
                processPendingVectors()
            }
        }
    }

    fun setMemoryEmbeddingModel(modelId: Uuid?) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    memoryEmbeddingConfig = settings.memoryEmbeddingConfig.copy(
                        modelId = modelId,
                        enabled = modelId != null,
                    ),
                )
            }
            _maintenanceMessage.value = if (modelId == null) {
                "已清除 Embedding 模型并关闭向量库；长期记忆抽取不受影响"
            } else {
                "已选择 Embedding 模型并启用向量库，正在处理已有记忆"
            }
            if (modelId != null) {
                processPendingVectors()
            }
        }
    }

    fun setMemoryEmbeddingDimensions(value: String) {
        val dimensions = value.trim().toIntOrNull()?.takeIf { it > 0 }
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(memoryEmbeddingConfig = settings.memoryEmbeddingConfig.copy(dimensions = dimensions))
            }
        }
    }

    fun embeddingModels(settings: Settings): List<Model> =
        settings.providers.flatMap { provider -> provider.models }
            .filter { model -> model.type == ModelType.EMBEDDING }

    private fun memoryVectorBlocker(settings: Settings): String? {
        val config = settings.memoryEmbeddingConfig
        if (!config.enabled) {
            return "向量化未开始：本地向量库尚未启用；长期记忆抽取仍可正常工作"
        }
        val modelId = config.modelId
            ?: return "向量化未开始：请先选择一个 Embedding 模型"
        val model = settings.findModelById(modelId)
            ?: return "向量化未开始：已选择的 Embedding 模型不存在，请重新选择"
        if (model.type != ModelType.EMBEDDING) {
            return "向量化未开始：当前选择的不是 Embedding 模型"
        }
        if (model.findProvider(settings.providers) == null) {
            return "向量化未开始：Embedding 模型对应的提供商不存在或已被删除"
        }
        return null
    }
}

private fun collapseArchiveSources(memories: List<MemoryBankEntity>): List<MemoryBankEntity> {
    val coveredIds = memories.asSequence()
        .filter(MemoryBankEntity::isDailyArchive)
        .flatMap { it.sourceMemoryIds().asSequence() }
        .toSet()
    if (coveredIds.isEmpty()) return memories
    return memories.filter { memory -> memory.isDailyArchive() || memory.id !in coveredIds }
}

private fun MemoryBankEntity.isDailyArchive(): Boolean =
    type == "daily_summary" || memoryKind == "daily_archive"

private fun MemoryBankEntity.sourceMemoryIds(): List<Int> = runCatching {
    JsonInstant.decodeFromString(
        ListSerializer(String.serializer()),
        sourceMemoryIdsJson ?: relatedMemoryIdsJson.orEmpty(),
    ).mapNotNull(String::toIntOrNull)
}.getOrDefault(emptyList())

private fun MemoryBankEntity.canArchiveUnderDailySummary(): Boolean =
    id > 0 &&
        !deprecated &&
        !pinned &&
        type == "message" &&
        memoryKind !in DAILY_ARCHIVE_PROTECTED_KINDS

private val DAILY_ARCHIVE_PROTECTED_KINDS = setOf(
    "user_boundary",
    "promise",
    "correction",
)
