package me.rerere.rikkahub.ui.pages.game

internal const val PERFECT_MAN_WAITING_MARKER = "（选择角色并开始这一轮）"
internal const val PERFECT_MAN_GENERATING_MARKER = "（正在生成角色回应）"
internal const val PERFECT_MAN_REPLY_FAILURE_MARKER = "（本轮角色回复生成失败，请重试）"
internal const val PERFECT_MAN_REPLY_FORMAT_FAILURE_MARKER = "（本轮角色回复缺少有效分数，请重试）"

internal enum class PerfectManVoiceInputTarget {
    Flaw,
    Guess,
}

internal enum class PerfectManRoundPhase {
    UserGuesses,
    PartnerGuesses,
}

internal data class PerfectManRoundResult(
    val guess: Int,
    val score: Int,
    val success: Boolean,
    val diff: Int,
)

internal val PerfectManExampleFlaws = listOf(
    "10天不洗脚，也不洗澡。",
    "每次约会都要先讲半小时自己的梦。",
    "微信回复很快，但每句话都带工作总结格式。",
    "长得像建模脸，但是吃饭会把香菜当主菜。",
    "情绪稳定到吵架时会拿白板画流程图。",
    "很会做饭，但所有菜都坚持放薄荷。",
    "记得所有纪念日，但礼物永远买同款保温杯。",
    "声音特别好听，但睡前故事只讲刑法案例。",
)

internal fun perfectManActionTitle(
    phase: PerfectManRoundPhase,
    promptReady: Boolean,
): String = when (phase) {
    PerfectManRoundPhase.UserGuesses -> if (promptReady) "我猜分" else "开始这一轮"
    PerfectManRoundPhase.PartnerGuesses -> "我来描述"
}
