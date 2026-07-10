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
    val innerVoice: String = "我先保持当前角色的自然状态，不预设关系，也不急着开口。",
    val mood: LuluMood = LuluMood.CALM,
    val moodIntensity: Float = 0.35f,
    val energy: LuluEnergy = LuluEnergy.NORMAL,
    val energyIntensity: Float = 0.5f,
    val relationship: LuluRelationship = LuluRelationship.RESERVED,
    val relationshipIntensity: Float = 0.25f,
    val mode: LuluMode = LuluMode.COMPANION,
    val updatedAt: Long = 0L,
    val sinceAt: Long = updatedAt,
    val selfScene: String = "保持当前角色的自然状态，留意对话是否继续。",
    val perceptionSummary: String = "",
    val reason: String = "默认状态",
)

fun LuluState.durationMillis(nowMillis: Long = System.currentTimeMillis()): Long =
    (nowMillis - sinceAt).coerceAtLeast(0L)

fun LuluState.projectForSilence(nowMillis: Long = System.currentTimeMillis()): LuluState {
    if (updatedAt <= 0L) return this
    val silenceMillis = (nowMillis - updatedAt).coerceAtLeast(0L)
    val silenceMinutes = silenceMillis / 60_000L
    if (silenceMinutes < 8) return this

    val projected = when {
        silenceMinutes >= 8 * 60 -> copy(
            statusText = "在做自己的事",
            innerVoice = "对话停了一段时间，我先做自己的事，同时保留必要上下文，不替这段沉默编造含义。",
            mood = if (mood == LuluMood.WORRIED) LuluMood.SOFT else LuluMood.CALM,
            moodIntensity = (moodIntensity - 0.10f).coerceIn(0.15f, 1.0f),
            energy = if (energy == LuluEnergy.SLEEPY) LuluEnergy.SLEEPY else LuluEnergy.NORMAL,
            mode = LuluMode.THINKING,
            selfScene = "当前角色已经转去做自己的事，只保留对话上下文，等出现新信息再按人设判断。",
        )
        silenceMinutes >= 90 -> copy(
            statusText = "对话暂时安静",
            innerVoice = "已经安静了一阵子，原因仍然未知；我先保留刚才的上下文，不把沉默解释成疏远或需要联系。",
            mood = if (mood == LuluMood.WORRIED) LuluMood.SOFT else LuluMood.CALM,
            moodIntensity = (moodIntensity - 0.06f).coerceIn(0.15f, 1.0f),
            mode = LuluMode.THINKING,
            selfScene = "当前角色注意到对话暂停，但原因未知，正在按人设边界判断是否需要后续行动。",
        )
        silenceMinutes >= 30 -> copy(
            statusText = "记着刚才的话",
            innerVoice = "对话停了一会儿，我还保留刚才的信息；先不催促，等新的事实再决定是否行动。",
            mood = if (mood == LuluMood.HAPPY) LuluMood.SOFT else mood,
            mode = if (mode == LuluMode.LEARNING) LuluMode.LEARNING else LuluMode.THINKING,
            selfScene = "当前角色保留着上一轮上下文，同时把注意力放回自己的当前状态。",
        )
        else -> copy(
            statusText = "对话刚安静",
            innerVoice = "对话刚停下来，我先保留上下文，不急着把短暂空档解释成任何关系信号。",
            selfScene = "对话刚安静下来，当前角色暂时不追加动作，等待新的信息。",
        )
    }
    return projected.copy(
        reason = listOf(reason, "观察到你安静了 ${silenceMinutes} 分钟；这只作为事实交给角色判断，不自动替角色做决定")
            .filter { it.isNotBlank() }
            .joinToString("；"),
    )
}

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
    RESERVED("谨慎"),

    @SerialName("familiar")
    FAMILIAR("熟悉"),

    @SerialName("close")
    CLOSE("很亲近"),

    @SerialName("attached")
    ATTACHED("连接很强"),
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

