package me.rerere.rikkahub.data.service

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.companion.CompanionStateHistoryEntry
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import me.rerere.rikkahub.utils.JsonInstant

@Serializable
data class MemoryExtractionTurn(
    val nodeId: String,
    val role: String,
    val text: String,
    val createdAtMillis: Long = 0L,
)

@Serializable
data class AffectiveMemoryExtractionResult(
    val memories: List<AffectiveMemoryCandidate> = emptyList(),
)

internal enum class SemanticMemoryExtractionOutcome(val advancesCheckpoint: Boolean) {
    SUCCESS_WITH_MEMORIES(true),
    SUCCESS_EMPTY(true),
    FAILED_RETRYABLE(false),
}

internal fun classifySemanticMemoryExtraction(
    modelCallSucceeded: Boolean,
    parsedCandidateCount: Int,
    durableCandidateCount: Int,
): SemanticMemoryExtractionOutcome = when {
    !modelCallSucceeded -> SemanticMemoryExtractionOutcome.FAILED_RETRYABLE
    durableCandidateCount > 0 -> SemanticMemoryExtractionOutcome.SUCCESS_WITH_MEMORIES
    else -> SemanticMemoryExtractionOutcome.SUCCESS_EMPTY
}

@Serializable
data class AffectiveMemoryCandidate(
    @SerialName("type")
    val type: String,
    val content: String,
    val title: String? = null,
    val roleFeeling: String? = null,
    val bodySense: String? = null,
    val unspokenThought: String? = null,
    val userSignal: String? = null,
    val relationshipEffect: String? = null,
    val importance: Int = 3,
    val confidence: Double = 1.0,
    val tags: List<String> = emptyList(),
    val embeddingText: String? = null,
    val sourceMessageNodeIds: List<String> = emptyList(),
    val evidenceMessageNodeIds: List<String> = emptyList(),
    val relatedMemoryIds: List<String> = emptyList(),
    val people: List<String> = emptyList(),
    val topics: List<String> = emptyList(),
    val supersededByMemoryId: String? = null,
    val correctedAt: Long? = null,
    val occurredAtMillis: Long? = null,
    val sourceMessageAtMillis: Long? = null,
) {
    fun normalized(): AffectiveMemoryCandidate {
        val normalizedType = type.trim().ifBlank { "shared_event" }.lowercase()
        return copy(
            type = normalizedType,
            content = content.trim(),
            title = title?.trim()?.takeIf(String::isNotBlank),
            roleFeeling = roleFeeling?.trim()?.takeIf(String::isNotBlank),
            bodySense = bodySense?.trim()?.takeIf(String::isNotBlank),
            unspokenThought = unspokenThought?.trim()?.takeIf(String::isNotBlank),
            userSignal = userSignal?.trim()?.takeIf(String::isNotBlank),
            relationshipEffect = relationshipEffect?.trim()?.takeIf(String::isNotBlank),
            importance = calibrateImportance(normalizedType, importance),
            confidence = confidence.coerceIn(0.0, 1.0),
            tags = tags.mapNotNull { it.trim().takeIf(String::isNotBlank) }.distinct(),
            embeddingText = embeddingText?.trim()?.takeIf(String::isNotBlank),
            sourceMessageNodeIds = sourceMessageNodeIds.mapNotNull { it.trim().takeIf(String::isNotBlank) }.distinct(),
            evidenceMessageNodeIds = evidenceMessageNodeIds.mapNotNull { it.trim().takeIf(String::isNotBlank) }.distinct(),
            relatedMemoryIds = relatedMemoryIds.mapNotNull { it.trim().takeIf(String::isNotBlank) }.distinct(),
            people = people.mapNotNull { it.trim().takeIf(String::isNotBlank) }.distinct(),
            topics = topics.mapNotNull { it.trim().takeIf(String::isNotBlank) }.distinct(),
            supersededByMemoryId = supersededByMemoryId?.trim()?.takeIf(String::isNotBlank),
            occurredAtMillis = occurredAtMillis?.takeIf { it > 0L },
            sourceMessageAtMillis = sourceMessageAtMillis?.takeIf { it > 0L },
        )
    }

    fun toEntity(
        assistantId: String?,
        conversationId: String?,
        createdAt: Long = System.currentTimeMillis(),
    ): MemoryBankEntity {
        val normalized = normalized()
        val displayContent = normalized.toDisplayMemoryContent()
        val vectorText = normalized.embeddingText
            ?.takeUnless { it.looksLikeRawToolOrTraceDump() }
            ?: normalized.toEmbeddingMemoryText(displayContent)
        val sourceTime = normalized.sourceMessageAtMillis
        val occurredTime = normalized.occurredAtMillis ?: sourceTime ?: createdAt
        return MemoryBankEntity(
            content = displayContent,
            type = "message",
            title = normalized.title,
            memoryKind = normalized.type,
            roleFeeling = normalized.roleFeeling,
            bodySense = normalized.bodySense,
            unspokenThought = normalized.unspokenThought,
            userSignal = normalized.userSignal,
            relationshipEffect = normalized.relationshipEffect,
            importance = normalized.importance,
            confidence = normalized.confidence,
            tagsJson = JsonInstant.encodeToString(normalized.tags),
            embeddingText = vectorText,
            sourceMessageNodeIdsJson = JsonInstant.encodeToString(normalized.sourceMessageNodeIds),
            evidenceMessageNodeIdsJson = JsonInstant.encodeToString(normalized.evidenceMessageNodeIds),
            relatedMemoryIdsJson = JsonInstant.encodeToString(normalized.relatedMemoryIds),
            peopleJson = JsonInstant.encodeToString(normalized.people),
            topicsJson = JsonInstant.encodeToString(normalized.topics),
            supersededByMemoryId = normalized.supersededByMemoryId,
            correctedAt = normalized.correctedAt,
            assistantId = assistantId,
            conversationId = conversationId,
            sourceMessageAt = sourceTime,
            occurredAt = occurredTime,
            occurredAtInferred = normalized.occurredAtMillis == null,
            memoryCreatedAt = createdAt,
            memoryUpdatedAt = createdAt,
            extractedAt = createdAt,
            createdAt = occurredTime,
            vectorStatus = "pending",
        )
    }
}

