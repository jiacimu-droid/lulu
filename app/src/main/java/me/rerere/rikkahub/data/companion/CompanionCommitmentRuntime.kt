package me.rerere.rikkahub.data.companion

fun beginCompanionCommitment(
    current: CompanionPersistedState,
    assistantId: String,
    commitmentId: String,
    nowMillis: Long,
): CompanionRuntimeReduction {
    val snapshot = current.snapshotOrEmpty(assistantId)
    val existing = snapshot.commitments.firstOrNull {
        it.assistantId == assistantId && it.id == commitmentId
    } ?: return current.unchangedReduction(snapshot)
    if (existing.dueAt > nowMillis) {
        return current.unchangedReduction(snapshot)
    }

    val transitions = when (existing.status) {
        CompanionCommitmentStatus.ACTIVE,
        CompanionCommitmentStatus.RETRY_SCHEDULED -> listOf(
            CompanionCommitmentChange.Transition(
                assistantId = assistantId,
                commitmentId = commitmentId,
                status = CompanionCommitmentStatus.DUE,
                reason = "Commitment became due",
            ),
            CompanionCommitmentChange.Transition(
                assistantId = assistantId,
                commitmentId = commitmentId,
                status = CompanionCommitmentStatus.EXECUTING,
                reason = "Commitment execution started",
            ),
        )
        CompanionCommitmentStatus.DUE -> listOf(
            CompanionCommitmentChange.Transition(
                assistantId = assistantId,
                commitmentId = commitmentId,
                status = CompanionCommitmentStatus.EXECUTING,
                reason = "Commitment execution started",
            ),
        )
        CompanionCommitmentStatus.EXECUTING -> {
            if (nowMillis - existing.updatedAt < STALE_COMMITMENT_EXECUTION_MILLIS) {
                return current.unchangedReduction(snapshot)
            }
            listOf(
                CompanionCommitmentChange.Transition(
                    assistantId = assistantId,
                    commitmentId = commitmentId,
                    status = CompanionCommitmentStatus.FAILED,
                    reason = "Previous execution was interrupted",
                ),
                CompanionCommitmentChange.Transition(
                    assistantId = assistantId,
                    commitmentId = commitmentId,
                    status = CompanionCommitmentStatus.RETRY_SCHEDULED,
                    reason = "Recovering interrupted execution",
                    nextDueAt = nowMillis,
                ),
                CompanionCommitmentChange.Transition(
                    assistantId = assistantId,
                    commitmentId = commitmentId,
                    status = CompanionCommitmentStatus.DUE,
                    reason = "Recovered commitment became due",
                ),
                CompanionCommitmentChange.Transition(
                    assistantId = assistantId,
                    commitmentId = commitmentId,
                    status = CompanionCommitmentStatus.EXECUTING,
                    reason = "Recovered commitment execution started",
                ),
            )
        }
        else -> return current.unchangedReduction(snapshot)
    }
    val transitioned = CompanionCommitmentReducer.apply(snapshot.commitments, transitions, nowMillis)
    val executing = transitioned.firstOrNull { it.id == commitmentId }
        ?.takeIf { it.status == CompanionCommitmentStatus.EXECUTING }
        ?: return current.unchangedReduction(snapshot)
    val updatedCommitment = executing.copy(
        attemptCount = existing.attemptCount + 1,
        updatedAt = nowMillis,
    )
    val updatedSnapshot = snapshot.copy(
        commitments = transitioned.map { if (it.id == commitmentId) updatedCommitment else it },
        updatedAt = maxOf(snapshot.updatedAt, nowMillis),
    )
    return current.withUpdatedSnapshot(updatedSnapshot, affectedCommitmentId = commitmentId)
}

