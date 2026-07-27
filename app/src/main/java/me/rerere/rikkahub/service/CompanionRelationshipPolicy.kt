package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.companion.CompanionRelationshipState

/**
 * Relationship measurements may still protect sensitive tools, but they must not silently
 * override the user-editable interaction profile that defines initiative, follow-up and expression.
 */
internal fun CompanionIntentDecision.enforceRelationshipPolicy(
    relationship: CompanionRelationshipState,
): CompanionIntentDecision {
    val filteredTools = toolNames.filter { relationship.allowsUnpromptedTool(it) }
    val filteredAction = actionToolName?.takeIf { relationship.allowsAutonomousAction(it) }
    val validIntent = if (intent == CompanionIntent.SELF_ACTIVITY && filteredAction == null) {
        CompanionIntent.WAIT
    } else {
        intent
    }
    return copy(
        intent = validIntent,
        shouldMessageNow = shouldMessageNow && validIntent != CompanionIntent.WAIT,
        delayMinutes = delayMinutes.takeIf { validIntent != CompanionIntent.WAIT },
        toolNames = filteredTools,
        followUps = followUps,
        actionToolName = filteredAction,
        actionArgumentsJson = actionArgumentsJson.takeIf { filteredAction != null } ?: "{}",
    )
}

internal fun CompanionChatTurnPlan.enforceRelationshipPolicy(
    relationship: CompanionRelationshipState,
    latestUserText: String,
): CompanionChatTurnPlan {
    return copy(
        toolRequests = toolRequests.filter { request ->
            relationship.allowsUnpromptedTool(request.toolName) ||
                latestUserText.explicitlyRequestsTool(request.toolName)
        },
        // Follow-up and expression are governed by the role's editable interaction profile.
        // Hidden relationship scores no longer erase a role's configured tendency to pursue,
        // wait, speak briefly, use voice, or express care.
        followUpDelayMinutes = followUpDelayMinutes,
        followUpReason = followUpReason,
        followUps = followUps,
        expressionAffordances = expressionAffordances,
    )
}

private fun CompanionRelationshipState.allowsUnpromptedTool(toolName: String): Boolean {
    if (unresolvedTension >= HIGH_TENSION && toolName in USER_AFFECTING_TOOLS) return false
    if (trust < LOW_TRUST && toolName in USER_AFFECTING_TOOLS) return false
    if (boundaryConfidence < LOW_BOUNDARY_CONFIDENCE && toolName in INTRUSIVE_OBSERVATION_TOOLS) return false
    return true
}

private fun CompanionRelationshipState.allowsAutonomousAction(toolName: String): Boolean {
    if (toolName in SELF_CONTAINED_DIGITAL_LIFE_TOOLS) return true
    return allowsUnpromptedTool(toolName)
}

private fun String.explicitlyRequestsTool(toolName: String): Boolean = when (toolName) {
    "set_alarm" -> hasAny("闹钟", "提醒我", "叫我", "alarm", "remind")
    "calendar_tool" -> hasAny("日历", "日程", "行程", "calendar")
    "camera_capture" -> hasAny("摄像头", "相机", "拍照", "看看周围", "camera")
    "read_sms" -> hasAny("短信", "sms")
    "get_notifications" -> hasAny("通知", "notification")
    "clipboard_tool" -> hasAny("剪贴板", "clipboard")
    "get_location", "explore_nearby" -> hasAny("我在哪", "位置", "地址", "定位", "附近", "location")
    "get_app_usage" -> hasAny("应用使用", "屏幕使用", "使用时长", "screen time", "app usage")
    "get_gadgetbridge_data" -> hasAny("睡眠", "健康", "步数", "心率", "运动", "sleep", "health")
    "control_music" -> hasAny("音乐", "播放", "暂停", "下一首", "music")
    "write_lulu_journal" -> hasAny("日记", "辞海", "journal")
    "write_files" -> hasAny("写文件", "保存文件", "文件", "file")
    "eval_javascript" -> hasAny("javascript", "js", "执行代码")
    else -> false
}

private fun String.hasAny(vararg markers: String): Boolean {
    val normalized = lowercase()
    return markers.any { marker -> marker.lowercase() in normalized }
}

private val INTRUSIVE_OBSERVATION_TOOLS = setOf(
    "camera_capture",
    "read_sms",
    "get_notifications",
    "clipboard_tool",
    "get_location",
    "explore_nearby",
    "get_app_usage",
    "get_gadgetbridge_data",
)

private val USER_AFFECTING_TOOLS = INTRUSIVE_OBSERVATION_TOOLS + setOf(
    "set_alarm",
    "calendar_tool",
    "control_music",
    "write_files",
    "eval_javascript",
)

private val SELF_CONTAINED_DIGITAL_LIFE_TOOLS = setOf(
    "play_companion_game",
    "write_lulu_journal",
)

private const val HIGH_TENSION = 0.6f
private const val LOW_TRUST = 0.4f
private const val LOW_BOUNDARY_CONFIDENCE = 0.45f
