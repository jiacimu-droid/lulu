package me.rerere.rikkahub.ui.pages.cihai

import me.rerere.rikkahub.data.companion.CompanionRelationshipEvent
import me.rerere.rikkahub.data.companion.CompanionRelationshipEventKind
import kotlin.math.roundToInt

data class CompanionRelationshipTimelineItem(
    val id: String,
    val title: String,
    val detail: String?,
    val deltaText: String,
    val createdAt: Long,
)

fun buildCompanionRelationshipTimeline(
    events: List<CompanionRelationshipEvent>,
    limit: Int = 20,
): List<CompanionRelationshipTimelineItem> = events
    .sortedWith(compareByDescending<CompanionRelationshipEvent> { it.createdAt }.thenByDescending { it.id })
    .take(limit.coerceAtLeast(0))
    .map { event ->
        CompanionRelationshipTimelineItem(
            id = event.id,
            title = event.kind.timelineTitle(),
            detail = event.evidence.humanRelationshipEvidence(),
            deltaText = event.relationshipDeltaText(),
            createdAt = event.createdAt,
        )
    }

private fun CompanionRelationshipEventKind.timelineTitle(): String = when (this) {
    CompanionRelationshipEventKind.MEANINGFUL_DISCLOSURE -> "你分享了一件重要的事"
    CompanionRelationshipEventKind.PREFERENCE_RESPECTED -> "你的偏好被认真尊重"
    CompanionRelationshipEventKind.COMMITMENT_FULFILLED -> "答应的事已经做到"
    CompanionRelationshipEventKind.COMMITMENT_FAILED -> "答应的事没有按计划完成"
    CompanionRelationshipEventKind.BOUNDARY_EXPRESSED -> "你表达了自己的边界"
    CompanionRelationshipEventKind.BOUNDARY_RESPECTED -> "你的边界被认真尊重"
    CompanionRelationshipEventKind.CONFLICT -> "相处中出现了一次不舒服"
    CompanionRelationshipEventKind.REPAIR -> "彼此完成了一次关系修复"
    CompanionRelationshipEventKind.MANUAL -> "关系状态有了新的变化"
}

private fun CompanionRelationshipEvent.relationshipDeltaText(): String = buildList {
    trustDelta.toTimelineDelta("信任")?.let(::add)
    closenessDelta.toTimelineDelta("亲近")?.let(::add)
    reliabilityDelta.toTimelineDelta("说到做到")?.let(::add)
    boundaryDelta.toTimelineDelta("边界默契")?.let(::add)
    tensionDelta.toTimelineDelta("未解心结")?.let(::add)
}.joinToString(" · ")

private fun Float.toTimelineDelta(label: String): String? {
    val points = (this * 100).roundToInt()
    if (points == 0) return null
    return "$label ${if (points > 0) "+" else ""}$points"
}

private fun String.humanRelationshipEvidence(): String? {
    val normalized = trim().replace(Regex("\\s+"), " ").take(160)
    if (normalized.isBlank()) return null
    val lower = normalized.lowercase()
    return normalized.takeUnless { TECHNICAL_RELATIONSHIP_EVIDENCE.any { marker -> marker in lower } }
}

private val TECHNICAL_RELATIONSHIP_EVIDENCE = listOf(
    "provider",
    "api request",
    "tool call",
    "exception",
    "stack trace",
    "no model",
    "not configured",
    "unavailable",
    "timeout",
    "failed execution",
)
