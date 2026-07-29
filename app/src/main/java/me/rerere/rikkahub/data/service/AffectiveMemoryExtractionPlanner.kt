package me.rerere.rikkahub.data.service

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.MessageNode
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.security.MessageDigest

const val DEFAULT_MEMORY_EXTRACTION_INTERVAL = 20
const val DEFAULT_MEMORY_EXTRACTION_PROTECTED_RECENT_COUNT = 10

data class AffectiveMemoryExtractionPlan(
    val turns: List<MemoryExtractionTurn>,
    val reason: String,
)

enum class MemoryExtractionDirection {
    OLDEST_FIRST,
    RECENT_FIRST,
}

internal fun buildSelectedConversationBranchId(
    messageNodes: List<MessageNode>,
    endSequenceInclusive: Int = messageNodes.size,
): String {
    val selectedPath = messageNodes
        .take(endSequenceInclusive.coerceIn(0, messageNodes.size))
        .mapNotNull { node ->
            runCatching { "${node.id}:${node.currentMessage.id}" }.getOrNull()
        }
        .joinToString("|")
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(selectedPath.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return "selected:${digest.take(24)}"
}

internal fun firstSelectedBranchMutationSequence(
    before: List<MessageNode>,
    after: List<MessageNode>,
): Int? {
    val sharedSize = minOf(before.size, after.size)
    for (index in 0 until sharedSize) {
        val beforeIdentity = runCatching {
            before[index].id.toString() to before[index].currentMessage.id.toString()
        }.getOrNull()
        val afterIdentity = runCatching {
            after[index].id.toString() to after[index].currentMessage.id.toString()
        }.getOrNull()
        if (beforeIdentity != afterIdentity) return index + 1
    }
    return if (before.size != after.size) sharedSize + 1 else null
}

fun buildAffectiveMemoryExtractionPlan(
    messageNodes: List<MessageNode>,
    processedSourceNodeIds: Set<String>,
    extractionInterval: Int = DEFAULT_MEMORY_EXTRACTION_INTERVAL,
    protectedRecentCount: Int = DEFAULT_MEMORY_EXTRACTION_PROTECTED_RECENT_COUNT,
    direction: MemoryExtractionDirection = MemoryExtractionDirection.OLDEST_FIRST,
): AffectiveMemoryExtractionPlan? {
    if (extractionInterval <= 0) return null

    val logicalTurns = messageNodes.toMemoryExtractionTurns()
    val stableTurns = logicalTurns.dropLast(protectedRecentCount.coerceAtLeast(0))
    if (stableTurns.size < extractionInterval) return null

    // Build fixed, non-overlapping windows from the beginning of the stable region. The planner is
    // deliberately level-triggered: if an automatic run missed the exact 20/40/60 boundary, the
    // next completed reply can still recover that aligned batch without creating a sliding window.
    val pendingWindows = stableTurns
        .chunked(extractionInterval)
        .asSequence()
        .filter { window -> window.size == extractionInterval }
        .filter { window -> window.any { turn -> turn.nodeId !in processedSourceNodeIds } }
        .toList()
    if (pendingWindows.isEmpty()) return null

    // A partially checkpointed legacy window is rebuilt as one complete aligned batch. Saved
    // memories retain source-node IDs, so storage-level identity checks prevent duplicate rows.
    val selectedTurns = when (direction) {
        MemoryExtractionDirection.OLDEST_FIRST -> pendingWindows.first()
        MemoryExtractionDirection.RECENT_FIRST -> pendingWindows.last()
    }

    return AffectiveMemoryExtractionPlan(
        turns = selectedTurns,
        reason = if (direction == MemoryExtractionDirection.RECENT_FIRST) "recent_interval" else "interval",
    )
}

internal fun List<MessageNode>.toMemoryExtractionTurns(): List<MemoryExtractionTurn> =
    mapNotNull { node ->
        val message = runCatching { node.currentMessage }.getOrNull() ?: return@mapNotNull null
        if (message.role != MessageRole.USER && message.role != MessageRole.ASSISTANT) return@mapNotNull null
        val text = message.toText().trim()
        if (text.isBlank()) return@mapNotNull null
        MemoryExtractionTurn(
            nodeId = node.id.toString(),
            role = message.role.name.lowercase(),
            text = text,
            createdAtMillis = runCatching {
                message.createdAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            }.getOrDefault(0L),
        )
    }