fun finishCompanionCommitment(
    current: CompanionPersistedState,
    assistantId: String,
    commitmentId: String,
    result: CompanionActionResult,
    retryAt: Long? = null,
): CompanionRuntimeReduction {
    val snapshot = current.snapshotOrEmpty(assistantId)
    val existing = snapshot.commitments.firstOrNull {
        it.assistantId == assistantId && it.id == commitmentId
    } ?: return current.unchangedReduction(snapshot)
    if (existing.status != CompanionCommitmentStatus.EXECUTING) {
        return current.unchangedReduction(snapshot)
    }

    val cleanResult = result.copy(
        summary = result.summary.trim().take(500),
        outputReference = result.outputReference?.trim()?.takeIf(String::isNotBlank),
    )
    val transitions = if (cleanResult.success) {
        listOf(
            CompanionCommitmentChange.Transition(
                assistantId = assistantId,
                commitmentId = commitmentId,
                status = CompanionCommitmentStatus.FULFILLED,
                reason = cleanResult.summary,
            ),
        )
    } else {
        buildList {
            add(
                CompanionCommitmentChange.Transition(
                    assistantId = assistantId,
                    commitmentId = commitmentId,
                    status = CompanionCommitmentStatus.FAILED,
                    reason = cleanResult.summary,
                ),
            )
            retryAt?.takeIf { it > cleanResult.completedAt }?.let { nextDueAt ->
                add(
                    CompanionCommitmentChange.Transition(
                        assistantId = assistantId,
                        commitmentId = commitmentId,
                        status = CompanionCommitmentStatus.RETRY_SCHEDULED,
                        reason = "Retry scheduled after failed execution",
                        nextDueAt = nextDueAt,
                    ),
                )
            }
        }
    }
    val transitioned = CompanionCommitmentReducer.apply(
        current = snapshot.commitments,
        changes = transitions,
        nowMillis = cleanResult.completedAt,
    )
    val transitionedCommitment = transitioned.first { it.id == commitmentId }
    val updatedHistory = transitionedCommitment.history.toMutableList().also { history ->
        val lastIndex = history.indexOfLast { entry ->
            entry.toStatus == transitionedCommitment.status &&
                entry.occurredAt == cleanResult.completedAt
        }
        if (lastIndex >= 0) {
            history[lastIndex] = history[lastIndex].copy(actionResult = cleanResult)
        }
    }
    val updatedCommitment = transitionedCommitment.copy(
        lastActionResult = cleanResult,
        updatedAt = cleanResult.completedAt,
        history = updatedHistory,
    )
    val matchingConcerns = snapshot.concerns.filter { concern ->
        concern.assistantId == assistantId &&
            concern.subjectKey == existing.subjectKey &&
            concern.status == CompanionConcernStatus.ACTIVE
    }
    val validRetryAt = retryAt?.takeIf { it > cleanResult.completedAt }
    val concernChanges = when {
        cleanResult.success -> matchingConcerns.map { concern ->
            CompanionConcernChange.Complete(
                assistantId = assistantId,
                concernId = concern.id,
                reason = cleanResult.summary,
            )
        }
        validRetryAt != null -> matchingConcerns.map { concern ->
            CompanionConcernChange.Upsert(
                concern.copy(nextPerceptionAt = validRetryAt),
            )
        }
        else -> matchingConcerns.map { concern ->
            CompanionConcernChange.Cancel(
                assistantId = assistantId,
                concernId = concern.id,
                reason = cleanResult.summary,
            )
        }
    }
    val updatedSnapshot = snapshot.copy(
        concerns = CompanionConcernReducer.apply(
            current = snapshot.concerns,
            changes = concernChanges,
            nowMillis = cleanResult.completedAt,
        ),
        commitments = transitioned.map { if (it.id == commitmentId) updatedCommitment else it },
        updatedAt = maxOf(snapshot.updatedAt, cleanResult.completedAt),
    )
    val relationshipEventKind = if (cleanResult.success) {
        CompanionRelationshipEventKind.COMMITMENT_FULFILLED
    } else {
        CompanionRelationshipEventKind.COMMITMENT_FAILED
    }
    val relationshipEvent = CompanionRelationshipEvent(
        id = "$commitmentId:${relationshipEventKind.name}",
        assistantId = assistantId,
        sourceId = commitmentId,
        kind = relationshipEventKind,
        trustDelta = if (cleanResult.success) 0.01f else -0.01f,
        reliabilityDelta = if (cleanResult.success) 0.03f else -0.03f,
        tensionDelta = if (cleanResult.success) -0.01f else 0.02f,
        evidence = cleanResult.summary,
        createdAt = cleanResult.completedAt,
    )
    val relationshipReduction = CompanionRelationshipReducer.apply(
        assistantId = assistantId,
        current = updatedSnapshot.relationship,
        appliedEventIds = current.appliedRelationshipEventIds.toSet(),
        events = listOf(relationshipEvent),
        nowMillis = cleanResult.completedAt,
    )
    return current.withUpdatedSnapshot(
        snapshot = updatedSnapshot.copy(
            relationship = relationshipReduction.relationship,
            relationshipHistory = updatedSnapshot.relationshipHistory.appendCommitmentRelationshipEvents(
                relationshipReduction.appliedEvents,
            ),
        ),
        appliedRelationshipEventIds = relationshipReduction.appliedEventIds,
        affectedCommitmentId = commitmentId,
    )
}