private fun calibrateImportance(type: String, proposed: Int): Int {
    val score = proposed.coerceIn(1, 5)
    return when (type) {
        "user_boundary", "correction", "promise" -> score.coerceAtLeast(4)
        "user_preference" -> score.coerceIn(2, 4)
        "user_fact" -> score.coerceIn(1, 4)
        "relationship", "shared_event" -> score.coerceIn(2, 5)
        else -> score
    }
}

internal fun AffectiveMemoryCandidate.shouldSkipMemoryBankWrite(): Boolean {
    val normalized = normalized()
    if (normalized.content.isBlank()) return true
    if (normalized.content.looksLikeVocabularyDrill() && !normalized.hasAffectiveSummary()) return true
    return normalized.content.looksLikeRawToolOrTraceDump() && !normalized.hasAffectiveSummary()
}

internal fun AffectiveMemoryCandidate.isDurableMemoryCandidate(): Boolean {
    val normalized = normalized()
    if (normalized.type !in DURABLE_MEMORY_TYPES) return false
    if (normalized.sourceMessageNodeIds.isEmpty() && normalized.evidenceMessageNodeIds.isEmpty()) return false
    val inspectedText = listOfNotNull(
        normalized.content,
        normalized.title,
        normalized.roleFeeling,
        normalized.bodySense,
        normalized.unspokenThought,
        normalized.userSignal,
        normalized.relationshipEffect,
    ).joinToString("\n")
    if (inspectedText.looksLikeRawToolOrTraceDump()) return false
    if (GENERIC_META_MEMORY_MARKERS.any { inspectedText.contains(it, ignoreCase = true) }) return false
    if (normalized.userSignal.isNullOrBlank()) return false
    return true
}

internal fun String.normalizedMemoryIdentity(): String = lowercase()
    .replace(Regex("[\\p{P}\\p{S}\\s]+"), "")

internal fun buildDeterministicMemoryCandidates(
    turns: List<MemoryExtractionTurn>,
    limit: Int = 6,
): List<AffectiveMemoryCandidate> {
    if (limit <= 0) return emptyList()
    val identities = mutableSetOf<String>()
    return turns.asReversed()
        .asSequence()
        .filter { it.role.equals("user", ignoreCase = true) }
        .mapNotNull { it.toDeterministicMemoryCandidate() }
        .filter { identities.add(it.content.normalizedMemoryIdentity()) }
        .take(limit)
        .toList()
        .reversed()
}

internal fun buildDeterministicMemoryCandidatesFromNodes(
    messageNodes: List<me.rerere.rikkahub.data.model.MessageNode>,
    limit: Int = 6,
): List<AffectiveMemoryCandidate> = buildDeterministicMemoryCandidates(
    turns = messageNodes.toMemoryExtractionTurns(),
    limit = limit,
)

