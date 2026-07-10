package me.rerere.rikkahub.data.cihai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class CihaiState(
    val selectedAssistantId: String = "",
    val entries: List<CihaiEntry> = emptyList(),
    val memoryQueue: List<CihaiMemoryQueueItem> = emptyList(),
)

@Serializable
data class CihaiMemoryQueueItem(
    val entryId: String,
    val assistantId: String,
    val enqueuedAt: Long,
    val attemptCount: Int = 0,
    val nextAttemptAt: Long = enqueuedAt,
    val lastError: String? = null,
)

@Serializable
enum class CihaiMemoryDisposition {
    @SerialName("pending")
    PENDING,

    @SerialName("saved")
    SAVED,

    @SerialName("cihai_only")
    CIHAI_ONLY,
}

@Serializable
data class CihaiMemoryPolicy(
    val recentCihaiContextLimit: Int = 60,
    val unsummarizedCihaiLimit: Int = 60,
    val summarizeEveryEntries: Int = 60,
)

data class CihaiMemoryContext(
    val recentEntries: List<CihaiEntry>,
    val unsummarizedEntries: List<CihaiEntry>,
    val shouldSummarize: Boolean,
)

fun buildCihaiMemoryContext(
    entries: List<CihaiEntry>,
    policy: CihaiMemoryPolicy = CihaiMemoryPolicy(),
): CihaiMemoryContext {
    val ordered = entries.sortedBy { it.createdAt }
    val unsummarized = ordered.filter { it.resolvedMemoryDisposition == CihaiMemoryDisposition.PENDING }
    return CihaiMemoryContext(
        recentEntries = ordered.takeLast(policy.recentCihaiContextLimit.coerceAtLeast(0)),
        unsummarizedEntries = unsummarized.take(policy.unsummarizedCihaiLimit.coerceAtLeast(0)),
        shouldSummarize = unsummarized.size >= policy.summarizeEveryEntries.coerceAtLeast(1),
    )
}

@Serializable
data class CihaiEntry(
    val id: String = Uuid.random().toString(),
    val assistantId: String,
    val kind: CihaiEntryKind,
    val title: String,
    val content: String,
    val emotion: String = "",
    val sourceTitle: String? = null,
    val sourceExcerpt: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val memoryDisposition: CihaiMemoryDisposition = CihaiMemoryDisposition.CIHAI_ONLY,
    val memorySaved: Boolean = false,
) {
    val resolvedMemoryDisposition: CihaiMemoryDisposition
        get() = if (memorySaved) CihaiMemoryDisposition.SAVED else memoryDisposition
}

@Serializable
enum class CihaiEntryKind(val label: String) {
    @SerialName("diary")
    DIARY("日记"),

    @SerialName("inner_journal")
    INNER_JOURNAL("心迹"),

    @SerialName("action_log")
    ACTION_LOG("行动"),

    @SerialName("reading_note")
    READING_NOTE("阅读"),

    @SerialName("reflection")
    REFLECTION("沉淀"),
}
