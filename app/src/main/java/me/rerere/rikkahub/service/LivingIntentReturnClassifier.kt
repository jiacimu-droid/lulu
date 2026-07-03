package me.rerere.rikkahub.service

object LivingIntentReturnClassifier {
    fun shouldCompleteOnUserReturn(
        intent: LivingIntent,
        userText: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val text = userText.lowercase()
        val hasBeenActivelyHeld = intent.spokenCount > 0 ||
            intent.silentEvaluationCount > 0 ||
            intent.lastEvaluatedAt != null
        return when (intent.kind) {
            LivingIntentKind.ORDINARY_SILENCE -> hasBeenActivelyHeld && text.isNotBlank()
            LivingIntentKind.HEALTH_SAFETY -> hasBeenActivelyHeld && text.containsAny(
                "没事",
                "好多",
                "不疼",
                "不痛",
                "不难受",
                "舒服",
                "缓过来",
                "吃药",
                "吃了药",
                "还好",
                "好了",
                "好点",
            )
            LivingIntentKind.STUDY_FOCUS -> hasBeenActivelyHeld && text.containsAny(
                "开始学",
                "开始背",
                "开始看课",
                "进入学习",
                "学完",
                "背完",
                "刷完",
                "做完",
                "完成",
                "番茄结束",
                "休息",
                "忙完",
                "结束",
            ) && !text.hasNegatedCompletion()
            LivingIntentKind.DEADLINE -> {
                val explicitDone = text.containsAny(
                    "完成",
                    "做完",
                    "交了",
                    "提交",
                    "搞定",
                    "弄完",
                    "交上去",
                    "发过去",
                    "忙完",
                ) && !text.hasNegatedCompletion()
                explicitDone || intent.deadlineAtMillis?.let { nowMillis >= it && hasBeenActivelyHeld } == true
            }
            LivingIntentKind.WAKE_UP -> text.containsAny(
                "醒了",
                "起了",
                "起来",
                "起床",
                "已经醒",
                "已经起",
                "我起来了",
                "我起床了",
            ) || intent.targetAtMillis?.let { nowMillis >= it + 25 * MINUTE_MILLIS && hasBeenActivelyHeld } == true
        }
    }

    fun completeReason(intent: LivingIntent, userText: String, nowMillis: Long = System.currentTimeMillis()): String =
        when (intent.kind) {
            LivingIntentKind.ORDINARY_SILENCE -> "用户重新回来发消息，沉默回复预期结束。"
            LivingIntentKind.HEALTH_SAFETY -> "用户反馈了身体状态：${userText.take(80)}"
            LivingIntentKind.STUDY_FOCUS -> "用户反馈了学习、休息或专注状态：${userText.take(80)}"
            LivingIntentKind.DEADLINE -> if (
                intent.deadlineAtMillis != null &&
                nowMillis >= intent.deadlineAtMillis &&
                !userText.lowercase().containsAny("完成", "做完", "交了", "提交", "搞定", "弄完", "忙完")
            ) {
                "截止时间已过且用户重新回来，归档这轮 DDL 挂心事：${userText.take(80)}"
            } else {
                "用户反馈任务完成或提交：${userText.take(80)}"
            }
            LivingIntentKind.WAKE_UP -> "用户反馈已经醒来、起床或在目标时间后回来：${userText.take(80)}"
        }

    private fun String.containsAny(vararg words: String): Boolean =
        words.any { contains(it) }

    private fun String.hasNegatedCompletion(): Boolean =
        containsAny(
            "没完成",
            "没有完成",
            "还没完成",
            "没做完",
            "没有做完",
            "还没做完",
            "没提交",
            "没有提交",
            "还没提交",
            "没交",
            "还没交",
            "没学完",
            "还没学完",
        )

    private const val MINUTE_MILLIS = 60_000L
}
