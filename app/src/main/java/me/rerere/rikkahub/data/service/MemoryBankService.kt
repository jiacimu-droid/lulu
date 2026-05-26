package me.rerere.rikkahub.data.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.MemoryBankDAO
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import me.rerere.rikkahub.data.db.entity.MemoryVectorEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

/**
 * 记忆库服务
 * 负责消息存储、向量化、召回、阶段总结和每日总结
 * 搜索优先级：HNSW本地向量索引 → 网关远程搜索 → 关键词降级搜索
 */
class MemoryBankService(
    private val memoryBankDAO: MemoryBankDAO,
    private val okHttpClient: OkHttpClient,
    private val context: Context,
) {
    companion object {
        private const val TAG = "MemoryBankService"
        private const val MAX_EMBEDDING_BATCH = 20
    }

    /** 本地 HNSW 向量索引 */
    private val hnswIndex = HNSWIndex(context)

    /** 索引是否已加载 */
    private var indexLoaded = false

    // ===== 插件配置（由外部从插件配置更新） =====
    var embeddingApiKey: String = ""
    var embeddingBaseUrl: String = "https://api.openai.com/v1"
    var embeddingModel: String = "text-embedding-3-small"
    var summaryApiKey: String = ""
    var summaryBaseUrl: String = "https://api.openai.com/v1"
    var summaryModel: String = "gpt-4o-mini"
    var recallCount: Int = 5
    var recentMessageCount: Int = 10
    var phaseSummaryCount: Int = 3
    var dailySummaryCount: Int = 3
    var phaseSummaryTrigger: Int = 20
    var maxRetryCount: Int = 3
    var autoStoreMessages: Boolean = true
    var autoRecall: Boolean = true
    var autoPhaseSummary: Boolean = true
    var autoDailySummary: Boolean = true

    // ===== 存储操作 =====

    /**
     * 存储一条消息到记忆库
     */
    suspend fun storeMessage(
        content: String,
        role: String,
        conversationId: String? = null,
        assistantId: String? = null,
    ): MemoryBankEntity = withContext(Dispatchers.IO) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val entity = MemoryBankEntity(
            content = content,
            type = "message",
            conversationId = conversationId,
            assistantId = assistantId,
            role = role,
            dateGroup = today,
            vectorStatus = "pending",
        )
        val id = memoryBankDAO.insertMemory(entity)
        val saved = entity.copy(id = id.toInt())

        // 尝试向量化（不阻塞当前流程）
        try {
            vectorizeMemory(saved)
        } catch (e: Exception) {
            Log.w(TAG, "向量化失败，将在后台重试: ${e.message}")
        }

        saved
    }

    /**
     * 手动保存一条记忆
     */
    suspend fun saveManualMemory(
        content: String,
        assistantId: String? = null,
    ): MemoryBankEntity = withContext(Dispatchers.IO) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val entity = MemoryBankEntity(
            content = content,
            type = "manual",
            assistantId = assistantId,
            dateGroup = today,
            vectorStatus = "pending",
        )
        val id = memoryBankDAO.insertMemory(entity)
        val saved = entity.copy(id = id.toInt())

        try {
            vectorizeMemory(saved)
        } catch (e: Exception) {
            Log.w(TAG, "向量化失败: ${e.message}")
        }

        saved
    }

    // ===== 召回操作 =====

    /**
     * 确保HNSW索引已加载
     */
    private suspend fun ensureIndexLoaded() {
        if (!indexLoaded) {
            hnswIndex.load()
            indexLoaded = true
            Log.d(TAG, "HNSW索引已加载: ${hnswIndex.size()} 个节点")
        }
    }

    /**
     * 重建HNSW索引（从数据库全量加载向量）
     */
    suspend fun rebuildIndex() = withContext(Dispatchers.IO) {
        hnswIndex.clear()
        val allVectors = memoryBankDAO.getAllVectorsWithMemories()
        for (vm in allVectors) {
            val vector = parseVectorString(vm.vector)
            if (vector.isNotEmpty()) {
                hnswIndex.add(vm.memoryId, vector.toFloatArray())
            }
        }
        hnswIndex.save()
        indexLoaded = true
        Log.i(TAG, "HNSW索引重建完成: ${hnswIndex.size()} 个节点")
    }

    /**
     * 从记忆库中召回相关记忆
     * 搜索优先级：HNSW本地向量索引 → 暴力向量搜索 → 关键词降级搜索
     */
    suspend fun recallMemories(
        query: String,
        count: Int = recallCount,
    ): List<MemoryBankEntity> = withContext(Dispatchers.IO) {
        try {
            ensureIndexLoaded()
            val queryVector = generateEmbedding(listOf(query)).first()

            // 优先级1: 使用HNSW索引搜索
            if (hnswIndex.size() > 0) {
                val results = hnswIndex.search(queryVector.toFloatArray(), k = count)
                if (results.isNotEmpty()) {
                    Log.d(TAG, "HNSW索引搜索命中: ${results.size} 条, 最高分: ${results.first().score}")
                    return@withContext results.mapNotNull { r ->
                        memoryBankDAO.getMemoryById(r.id)
                    }
                }
            }

            // 优先级2: 暴力向量搜索（索引为空时降级）
            val allVectors = memoryBankDAO.getAllVectorsWithMemories()
            if (allVectors.isNotEmpty()) {
                val scored = allVectors.mapNotNull { vm ->
                    val vector = parseVectorString(vm.vector)
                    if (vector.size != queryVector.size) return@mapNotNull null
                    val similarity = cosineSimilarity(queryVector, vector)
                    vm to similarity
                }.sortedByDescending { it.second }
                    .take(count)
                    .map { it.first }

                return@withContext scored.mapNotNull { vm ->
                    memoryBankDAO.getMemoryById(vm.memoryId)
                }
            }

            // 优先级3: 关键词搜索降级
            memoryBankDAO.searchMemoriesByKeyword(query, count)
        } catch (e: Exception) {
            Log.e(TAG, "向量召回失败，回退到关键词搜索: ${e.message}")
            memoryBankDAO.searchMemoriesByKeyword(query, count)
        }
    }

    /**
     * 搜索记忆（用于管理）
     */
    suspend fun searchMemories(
        keyword: String = "",
        type: String = "",
        limit: Int = 20,
        assistantId: String? = null,
    ): List<MemoryBankEntity> = withContext(Dispatchers.IO) {
        when {
            keyword.isNotEmpty() && type.isNotEmpty() ->
                memoryBankDAO.searchMemoriesByKeywordAndType(keyword, type, limit)
            keyword.isNotEmpty() ->
                memoryBankDAO.searchMemoriesByKeyword(keyword, limit)
            assistantId != null && type.isNotEmpty() ->
                memoryBankDAO.getMemoriesByAssistantAndTypeLimit(assistantId, type, limit)
            assistantId != null ->
                memoryBankDAO.getMemoriesByAssistant(assistantId)
            type.isNotEmpty() ->
                memoryBankDAO.getMemoriesByTypeLimit(type, limit)
            else ->
                memoryBankDAO.getRecentMemories(limit)
        }
    }

    /**
     * 获取所有助手ID列表
     */
    suspend fun getAssistantIds(): List<String> = withContext(Dispatchers.IO) {
        memoryBankDAO.getDistinctAssistantIds()
    }

    /**
     * 获取今日阶段总结
     */
    suspend fun getTodayPhaseSummaries(assistantId: String? = null): List<MemoryBankEntity> = withContext(Dispatchers.IO) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        if (assistantId != null) {
            memoryBankDAO.getMemoriesByAssistantTypeAndDateGroup(assistantId, "phase_summary", today)
        } else {
            memoryBankDAO.getMemoriesByTypeAndDateGroup("phase_summary", today)
        }
    }

    /**
     * 获取每日总结（日记）
     */
    suspend fun getDailySummaries(assistantId: String? = null, limit: Int = 20): List<MemoryBankEntity> = withContext(Dispatchers.IO) {
        if (assistantId != null) {
            memoryBankDAO.getMemoriesByAssistantAndTypeLimit(assistantId, "daily_summary", limit)
        } else {
            memoryBankDAO.getMemoriesByTypeLimit("daily_summary", limit)
        }
    }

    /**
     * 使用 COUNT 查询更新统计信息
     */
    suspend fun getStats(assistantId: String? = null): MemoryStats = withContext(Dispatchers.IO) {
        MemoryStats(
            total = if (assistantId != null) memoryBankDAO.getCountByAssistant(assistantId) else memoryBankDAO.getTotalCount(),
            messageCount = if (assistantId != null) memoryBankDAO.getCountByAssistantAndType(assistantId, "message") else memoryBankDAO.getCountByType("message"),
            summaryCount = memoryBankDAO.getSummaryCount(),
            manualCount = memoryBankDAO.getCountByType("manual"),
            vectorizedCount = memoryBankDAO.getCountByVectorStatus("done"),
            pendingCount = memoryBankDAO.getCountByVectorStatus("pending"),
            failedCount = memoryBankDAO.getCountByVectorStatus("failed"),
        )
    }

    data class MemoryStats(
        val total: Int = 0,
        val messageCount: Int = 0,
        val summaryCount: Int = 0,
        val manualCount: Int = 0,
        val vectorizedCount: Int = 0,
        val pendingCount: Int = 0,
        val failedCount: Int = 0,
    )

    /**
     * 删除记忆
     */
    suspend fun deleteMemory(id: Int) = withContext(Dispatchers.IO) {
        memoryBankDAO.deleteVectorByMemoryId(id)
        memoryBankDAO.deleteMemoryById(id)
    }

    /**
     * 获取近期原始消息
     */
    suspend fun getRecentMessages(count: Int = recentMessageCount): List<MemoryBankEntity> {
        return memoryBankDAO.getMemoriesByTypeLimit("message", count)
    }

    /**
     * 获取近期阶段总结
     */
    suspend fun getRecentPhaseSummaries(count: Int = phaseSummaryCount): List<MemoryBankEntity> {
        return memoryBankDAO.getMemoriesByTypeLimit("phase_summary", count)
    }

    /**
     * 获取近期每日总结
     */
    suspend fun getRecentDailySummaries(count: Int = dailySummaryCount): List<MemoryBankEntity> {
        val dateGroups = memoryBankDAO.getRecentDateGroups(count)
        return dateGroups.flatMap { dateGroup ->
            memoryBankDAO.getMemoriesByDateGroupAndType(dateGroup, "daily_summary")
        }
    }

    // ===== 阶段总结 =====

    /**
     * 检查并执行阶段总结（懒执行 - 下次发消息时检查阈值并补做）
     * 返回 true 表示执行了阶段总结
     */
    suspend fun checkAndPerformPhaseSummary(assistantId: String? = null): Boolean {
        if (!autoPhaseSummary) return false

        val lastSummaries = memoryBankDAO.getMemoriesByTypeLimit("phase_summary", 1)
        val lastSummaryTime = lastSummaries.firstOrNull()?.createdAt ?: 0L

        val messageCountSinceLastSummary = memoryBankDAO.getMessageCountSince(lastSummaryTime)

        return if (messageCountSinceLastSummary >= phaseSummaryTrigger) {
            performPhaseSummary(assistantId)
            true
        } else {
            false
        }
    }

    /**
     * 执行阶段总结
     */
    suspend fun performPhaseSummary(assistantId: String? = null) = withContext(Dispatchers.IO) {
        try {
            val lastSummaries = memoryBankDAO.getMemoriesByTypeLimit("phase_summary", 1)
            val lastSummaryTime = lastSummaries.firstOrNull()?.createdAt ?: 0L

            val recentMessages = memoryBankDAO.getRecentMemories(phaseSummaryTrigger * 2)
                .filter { it.type == "message" && it.createdAt > lastSummaryTime }

            if (recentMessages.isEmpty()) return@withContext

            val messagesText = recentMessages.joinToString("\n") { msg ->
                "[${msg.role ?: "unknown"}]: ${msg.content}"
            }

            val summary = generateSummary(
                prompt = "请总结以下对话的关键信息，包括用户偏好、重要事实、决策和计划。用简洁的要点形式输出：\n\n$messagesText",
                systemPrompt = "你是一个对话总结助手。请提取对话中的关键信息，用简洁的要点总结。只输出总结内容，不要额外解释。"
            )

            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val entity = MemoryBankEntity(
                content = summary,
                type = "phase_summary",
                assistantId = assistantId,
                dateGroup = today,
                vectorStatus = "pending",
            )
            val id = memoryBankDAO.insertMemory(entity)
            val saved = entity.copy(id = id.toInt())

            try {
                vectorizeMemory(saved)
            } catch (e: Exception) {
                Log.w(TAG, "阶段总结向量化失败: ${e.message}")
            }

            Log.i(TAG, "阶段总结完成: $summary")
        } catch (e: Exception) {
            Log.e(TAG, "阶段总结失败: ${e.message}")
        }
    }

    // ===== 每日总结 =====

    /**
     * 执行每日总结（由 WorkManager 定时任务调用）
     */
    suspend fun performDailySummary(assistantId: String? = null) = withContext(Dispatchers.IO) {
        if (!autoDailySummary) return@withContext

        try {
            val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)

            // 检查是否已经生成过该日的总结
            val existingDaily = memoryBankDAO.getMemoriesByDateGroupAndType(yesterday, "daily_summary")
            if (existingDaily.isNotEmpty()) {
                Log.i(TAG, "昨日($yesterday)总结已存在，跳过")
                return@withContext
            }

            // 获取昨日的所有消息
            val yesterdayMessages = memoryBankDAO.getMemoriesByDateGroup(yesterday)
                .filter { it.type == "message" }

            if (yesterdayMessages.isEmpty()) {
                Log.i(TAG, "昨日($yesterday)无消息，跳过总结")
                return@withContext
            }

            // 获取昨日的阶段总结
            val yesterdayPhaseSummaries = memoryBankDAO.getMemoriesByDateGroupAndType(yesterday, "phase_summary")

            val contextText = buildString {
                append("=== 昨日对话消息 ===\n")
                yesterdayMessages.forEach { msg ->
                    append("[${msg.role ?: "unknown"}]: ${msg.content}\n")
                }
                if (yesterdayPhaseSummaries.isNotEmpty()) {
                    append("\n=== 昨日阶段总结 ===\n")
                    yesterdayPhaseSummaries.forEach { s ->
                        append("${s.content}\n")
                    }
                }
            }

            val summary = generateSummary(
                prompt = "请总结以下一整天的对话内容，提取最重要的信息，包括：\n1. 用户的主要话题和关注点\n2. 重要的决定和计划\n3. 用户的偏好和习惯\n4. 需要长期记住的关键事实\n\n$contextText",
                systemPrompt = "你是一个每日对话总结助手。请生成一份结构化的每日总结，重点提取需要长期记住的信息。用简洁的要点形式输出。"
            )

            val entity = MemoryBankEntity(
                content = summary,
                type = "daily_summary",
                assistantId = assistantId,
                dateGroup = yesterday,
                vectorStatus = "pending",
            )
            val id = memoryBankDAO.insertMemory(entity)
            val saved = entity.copy(id = id.toInt())

            try {
                vectorizeMemory(saved)
            } catch (e: Exception) {
                Log.w(TAG, "每日总结向量化失败: ${e.message}")
            }

            Log.i(TAG, "每日总结完成($yesterday): $summary")
        } catch (e: Exception) {
            Log.e(TAG, "每日总结失败: ${e.message}")
        }
    }

    // ===== 向量化操作 =====

    /**
     * 对单条记忆进行向量化
     */
    private suspend fun vectorizeMemory(memory: MemoryBankEntity) {
        if (embeddingApiKey.isBlank()) {
            Log.w(TAG, "向量化API Key未配置，跳过")
            return
        }

        try {
            val embeddings = generateEmbedding(listOf(memory.content))
            val vectorStr = embeddings.first().joinToString(",") { it.toString() }

            val vectorEntity = MemoryVectorEntity(
                memoryId = memory.id,
                vector = vectorStr,
                dimensions = embeddings.first().size,
                model = embeddingModel,
            )
            memoryBankDAO.insertVector(vectorEntity)
            memoryBankDAO.updateVectorStatus(memory.id, "done", memory.vectorRetryCount)

            Log.d(TAG, "向量化完成: memoryId=${memory.id}, dimensions=${vectorEntity.dimensions}")
        } catch (e: Exception) {
            val newRetryCount = memory.vectorRetryCount + 1
            val newStatus = if (newRetryCount >= maxRetryCount) "failed" else "pending"
            memoryBankDAO.updateVectorStatus(memory.id, newStatus, newRetryCount)
            Log.w(TAG, "向量化失败(memoryId=${memory.id}, retry=$newRetryCount): ${e.message}")
            throw e
        }
    }

    /**
     * 批量处理待向量化的记忆（由后台任务调用）
     */
    suspend fun processPendingVectors() = withContext(Dispatchers.IO) {
        val pendingMemories = memoryBankDAO.getPendingVectorMemories(maxRetryCount, MAX_EMBEDDING_BATCH)

        for (memory in pendingMemories) {
            try {
                vectorizeMemory(memory)
            } catch (e: Exception) {
                Log.w(TAG, "批量向量化跳过 memoryId=${memory.id}: ${e.message}")
            }
        }
    }

    // ===== 向量计算 =====

    /**
     * 调用 Embedding API 生成向量
     */
    private suspend fun generateEmbedding(texts: List<String>): List<List<Float>> =
        withContext(Dispatchers.IO) {
            if (embeddingApiKey.isBlank()) error("Embedding API Key 未配置")

            val json = JSONObject().apply {
                put("model", embeddingModel)
                put("input", JSONArray(texts))
            }

            val request = Request.Builder()
                .url("${embeddingBaseUrl.trimEnd('/')}/embeddings")
                .addHeader("Authorization", "Bearer $embeddingApiKey")
                .addHeader("Content-Type", "application/json")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                error("Embedding API 请求失败: ${response.code} ${response.body?.string()}")
            }

            val responseBody = response.body?.string() ?: error("Empty response body")
            val responseJson = JSONObject(responseBody)
            val dataArray = responseJson.getJSONArray("data")

            (0 until dataArray.length()).map { i ->
                val embeddingArray = dataArray.getJSONObject(i).getJSONArray("embedding")
                (0 until embeddingArray.length()).map { j ->
                    embeddingArray.getDouble(j).toFloat()
                }
            }
        }

    /**
     * 调用总结模型生成总结
     */
    private suspend fun generateSummary(prompt: String, systemPrompt: String): String =
        withContext(Dispatchers.IO) {
            if (summaryApiKey.isBlank()) error("总结服务 API Key 未配置")

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }

            val json = JSONObject().apply {
                put("model", summaryModel)
                put("messages", messages)
                put("temperature", 0.3)
                put("max_tokens", 1000)
            }

            val request = Request.Builder()
                .url("${summaryBaseUrl.trimEnd('/')}/chat/completions")
                .addHeader("Authorization", "Bearer $summaryApiKey")
                .addHeader("Content-Type", "application/json")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                error("总结 API 请求失败: ${response.code} ${response.body?.string()}")
            }

            val responseBody = response.body?.string() ?: error("Empty response body")
            val responseJson = JSONObject(responseBody)
            responseJson.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }

    /**
     * 从逗号分隔字符串解析向量
     */
    private fun parseVectorString(vectorStr: String): List<Float> {
        return vectorStr.split(",").map { it.trim().toFloat() }
    }

    /**
     * 计算余弦相似度
     */
    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0f || normB == 0f) return 0f
        return dotProduct / (sqrt(normA) * sqrt(normB))
    }

    /**
     * 从插件配置 Map 更新服务配置
     */
    fun updateFromConfig(config: Map<String, Any?>) {
        embeddingApiKey = (config["embedding_api_key"] as? String) ?: embeddingApiKey
        embeddingBaseUrl = (config["embedding_base_url"] as? String) ?: embeddingBaseUrl
        embeddingModel = (config["embedding_model"] as? String) ?: embeddingModel
        summaryApiKey = (config["summary_api_key"] as? String) ?: summaryApiKey
        summaryBaseUrl = (config["summary_base_url"] as? String) ?: summaryBaseUrl
        summaryModel = (config["summary_model"] as? String) ?: summaryModel
        recallCount = (config["recall_count"] as? Number)?.toInt() ?: recallCount
        recentMessageCount = (config["recent_message_count"] as? Number)?.toInt() ?: recentMessageCount
        phaseSummaryCount = (config["phase_summary_count"] as? Number)?.toInt() ?: phaseSummaryCount
        dailySummaryCount = (config["daily_summary_count"] as? Number)?.toInt() ?: dailySummaryCount
        phaseSummaryTrigger = (config["phase_summary_trigger"] as? Number)?.toInt() ?: phaseSummaryTrigger
        maxRetryCount = (config["max_retry_count"] as? Number)?.toInt() ?: maxRetryCount
        autoStoreMessages = (config["auto_store_messages"] as? Boolean) ?: autoStoreMessages
        autoRecall = (config["auto_recall"] as? Boolean) ?: autoRecall
        autoPhaseSummary = (config["auto_phase_summary"] as? Boolean) ?: autoPhaseSummary
        autoDailySummary = (config["auto_daily_summary"] as? Boolean) ?: autoDailySummary
    }
}