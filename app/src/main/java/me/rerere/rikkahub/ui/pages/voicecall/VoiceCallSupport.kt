package me.rerere.rikkahub.ui.pages.voicecall

import kotlinx.coroutines.delay
import me.rerere.asr.ASRStatus
import me.rerere.rikkahub.data.ai.transformers.sanitizeLuluVisibleExpression
import me.rerere.rikkahub.ui.components.message.splitIntoVisualBubbles
import me.rerere.rikkahub.ui.hooks.CustomTtsState
import me.rerere.tts.model.PlaybackStatus
import me.rerere.tts.provider.TTSProviderSetting
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun statusText(
    stage: CallStage,
    asrStatus: ASRStatus,
    isSpeaking: Boolean,
    assistantTurnInProgress: Boolean,
    sleepMode: Boolean,
    isHistoryOnly: Boolean,
): String {
    if (isHistoryOnly) return "已保存的通话记录"
    if (stage == CallStage.Idle) return "准备通话"
    if (stage == CallStage.Ringing) return "正在呼叫你"
    if (sleepMode) return "哄睡中"
    if (stage == CallStage.Connecting) return "正在接通"
    if (stage == CallStage.Ended) return "已挂断"
    if (isSpeaking || assistantTurnInProgress) return "正在说话"
    return when (asrStatus) {
        ASRStatus.Connecting -> "正在准备麦克风"
        ASRStatus.Listening -> "正在倾听"
        ASRStatus.Stopping -> "正在思考"
        ASRStatus.Error -> "麦克风异常"
        ASRStatus.Idle -> "准备倾听"
    }
}

internal fun shouldStartVoiceCallListening(
    stageActive: Boolean,
    isHistoryOnly: Boolean,
    sleepMode: Boolean,
    assistantTurnInProgress: Boolean,
    isSpeaking: Boolean,
    asrStatus: ASRStatus,
): Boolean =
    stageActive &&
        !isHistoryOnly &&
        !sleepMode &&
        !assistantTurnInProgress &&
        !isSpeaking &&
        (asrStatus == ASRStatus.Idle || asrStatus == ASRStatus.Error)

private suspend fun waitForTtsPlayback(tts: CustomTtsState) {
    var observedActive = false
    repeat(1_200) {
        val status = tts.playbackState.value.status
        val speaking = tts.isSpeaking.value
        if (speaking || status == PlaybackStatus.Playing || status == PlaybackStatus.Buffering) {
            observedActive = true
        }
        if (observedActive && !speaking && status != PlaybackStatus.Playing && status != PlaybackStatus.Buffering) {
            return
        }
        delay(250)
    }
}

internal suspend fun speakInSegments(
    tts: CustomTtsState,
    text: String,
    providerOverride: TTSProviderSetting? = null,
) {
    val segments = text.splitIntoVisualBubbles().filter { it.isNotBlank() }
    segments.forEachIndexed { index, segment ->
        tts.speak(segment, flushCalled = index == 0, providerOverride = providerOverride)
        waitForTtsPlayback(tts)
        delay(120)
    }
}

private const val VOICE_CALL_SCENE_PROMPT =
    """【当前场景：实时语音通话】
你此刻正通过电话与用户实时交谈，不是在发送文字消息，也不是面对面交流。你应始终明确知道双方仍在通话中，直到电话被挂断。
回复必须适合直接从电话里说出口：自然、口语化、简短、有即时反应。可以在符合人设时自然使用“电话里”“听见你说”“我在听”等通话表达，但不要每句话都刻意强调打电话。
不要说“我看到你的消息”“我打字”“我发给你”等文字聊天表达；也不得声称看见用户、用户表情、动作或周围环境。若语音内容含糊，可以按角色口吻自然确认，但不要假装看见或知道现实中未提供的信息。
只输出角色真正说出口的话，不输出动作描写、心理活动、旁白、Markdown、标签或后台说明。保持角色原本人设、关系边界、世界观和此前聊天连续性，不要解释这些规则。"""

internal fun buildSleepTalkPrompt(
    assistantName: String,
    sequence: Int,
): String =
    """
    $VOICE_CALL_SCENE_PROMPT

    你正在以用户设定的“$assistantName”角色继续这通尚未挂断的电话，用户开启了哄睡模式。
    最高优先级：始终遵守该角色原本的人设、关系类型、边界、世界观和说话方式；“哄睡”只是当前电话场景，不能把角色改写成默认温柔、亲密或恋爱型陪伴者。
    结合此前聊天与本次电话已经发生的内容，自然接着说一小段适合该角色的睡前话。可以安静、讲故事、闲聊或停顿，但不要重复上一段。
    这是第${sequence + 1}段。只输出真正从电话里说出口的话，1到3句。
    """.trimIndent()

