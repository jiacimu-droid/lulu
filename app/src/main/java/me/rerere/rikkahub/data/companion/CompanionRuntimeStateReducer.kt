package me.rerere.rikkahub.data.companion

fun selectNextCompanionCommitment(
    snapshots: List<CompanionSnapshot>,
    nowMillis: Long,
): CompanionCommitment? = snapshots
    .asSequence()
    .flatMap { snapshot ->
        snapshot.commitments.asSequence().filter { it.assistantId == snapshot.assistantId }
    }
    .map { commitment ->
        if (commitment.status == CompanionCommitmentStatus.EXECUTING) {
            commitment.copy(
                dueAt = maxOf(
                    commitment.dueAt,
                    commitment.updatedAt + STALE_COMMITMENT_EXECUTION_MILLIS,
                ),
            )
        } else {
            commitment
        }
    }
    .filter { commitment ->
        commitment.status in SCHEDULABLE_COMMITMENT_STATUSES ||
            commitment.status == CompanionCommitmentStatus.EXECUTING
    }
    .sortedWith(
        compareBy<CompanionCommitment> { it.dueAt > nowMillis }
            .thenBy { it.dueAt }
            .thenBy { it.createdAt }
            .thenBy { it.id },
    )
    .firstOrNull()

fun reduceCompanionRuntimeState(
    current: CompanionPersistedState,
    mutation: CompanionTurnMutation,
): CompanionRuntimeReduction {
    val assistantId = mutation.assistantId.trim()
    require(assistantId.isNotBlank()) { "Companion mutation requires an assistant ID" }

    val existing = current.snapshots.firstOrNull { it.assistantId == assistantId }
        ?: CompanionSnapshot.empty(assistantId)
    val relationshipReduction = CompanionRelationshipReducer.apply(
        assistantId = assistantId,
        current = existing.relationship,
        appliedEventIds = current.appliedRelationshipEventIds.toSet(),
        events = mutation.relationshipEvents.filter { it.assistantId == assistantId },
        nowMillis = mutation.nowMillis,
    )
    val existingLifeEventIds = existing.lifeEvents.mapTo(mutableSetOf()) { it.id }
    val incomingLifeEvents = mutation.lifeEvents
        .filter { event -> event.assistantId == assistantId && event.id.isNotBlank() }
        .distinctBy { it.id }
        .filterNot { it.id in existingLifeEventIds }
    val nextNeuroState = reduceCompanionNeuroState(
        previous = existing.neuroState,
        lifeEvents = incomingLifeEvents,
        relationshipEvents = relationshipReduction.appliedEvents,
        nowMillis = mutation.nowMillis,
    )
    val nextPrivateImpression = mutation.privateImpression
        ?.takeIf { it.updatedAt >= existing.privateImpression.updatedAt }
        ?: existing.privateImpression
    val incomingAlwaysOnAnchors = mutation.alwaysOnAnchors
        .filter { anchor -> anchor.assistantId == assistantId && anchor.statement.isNotBlank() }
        .map { anchor -> anchor.copy(status = CompanionAlwaysOnAnchorStatus.ACTIVE) }
    val nextAlwaysOnAnchors = (existing.alwaysOnAnchors + incomingAlwaysOnAnchors)
        .filterNot { anchor -> anchor.id in mutation.cancelAlwaysOnAnchorIds }
        .groupBy { anchor -> anchor.id }
        .values
        .map { entries -> entries.maxBy { entry -> entry.updatedAt } }
        .filter { anchor ->
            anchor.status == CompanionAlwaysOnAnchorStatus.ACTIVE &&
                (anchor.expiresAt == null || anchor.expiresAt > mutation.nowMillis)
        }
        .sortedWith(
            compareByDescending<CompanionAlwaysOnAnchor> { it.importance }
                .thenByDescending { it.updatedAt },
        )
        .take(MAX_ALWAYS_ON_ANCHORS)
    val nextGoals = reduceCompanionGoals(
        assistantId = assistantId,
        previous = existing.goals,
        proposed = mutation.goals,
        lifeEvents = incomingLifeEvents,
        nowMillis = mutation.nowMillis,
    )
    val concernChanges = mutation.concernChanges.filter { it.belongsTo(assistantId) }
    val acceptedCommitmentChanges = mutation.acceptedCommitments
        .filter { it.assistantId == assistantId }
        .flatMap { commitment ->
            val proposed = commitment.copy(status = CompanionCommitmentStatus.PROPOSED)
            listOf(
                CompanionCommitmentChange.Upsert(proposed),
                CompanionCommitmentChange.Transition(
                    assistantId = assistantId,
                    commitmentId = proposed.id,
                    status = CompanionCommitmentStatus.ACTIVE,
                    reason = "Accepted companion commitment",
                ),
            )
        }
    val nextState = mutation.state
        ?.takeIf { candidate -> candidate.updatedAt >= existing.state.updatedAt }
        ?.copy(updatedAt = maxOf(mutation.state.updatedAt, mutation.nowMillis))
        ?: existing.state
    val nextExplainableState = mutation.explainableState
        ?.takeIf { candidate -> candidate.updatedAt >= existing.explainableState.updatedAt }
        ?.normalizedExplainableState()
        ?: existing.explainableState
    val nextLifeAnchor = reduceCompanionLifeAnchor(
        current = existing.lifeAnchor,
        incoming = mutation.lifeAnchor,
        nowMillis = mutation.nowMillis,
    )
    val nextContinuity = mutation.continuity
        ?.takeIf { candidate -> candidate.updatedAt >= existing.continuity.updatedAt }
        ?.let { candidate ->
            candidate.copy(updatedAt = maxOf(candidate.updatedAt, mutation.nowMillis))
        }
        ?: existing.continuity
    val reducedInteractionTimeline = reduceCompanionInteractionTimeline(
        current = existing.interactionTimeline,
        events = mutation.interactionEvents,
    )
    val nextInteractionTimeline = if (nextLifeAnchor != existing.lifeAnchor) {
        reducedInteractionTimeline.copy(
            lastLifeAnchorUpdatedAt = maxOf(
                reducedInteractionTimeline.lastLifeAnchorUpdatedAt ?: Long.MIN_VALUE,
                mutation.nowMillis,
            ),
        )
    } else {
        reducedInteractionTimeline
    }
    val nextStateHistory = if (
        mutation.state != null &&
        nextState.hasVisibleStateContent() &&
        !nextState.hasSameVisibleContent(existing.state)
    ) {
        existing.stateHistory + CompanionStateHistoryEntry(
            state = nextState,
            recordedAt = nextState.updatedAt,
        )
    } else {
        existing.stateHistory
    }
    val updatedSnapshot = existing.copy(
        state = nextState,
        stateHistory = nextStateHistory,
        explainableState = nextExplainableState,
        lifeAnchor = nextLifeAnchor,
        neuroState = nextNeuroState,
        privateImpression = nextPrivateImpression,
        alwaysOnAnchors = nextAlwaysOnAnchors,
        goals = nextGoals,
        lifeEvents = (existing.lifeEvents + incomingLifeEvents).distinctBy { it.id },
        relationship = relationshipReduction.relationship,
        relationshipHistory = existing.relationshipHistory.appendRelationshipEvents(
            relationshipReduction.appliedEvents,
        ),
        concerns = CompanionConcernReducer.apply(
            current = existing.concerns,
            changes = concernChanges,
            nowMillis = mutation.nowMillis,
        ),
        commitments = CompanionCommitmentReducer.apply(
            current = existing.commitments,
            changes = acceptedCommitmentChanges,
            nowMillis = mutation.nowMillis,
        ),
        continuity = nextContinuity,
        interactionTimeline = nextInteractionTimeline,
        updatedAt = maxOf(existing.updatedAt, mutation.nowMillis),
    )
    return current.withUpdatedSnapshot(
        snapshot = updatedSnapshot,
        appliedRelationshipEventIds = relationshipReduction.appliedEventIds,
    )
}