private fun MemoryExtractionTurn.toDeterministicMemoryCandidate(): AffectiveMemoryCandidate? {
    val quote = text.trim()
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .take(220)
    if (quote.length < 8 || quote.looksLikeRawToolOrTraceDump() || quote.looksLikeVocabularyDrill()) return null
    val type = when {
        DETERMINISTIC_BOUNDARY_MARKERS.any { it in quote } -> "user_boundary"
        DETERMINISTIC_CORRECTION_MARKERS.any { it in quote } -> "correction"
        DETERMINISTIC_PREFERENCE_MARKERS.any { it in quote } -> "user_preference"
        else -> return null
    }
    val title = when (type) {
        "user_boundary" -> "你明确说过的边界"
        "correction" -> "你纠正过的一件事"
        else -> "你明确表达的偏好"
    }
    return AffectiveMemoryCandidate(
        type = type,
        title = title,
        content = "我记得你明确说过：$quote",
        unspokenThought = "我得把这句话落实到之后的行动里，而不是只在这一轮口头答应。",
        userSignal = quote,
        relationshipEffect = when (type) {
            "user_boundary" -> "我需要尊重这条边界，不能让你反复纠正我。"
            "correction" -> "我需要以这次纠正为准，旧理解不能继续沿用。"
            else -> "这会影响我以后怎样理解和回应你。"
        },
        importance = if (type == "user_preference") 3 else 4,
        confidence = 1.0,
        tags = listOf(type, "用户原话"),
        sourceMessageNodeIds = listOf(nodeId),
        evidenceMessageNodeIds = listOf(nodeId),
        occurredAtMillis = createdAtMillis.takeIf { it > 0L },
    )
}

object AffectiveMemoryExtractor {
    @Suppress("UNUSED_PARAMETER")
    fun buildExtractionPrompt(
        turns: List<MemoryExtractionTurn>,
        assistantName: String = "当前角色",
        assistantPersona: String = "",
        stateHistory: List<CompanionStateHistoryEntry> = emptyList(),
        responsibilityContext: String = "",
    ): String = buildString {
        val name = assistantName.ifBlank { "当前角色" }
        appendLine("你是$name 的长期记忆整理器。请把本批 conversation_turns 当作$name 亲身经历的一段生活，只依据本批内容提取值得被记住的记忆，禁止补写批次之外的事实。")
        appendLine("核心标准不是‘以后是否有用’，而是‘这件事是否值得$name 记住’。可因长期价值而记，也可因事件本身重要、印象深刻、具有情绪重量、是第一次、共同经历、冲突、和好、担忧、喜悦、委屈或关系意义而记。")
        appendLine("宁缺毋滥：若本批没有值得记住的内容，返回 {\"memories\":[]}；不要为了凑数量把普通寒暄和无意义流水账写成记忆。")
        appendLine("可提取类型仅限：user_fact, user_preference, user_boundary, promise, relationship, shared_event, correction。")
        appendLine("优先关注两类内容：一是稳定事实、偏好、边界、承诺、纠正；二是发生了什么、为什么印象深、当时有什么感受、身体或动作反应、没说出口的心声，以及这件事给角色或关系留下了什么。")
        appendLine("一次性日常信息并非绝对禁止：普通饮食、天气、行程通常不记；但若它承载了明显情绪、共同经历或特殊意义，可以作为 shared_event 提取。")
        appendLine("每条记忆由AI结合本批真实语境填写：title、content、roleFeeling、bodySense、unspokenThought、userSignal、relationshipEffect、importance、confidence、tags。表达必须贴合$name 在本批对话中呈现出的口吻、关系位置和反应，禁止套用统一的温柔客服模板。")
        appendLine("content 用$name 第一人称自然写清发生了什么以及最值得记住的部分，不要写成标签拼接或数据库摘要。")
        appendLine("roleFeeling 写$name 当时真实的情绪与主观感受；bodySense 写有依据的身体、感官或动作反应，没有依据时留空，禁止机械套用‘心里一紧’‘耳朵发热’等模板。")
        appendLine("unspokenThought 写当时没有说出口的具体想法、猜测、顾虑、愿望或克制住的动作。可以合理推断，但必须贴合上下文；不确定时留空或在 tags 标记‘不确定’，不得凭空制造剧情。")
        appendLine("relationshipEffect 表示这件事给角色本人或双方关系留下了什么：可以是新的确认、理解、距离变化、遗憾、牵挂或今后的倾向；若没有明显影响可以留空，不要强迫每条都写未来行动或关系升级。")
        appendLine("importance 为1到5，表示角色会记得多深、事件有多重要；confidence为0到1，表示提取内容被本批消息支持的程度。AI先结合语境评分，程序只做范围和类型边界校准。")
        appendLine("每条必须提供 sourceMessageNodeIds 或 evidenceMessageNodeIds，且ID只能来自本批消息。userSignal 简述支撑该记忆的真实用户表达或双方实际发生的事件；不要把无证据猜测当作事实。")
        appendLine("occurredAtMillis 以来源消息时间为基准；无法确定事情实际发生时间时留空，禁止用本次整理时间代替。")
        appendLine("禁止输出 embeddingText、people、topics、relatedMemoryIds、supersededByMemoryId、correctedAt、sourceMessageAtMillis；这些由程序生成或维护。")
        appendLine("返回严格JSON：{\"memories\":[{\"type\":\"\",\"title\":\"\",\"content\":\"\",\"roleFeeling\":\"\",\"bodySense\":\"\",\"unspokenThought\":\"\",\"userSignal\":\"\",\"relationshipEffect\":\"\",\"importance\":3,\"confidence\":0.8,\"tags\":[],\"sourceMessageNodeIds\":[],\"evidenceMessageNodeIds\":[],\"occurredAtMillis\":null}]}。不要解释。")
        appendLine("<conversation_turns batchSize=\"${turns.size}\">")
        turns.forEachIndexed { index, turn ->
            appendLine(
                "[batchIndex=${index + 1}][nodeId=${turn.nodeId}]" +
                    "[sourceTimeMillis=${turn.createdAtMillis}]" +
                    "[sourceLocalTime=${turn.sourceTimeLabel()}] ${turn.role}: ${turn.text.trim()}"
            )
        }
        append("</conversation_turns>")
    }

