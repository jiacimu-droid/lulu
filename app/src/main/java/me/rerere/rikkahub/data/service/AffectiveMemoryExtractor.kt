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
        val normalizedContent = content.trim()
        val normalizedSourceIds = sourceMessageNodeIds
            .mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .distinct()
        val normalizedEvidenceIds = evidenceMessageNodeIds
            .mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .distinct()
            .ifEmpty { normalizedSourceIds }
        return copy(
            type = normalizedType,
            content = normalizedContent,
            title = title?.trim()?.takeIf(String::isNotBlank)
                ?: buildProgrammaticMemoryTitle(normalizedType, normalizedContent),
            roleFeeling = roleFeeling?.trim()?.takeIf(String::isNotBlank),
            bodySense = bodySense?.trim()?.takeIf(String::isNotBlank),
            unspokenThought = unspokenThought?.trim()?.takeIf(String::isNotBlank),
            userSignal = userSignal?.trim()?.takeIf(String::isNotBlank),
            relationshipEffect = relationshipEffect?.trim()?.takeIf(String::isNotBlank),
            importance = calibrateImportance(normalizedType, importance),
            confidence = confidence.coerceIn(0.0, 1.0),
            tags = tags.mapNotNull { it.trim().takeIf(String::isNotBlank) }
                .distinct()
                .ifEmpty { listOf(normalizedType) },
            embeddingText = embeddingText?.trim()?.takeIf(String::isNotBlank),
            sourceMessageNodeIds = normalizedSourceIds,
            evidenceMessageNodeIds = normalizedEvidenceIds,
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

private fun buildProgrammaticMemoryTitle(type: String, content: String): String? {
    val compact = content
        .replace(Regex("\\s+"), " ")
        .removePrefix("我记得：")
        .removePrefix("我记得")
        .trim('：', ':', ' ', '“', '”')
    if (compact.isBlank()) return null
    val prefix = when (type) {
        "user_fact" -> "关于你的事"
        "user_preference" -> "你的偏好"
        "user_boundary" -> "你明确的边界"
        "promise" -> "我们的承诺"
        "relationship" -> "关系中的一刻"
        "correction" -> "你纠正过的事"
        else -> "共同经历"
    }
    return "$prefix · ${compact.take(18)}"
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
    if (normalized.sourceMessageNodeIds.isEmpty()) return false
    val inspectedText = listOfNotNull(
        normalized.content,
        normalized.title,
        normalized.roleFeeling,
        normalized.bodySense,
        normalized.unspokenThought,
        normalized.relationshipEffect,
    ).joinToString("\n")
    if (inspectedText.looksLikeRawToolOrTraceDump()) return false
    if (GENERIC_META_MEMORY_MARKERS.any { inspectedText.contains(it, ignoreCase = true) }) return false
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
    return AffectiveMemoryCandidate(
        type = type,
        content = "我记得你明确说过：$quote",
        unspokenThought = "我得把这句话真正记住，而不是只在这一轮口头答应。",
        importance = if (type == "user_preference") 3 else 4,
        confidence = 1.0,
        sourceMessageNodeIds = listOf(nodeId),
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
        appendLine("你是当前角色的长期记忆整理器；当前角色在本会话中的动态名称是：$name。把 conversation_turns 当作当前角色参与、观察或见证的一段生活，只依据正文提取真正值得记住的内容，禁止补写批次外的信息。")
        appendLine("用户与当前角色是两个独立主体。必须先根据每条消息的 role 和正文判断‘谁做了什么’，再转换叙述视角；不能因为要求角色第一人称，就把用户做过、经历过、决定过或感受到的事情改写成角色本人做过或经历过。")
        appendLine("不是‘有用才记’，而是‘值得记才记’：稳定事实、明确偏好、边界、承诺、纠正，以及印象深刻的共同经历、第一次、冲突、和好、担忧、喜悦、委屈或关系时刻都可以记。")
        appendLine("宁缺毋滥。普通寒暄、随口一句、无特殊意义的吃饭喝水天气行程、学习材料原文、工具结果、系统信息和模型内部过程都不要提取；本批没有值得记住的内容就返回 {\"memories\":[]}。")
        appendLine("每批最多提取6条。同一件事只能形成一条记忆；如果多个候选引用相同消息证据且意思接近，必须合并后只输出信息最完整的一条，禁止换句话重复。")
        appendLine("可用类型仅限：user_fact, user_preference, user_boundary, promise, relationship, shared_event, correction。")
        appendLine("AI只允许填写以下字段：type、content、roleFeeling、bodySense、unspokenThought、relationshipEffect、importance、confidence、sourceMessageNodeIds。不要输出标题、标签、用户证据摘要、向量、人物、主题、关联ID或任何其他字段。")
        appendLine("content：以当前角色作为叙述者，用当前角色的第一人称自然写清发生了什么以及最值得记住的部分；这里的‘我’始终只代表当前角色。用户做的事仍然属于用户，角色做的事才属于角色。")
        appendLine("角色观察到用户行为时，可以写‘我看到/听到/知道用户……’。需要称呼用户时，优先沿用本批正文中当前角色真实使用的称呼；无法确定时用中性表达或自然省略，不得写死用户名、性别、关系称谓，也不得把动态角色名或用户名假定为固定值。")
        appendLine("roleFeeling：只写当前角色当时真实、具体且有正文依据的主观感受；用户的情绪只能作为角色观察到或得知的事实，不能冒充角色自己的感受；没有明显感受就留空。")
        appendLine("bodySense：只写当前角色在正文中确有依据的身体、感官或动作反应；用户的身体状态仍属于用户；没有依据必须留空，禁止凭空套用心里一紧、耳朵发热、心口发烫等模板。")
        appendLine("unspokenThought：只写当前角色当时没有说出口、但能由正文合理支持的具体心声；无法确定必须留空，禁止自行编剧情或把用户的想法据为角色所有。")
        appendLine("relationshipEffect：写这件事给当前角色本人或双方关系留下了什么；没有明显影响就留空，不要强制关系升级，也不要强制写未来行动。")
        appendLine("importance为1到5，表示记忆深度和事件重要性；confidence为0到1，表示正文证据充分程度。不要虚高。")
        appendLine("sourceMessageNodeIds必须填写，只能引用本批出现的nodeId。它是程序核验证据的唯一来源字段。")
        appendLine("返回严格JSON，不要解释：{\"memories\":[{\"type\":\"\",\"content\":\"\",\"roleFeeling\":\"\",\"bodySense\":\"\",\"unspokenThought\":\"\",\"relationshipEffect\":\"\",\"importance\":3,\"confidence\":0.8,\"sourceMessageNodeIds\":[]}]}。")
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
            memories = deduplicateMemoryCandidates(candidates)
                .take(MAX_MEMORIES_PER_BATCH)
        )
    }
}