internal fun reduceCompanionInteractionTimeline(
    current: CompanionInteractionTimeline,
    events: List<CompanionInteractionEvent>,
): CompanionInteractionTimeline = events
    .sortedBy(CompanionInteractionEvent::occurredAt)
    .fold(current) { timeline, event -> timeline.applyInteractionEvent(event) }
    .let { timeline ->
        timeline.copy(
            outboundContacts = timeline.outboundContacts
                .sortedByDescending(CompanionOutboundContact::generatedAt)
                .take(MAX_OUTBOUND_CONTACT_HISTORY),
        )
    }

private fun CompanionInteractionTimeline.applyInteractionEvent(
    event: CompanionInteractionEvent,
): CompanionInteractionTimeline {
    val matchingId = event.contactId ?: event.sourceMessageId
    fun updateContact(status: CompanionOutboundStatus): List<CompanionOutboundContact> {
        if (matchingId.isNullOrBlank()) return outboundContacts
        val existing = outboundContacts.firstOrNull { it.id == matchingId }
        val base = existing ?: CompanionOutboundContact(
            id = matchingId,
            conversationId = event.conversationId,
            sourceMessageId = event.sourceMessageId,
            generatedAt = event.occurredAt,
        )
        val updated = base.copy(
            conversationId = event.conversationId ?: base.conversationId,
            sourceMessageId = event.sourceMessageId ?: base.sourceMessageId,
            status = status,
            sentAt = if (status >= CompanionOutboundStatus.SENT) base.sentAt ?: event.occurredAt else base.sentAt,
            deliveredAt = if (status >= CompanionOutboundStatus.DELIVERED) base.deliveredAt ?: event.occurredAt else base.deliveredAt,
            openedAt = if (status == CompanionOutboundStatus.OPENED) event.occurredAt else base.openedAt,
            resolvedAt = if (status.isTerminalOutboundStatus()) event.occurredAt else base.resolvedAt,
            result = event.detail ?: base.result,
        )
        return outboundContacts.filterNot { it.id == matchingId } + updated
    }
    fun resolveLatest(status: CompanionOutboundStatus): List<CompanionOutboundContact> {
        val latest = outboundContacts
            .filterNot { it.status.isTerminalOutboundStatus() }
            .maxByOrNull(CompanionOutboundContact::generatedAt)
            ?: return outboundContacts
        return outboundContacts.map { contact ->
            if (contact.id == latest.id) contact.copy(
                status = status,
                resolvedAt = event.occurredAt,
                result = event.detail ?: contact.result,
            ) else contact
        }
    }
    fun updateLatest(status: CompanionOutboundStatus): List<CompanionOutboundContact> {
        val latest = outboundContacts
            .filterNot { it.status.isTerminalOutboundStatus() }
            .maxByOrNull(CompanionOutboundContact::generatedAt)
            ?: return outboundContacts
        return outboundContacts.map { contact ->
            if (contact.id == latest.id) {
                contact.copy(
                    status = status,
                    openedAt = if (status == CompanionOutboundStatus.OPENED) event.occurredAt else contact.openedAt,
                    resolvedAt = if (status.isTerminalOutboundStatus()) event.occurredAt else contact.resolvedAt,
                    result = event.detail ?: contact.result,
                )
            } else contact
        }
    }
    return when (event.kind) {
        CompanionInteractionEventKind.LOCAL_HEARTBEAT -> copy(lastHeartbeatAt = event.occurredAt)
        CompanionInteractionEventKind.USER_ACTIVITY -> copy(lastUserActivityAt = event.occurredAt)
        CompanionInteractionEventKind.USER_REPLY -> copy(
            lastUserActivityAt = event.occurredAt,
            lastUserReplyAt = event.occurredAt,
        )
        CompanionInteractionEventKind.ORDINARY_ASSISTANT -> copy(lastOrdinaryAssistantAt = event.occurredAt)
        CompanionInteractionEventKind.OUTBOUND_GENERATED -> copy(
            outboundContacts = updateContact(CompanionOutboundStatus.GENERATED),
        )
        CompanionInteractionEventKind.OUTBOUND_SENT -> copy(
            lastOutboundAt = event.occurredAt,
            outboundContacts = updateContact(CompanionOutboundStatus.SENT),
        )
        CompanionInteractionEventKind.OUTBOUND_DELIVERED -> copy(
            lastOutboundAt = event.occurredAt,
            outboundContacts = updateContact(CompanionOutboundStatus.DELIVERED),
        )
        CompanionInteractionEventKind.OUTBOUND_OPENED -> copy(
            lastOpenedAt = event.occurredAt,
            outboundContacts = if (matchingId.isNullOrBlank()) {
                updateLatest(CompanionOutboundStatus.OPENED)
            } else {
                updateContact(CompanionOutboundStatus.OPENED)
            },
        )
        CompanionInteractionEventKind.OUTBOUND_UNANSWERED ->
            copy(outboundContacts = updateContact(CompanionOutboundStatus.UNANSWERED))
        CompanionInteractionEventKind.OUTBOUND_REPLIED ->
            copy(outboundContacts = resolveLatest(CompanionOutboundStatus.REPLIED))
        CompanionInteractionEventKind.USER_BUSY ->
            copy(outboundContacts = resolveLatest(CompanionOutboundStatus.USER_BUSY))
        CompanionInteractionEventKind.TOPIC_CHANGED ->
            copy(outboundContacts = resolveLatest(CompanionOutboundStatus.TOPIC_CHANGED))
        CompanionInteractionEventKind.DECLINED ->
            copy(outboundContacts = resolveLatest(CompanionOutboundStatus.DECLINED))
        CompanionInteractionEventKind.REMINDER_COMPLETED ->
            copy(
                outboundContacts = if (matchingId.isNullOrBlank()) {
                    updateLatest(CompanionOutboundStatus.REMINDER_COMPLETED)
                } else {
                    updateContact(CompanionOutboundStatus.REMINDER_COMPLETED)
                },
            )
        CompanionInteractionEventKind.OUTBOUND_FAILED ->
            copy(outboundContacts = updateContact(CompanionOutboundStatus.FAILED))
        CompanionInteractionEventKind.OUTBOUND_CANCELLED ->
            copy(outboundContacts = updateContact(CompanionOutboundStatus.CANCELLED))
        CompanionInteractionEventKind.LIFE_ANCHOR_UPDATED ->
            copy(lastLifeAnchorUpdatedAt = event.occurredAt)
    }
}