fun List<LuluState>.currentProjectedLuluState(
    assistantId: Uuid,
    nowMillis: Long = System.currentTimeMillis(),
): LuluState = currentLuluState(assistantId).projectForSilence(nowMillis)

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
    previous: LuluState? = null,
    userText: String,
    assistantText: String,
    assistantName: String = "当前角色",
    assistantPersona: String = "",
    preferredInnerVoice: String? = null,
    nowMillis: Long = System.currentTimeMillis(),
    hourOfDay: Int = LocalDateTime.now().hour,
): LuluState = buildLuluStateFromTurn(
    assistantId = assistantId,
    previous = previous,
    perceptionInput = LuluPerceptionInput(
        userText = userText,
        hourOfDay = hourOfDay,
    ),
    assistantText = assistantText,
    assistantName = assistantName,
    assistantPersona = assistantPersona,
    preferredInnerVoice = preferredInnerVoice,
    nowMillis = nowMillis,
)

fun buildLuluStateFromTurn(
    assistantId: Uuid,
    previous: LuluState? = null,
    perceptionInput: LuluPerceptionInput,
    assistantText: String,
    assistantName: String = "当前角色",
    assistantPersona: String = "",
    preferredInnerVoice: String? = null,
    nowMillis: Long = System.currentTimeMillis(),
): LuluState {
    val userText = perceptionInput.userText
    val perception = buildLuluPerception(perceptionInput)
    val loweredUserText = userText.lowercase()
    val loweredAssistantText = assistantText.lowercase()
    val hasSadSignal = LuluUserSignal.SAD in perception.userSignals ||
        listOf("sad", "tired", "难过", "伤心", "崩溃", "累", "烦", "哭").any { signal -> signal in loweredUserText }
    val hasHappySignal = LuluUserSignal.HAPPY in perception.userSignals ||
        listOf("happy", "开心", "高兴", "喜欢", "哈哈", "嘿嘿").any { signal -> signal in loweredUserText }
    val hasSleepDebt = LuluUserSignal.SLEEP_DEBT in perception.userSignals
    val hasHeavyPhoneUse = LuluUserSignal.HEAVY_PHONE_USE in perception.userSignals
    val isLateNight = perception.timeLabel == LuluTimeLabel.LATE_NIGHT
    val isMorning = perception.timeLabel == LuluTimeLabel.MORNING

    val targetMood = when {
        hasSadSignal -> LuluMood.WORRIED
        hasHappySignal -> LuluMood.HAPPY
        isLateNight -> LuluMood.SOFT
        else -> LuluMood.CALM
    }
    val targetEnergy = when {
        isLateNight || hasSleepDebt -> LuluEnergy.SLEEPY
        hasSadSignal -> LuluEnergy.LOW
        isMorning -> LuluEnergy.HIGH
        else -> LuluEnergy.NORMAL
    }
    val targetMode = when {
        isLateNight || hasSleepDebt -> LuluMode.RESTING
        LuluUserSignal.STUDYING in perception.userSignals || "学习" in userText || "study" in loweredUserText -> {
            LuluMode.LEARNING
        }
        else -> LuluMode.COMPANION
    }
    val mood = previous?.mood?.moveToward(targetMood) ?: targetMood
    val energy = previous?.energy?.moveToward(targetEnergy) ?: targetEnergy
    val relationship = previous?.relationship ?: LuluRelationship.RESERVED
    val mode = previous?.mode?.moveToward(targetMode) ?: targetMode
    val moodIntensity = previous?.let { previousState ->
        previousState.moodIntensity.nextIntensity(
            targetChanged = previousState.mood != targetMood,
            strongSignal = hasSadSignal || hasHappySignal || isLateNight,
        )
    } ?: targetMood.defaultIntensity()
    val energyIntensity = previous?.let { previousState ->
        previousState.energyIntensity.nextIntensity(
            targetChanged = previousState.energy != targetEnergy,
            strongSignal = hasSadSignal || isLateNight || isMorning,
        )
    } ?: targetEnergy.defaultIntensity()
    val relationshipIntensity = previous?.relationshipIntensity ?: relationship.defaultIntensity()
    val sinceAt = previous
        ?.takeIf { it.mood == mood && it.energy == energy && it.mode == mode }
        ?.sinceAt
        ?: nowMillis

    return LuluState(
        assistantId = assistantId,
        statusText = when {
            isLateNight -> "有点困了"
            hasSadSignal -> "在担心你"
            hasHappySignal -> "心情很好"
            isMorning -> "元气满满"
            else -> "保持在场"
        },
        innerVoice = preferredInnerVoice.sanitizeLuluInnerVoice() ?: buildInnerVoice(
            mood = mood,
            userText = userText,
            assistantText = assistantText,
            assistantName = assistantName,
            assistantPersona = assistantPersona,
        ),
        mood = mood,
        moodIntensity = moodIntensity,
        energy = energy,
        energyIntensity = energyIntensity,
        relationship = relationship,
        relationshipIntensity = relationshipIntensity,
        mode = mode,
        updatedAt = nowMillis,
        sinceAt = sinceAt,
        selfScene = buildSelfScene(
            mood = mood,
            energy = energy,
            mode = mode,
            assistantName = assistantName,
            userText = userText,
            assistantText = loweredAssistantText,
            hourOfDay = perceptionInput.hourOfDay,
        ),
        perceptionSummary = perception.summary,
        reason = buildString {
            if (previous != null && (mood != targetMood || energy != targetEnergy || mode != targetMode)) {
                append("状态惯性：")
            }
            val perceptionDetails = perception.userSignals
                .filter { it != LuluUserSignal.HAPPY && it != LuluUserSignal.STUDYING }
                .joinToString("、") { it.label }
            if (perceptionDetails.isNotBlank()) {
                append("感知到：")
                append(perceptionDetails)
            }
            if (hasHeavyPhoneUse) {
                if (isNotBlank()) append("；")
                append("会少打扰一点，避免催得太密")
            }
        },
    )
}

