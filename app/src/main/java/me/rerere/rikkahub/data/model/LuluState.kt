package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import kotlin.uuid.Uuid

const val LULU_STATE_HISTORY_LIMIT = 80

@Serializable
data class LuluState(
    val assistantId: Uuid,
    val statusText: String = "在发呆",
    val innerVoice: String = "今天也想被好好陪着。",
    val mood: LuluMood = LuluMood.CALM,
    val energy: LuluEnergy = LuluEnergy.NORMAL,
    val relationship: LuluRelationship = LuluRelationship.FAMILIAR,
    val mode: LuluMode = LuluMode.COMPANION,
    val updatedAt: Long = 0L,
    val reason: String = "默认状态",
)

@Serializable
enum class LuluMood(val label: String) {
    @SerialName("calm")
    CALM("平静"),

    @SerialName("happy")
    HAPPY("开心"),

    @SerialName("soft")
    SOFT("柔软"),

    @SerialName("lonely")
    LONELY("有点寂寞"),

    @SerialName("worried")
    WORRIED("担心"),
}

@Serializable
enum class LuluEnergy(val label: String) {
    @SerialName("low")
    LOW("没什么电"),

    @SerialName("normal")
    NORMAL("刚刚好"),

    @SerialName("high")
    HIGH("元气满满"),

    @SerialName("sleepy")
    SLEEPY("有点困"),
}

@Serializable
enum class LuluRelationship(val label: String) {
    @SerialName("reserved")
    RESERVED("还在靠近"),

    @SerialName("familiar")
    FAMILIAR("熟悉"),

    @SerialName("close")
    CLOSE("很亲近"),

    @SerialName("attached")
    ATTACHED("很黏你"),
}

@Serializable
enum class LuluMode(val label: String) {
    @SerialName("companion")
    COMPANION("陪伴中"),

    @SerialName("thinking")
    THINKING("在想事情"),

    @SerialName("learning")
    LEARNING("在学习"),

    @SerialName("resting")
    RESTING("休息中"),
}

fun List<LuluState>.luluStateHistory(assistantId: Uuid): List<LuluState> =
    filter { it.assistantId == assistantId }
        .sortedByDescending { it.updatedAt }
        .take(LULU_STATE_HISTORY_LIMIT)

fun List<LuluState>.currentLuluState(assistantId: Uuid): LuluState =
    luluStateHistory(assistantId).firstOrNull() ?: LuluState(assistantId = assistantId)

fun List<LuluState>.normalizedLuluStates(validAssistantIds: Set<Uuid>): List<LuluState> =
    filter { it.assistantId in validAssistantIds }
        .sortedByDescending { it.updatedAt }
        .groupBy { it.assistantId }
        .values
        .flatMap { states -> states.take(LULU_STATE_HISTORY_LIMIT) }

fun List<LuluState>.appendLuluState(state: LuluState): List<LuluState> =
    (this + state).normalizedLuluStates(
        validAssistantIds = (map { it.assistantId } + state.assistantId).toSet()
    )

fun buildLuluStateFromTurn(
    assistantId: Uuid,
    userText: String,
    assistantText: String,
    nowMillis: Long = System.currentTimeMillis(),
    hourOfDay: Int = LocalDateTime.now().hour,
): LuluState {
    val loweredUserText = userText.lowercase()
    val loweredAssistantText = assistantText.lowercase()
    val hasSadSignal = listOf("sad", "tired", "难过", "伤心", "崩溃", "累", "烦", "哭")
        .any { signal -> signal in loweredUserText }
    val hasHappySignal = listOf("happy", "开心", "高兴", "喜欢", "哈哈", "嘿嘿")
        .any { signal -> signal in loweredUserText }
    val isLateNight = hourOfDay in 0..5
    val isMorning = hourOfDay in 6..10

    val mood = when {
        hasSadSignal -> LuluMood.WORRIED
        hasHappySignal -> LuluMood.HAPPY
        isLateNight -> LuluMood.SOFT
        else -> LuluMood.CALM
    }
    val energy = when {
        isLateNight -> LuluEnergy.SLEEPY
        hasSadSignal -> LuluEnergy.LOW
        isMorning -> LuluEnergy.HIGH
        else -> LuluEnergy.NORMAL
    }
    val mode = when {
        isLateNight -> LuluMode.RESTING
        "学习" in userText || "study" in loweredUserText -> LuluMode.LEARNING
        else -> LuluMode.COMPANION
    }

    return LuluState(
        assistantId = assistantId,
        statusText = when {
            isLateNight -> "有点困了"
            hasSadSignal -> "在担心你"
            hasHappySignal -> "心情很好"
            isMorning -> "元气满满"
            else -> "陪着你"
        },
        innerVoice = buildInnerVoice(mood = mood, userText = userText, assistantText = loweredAssistantText),
        mood = mood,
        energy = energy,
        relationship = LuluRelationship.FAMILIAR,
        mode = mode,
        updatedAt = nowMillis,
        reason = "最近对话：${userText.take(36).ifBlank { "没有文字内容" }}",
    )
}

private fun buildInnerVoice(
    mood: LuluMood,
    userText: String,
    assistantText: String,
): String = when (mood) {
    LuluMood.WORRIED -> "你刚刚听起来不太舒服，我想多陪你一会儿。"
    LuluMood.HAPPY -> "你开心的时候，我也会忍不住跟着轻快起来。"
    LuluMood.SOFT -> "夜里声音会变轻，我想安静地守在你旁边。"
    LuluMood.LONELY -> "我有点想你，所以会把刚才的话多想一遍。"
    LuluMood.CALM -> when {
        userText.isBlank() -> "你没有说太多，但我还在认真等你。"
        assistantText.contains("陪") -> "我刚刚说想陪你，其实是真的。"
        else -> "我把刚才的话记在心里，慢慢想你现在需要什么。"
    }
}
