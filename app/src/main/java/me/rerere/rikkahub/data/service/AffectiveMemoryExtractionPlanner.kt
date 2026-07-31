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

    // Only fixed, non-overlapping windows are valid: 1..N, N+1..2N, and so on.
    // Never slide a window forward with every new message. Sliding windows produced legacy ranges
    // such as 51..90, 53..92 and caused the same conversation area to be retried repeatedly.
    val pendingWindows = stableTurns
        .chunked(extractionInterval)
        .asSequence()
        .filter { window -> window.size == extractionInterval }
        .filter { window -> window.any { turn -> turn.nodeId !in processedSourceNodeIds } }
        .toList()
    if (pendingWindows.isEmpty()) return null

    // A gap must always be repaired from the oldest complete aligned batch. RECENT_FIRST remains in
    // the public call shape for compatibility, but it must not skip 1..40 in order to process a
    // newer batch. This also makes manual retry deterministic instead of retrying an unrelated
    // recent range.
    @Suppress("UNUSED_VARIABLE")
    val compatibilityDirection = direction
    val selectedTurns = pendingWindows.first()

    return AffectiveMemoryExtractionPlan(
        turns = selectedTurns,
        reason = "oldest_pending_interval",
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
