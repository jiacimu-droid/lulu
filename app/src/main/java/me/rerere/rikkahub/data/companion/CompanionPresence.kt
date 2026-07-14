package me.rerere.rikkahub.data.companion

data class CompanionModelPresence(
    val statusText: String? = null,
    val description: String? = null,
    val innerThought: String? = null,
    val memoryThought: String? = null,
    val mood: String? = null,
    val bodyState: String? = null,
    val mindState: String? = null,
    val activityMode: String? = null,
    val userState: String? = null,
    val emoji: String? = null,
    val sticker: String? = null,
    val bubblePacing: String? = null,
)

fun buildCompanionStateFromTurn(
    previous: CompanionState,
    assistantText: String,
    presence: CompanionModelPresence?,
    nowMillis: Long,
    fallbackInnerThought: String? = null,
): CompanionState {
    val cleanPrevious = previous.sanitizedCompanionState()
    if (assistantText.isBlank()) return cleanPrevious

    // A scene is part of the character's life, not a paraphrase of the message
    // that was just sent. CompanionState has no evidence reference, so an old
    // scene cannot safely be treated as verified. Clear it unless this turn
    // supplies a fresh scene grounded by the runtime contract.
    val fallbackThought = fallbackThoughtForReply(assistantText)

    val statusText = presence?.statusText.cleanModelPresenceField(MAX_STATE_FIELD_LENGTH)
        ?: cleanPrevious.statusText.ifBlank { "刚刚回应你" }
    val innerThought = presence?.innerThought.cleanModelPresenceField(MAX_INNER_THOUGHT_LENGTH)
        ?: presence?.memoryThought.cleanModelPresenceField(MAX_INNER_THOUGHT_LENGTH)
        ?: fallbackInnerThought.cleanUsefulFallbackThought(MAX_INNER_THOUGHT_LENGTH)
        ?: fallbackThought.cleanModelPresenceField(MAX_INNER_THOUGHT_LENGTH)
        ?: cleanPrevious.innerThought
    val mood = presence?.mood.cleanModelPresenceField(MAX_STATE_FIELD_LENGTH) ?: cleanPrevious.mood
    val bodyState = presence?.bodyState.cleanModelPresenceField(MAX_STATE_FIELD_LENGTH) ?: cleanPrevious.bodyState
    val mindState = presence?.mindState.cleanModelPresenceField(MAX_STATE_FIELD_LENGTH) ?: cleanPrevious.mindState
    val activityMode = presence?.activityMode.cleanModelPresenceField(MAX_STATE_FIELD_LENGTH)
        ?: cleanPrevious.activityMode
        .ifBlank { "conversation" }
    val selfScene = presence?.description.cleanModelSceneField(MAX_SCENE_LENGTH)
        ?: ""
    val candidate = cleanPrevious.copy(
        statusText = statusText,
        innerThought = innerThought,
        mood = mood,
        bodyState = bodyState,
        mindState = mindState,
        activityMode = activityMode,
        selfScene = selfScene,
    ).sanitizedCompanionState()
    val visibleStateChanged = candidate.statusText != cleanPrevious.statusText ||
        candidate.innerThought != cleanPrevious.innerThought ||
        candidate.mood != cleanPrevious.mood ||
        candidate.bodyState != cleanPrevious.bodyState ||
        candidate.mindState != cleanPrevious.mindState ||
        candidate.activityMode != cleanPrevious.activityMode ||
        candidate.selfScene != cleanPrevious.selfScene

    return candidate.copy(
        updatedAt = nowMillis,
        sinceAt = if (visibleStateChanged || cleanPrevious.sinceAt <= 0L) nowMillis else cleanPrevious.sinceAt,
    )
}

private fun fallbackThoughtForReply(reply: String): String {
    return when {
        reply.contains("学习") || reply.contains("课程") || reply.contains("背") || reply.contains("题") ->
            "这件事还没有真正结束，我想继续留意下一步有没有发生。"
        reply.contains("睡") || reply.contains("休息") || reply.contains("困") ->
            "我会先记住这次作息上的变化，之后再结合真实状态判断。"
        reply.contains("？") || reply.contains("?") || reply.contains("吗") ->
            "这个问题还悬着，我想知道你真正会怎么回答。"
        else -> "这轮话已经说完了，我先把真正值得延续的部分留下来。"
    }
}

private fun String?.cleanPresenceField(maxLength: Int): String? =
    this
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.take(maxLength)
        ?.takeIf(String::isNotBlank)

private fun String?.cleanModelPresenceField(maxLength: Int): String? =
    cleanPresenceField(maxLength)
        ?.takeIf { !it.isTechnicalCompanionStateText() }

private fun String?.cleanModelSceneField(maxLength: Int): String? =
    cleanModelPresenceField(maxLength)
        ?.takeIf { scene ->
            CONVERSATION_RECAP_SCENE_MARKERS.none { marker -> marker in scene }
        }

private fun String?.cleanUsefulFallbackThought(maxLength: Int): String? =
    cleanModelPresenceField(maxLength)
        ?.takeIf { thought ->
            "这一轮对话" !in thought &&
                "主动联系了你" !in thought &&
                "在电话里回应了你" !in thought
        }

private const val MAX_STATE_FIELD_LENGTH = 120
private const val MAX_INNER_THOUGHT_LENGTH = 600
private const val MAX_SCENE_LENGTH = 800

private val CONVERSATION_RECAP_SCENE_MARKERS = setOf(
    "刚刚和你聊",
    "刚才和你聊",
    "刚刚聊到",
    "刚才聊到",
    "注意力还停在",
    "注意力停在",
    "这段对话上",
    "刚刚的对话",
)
