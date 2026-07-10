package me.rerere.rikkahub.service

import java.util.concurrent.TimeUnit

data class LivingPresenceInput(
    val assistantName: String,
    val userText: String,
    val assistantText: String,
    val preferredToolNames: List<String> = emptyList(),
)

object LivingPresencePlanner {
    fun planRollingJudgments(
        input: LivingPresenceInput,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<ProactiveReminderPlan> {
        if (input.userText.isBlank() && input.assistantText.isBlank()) return emptyList()
        val intent = RollingJudgmentLoop.createIntent(
            assistantName = input.assistantName,
            userText = input.userText,
            assistantText = input.assistantText,
            nowMillis = nowMillis,
        )
        if (intent.kind == LivingIntentKind.ORDINARY_SILENCE) {
            return emptyList()
        }
        return intent.perceptionCadence.delaysMinutes.take(1).mapIndexed { index, delay ->
            ProactiveReminderPlan(
                triggerAtMillis = nowMillis + TimeUnit.MINUTES.toMillis(delay),
                kind = intent.kind.toReminderKind(),
                reason = buildReason(intent, index),
                userText = input.userText.take(160),
                preferredToolNames = preferredTools(intent.kind, input.preferredToolNames),
                actionHints = buildActionHints(intent.kind),
            )
        }.take(5)
    }

    private fun buildReason(intent: LivingIntent, index: Int): String = buildString {
        append("活人感动态感知#${index + 1}：感知世界包-意义评估-动态判断-行动实现-状态生成-辞海记忆。")
        append("事件=${intent.concernEvent}。目标=${intent.concernGoal}。")
        append("感知层必须先装入角色人设、上下文、未总结聊天、辞海、挂心任务、工具状态、工具结果、向量记忆和上一轮状态栏。")
        append("意义评估 Appraisal=${intent.appraisal.meaning}；风险=${intent.appraisal.risk}；价值=${intent.appraisal.value}；成本=${intent.appraisal.cost}；资源=${intent.appraisal.resources}.")
        append("判断层 intention=${intent.intention}；是否开口、是否查工具、是否等待、下一次什么时候感知，都必须根据本轮感知和人设重新决定。")
        append("行动池 includes MESSAGE, WAIT, TOOL_USE, SET_ALARM, SCHEDULE_NEXT_PERCEPTION, ASK_USER, PASS；正式日记只通过 write_lulu_journal 工具保存，静默本身不生成辞海记录。")
        append("状态栏只生成心情、身体、精神、亲密和第一人称没说出口；不要把 belief/motive/intention 当成状态栏展示。")
        append("Consolidation=${intent.consolidation.episodicTrace} / ${intent.consolidation.policyLearning}.")
        append("Hypotheses: ${intent.hypotheses.joinToString(" / ")}.")
        append("到点后必须重新从感知开始，不要把这段 reason 当成预写消息。")
    }

    private fun buildActionHints(kind: LivingIntentKind): List<ProactiveActionHint> = buildList {
        if (kind == LivingIntentKind.HEALTH_SAFETY) {
            add(
                ProactiveActionHint(
                    toolName = "TOOL_USE",
                    reason = "身体不适场景优先查看健康、电量、应用或位置线索，再决定是否发消息。",
                )
            )
        }
    }

    private fun LivingIntentKind.toReminderKind(): ProactiveReminderKind = when (this) {
        LivingIntentKind.STUDY_FOCUS -> ProactiveReminderKind.STUDY
        LivingIntentKind.DEADLINE, LivingIntentKind.WAKE_UP -> ProactiveReminderKind.SCHEDULE
        LivingIntentKind.HEALTH_SAFETY, LivingIntentKind.ORDINARY_SILENCE -> ProactiveReminderKind.GENERAL
    }

    private fun preferredTools(kind: LivingIntentKind, seed: List<String>): List<String> {
        val base = when (kind) {
            LivingIntentKind.HEALTH_SAFETY -> listOf("get_gadgetbridge_data", "get_battery_info", "get_app_usage", "get_location")
            LivingIntentKind.STUDY_FOCUS -> listOf("get_app_usage", "control_music", "get_battery_info")
            LivingIntentKind.DEADLINE -> listOf("get_time_info", "calendar_tool", "get_app_usage", "get_battery_info")
            LivingIntentKind.WAKE_UP -> listOf("get_time_info", "set_alarm", "get_gadgetbridge_data", "get_app_usage", "get_battery_info")
            LivingIntentKind.ORDINARY_SILENCE -> listOf("get_app_usage", "get_battery_info")
        }
        return (seed + base).distinct().take(5)
    }
}
