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
        appendLine("你是$name 的长期记忆整理器。只依据本批 conversation_turns 提取记忆，禁止使用或补写批次之外的信息。")
        appendLine("宁缺毋滥：没有会影响未来交流、照顾、承诺履行或关系连续性的内容，就返回 {\"memories\":[]}。不要为了凑数量提取日常流水账。")
        appendLine("可提取类型仅限：user_fact, user_preference, user_boundary, promise, relationship, shared_event, correction。")
        appendLine("优先提取：稳定事实与偏好、明确边界、承诺及结果、用户纠正、具有长期意义的共同事件、已有充分证据的关系变化。")
        appendLine("禁止提取：普通寒暄、一次性饮食天气、重复工具结果、单词表、学习材料原文、无证据猜测、模型自造的人设或动作。")
        appendLine("每条记忆应由AI贴合本批真实语境填写：title、content、roleFeeling、bodySense、unspokenThought、userSignal、relationshipEffect、importance、confidence、tags。")
        appendLine("content、roleFeeling、bodySense、unspokenThought、relationshipEffect 必须使用$name 第一人称。bodySense 没有自然依据时留空，禁止机械套用心里一紧、耳朵发热等模板。")
        appendLine("unspokenThought 应写当时未说出口的具体判断、顾虑或意图；只能从本批对话推断，无法确定时留空或在 tags 标记不确定。")
        appendLine("relationshipEffect 写这件事以后会怎样影响我的理解、回应或行动；不是每条都必须造成关系升级。")
        appendLine("importance 为1到5，confidence为0到1；结合语境判断。程序会做边界校准，但不要虚高。")
        appendLine("每条必须提供 sourceMessageNodeIds 或 evidenceMessageNodeIds，并且这些ID只能来自本批消息。userSignal 必须简述真实用户证据。")
        appendLine("occurredAtMillis 以来源消息时间为准；不能确定具体事件时间时留空。禁止用整理时间代替发生时间。")
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
