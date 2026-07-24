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

    // Automatic extraction is edge-triggered instead of level-triggered.
    // With an interval of 40, only stable counts 40, 80, 120... are eligible.
    // Counts such as 41, 59 or 79 cannot create sliding 2..41 / 20..59 windows.
    if (stableTurns.size % extractionInterval != 0) return null

    val boundaryWindow = stableTurns.takeLast(extractionInterval)
    if (boundaryWindow.size != extractionInterval) return null
    if (boundaryWindow.all { turn -> turn.nodeId in processedSourceNodeIds }) return null

    return AffectiveMemoryExtractionPlan(
        turns = boundaryWindow,
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