private fun CompanionOutboundStatus.isTerminalOutboundStatus(): Boolean = when (this) {
    CompanionOutboundStatus.GENERATED,
    CompanionOutboundStatus.SENT,
    CompanionOutboundStatus.DELIVERED,
    CompanionOutboundStatus.OPENED,
    CompanionOutboundStatus.UNANSWERED -> false
    CompanionOutboundStatus.REPLIED,
    CompanionOutboundStatus.USER_BUSY,
    CompanionOutboundStatus.TOPIC_CHANGED,
    CompanionOutboundStatus.DECLINED,
    CompanionOutboundStatus.REMINDER_COMPLETED,
    CompanionOutboundStatus.FAILED,
    CompanionOutboundStatus.CANCELLED -> true
}

internal fun userReplyInteractionEvents(
    text: String,
    occurredAt: Long,
): List<CompanionInteractionEvent> = buildList {
    add(CompanionInteractionEvent(CompanionInteractionEventKind.USER_REPLY, occurredAt))
    val normalized = text.trim().lowercase()
    add(
        when {
            BUSY_REPLY_MARKERS.any(normalized::contains) ->
                CompanionInteractionEvent(CompanionInteractionEventKind.USER_BUSY, occurredAt, detail = "user_busy")
            DECLINE_REPLY_MARKERS.any(normalized::contains) ->
                CompanionInteractionEvent(CompanionInteractionEventKind.DECLINED, occurredAt, detail = "user_declined")
            REMINDER_COMPLETED_MARKERS.any(normalized::contains) ->
                CompanionInteractionEvent(
                    CompanionInteractionEventKind.REMINDER_COMPLETED,
                    occurredAt,
                    detail = "user_reported_completion",
                )
            TOPIC_CHANGED_MARKERS.any(normalized::contains) ->
                CompanionInteractionEvent(
                    CompanionInteractionEventKind.TOPIC_CHANGED,
                    occurredAt,
                    detail = "user_changed_topic",
                )
            else -> CompanionInteractionEvent(CompanionInteractionEventKind.OUTBOUND_REPLIED, occurredAt)
        },
    )
}

