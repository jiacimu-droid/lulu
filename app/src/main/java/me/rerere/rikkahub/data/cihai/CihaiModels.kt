package me.rerere.rikkahub.data.cihai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.service.AffectiveMemoryCandidate
import kotlin.uuid.Uuid

@Serializable
data class CihaiState(
    val selectedAssistantId: String = "",
    val entries: List<CihaiEntry> = emptyList(),
    val books: List<CihaiBook> = emptyList(),
)

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
    val memorySaved: Boolean = false,
)

@Serializable
enum class CihaiEntryKind(val label: String) {
    @SerialName("inner_journal")
    INNER_JOURNAL("心迹"),

    @SerialName("action_log")
    ACTION_LOG("行动"),

    @SerialName("reading_note")
    READING_NOTE("阅读"),

    @SerialName("reflection")
    REFLECTION("沉淀"),
}

@Serializable
data class CihaiBook(
    val id: String = Uuid.random().toString(),
    val assistantId: String,
    val title: String,
    val content: String,
    val progressPercent: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long? = null,
)

fun CihaiEntry.toMemoryCandidate(): AffectiveMemoryCandidate {
    val kindName = when (kind) {
        CihaiEntryKind.INNER_JOURNAL -> "cihai_journal"
        CihaiEntryKind.ACTION_LOG -> "cihai_action"
        CihaiEntryKind.READING_NOTE -> "cihai_reading"
        CihaiEntryKind.REFLECTION -> "cihai_reflection"
    }
    val source = sourceTitle?.takeIf { it.isNotBlank() }?.let { "来源：《$it》\n" }.orEmpty()
    val feeling = emotion.takeIf { it.isNotBlank() }
    val fullContent = buildString {
        append(source)
        append(content.trim())
        if (!feeling.isNullOrBlank()) {
            append("\n当时情绪：")
            append(feeling)
        }
    }.trim()
    return AffectiveMemoryCandidate(
        type = kindName,
        title = title.ifBlank { kind.label },
        content = fullContent,
        roleFeeling = feeling,
        unspokenThought = if (kind == CihaiEntryKind.INNER_JOURNAL) content.trim() else null,
        relationshipEffect = when (kind) {
            CihaiEntryKind.INNER_JOURNAL -> "角色在用户沉默时产生了内心判断，并把未说出口的想法沉淀下来。"
            CihaiEntryKind.ACTION_LOG -> "角色记录了自己等待、克制、观察或照看的行动选择。"
            CihaiEntryKind.READING_NOTE -> "角色通过阅读形成了新的理解，可能改变之后陪伴用户的方式。"
            CihaiEntryKind.REFLECTION -> "角色把多次判断后的经验整理成后续可复用的长期记忆。"
        },
        importance = when (kind) {
            CihaiEntryKind.REFLECTION -> 5
            CihaiEntryKind.READING_NOTE -> 4
            else -> 3
        },
        confidence = 1.0,
        tags = listOf("辞海", kind.label) + sourceTitle?.takeIf { it.isNotBlank() }.let { listOfNotNull(it) },
        embeddingText = buildString {
            append(title)
            append("\n")
            append(fullContent)
            append("\n记忆用途：之后遇到相似沉默、学习、身体状态或关系情境时，角色应参考这次判断。")
        },
        people = listOf("用户", "角色"),
        topics = listOf("活人感", "内心日志", kind.label),
    )
}
