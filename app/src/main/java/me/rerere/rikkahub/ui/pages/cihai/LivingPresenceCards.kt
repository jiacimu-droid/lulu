package me.rerere.rikkahub.ui.pages.cihai

import me.rerere.rikkahub.service.LivingIntent
import me.rerere.rikkahub.service.LivingIntentKind
import me.rerere.rikkahub.service.LivingIntentStatus
import java.util.concurrent.TimeUnit

data class LivingIntentCardModel(
    val id: String,
    val kind: LivingIntentKind,
    val title: String,
    val nextEvaluateText: String,
    val statusText: String,
    val bdiLine: String,
    val hypothesesLine: String,
    val cadenceLine: String,
    val countLine: String,
    val emotionLine: String,
)

fun buildLivingIntentCards(
    intents: List<LivingIntent>,
    selectedAssistantId: String,
    nowMillis: Long = System.currentTimeMillis(),
): List<LivingIntentCardModel> =
    intents
        .filter { intent ->
            intent.status != LivingIntentStatus.COMPLETED &&
                intent.status != LivingIntentStatus.CANCELLED &&
                (intent.assistantId.isBlank() || intent.assistantId == selectedAssistantId)
        }
        .sortedBy { it.nextEvaluateAt }
        .take(8)
        .map { intent -> intent.toCardModel(nowMillis) }

private fun LivingIntent.toCardModel(nowMillis: Long): LivingIntentCardModel {
    val minutesUntil = ((nextEvaluateAt - nowMillis) / TimeUnit.MINUTES.toMillis(1)).coerceAtLeast(0L)
    return LivingIntentCardModel(
        id = id,
        kind = kind,
        title = kind.title(),
        nextEvaluateText = if (nextEvaluateAt <= nowMillis) {
            "现在该重新判断"
        } else {
            "下次判断：${minutesUntil.coerceAtLeast(1)} 分钟后"
        },
        statusText = when (status) {
            LivingIntentStatus.ACTIVE -> "挂在心里"
            LivingIntentStatus.RESTRAINED -> "克制观察"
            LivingIntentStatus.COMPLETED -> "已完成"
            LivingIntentStatus.CANCELLED -> "已取消"
        },
        bdiLine = "信念：$belief\n欲望：$desire\n意图：$intention",
        hypothesesLine = "猜测：${hypotheses.joinToString(" / ")}",
        cadenceLine = "节奏：${evaluationCadence.delaysMinutes.joinToString("/")} 分钟 · ${evaluationCadence.reason}",
        countLine = "默默判断 $silentEvaluationCount 次 · 开口 $spokenCount 次 · 克制 $restraint/10",
        emotionLine = "情绪：${emotion.label} · 担心 ${emotion.concern}/10 · 依恋 ${emotion.attachment}/10",
    )
}

private fun LivingIntentKind.title(): String = when (this) {
    LivingIntentKind.HEALTH_SAFETY -> "身体安全挂心事"
    LivingIntentKind.ORDINARY_SILENCE -> "沉默回复预期"
    LivingIntentKind.STUDY_FOCUS -> "学习专注守护"
    LivingIntentKind.DEADLINE -> "DDL 进度照看"
    LivingIntentKind.WAKE_UP -> "起床唤醒计划"
}