private val BUSY_REPLY_MARKERS = setOf("在忙", "忙着", "没空", "稍后", "等会", "晚点", "busy", "later")
private val DECLINE_REPLY_MARKERS = setOf("别发", "别问", "不想聊", "不要提醒", "拒绝", "stop", "leave me alone")
private val REMINDER_COMPLETED_MARKERS = setOf("做完了", "完成了", "已经完成", "弄好了", "办完了", "done", "finished")
private val TOPIC_CHANGED_MARKERS = setOf("换个话题", "说点别的", "不说这个", "聊点别的", "change the subject")
private const val MAX_OUTBOUND_CONTACT_HISTORY = 80

private fun CompanionState.hasVisibleStateContent(): Boolean = listOf(
    statusText,
    innerThought,
    mood,
    bodyState,
    mindState,
    activityMode,
    selfScene,
).any(String::isNotBlank)

private fun CompanionState.hasSameVisibleContent(other: CompanionState): Boolean =
    statusText.trim() == other.statusText.trim() &&
        innerThought.trim() == other.innerThought.trim() &&
        mood.trim() == other.mood.trim() &&
        bodyState.trim() == other.bodyState.trim() &&
        mindState.trim() == other.mindState.trim() &&
        activityMode.trim() == other.activityMode.trim() &&
        selfScene.trim() == other.selfScene.trim()