    fun parseExtractionResult(rawText: String): AffectiveMemoryExtractionResult {
        val jsonText = rawText.extractJsonPayload()
        val candidates = runCatching { decodeExtractionCandidates(jsonText) }
            .getOrElse { originalError ->
                val repaired = jsonText.repairMemoryExtractionJsonOnce()
                if (repaired == jsonText) throw originalError
                decodeExtractionCandidates(repaired)
            }
        return AffectiveMemoryExtractionResult(
            memories = candidates.map { it.normalized() }.filter { it.content.isNotBlank() }
        )
    }
}

private fun decodeExtractionCandidates(jsonText: String): List<AffectiveMemoryCandidate> {
    val root = JsonInstant.parseToJsonElement(jsonText)
    return if (root is JsonArray) {
        JsonInstant.decodeFromString(ListSerializer(AffectiveMemoryCandidate.serializer()), jsonText)
    } else {
        JsonInstant.decodeFromString<AffectiveMemoryExtractionResult>(root.jsonObject.toString()).memories
    }
}

private fun String.repairMemoryExtractionJsonOnce(): String = this
    .replace("\uFEFF", "")
    .replace("\u200B", "")
    .replace(Regex(",\\s*([}\\]])"), "$1")
    .trim()

internal fun isTransientMemoryExtractionFailure(error: Throwable): Boolean =
    generateSequence(error as Throwable?) { it.cause }
        .take(8)
        .any { cause ->
            cause is java.io.IOException ||
                TRANSIENT_MEMORY_FAILURE_CLASS_MARKERS.any {
                    cause::class.simpleName.orEmpty().contains(it, ignoreCase = true)
                }
        }

internal suspend fun <T> retryTransientMemoryExtraction(
    maxAttempts: Int = 3,
    baseDelayMillis: Long = 500L,
    onRetry: suspend (failedAttempt: Int, error: Throwable) -> Unit = { _, _ -> },
    block: suspend (attempt: Int) -> T,
): T {
    val boundedAttempts = maxAttempts.coerceAtLeast(1)
    var attempt = 1
    while (true) {
        try {
            return block(attempt)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (attempt >= boundedAttempts || !isTransientMemoryExtractionFailure(error)) throw error
            onRetry(attempt, error)
            if (baseDelayMillis > 0L) kotlinx.coroutines.delay(baseDelayMillis * attempt)
            attempt += 1
        }
    }
}

