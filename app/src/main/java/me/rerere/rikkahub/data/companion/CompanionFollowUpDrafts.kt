package me.rerere.rikkahub.data.companion

import java.nio.charset.StandardCharsets
import java.util.UUID

data class CompanionFollowUpDraft(
    val assistantId: String,
    val category: String,
    val reason: String,
    val sourceText: String,
    val dueAt: Long,
    val sourceConversationId: String? = null,
    val sourceMessageId: String? = null,
    val preferredToolNames: List<String> = emptyList(),
    val importance: Int = 3,
    val actionType: CompanionActionType = CompanionActionType.CHECK_IN,
    val argumentsJson: String = "{}",
    val subjectKeyOverride: String? = null,
    val commitmentIdOverride: String? = null,
) {
    fun toConcern(nowMillis: Long): CompanionConcern {
        val subjectKey = stableSubjectKey()
        val humanText = neutralResponsibilityText()
        return CompanionConcern(
            id = stableId("concern", subjectKey),
            assistantId = assistantId,
            subjectKey = subjectKey,
            event = humanText.first,
            goal = humanText.second,
            importance = importance.coerceIn(1, 5),
            nextPerceptionAt = dueAt,
            sourceMessageIds = listOfNotNull(sourceMessageId?.trim()?.takeIf(String::isNotBlank)),
            createdAt = nowMillis,
            lastUpdatedAt = nowMillis,
        )
    }

    fun toCommitment(nowMillis: Long): CompanionCommitment {
        val subjectKey = stableSubjectKey()
        val humanText = neutralResponsibilityText()
        return CompanionCommitment(
            id = commitmentIdOverride?.trim()?.takeIf(String::isNotBlank)
                ?: stableId("commitment", subjectKey),
            assistantId = assistantId,
            subjectKey = subjectKey,
            promise = humanText.second,
            dueAt = dueAt,
            promisorId = assistantId,
            beneficiary = "user",
            responsibility = humanText.second,
            schedule = CompanionCommitmentSchedule(
                timeDescription = dueAt.toString(),
                frequency = if (sourceText.hasRecurringResponsibilityIntent()) "持续/按约定重复" else "一次",
                condition = reason.trim().take(160),
            ),
            executionMethod = actionType.name.lowercase(),
            actionPlan = CompanionActionPlan(
                type = actionType,
                argumentsJson = argumentsJson,
                userFacingSummary = humanText.second,
                contextText = sourceText.trim(),
                category = category.trim(),
                preferredToolNames = preferredToolNames,
            ),
            sourceConversationId = sourceConversationId?.trim()?.takeIf(String::isNotBlank),
            sourceMessageId = sourceMessageId?.trim()?.takeIf(String::isNotBlank),
            createdAt = nowMillis,
            updatedAt = nowMillis,
        )
    }

    private fun stableSubjectKey(): String = subjectKeyOverride
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let(::normalizeCompanionSubjectKey)
        ?: normalizeCompanionSubjectKey(
            "${category.family()}:${sourceText.semanticConcernText().take(160)}",
        )

    private fun stableId(prefix: String, subjectKey: String): String {
        val evidence = "$assistantId|$subjectKey|${sourceConversationId.orEmpty()}"
        return "$prefix:${UUID.nameUUIDFromBytes(evidence.toByteArray(StandardCharsets.UTF_8))}"
    }

    private fun neutralResponsibilityText(): Pair<String, String> = when (category.family()) {
        "wake" -> "起床监督" to "按约定时间执行叫醒并核验完成状态"
        "sleep" -> "睡眠监督" to "按约定条件执行休息提醒并记录结果"
        "study" -> "学习监督" to "按当前计划执行学习跟进并记录状态"
        "health" -> "健康跟进" to "按约定时间核验身体状态"
        "meal" -> "用餐跟进" to "按约定时间核验用餐状态"
        "time" -> "定时事项" to "在约定时间执行对应提醒或动作"
        else -> {
            val family = category.family().ifBlank { "follow_up" }
            "待跟进事项:$family" to "执行待跟进事项:$family"
        }
    }
}