fun fulfillCompanionCommitmentFromEvidence(
    current: CompanionPersistedState,
    assistantId: String,
    commitmentId: String,
    summary: String,
    completedAt: Long,
    outputReference: String? = null,
): CompanionRuntimeReduction {
    val snapshot = current.snapshotOrEmpty(assistantId)
    val existing = snapshot.commitments.firstOrNull {
        it.assistantId == assistantId && it.id == commitmentId
    } ?: return current.unchangedReduction(snapshot)
    val executingState = when (existing.status) {
        CompanionCommitmentStatus.EXECUTING -> current
        CompanionCommitmentStatus.ACTIVE,
        CompanionCommitmentStatus.DUE,
        CompanionCommitmentStatus.RETRY_SCHEDULED -> {
            val transitions = buildList {
                if (existing.status != CompanionCommitmentStatus.DUE) {
                    add(
                        CompanionCommitmentChange.Transition(
                            assistantId = assistantId,
                            commitmentId = commitmentId,
                            status = CompanionCommitmentStatus.DUE,
                            reason = "External evidence resolved the commitment",
                        ),
                    )
                }
                add(
                    CompanionCommitmentChange.Transition(
                        assistantId = assistantId,
                        commitmentId = commitmentId,
                        status = CompanionCommitmentStatus.EXECUTING,
                        reason = "Applying external completion evidence",
                    ),
                )
            }
            val transitioned = CompanionCommitmentReducer.apply(
                current = snapshot.commitments,
                changes = transitions,
                nowMillis = completedAt,
            )
            val executing = transitioned.firstOrNull { it.id == commitmentId }
                ?.takeIf { it.status == CompanionCommitmentStatus.EXECUTING }
                ?: return current.unchangedReduction(snapshot)
            current.withUpdatedSnapshot(
                snapshot = snapshot.copy(
                    commitments = transitioned.map {
                        if (it.id == commitmentId) executing.copy(updatedAt = completedAt) else it
                    },
                    updatedAt = maxOf(snapshot.updatedAt, completedAt),
                ),
                affectedCommitmentId = commitmentId,
            ).persistedState
        }
        else -> return current.unchangedReduction(snapshot)
    }
    return finishCompanionCommitment(
        current = executingState,
        assistantId = assistantId,
        commitmentId = commitmentId,
        result = CompanionActionResult(
            success = true,
            summary = summary,
            completedAt = completedAt,
            outputReference = outputReference,
        ),
    )
}