internal fun deduplicateMemoryCandidates(
    candidates: List<AffectiveMemoryCandidate>,
): List<AffectiveMemoryCandidate> {
    val ranked = candidates
        .map { it.normalized() }
        .filter { it.content.isNotBlank() }
        .sortedWith(
            compareByDescending<AffectiveMemoryCandidate> { it.importance }
                .thenByDescending { it.confidence }
                .thenByDescending { it.memoryDetailScore() }
        )
    val selected = mutableListOf<AffectiveMemoryCandidate>()
    ranked.forEach { candidate ->
        val duplicateIndex = selected.indexOfFirst { existing -> existing.isSameMemoryAs(candidate) }
        if (duplicateIndex < 0) {
            selected += candidate
        } else {
            selected[duplicateIndex] = selected[duplicateIndex].mergeDuplicateEvidence(candidate)
        }
    }
    return selected.sortedWith(
        compareByDescending<AffectiveMemoryCandidate> { it.importance }
            .thenByDescending { it.confidence }
            .thenByDescending { it.memoryDetailScore() }
    )
}

private fun AffectiveMemoryCandidate.isSameMemoryAs(other: AffectiveMemoryCandidate): Boolean {
    if (content.normalizedMemoryIdentity() == other.content.normalizedMemoryIdentity()) return true
    if (type != other.type) return false
    val evidence = (sourceMessageNodeIds + evidenceMessageNodeIds).toSet()
    val otherEvidence = (other.sourceMessageNodeIds + other.evidenceMessageNodeIds).toSet()
    if (evidence.isEmpty() || otherEvidence.isEmpty() || evidence.intersect(otherEvidence).isEmpty()) return false
    return memoryTextSimilarity(content, other.content) >= MEMORY_TEXT_DUPLICATE_THRESHOLD
}

private fun AffectiveMemoryCandidate.mergeDuplicateEvidence(
    other: AffectiveMemoryCandidate,
): AffectiveMemoryCandidate = copy(
    sourceMessageNodeIds = (sourceMessageNodeIds + other.sourceMessageNodeIds).distinct(),
    evidenceMessageNodeIds = (evidenceMessageNodeIds + other.evidenceMessageNodeIds).distinct(),
    relatedMemoryIds = (relatedMemoryIds + other.relatedMemoryIds).distinct(),
    people = (people + other.people).distinct(),
    topics = (topics + other.topics).distinct(),
    tags = (tags + other.tags).distinct(),
)

private fun AffectiveMemoryCandidate.memoryDetailScore(): Int = listOfNotNull(
    content,
    roleFeeling,
    bodySense,
    unspokenThought,
    relationshipEffect,
).sumOf { it.length }

private fun memoryTextSimilarity(left: String, right: String): Double {
    val leftParts = left.memoryCharacterBigrams()
    val rightParts = right.memoryCharacterBigrams()
    if (leftParts.isEmpty() || rightParts.isEmpty()) return 0.0
    val intersection = leftParts.intersect(rightParts).size.toDouble()
    val union = (leftParts + rightParts).size.toDouble()
    return if (union == 0.0) 0.0 else intersection / union
}

private fun String.memoryCharacterBigrams(): Set<String> {
    val compact = normalizedMemoryIdentity()
        .removePrefix("我记得")
        .removePrefix("我知道")
        .removePrefix("我看到")
        .removePrefix("我听到")
    if (compact.length < 2) return setOf(compact).filter(String::isNotBlank).toSet()
    return compact.windowed(2).toSet()
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
        "system prompt", "stacktrace", "exception at", "github actions",
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

private const val MEMORY_TEXT_DUPLICATE_THRESHOLD = 0.58