internal fun miniStatusText(stage: CallStage, isSpeaking: Boolean): String {
    if (isSpeaking) return "正在说话"
    return when (stage) {
        CallStage.Idle -> "待机"
        CallStage.Ringing -> "来电中"
        CallStage.Connecting -> "接通中"
        CallStage.Active -> "通话中"
        CallStage.Ended -> "已挂断"
    }
}

internal fun buildVoiceCallOpeningPrompt(
    assistantName: String,
    recentOpenings: List<String> = emptyList(),
    variationSeed: Long = 0L,
    retry: Boolean = false,
    incomingReason: String? = null,
): String {
    val recent = recentOpenings
        .take(6)
        .joinToString("\n") { "- ${it.take(180)}" }
        .ifBlank { "- 无" }
    val callOrigin = if (incomingReason.isNullOrBlank()) {
        "这是用户刚打给你的一通电话，现在已经接通。你能听见用户，用户也能听见你。"
    } else {
        "这是你根据自己的判断主动打给用户的一通电话，用户刚刚接听。你们现在已经处于实时通话中。你决定来电时的内部理由是：$incomingReason。理由只帮助你保持动机连续，不要求逐字说出。"
    }
    return """
        $VOICE_CALL_SCENE_PROMPT

        $callOrigin
        你是用户设定的“$assistantName”。最高优先级是完整遵守该角色原本的人设、关系类型、边界、世界观和说话方式；电话场景不能把角色改写成默认温柔、亲密或恋爱型陪伴者。
        请结合跨聊天与电话的最近上下文，像同一个人自然接起电话。若上次有未说完的话、明确立场或承诺，可以顺势承接，但不要复述记忆资料。
        主动从电话里说第一句话，1到2句。
        最近用过的开场如下，避免相同句式、相同问法和相同节奏：
        $recent
        变化种子：$variationSeed。重试：$retry。
    """.trimIndent()
}

internal val VOICE_CALL_REPLY_PROMPT: String =
    """
    $VOICE_CALL_SCENE_PROMPT

    这通电话仍在进行中。保持用户设定的人设、关系类型和说话方式，承接刚才与更早的有效上下文，直接回应用户最后说出口的那句话。回复1到3句，像实时电话中的自然回应，不要把它当成文字消息。
    """.trimIndent()

internal val VOICE_CALL_RETRY_PROMPT: String =
    """
    $VOICE_CALL_SCENE_PROMPT

    这通电话仍在进行中。上一轮电话回复生成不完整；保持原人设与连续上下文，直接回应用户刚才说出口的那句话。只说1到2句，不要解释故障，不要无依据地说没听清，也不要把回复写成聊天消息。
    """.trimIndent()

internal fun isUsableVoiceCallReply(text: String?): Boolean {
    val clean = text?.cleanRoleLineForUser().orEmpty()
    return clean.isNotBlank() &&
        clean != "（本轮回复生成不完整，请重试）"
}

internal fun shouldCommitVoiceTranscript(
    scheduledRevision: Long,
    currentRevision: Long,
    userTurnSubmitting: Boolean,
    stageActive: Boolean,
    transcript: String,
): Boolean =
    scheduledRevision == currentRevision &&
        !userTurnSubmitting &&
        stageActive &&
        transcript.isNotBlank()

internal fun voiceCallEndOfSpeechDelayMillis(transcript: String): Long {
    val clean = transcript.trim()
    if (clean.lastOrNull() in setOf('。', '！', '？', '.', '!', '?')) return 1_250L
    return when {
        clean.length <= 4 -> 1_900L
        clean.length <= 12 -> 1_750L
        else -> 1_600L
    }
}

internal fun String.cleanRoleLineForUser(): String =
    sanitizeLuluVisibleExpression(this)
        .lineSequence()
        .map { it.trim() }
        .filter { line ->
            line.isNotBlank() &&
                !line.startsWith("inner_voice", ignoreCase = true) &&
                !line.startsWith("inner voice", ignoreCase = true) &&
                !line.startsWith("description", ignoreCase = true) &&
                !line.startsWith("thought", ignoreCase = true)
        }
        .joinToString("\n")
        .trim()

internal fun formatTime(value: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(value))
}