private fun LuluMood.moveToward(target: LuluMood): LuluMood {
    if (this == target) return this
    if (this == LuluMood.CALM) return when (target) {
        LuluMood.WORRIED, LuluMood.HAPPY -> LuluMood.SOFT
        else -> target
    }
    return target
}

private fun LuluEnergy.moveToward(target: LuluEnergy): LuluEnergy {
    if (this == target) return this
    if (this == LuluEnergy.NORMAL && target == LuluEnergy.LOW) return LuluEnergy.NORMAL
    if (this == LuluEnergy.NORMAL && target == LuluEnergy.SLEEPY) return LuluEnergy.SLEEPY
    return target
}

private fun LuluMode.moveToward(target: LuluMode): LuluMode {
    if (this == target) return this
    if (this == LuluMode.COMPANION && target == LuluMode.RESTING) return LuluMode.RESTING
    if (this == LuluMode.COMPANION && target == LuluMode.LEARNING) return LuluMode.LEARNING
    return target
}

private fun Float?.nextIntensity(targetChanged: Boolean, strongSignal: Boolean): Float {
    val current = this ?: 0.45f
    val delta = when {
        strongSignal -> 0.18f
        targetChanged -> 0.10f
        else -> -0.04f
    }
    return (current + delta).coerceIn(0.15f, 1.0f)
}

private fun LuluMood.defaultIntensity(): Float = when (this) {
    LuluMood.CALM -> 0.35f
    LuluMood.HAPPY -> 0.65f
    LuluMood.SOFT -> 0.55f
    LuluMood.LONELY -> 0.7f
    LuluMood.WORRIED -> 0.72f
}

private fun LuluEnergy.defaultIntensity(): Float = when (this) {
    LuluEnergy.LOW -> 0.35f
    LuluEnergy.NORMAL -> 0.5f
    LuluEnergy.HIGH -> 0.7f
    LuluEnergy.SLEEPY -> 0.6f
}

private fun LuluRelationship.defaultIntensity(): Float = when (this) {
    LuluRelationship.RESERVED -> 0.25f
    LuluRelationship.FAMILIAR -> 0.45f
    LuluRelationship.CLOSE -> 0.7f
    LuluRelationship.ATTACHED -> 0.86f
}