fun reconcileCompanionFollowUpDrafts(
    drafts: List<CompanionFollowUpDraft>,
    snapshot: CompanionSnapshot,
    latestUserText: String,
): List<CompanionFollowUpDraft> {
    val expandedDrafts = expandExplicitCompanionResponsibilities(drafts)
    if (!latestUserText.containsRescheduleIntent()) return expandedDrafts
    return expandedDrafts.map { draft ->
        val candidates = snapshot.commitments.filter { commitment ->
            commitment.status in setOf(
                CompanionCommitmentStatus.PROPOSED,
                CompanionCommitmentStatus.ACTIVE,
                CompanionCommitmentStatus.DUE,
                CompanionCommitmentStatus.RETRY_SCHEDULED,
            ) &&
                commitment.actionPlan.category.family() == draft.category.family() &&
                (draft.sourceConversationId == null || commitment.sourceConversationId == draft.sourceConversationId)
        }
        val existing = candidates.singleOrNull()
            ?: candidates.minByOrNull { commitment -> kotlin.math.abs(commitment.dueAt - draft.dueAt) }
        if (existing == null) {
            draft
        } else {
            draft.copy(
                subjectKeyOverride = existing.subjectKey,
                commitmentIdOverride = existing.id,
            )
        }
    }
}

internal fun expandExplicitCompanionResponsibilities(
    drafts: List<CompanionFollowUpDraft>,
): List<CompanionFollowUpDraft> = drafts
    .flatMap { draft ->
        val source = draft.sourceText.trim()
        if (!source.containsExplicitResponsibilityIntent()) return@flatMap listOf(draft)
        val families = buildList {
            if (WAKE_RESPONSIBILITY_MARKERS.any(source::contains)) add("wake")
            if (SLEEP_RESPONSIBILITY_MARKERS.any(source::contains)) add("sleep")
            if (STUDY_RESPONSIBILITY_MARKERS.any(source::contains)) add("study")
        }.distinct()
        if (families.size < 2) {
            listOf(draft)
        } else {
            families.map { family ->
                draft.copy(
                    category = family,
                    subjectKeyOverride = family,
                    commitmentIdOverride = null,
                )
            }
        }
    }
    .distinctBy { draft ->
        listOf(
            draft.assistantId,
            draft.sourceConversationId.orEmpty(),
            draft.sourceMessageId.orEmpty(),
            draft.category.family(),
        ).joinToString("|")
    }

private fun String.containsExplicitResponsibilityIntent(): Boolean =
    EXPLICIT_RESPONSIBILITY_MARKERS.any(::contains)

private fun String.hasRecurringResponsibilityIntent(): Boolean =
    RECURRING_RESPONSIBILITY_MARKERS.any(::contains)

private val RECURRING_RESPONSIBILITY_MARKERS =
    listOf("以后", "每天", "每晚", "每早", "长期", "一直", "持续")

private val EXPLICIT_RESPONSIBILITY_MARKERS = listOf(
    "监督",
    "督促",
    "提醒",
    "叫我",
    "帮我记",
    "负责",
    "记得让我",
    "以后要",
)

private val WAKE_RESPONSIBILITY_MARKERS = listOf("起床", "叫醒", "早起")
private val SLEEP_RESPONSIBILITY_MARKERS = listOf("睡觉", "睡眠", "休息", "早睡")
private val STUDY_RESPONSIBILITY_MARKERS = listOf("学习", "复习", "背书", "做题")

private fun String.containsRescheduleIntent(): Boolean {
    val normalized = lowercase()
    return RESCHEDULE_MARKERS.any { marker -> marker in normalized }
}

private fun String.semanticConcernText(): String = lowercase()
    .replace(Regex("\\d{1,2}[:：点时]\\d{0,2}分?"), " ")
    .replace(Regex("\\d+\\s*(分钟|小时|天|周)后"), " ")
    .replace(Regex("明天|后天|今天|今晚|早上|上午|中午|下午|晚上|凌晨"), " ")
    .replace(Regex("改成|改到|改为|换成|换到|推迟到|提前到|延后到|重新定|时间改"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
    .ifBlank { "ongoing" }

private fun String.family(): String {
    val normalized = lowercase()
    return when {
        "wake" in normalized || "起床" in normalized || "叫醒" in normalized -> "wake"
        "sleep" in normalized || "休息" in normalized || "睡" in normalized -> "sleep"
        "study" in normalized || "学习" in normalized -> "study"
        "health" in normalized || "身体" in normalized || "健康" in normalized -> "health"
        "meal" in normalized || "吃饭" in normalized -> "meal"
        normalized in setOf("schedule", "deadline", "reminder", "time") -> "time"
        else -> normalized.ifBlank { "follow-up" }
    }
}

private val RESCHEDULE_MARKERS = setOf(
    "改成", "改到", "改为", "换成", "换到", "推迟到", "提前到", "延后到", "重新定", "时间改",
    "reschedule", "move it to", "change it to",
)