private fun CompanionConcernChange.belongsTo(assistantId: String): Boolean = when (this) {
    is CompanionConcernChange.Upsert -> concern.assistantId == assistantId
    is CompanionConcernChange.Reopen -> concern.assistantId == assistantId
    is CompanionConcernChange.Complete -> this.assistantId == assistantId
    is CompanionConcernChange.Cancel -> this.assistantId == assistantId
}

private fun List<CompanionRelationshipEvent>.appendRelationshipEvents(
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

internal fun CompanionPersistedState.snapshotOrEmpty(assistantId: String): CompanionSnapshot =
    snapshots.firstOrNull { it.assistantId == assistantId } ?: CompanionSnapshot.empty(assistantId)

internal fun CompanionPersistedState.withUpdatedSnapshot(
    snapshot: CompanionSnapshot,
    appliedRelationshipEventIds: Set<String> = this.appliedRelationshipEventIds.toSet(),
    affectedCommitmentId: String? = null,
): CompanionRuntimeReduction {
    val updated = copy(
        snapshots = snapshots.filterNot { it.assistantId == snapshot.assistantId } + snapshot,
        appliedRelationshipEventIds = appliedRelationshipEventIds.toList(),
    ).normalizedCompanionState()
    val normalizedSnapshot = updated.snapshots.first { it.assistantId == snapshot.assistantId }
    return CompanionRuntimeReduction(
        persistedState = updated,
        snapshot = normalizedSnapshot,
        affectedCommitment = affectedCommitmentId?.let { id ->
            normalizedSnapshot.commitments.firstOrNull { it.id == id }
        },
    )
}

internal fun CompanionPersistedState.unchangedReduction(
    snapshot: CompanionSnapshot,
): CompanionRuntimeReduction = CompanionRuntimeReduction(
    persistedState = this,
    snapshot = snapshot,
)

internal val SCHEDULABLE_COMMITMENT_STATUSES = setOf(
    CompanionCommitmentStatus.ACTIVE,
    CompanionCommitmentStatus.DUE,
    CompanionCommitmentStatus.RETRY_SCHEDULED,
)

internal const val MAX_ALWAYS_ON_ANCHORS = 32
internal const val STALE_COMMITMENT_EXECUTION_MILLIS = 5L * 60L * 1_000L
