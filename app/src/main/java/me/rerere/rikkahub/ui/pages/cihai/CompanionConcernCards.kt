package me.rerere.rikkahub.ui.pages.cihai

import me.rerere.rikkahub.data.companion.CompanionActionType
import me.rerere.rikkahub.data.companion.CompanionCommitment
import me.rerere.rikkahub.data.companion.CompanionCommitmentStatus
import me.rerere.rikkahub.data.companion.CompanionConcern
import me.rerere.rikkahub.data.companion.CompanionConcernStatus
import me.rerere.rikkahub.data.companion.CompanionSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CompanionConcernCardModel(
    val id: String,
    val title: String,
    val nextPerceptionText: String,
    val statusText: String,
    val eventText: String,
    val goalText: String,
    val commitmentText: String?,
    val overdue: Boolean,
)

fun buildCompanionConcernCards(
    snapshot: CompanionSnapshot,
    nowMillis: Long = System.currentTimeMillis(),
): List<CompanionConcernCardModel> {
    val concerns = snapshot.concerns.filter { concern ->
        concern.assistantId == snapshot.assistantId &&
            concern.status in setOf(CompanionConcernStatus.ACTIVE, CompanionConcernStatus.PAUSED)
    }
    val commitments = snapshot.commitments.filter { commitment ->
        commitment.assistantId == snapshot.assistantId && commitment.status.isVisibleConcernCommitment()
    }
    val commitmentsBySubject = commitments.groupBy { it.subjectKey }
    val representedCommitmentIds = mutableSetOf<String>()
    val concernCards = concerns.map { concern ->
        val commitment = commitmentsBySubject[concern.subjectKey]
            ?.minByOrNull { it.dueAt }
            ?.also { representedCommitmentIds += it.id }
        concern.toCardModel(commitment, nowMillis)
    }
    val commitmentOnlyCards = commitments
        .filterNot { it.id in representedCommitmentIds }
        .map { it.toCardModel(nowMillis) }

    return (concernCards + commitmentOnlyCards)
        .sortedWith(compareBy<SortableConcernCard> { it.sortAt }.thenByDescending { it.importance })
        .take(12)
        .map { it.card }
}

private data class SortableConcernCard(
    val card: CompanionConcernCardModel,
    val sortAt: Long,
    val importance: Int,
)

private fun CompanionConcern.toCardModel(
    commitment: CompanionCommitment?,
    nowMillis: Long,
): SortableConcernCard {
    val targetAt = nextPerceptionAt ?: commitment?.dueAt
    val isOverdue = targetAt != null && targetAt <= nowMillis
    return SortableConcernCard(
        card = CompanionConcernCardModel(
            id = "concern:$id",
            title = commitment.concernTitle(),
            nextPerceptionText = targetAt.toPerceptionText(nowMillis),
            statusText = when {
                commitment?.status == CompanionCommitmentStatus.EXECUTING -> "正在处理"
                commitment?.status == CompanionCommitmentStatus.RETRY_SCHEDULED -> "等待重试"
                isOverdue -> "已经到点"
                status == CompanionConcernStatus.PAUSED -> "暂缓留意"
                else -> "挂心中"
            },
            eventText = event.trim().ifBlank { commitment?.promise.orEmpty().trim() },
            goalText = goal.trim(),
            commitmentText = commitment?.promise
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != event.trim() && it != goal.trim() },
            overdue = isOverdue,
        ),
        sortAt = targetAt ?: Long.MAX_VALUE,
        importance = importance,
    )
}

private fun CompanionCommitment.toCardModel(nowMillis: Long): SortableConcernCard {
    val isOverdue = dueAt <= nowMillis
    return SortableConcernCard(
        card = CompanionConcernCardModel(
            id = "commitment:$id",
            title = concernTitle(),
            nextPerceptionText = dueAt.toPerceptionText(nowMillis),
            statusText = when (status) {
                CompanionCommitmentStatus.EXECUTING -> "正在处理"
                CompanionCommitmentStatus.RETRY_SCHEDULED -> "等待重试"
                else -> if (isOverdue) "已经到点" else "挂心中"
            },
            eventText = promise.trim(),
            goalText = actionPlan.userFacingSummary.trim().takeIf { it != promise.trim() }.orEmpty(),
            commitmentText = null,
            overdue = isOverdue,
        ),
        sortAt = dueAt,
        importance = 3,
    )
}

private fun CompanionCommitment?.concernTitle(): String {
    val commitment = this ?: return "挂心事项"
    return when {
        commitment.actionPlan.category.contains("wake", ignoreCase = true) -> "起床提醒"
        commitment.actionPlan.category.contains("study", ignoreCase = true) -> "学习节奏"
        commitment.actionPlan.category.contains("health", ignoreCase = true) -> "身体与安全"
        commitment.actionPlan.category.contains("deadline", ignoreCase = true) -> "时间提醒"
        commitment.actionPlan.category.contains("schedule", ignoreCase = true) -> "时间提醒"
        commitment.actionPlan.type == CompanionActionType.ALARM -> "起床提醒"
        commitment.actionPlan.type == CompanionActionType.CALENDAR -> "日程安排"
        commitment.actionPlan.type == CompanionActionType.REMINDER -> "时间提醒"
        commitment.actionPlan.type == CompanionActionType.CHECK_IN -> "持续关心"
        else -> "挂心事项"
    }
}

private fun CompanionCommitmentStatus.isVisibleConcernCommitment(): Boolean = this in setOf(
    CompanionCommitmentStatus.PROPOSED,
    CompanionCommitmentStatus.ACTIVE,
    CompanionCommitmentStatus.DUE,
    CompanionCommitmentStatus.EXECUTING,
    CompanionCommitmentStatus.RETRY_SCHEDULED,
)

private fun Long?.toPerceptionText(nowMillis: Long): String {
    if (this == null || this <= 0L) return "持续留意，没有固定时间"
    val absoluteTime = Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
    return if (this <= nowMillis) {
        "原定留意时间：$absoluteTime"
    } else {
        "计划留意时间：$absoluteTime"
    }
}
