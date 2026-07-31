package me.rerere.rikkahub.data.companion

import java.util.concurrent.ConcurrentHashMap

class CompanionRuntime(
    private val store: CompanionStore,
) {
    private val recentSnapshots = ConcurrentHashMap<String, CompanionSnapshot>()

    fun snapshot(assistantId: String): CompanionSnapshot {
        val persisted = store.snapshot(assistantId)
        val recent = recentSnapshots[assistantId]
        return if (recent != null && recent.updatedAt >= persisted.updatedAt) recent else persisted
    }

    fun perception(input: CompanionPerceptionInput): CompanionPerceptionPacket =
        CompanionPerceptionAssembler.assemble(
            input = input,
            snapshot = snapshot(input.assistantId),
        )

    suspend fun applyTurn(mutation: CompanionTurnMutation): CompanionSnapshot {
        var reduction: CompanionRuntimeReduction? = null
        store.update { current ->
            reduceCompanionRuntimeState(current, mutation)
                .also { reduction = it }
                .persistedState
        }
        return reduction?.snapshot
            ?.also(::remember)
            ?: snapshot(mutation.assistantId)
    }

    suspend fun beginCommitment(
        assistantId: String,
        commitmentId: String,
        nowMillis: Long,
    ): CompanionCommitment? {
        var reduction: CompanionRuntimeReduction? = null
        store.update { current ->
            beginCompanionCommitment(
                current = current,
                assistantId = assistantId,
                commitmentId = commitmentId,
                nowMillis = nowMillis,
            ).also { reduction = it }.persistedState
        }
        reduction?.snapshot?.let(::remember)
        return reduction?.affectedCommitment
    }

    suspend fun finishCommitment(
        assistantId: String,
        commitmentId: String,
        result: CompanionActionResult,
        retryAt: Long? = null,
    ): CompanionCommitment? {
        var reduction: CompanionRuntimeReduction? = null
        store.update { current ->
            finishCompanionCommitment(
                current = current,
                assistantId = assistantId,
                commitmentId = commitmentId,
                result = result,
                retryAt = retryAt,
            ).also { reduction = it }.persistedState
        }
        reduction?.snapshot?.let(::remember)
        return reduction?.affectedCommitment
    }

    suspend fun fulfillCommitmentFromEvidence(
        assistantId: String,
        commitmentId: String,
        summary: String,
        completedAt: Long,
        outputReference: String? = null,
    ): CompanionCommitment? {
        var reduction: CompanionRuntimeReduction? = null
        store.update { current ->
            fulfillCompanionCommitmentFromEvidence(
                current = current,
                assistantId = assistantId,
                commitmentId = commitmentId,
                summary = summary,
                completedAt = completedAt,
                outputReference = outputReference,
            ).also { reduction = it }.persistedState
        }
        reduction?.snapshot?.let(::remember)
        return reduction?.affectedCommitment
    }

    suspend fun continueCommitment(
        assistantId: String,
        commitmentId: String,
        result: CompanionActionResult,
        nextDueAt: Long,
    ): CompanionCommitment? {
        var reduction: CompanionRuntimeReduction? = null
        store.update { current ->
            continueCompanionCommitment(
                current = current,
                assistantId = assistantId,
                commitmentId = commitmentId,
                result = result,
                nextDueAt = nextDueAt,
            ).also { reduction = it }.persistedState
        }
        reduction?.snapshot?.let(::remember)
        return reduction?.affectedCommitment
    }

    suspend fun cancelCommitment(
        assistantId: String,
        commitmentId: String,
        reason: String,
        nowMillis: Long,
    ): CompanionCommitment? {
        var reduction: CompanionRuntimeReduction? = null
        store.update { current ->
            cancelCompanionCommitment(
                current = current,
                assistantId = assistantId,
                commitmentId = commitmentId,
                reason = reason,
                nowMillis = nowMillis,
            ).also { reduction = it }.persistedState
        }
        reduction?.snapshot?.let(::remember)
        return reduction?.affectedCommitment
    }

    fun nextCommitment(
        assistantId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): CompanionCommitment? = selectNextCompanionCommitment(
        snapshots = effectiveSnapshots().filter { it.assistantId == assistantId },
        nowMillis = nowMillis,
    )

    fun nextCommitment(
        nowMillis: Long = System.currentTimeMillis(),
    ): CompanionCommitment? = selectNextCompanionCommitment(
        snapshots = effectiveSnapshots(),
        nowMillis = nowMillis,
    )

    suspend fun clearAssistant(assistantId: String) {
        if (assistantId.isBlank()) return
        recentSnapshots.remove(assistantId)
        store.clearAssistant(assistantId)
    }

    suspend fun repairRelationshipEventTimes(
        assistantId: String,
        sourceTimesByNodeId: Map<String, Long>,
        repairedAt: Long = System.currentTimeMillis(),
    ): Int {
        if (assistantId.isBlank() || sourceTimesByNodeId.isEmpty()) return 0
        var repairedCount = 0
        var repairedSnapshot: CompanionSnapshot? = null
        store.updateSnapshot(assistantId) { snapshot ->
            val history = snapshot.relationshipHistory.map { event ->
                val sourceAt = sourceTimesByNodeId[event.sourceId]
                    ?.takeIf { it > 0L }
                    ?: return@map event
                val occurredAt = event.occurredAt
                    ?.takeIf { it > 0L && it <= sourceAt + RELATIONSHIP_TIME_FUTURE_TOLERANCE_MS }
                    ?: sourceAt
                val updated = event.copy(
                    sourceMessageAt = sourceAt,
                    occurredAt = occurredAt,
                    extractedAt = event.extractedAt ?: repairedAt,
                    createdAt = occurredAt,
                )
                if (updated != event) repairedCount += 1
                updated
            }
            if (repairedCount == 0) return@updateSnapshot snapshot
            val rebuilt = CompanionRelationshipReducer.apply(
                assistantId = assistantId,
                current = CompanionRelationshipState(),
                appliedEventIds = emptySet(),
                events = history,
                nowMillis = repairedAt,
            )
            snapshot.copy(
                relationship = rebuilt.relationship,
                relationshipHistory = history,
                updatedAt = maxOf(snapshot.updatedAt, repairedAt),
            ).also { repairedSnapshot = it }
        }
        repairedSnapshot?.let(::remember)
        return repairedCount
    }

    private fun remember(snapshot: CompanionSnapshot) {
        recentSnapshots.compute(snapshot.assistantId) { _, current ->
            if (current == null || snapshot.updatedAt >= current.updatedAt) snapshot else current
        }
    }

    private fun effectiveSnapshots(): List<CompanionSnapshot> {
        val merged = store.state.value.snapshots.associateBy { it.assistantId }.toMutableMap()
        recentSnapshots.values.forEach { recent ->
            val persisted = merged[recent.assistantId]
            if (persisted == null || recent.updatedAt >= persisted.updatedAt) {
                merged[recent.assistantId] = recent
            }
        }
        return merged.values.toList()
    }
}

private const val RELATIONSHIP_TIME_FUTURE_TOLERANCE_MS = 24L * 60L * 60L * 1_000L