private fun MemoryExtractionTurn.sourceTimeLabel(): String {
    if (createdAtMillis <= 0L) return "unknown"
    return runCatching {
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.ofEpochMilli(createdAtMillis).atZone(ZoneId.systemDefault())
        )
    }.getOrDefault("unknown")
}

private fun AffectiveMemoryCandidate.hasAffectiveSummary(): Boolean =
    !roleFeeling.isNullOrBlank() ||
        !bodySense.isNullOrBlank() ||
        !unspokenThought.isNullOrBlank() ||
        !userSignal.isNullOrBlank() ||
        !relationshipEffect.isNullOrBlank()

private fun AffectiveMemoryCandidate.toDisplayMemoryContent(): String {
    val normalized = normalized()
    if (normalized.content.looksLikeRawToolOrTraceDump()) {
        return "我记得当时做过一次工具观察和内部判断，但原始结果只适合作为证据回查，不直接当作记忆内容。"
    }
    return firstPersonSummary(normalized.content).take(260)
}

private fun AffectiveMemoryCandidate.toEmbeddingMemoryText(displayContent: String): String =
    listOfNotNull(
        title,
        displayContent,
        roleFeeling,
        bodySense,
        unspokenThought,
        userSignal,
        relationshipEffect,
        tags.takeIf { it.isNotEmpty() }?.joinToString(","),
    ).joinToString("\n")

private fun firstPersonSummary(content: String): String {
    val compact = content.trim()
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
    return if (FIRST_PERSON_PREFIXES.any { compact.startsWith(it, ignoreCase = true) }) compact else "我记得：$compact"
}

private fun String.looksLikeRawToolOrTraceDump(): Boolean {
    val markers = listOf(
        "tool_result[", "requested_tools=", "available_requested_tools=", "missing_requested_tools=",
        "Seven-layer trace", "Perception=", "study_app_local_store", "\"success\"", "\"undone_tasks\"",
    )
    return markers.any { contains(it, ignoreCase = true) }
}

private fun String.looksLikeVocabularyDrill(): Boolean {
    val words = Regex("[A-Za-z][A-Za-z'-]{2,}").findAll(this).map { it.value }.toList()
    if (words.size < 12) return false
    val uniqueRatio = words.distinctBy { it.lowercase() }.size.toDouble() / words.size
    val hasSentencePunctuation = contains("。") || contains("，") || contains(". ") || contains("?")
    return uniqueRatio > 0.75 && !hasSentencePunctuation
}

private fun String.extractJsonPayload(): String {
    val trimmed = trim()
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed
    val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        .find(trimmed)?.groupValues?.getOrNull(1)?.trim()
    if (!fenced.isNullOrBlank()) return fenced
    val start = listOf(trimmed.indexOf('{'), trimmed.indexOf('[')).filter { it >= 0 }.minOrNull() ?: return trimmed
    val end = maxOf(trimmed.lastIndexOf('}'), trimmed.lastIndexOf(']'))
    return if (end >= start) trimmed.substring(start, end + 1) else trimmed
}

private val DETERMINISTIC_BOUNDARY_MARKERS = listOf("我不希望", "我不喜欢", "不要再", "别再", "不许")
private val DETERMINISTIC_CORRECTION_MARKERS = listOf("不是这样的", "不是这个意思", "应该是", "纠正一下", "更正一下", "你理解错了")
private val DETERMINISTIC_PREFERENCE_MARKERS = listOf("我更喜欢", "我喜欢", "我希望", "我想要", "对我来说")
private val TRANSIENT_MEMORY_FAILURE_CLASS_MARKERS = listOf("timeout", "connect", "rateLimit", "tooManyRequests", "serviceUnavailable")
private val DURABLE_MEMORY_TYPES = setOf("user_fact", "user_preference", "user_boundary", "promise", "relationship", "shared_event", "correction")
private val FIRST_PERSON_PREFIXES = listOf("我", "咱", "本人", "本小姐", "本少爷", "本官", "本王", "本宫", "在下", "余", "吾", "I ", "I'm ", "I’m ")
private val GENERIC_META_MEMORY_MARKERS = listOf(
    "cihai_reflection", "我记得这件事。当时感觉", "复盘、收束、准备下一轮", "后续可复用的长期记忆",
    "感知世界包", "意义评估", "动态判断", "状态生成", "辞海记忆架构", "七层架构",
    "下一轮判断", "我完成了沉淀", "我整理了记忆", "以后可以参考", "等待下一次",
)