fun continueCompanionCommitment(
    current: CompanionPersistedState,
    assistantId: String,
    commitmentId: String,
    result: CompanionActionResult,
    nextDueAt: Long,
): CompanionRuntimeReduction {
    val snapshot = current.snapshotOrEmpty(assistantId)
    val existing = snapshot.commitments.firstOrNull {
        it.assistantId == assistantId && it.id == commitmentId
    } ?: return current.unchangedReduction(snapshot)
    if (existing.status != CompanionCommitmentStatus.EXECUTING || nextDueAt <= result.completedAt) {
        return current.unchangedReduction(snapshot)
    }

    val cleanResult = result.copy(
        summary = result.summary.trim().take(500),
        outputReference = result.outputReference?.trim()?.takeIf(String::isNotBlank),
    )
    val transitioned = CompanionCommitmentReducer.apply(
        current = snapshot.commitments,
        changes = listOf(
            CompanionCommitmentChange.Transition(
                assistantId = assistantId,
                commitmentId = commitmentId,
                status = CompanionCommitmentStatus.RETRY_SCHEDULED,
                reason = cleanResult.summary,
                nextDueAt = nextDueAt,
            ),
        ),
        nowMillis = cleanResult.completedAt,
    )
    val continued = transitioned.firstOrNull { it.id == commitmentId }
        ?.takeIf { it.status == CompanionCommitmentStatus.RETRY_SCHEDULED }
        ?: return current.unchangedReduction(snapshot)
    val updatedCommitment = continued.copy(
        lastActionResult = cleanResult,
        updatedAt = cleanResult.completedAt,
    )
    val concernChanges = snapshot.concerns
        .filter { concern ->
            concern.assistantId == assistantId &&
                concern.subjectKey == existing.subjectKey &&
                concern.status == CompanionConcernStatus.ACTIVE
        }
        .map { concern ->
            CompanionConcernChange.Upsert(concern.copy(nextPerceptionAt = nextDueAt))
        }
    return current.withUpdatedSnapshot(
        snapshot = snapshot.copy(
            concerns = CompanionConcernReducer.apply(
                current = snapshot.concerns,
                changes = concernChanges,
                nowMillis = cleanResult.completedAt,
            ),
            commitments = transitioned.map {
                if (it.id == commitmentId) updatedCommitment else it
            },
            updatedAt = maxOf(snapshot.updatedAt, cleanResult.completedAt),
        ),
        affectedCommitmentId = commitmentId,
    )
}

fun cancelCompanionCommitment(
    current: CompanionPersistedState,
    assistantId: String,
    commitmentId: String,
    reason: String,
    nowMillis: Long,
): CompanionRuntimeReduction {
    val snapshot = current.snapshotOrEmpty(assistantId)
    val existing = snapshot.commitments.firstOrNull {
        it.assistantId == assistantId && it.id == commitmentId
    } ?: return current.unchangedReduction(snapshot)
    val transitioned = CompanionCommitmentReducer.apply(
        current = snapshot.commitments,
        changes = listOf(
            CompanionCommitmentChange.Transition(
                assistantId = assistantId,
                commitmentId = commitmentId,
                status = CompanionCommitmentStatus.CANCELLED,
                reason = reason,
            ),
        ),
        nowMillis = nowMillis,
    )
    val cancelled = transitioned.firstOrNull { it.id == commitmentId }
        ?.takeIf { it.status == CompanionCommitmentStatus.CANCELLED }
        ?: return current.unchangedReduction(snapshot)
    val concernChanges = snapshot.concerns
        .filter { concern ->
            concern.assistantId == assistantId &&
                concern.subjectKey == existing.subjectKey &&
                concern.status == CompanionConcernStatus.ACTIVE
        }
        .map { concern ->
            CompanionConcernChange.Cancel(
                assistantId = assistantId,
                concernId = concern.id,
                reason = reason,
            )
        }
    return current.withUpdatedSnapshot(
        snapshot = snapshot.copy(
            concerns = CompanionConcernReducer.apply(
                current = snapshot.concerns,
                changes = concernChanges,
                nowMillis = nowMillis,
            ),
            commitments = transitioned,
            updatedAt = maxOf(snapshot.updatedAt, nowMillis),
        ),
        affectedCommitmentId = cancelled.id,
    )
}

private fun List<CompanionRelationshipEvent>.appendCommitmentRelationshipEvents(
    events: List<CompanionRelationshipEvent>,
): List<CompanionRelationshipEvent> = (this + events)
    .groupBy { event -> "${event.assistantId}:${event.sourceId}:${event.kind.name}" }
    .values
    .map { duplicates ->
        duplicates.maxWith(
            compareBy<CompanionRelationshipEvent> { it.extractedAt ?: 0L }
                .thenBy { it.sourceMessageAt ?: 0L },
        )
    }
    .sortedWith(compareBy<CompanionRelationshipEvent> { it.createdAt }.thenBy { it.id })
    .takeLast(160)