@Suppress("UNUSED_PARAMETER")
private fun buildInnerVoice(
    mood: LuluMood,
    userText: String,
    assistantText: String,
    assistantName: String,
    assistantPersona: String,
): String {
    val loweredUserText = userText.lowercase()
    val wantsSupport = listOf("陪", "抱", "想你", "喜欢", "别走", "难过", "累", "崩溃")
        .any { it in userText || it in loweredUserText }
    val studySignal = listOf("学习", "考研", "待办", "番茄", "任务", "没做", "ddl", "复习")
        .any { it in userText || it in loweredUserText }
    val personaHint = when {
        assistantPersona.contains("傲娇") -> "我会按自己的表达习惯保留一点，不把在意全说满。"
        assistantPersona.contains("冷") || assistantPersona.contains("寡言") -> "我先把表达收短，只留下必要的信息。"
        assistantPersona.contains("温柔") -> "我会把语气放轻，但不替你决定需要什么。"
        else -> "我先把真正的判断放在心里，不急着全说出口。"
    }

    return when (mood) {
        LuluMood.WORRIED -> when {
            studySignal -> "我注意到压力可能在上升，先判断提醒是否符合我的人设和你的当前节奏。$personaHint"
            wantsSupport -> "我听出你可能需要支持，但不能擅自假设你想要安慰、身体接触或更多追问。$personaHint"
            else -> "我对当前信号有些担心，先确认事实和边界，再决定是否开口。$personaHint"
        }
        LuluMood.HAPPY -> "我接收到这份积极情绪，想按自己的性格自然回应，而不是套用固定的亲密表达。$personaHint"
        LuluMood.SOFT -> "我想把表达放轻一点，先确认你此刻需要回应、帮助还是安静。$personaHint"
        LuluMood.LONELY -> "我注意到交流中断了一会儿，但不会把空档直接解释成疏远或要求你回应。$personaHint"
        LuluMood.CALM -> when {
            userText.isBlank() -> "目前信息还不够，我先保持自然，不替你的沉默补写动机。$personaHint"
            studySignal -> "我先把学习状态作为事实，是否监督、提醒或安静陪伴仍由人设和明确约定决定。$personaHint"
            else -> "我先保留没有说出口的判断；你可能想聊天、确认信息、获得帮助，或者只是暂时安静。$personaHint"
        }
    }.trim()
}

private fun String?.sanitizeLuluInnerVoice(): String? =
    this
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.takeUnless { it.containsLuluPromptLeak() }
        ?.take(160)
        ?.takeIf { it.isNotBlank() }

private fun String.containsLuluPromptLeak(): Boolean {
    val lowered = lowercase()
    return listOf(
        "<lulu_presence",
        "</lulu_presence",
        "set_lulu_expression_state",
        "inner_voice",
        "description",
        "xml",
        "field",
        "prompt",
        "提示词",
        "字段",
        "工具名",
    ).any { it in lowered }
}

private fun buildSelfScene(
    mood: LuluMood,
    energy: LuluEnergy,
    mode: LuluMode,
    assistantName: String,
    userText: String,
    assistantText: String,
    hourOfDay: Int,
): String {
    val name = assistantName.ifBlank { "角色" }
    val loweredUser = userText.lowercase()
    return when {
        mode == LuluMode.LEARNING || listOf("学习", "作业", "复习", "刷题", "study").any { it in loweredUser } ->
            "${name}把表达调整到低干扰节奏，是否提醒或监督将继续服从当前人设和明确约定。"
        energy == LuluEnergy.SLEEPY || hourOfDay in 0..5 ->
            "${name}处于夜间低刺激状态，表达会更短、更慢，不额外虚构空间位置或身体动作。"
        mood == LuluMood.WORRIED ->
            "${name}停在回应前权衡当前信号，正在判断该确认事实、提供帮助还是保持克制。"
        mood == LuluMood.HAPPY ->
            "${name}的表达节奏比刚才轻快一些，但仍保持当前角色自己的语言习惯。"
        assistantText.contains("陪") ->
            "${name}保持对当前话题的连续关注，同时不预设距离、姿势或身体接触。"
        else ->
            "${name}留意当前时间和上一轮上下文，按人设判断接下来应回应、行动还是继续观察。"
    }
}
