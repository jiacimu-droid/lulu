package me.rerere.rikkahub.data.companion

internal fun CompanionCommitment.sanitizedHumanFacingText(): CompanionCommitment = copy(
    promise = promise.cleanCompanionHumanText("我会在合适的时候再确认这件事。"),
    actionPlan = actionPlan.copy(
        userFacingSummary = actionPlan.userFacingSummary.cleanCompanionHumanText(""),
        contextText = actionPlan.contextText.cleanCompanionHumanText(""),
    ),
    statusReason = statusReason?.cleanCompanionHumanText("")?.takeIf(String::isNotBlank),
    lastActionResult = lastActionResult?.copy(
        summary = lastActionResult.summary.cleanCompanionHumanText("这次没有顺利完成，我会重新留意。"),
    ),
)

internal fun String.cleanCompanionHumanText(fallback: String): String {
    val humanLines = lineSequence()
        .map { line -> line.trim().replace(Regex("\\s+"), " ") }
        .filter(String::isNotBlank)
        .takeWhile { line -> !line.containsTechnicalCompanionText() }
        .toList()
    val candidate = humanLines.joinToString("\n").trim().take(360)
    return candidate.ifBlank { fallback }
}

private fun String.containsTechnicalCompanionText(): Boolean {
    val lower = lowercase()
    return TECHNICAL_COMPANION_MARKERS.any { marker -> marker in lower } ||
        Regex("[a-z][a-z0-9_]{2,}\\s*[:=]\\s*(?:\\[|\\{)").containsMatchIn(lower)
}

private val TECHNICAL_COMPANION_MARKERS = listOf(
    "get_gadgetbridge_data",
    "get_app_usage",
    "get_battery_info",
    "set_alarm",
    "suggested args",
    "suggested_args",
    "tool call",
    "tool_name",
    "argumentsjson",
    "preferredtoolnames",
    "无需重复调用",
    "主动跟进这些动作",
    "后台判断",
    "后台判定",
    "planner",
    "fallback",
    "api request",
)
