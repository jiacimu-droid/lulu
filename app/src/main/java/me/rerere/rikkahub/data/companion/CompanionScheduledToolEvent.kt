package me.rerere.rikkahub.data.companion

import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.utils.JsonInstant
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CompanionToolExecution(
    val toolCallId: String,
    val toolName: String,
    val inputJson: String,
    val outputText: String,
)

fun buildScheduledToolFollowUp(
    execution: CompanionToolExecution,
    assistantId: String,
    conversationId: String?,
    sourceMessageId: String?,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): CompanionFollowUpDraft? {
    if (assistantId.isBlank() || execution.toolCallId.isBlank()) return null
    val input = execution.inputJson.parseObjectOrNull() ?: return null
    val output = execution.outputText.parseObjectOrNull() ?: return null
    if (output["success"]?.jsonPrimitive?.booleanOrNull != true) return null

    return when (execution.toolName) {
        "set_alarm" -> {
            val hour = input["hour"]?.jsonPrimitive?.intOrNull ?: return null
            val minute = input["minute"]?.jsonPrimitive?.intOrNull ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
            var target = now.toLocalDate().atTime(hour, minute).atZone(zoneId)
            if (!target.toInstant().isAfter(Instant.ofEpochMilli(nowMillis + MINIMUM_LEAD_MILLIS))) {
                target = target.plusDays(1)
            }
            val targetMillis = target.toInstant().toEpochMilli()
            val label = input["label"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            val displayTime = target.format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm", Locale.CHINA))
            val event = label.ifBlank { "设备闹钟" }
            CompanionFollowUpDraft(
                assistantId = assistantId,
                category = "schedule",
                reason = "在 $displayTime 前后确认用户是否已处理“$event”",
                sourceText = "已成功设置 $displayTime 的闹钟：$event",
                dueAt = reminderAt(
                    nowMillis = nowMillis,
                    targetMillis = targetMillis,
                ),
                sourceConversationId = conversationId,
                sourceMessageId = sourceMessageId ?: execution.toolCallId,
                importance = 5,
                actionType = CompanionActionType.REMINDER,
            )
        }

        "calendar_tool" -> {
            if (input["action"]?.jsonPrimitive?.contentOrNull != "create") return null
            val startTimeMillis = input["start_time_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ?: return null
            if (startTimeMillis <= nowMillis) return null
            val title = input["title"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                .ifBlank { "日历事项" }
            val displayTime = Instant.ofEpochMilli(startTimeMillis)
                .atZone(zoneId)
                .format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm", Locale.CHINA))
            CompanionFollowUpDraft(
                assistantId = assistantId,
                category = "schedule",
                reason = "在 $displayTime 前后确认用户是否按计划处理“$title”",
                sourceText = "已成功创建日历事项：$title，开始时间 $displayTime",
                dueAt = reminderAt(
                    nowMillis = nowMillis,
                    targetMillis = startTimeMillis,
                ),
                sourceConversationId = conversationId,
                sourceMessageId = sourceMessageId ?: execution.toolCallId,
                importance = 4,
                actionType = CompanionActionType.REMINDER,
            )
        }

        else -> null
    }
}

private fun String.parseObjectOrNull() = runCatching {
    JsonInstant.parseToJsonElement(trim()).jsonObject
}.getOrNull()

private fun reminderAt(nowMillis: Long, targetMillis: Long): Long =
    maxOf(nowMillis + MINIMUM_LEAD_MILLIS, targetMillis)

private const val MINIMUM_LEAD_MILLIS = 10_000L
