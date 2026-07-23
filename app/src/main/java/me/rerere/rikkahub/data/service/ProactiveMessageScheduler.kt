package me.rerere.rikkahub.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import androidx.core.app.NotificationCompat
import android.os.Build
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.ApiUsageSource
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.buildPromptInjectionPlannerContext
import me.rerere.rikkahub.data.ai.transformers.companionInputTransformers
import me.rerere.rikkahub.data.ai.transformers.companionModelPresence
import me.rerere.rikkahub.data.ai.transformers.companionOutputTransformers
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.ai.tools.SystemTools
import me.rerere.rikkahub.data.ai.tools.createAlarmTool
import me.rerere.rikkahub.data.ai.tools.createSearchTools
import me.rerere.rikkahub.data.ai.tools.createSkillTools
import me.rerere.rikkahub.data.ai.tools.createTodayStudyPlanTool
import me.rerere.rikkahub.data.ai.tools.createCompanionGameTool
import me.rerere.rikkahub.data.ai.tools.deduplicateByToolName
import me.rerere.rikkahub.data.ai.tools.activeModelTools
import me.rerere.rikkahub.data.ai.tools.selectRelevantToolsForPrompt
import me.rerere.rikkahub.data.ai.tools.selectCompanionToolsForGeneration
import me.rerere.rikkahub.data.ai.tools.withConciseToolDescriptions
import me.rerere.rikkahub.data.ai.tools.withHumanLikeToolPrompts
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.plugin.provider.PluginToolProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.data.datastore.ProactiveMessageSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.companion.CompanionActionResult
import me.rerere.rikkahub.data.companion.CompanionActionType
import me.rerere.rikkahub.data.companion.CompanionCommitment
import me.rerere.rikkahub.data.companion.CompanionCommitmentStatus
import me.rerere.rikkahub.data.companion.CompanionAlwaysOnAnchorStatus
import me.rerere.rikkahub.data.companion.CompanionContextFact
import me.rerere.rikkahub.data.companion.CompanionConversationTurn
import me.rerere.rikkahub.data.companion.CompanionInteractionEvent
import me.rerere.rikkahub.data.companion.CompanionInteractionEventKind
import me.rerere.rikkahub.data.companion.CompanionOutboundStatus
import me.rerere.rikkahub.data.companion.CompanionModelPresence
import me.rerere.rikkahub.data.companion.CompanionPerceptionInput
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.companion.CompanionState
import me.rerere.rikkahub.data.companion.CompanionTurnMutation
import me.rerere.rikkahub.data.companion.CompanionTurnRole
import me.rerere.rikkahub.data.companion.CompanionLifeEvent
import me.rerere.rikkahub.data.companion.CompanionLifeEventSource
import me.rerere.rikkahub.data.companion.CompanionToolExecution
import me.rerere.rikkahub.data.companion.buildToolLifeEvent
import me.rerere.rikkahub.data.companion.isSuccessfulToolExecution
import me.rerere.rikkahub.data.companion.buildCompanionStateFromTurn
import me.rerere.rikkahub.data.companion.commitmentStatusesBySourceMessageId
import me.rerere.rikkahub.data.companion.isSleepSupervisionGoal
import me.rerere.rikkahub.data.companion.isWakeGoal
import me.rerere.rikkahub.data.companion.retryMinutesOrDefault
import me.rerere.rikkahub.data.companion.toPromptContext
import me.rerere.rikkahub.data.companion.wakeTargetAtOrNull
import me.rerere.rikkahub.data.cihai.CihaiStore
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getProactiveMessageSetting
import me.rerere.rikkahub.data.cihai.CihaiEntry
import me.rerere.rikkahub.data.cihai.CihaiEntryKind
import me.rerere.rikkahub.data.gadgetbridge.GadgetbridgeReader
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.voicecall.ProactiveCallManager
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.study.StudyStore
import me.rerere.rikkahub.data.study.StudyTaskSource
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.CompanionDecisionMode
import me.rerere.rikkahub.service.CompanionIntent
import me.rerere.rikkahub.service.CompanionIntentDecision
import me.rerere.rikkahub.service.CompanionIntentFallbackPlanner
import me.rerere.rikkahub.service.CompanionIntentInput
import me.rerere.rikkahub.service.CompanionIntentModelPlanner
import me.rerere.rikkahub.service.collectCompanionPassivePerceptionFacts
import me.rerere.rikkahub.service.ProactiveReminderPlan
import me.rerere.rikkahub.service.toProactiveReminderPlan
import me.rerere.rikkahub.utils.sendNotification
import java.time.Instant
import java.time.ZoneId
import kotlin.uuid.Uuid
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Android scheduling, queue recovery and trigger dispatch for proactive turns. */
internal object ProactiveMessageScheduler {
    private const val TAG = "ProactiveMessageScheduler"
    const val ACTION_PROACTIVE_MESSAGE = "me.rerere.rikkahub.PROACTIVE_MESSAGE"
    private const val REQUEST_CODE = 10001
    private const val TARGETED_REQUEST_CODE = 10002
    private const val RESPONSIBILITY_REVIEW_REQUEST_CODE = 10003

    private const val PREFS_NAME = "proactive_message_prefs"
    private const val KEY_NEXT_TRIGGER_TIME = "next_trigger_time"
    private const val KEY_TARGETED_TRIGGER_TIME = "targeted_trigger_time"
    private const val KEY_TARGETED_REASON = "targeted_reason"
    private const val KEY_TARGETED_USER_TEXT = "targeted_user_text"
    private const val KEY_TARGETED_KIND = "targeted_kind"
    private const val KEY_TARGETED_COMMITMENT_ID = "targeted_commitment_id"
    private const val KEY_TARGETED_QUEUE = "targeted_queue"
    private const val EXTRA_TARGETED_REASON = "targeted_reason"
    private const val EXTRA_TARGETED_USER_TEXT = "targeted_user_text"
    private const val EXTRA_TARGETED_KIND = "targeted_kind"
    private const val EXTRA_COMMITMENT_ID = "commitment_id"
    private const val EXTRA_ASSISTANT_ID = "assistant_id"

    private fun requestCode(base: Int, identity: String): Int = base xor identity.hashCode()

    fun scheduleNext(context: Context, setting: ProactiveMessageSetting) {
        if (!setting.enabled) {
            cancel(context, setting.assistantId)
            return
        }

        val minMinutes = if (setting.naturalScheduling) 45 else setting.minIntervalMinutes.coerceAtLeast(1)
        val maxMinutes = if (setting.naturalScheduling) 90 else setting.maxIntervalMinutes.coerceAtLeast(minMinutes)
        val delayMinutes = Random.nextInt(minMinutes, maxMinutes + 1)
        val triggerTime = java.lang.System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes.toLong())

        // 保存下次触发时间到SharedPreferences
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_NEXT_TRIGGER_TIME, triggerTime)
            .putString(EXTRA_ASSISTANT_ID, setting.assistantId)
            .apply()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ProactiveMessageReceiver::class.java).apply {
            action = ACTION_PROACTIVE_MESSAGE
            data = Uri.parse("rikka://proactive/autonomous/${setting.assistantId}")
            putExtra(EXTRA_ASSISTANT_ID, setting.assistantId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(REQUEST_CODE, setting.assistantId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            // Android 12+ needs canScheduleExactAlarms() check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    // Fallback: use inexact alarm if exact alarm permission not granted
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.w(TAG, "Exact alarm permission not granted, using inexact alarm")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }

        Log.d(TAG, "Scheduled proactive message in $delayMinutes minutes, trigger at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(triggerTime))}")

        // Also schedule via WorkManager as a more reliable fallback
        ProactiveMessageWorker.scheduleNext(context, setting)
    }

    fun scheduleNext(
        context: Context,
        settings: Settings,
        minutesSinceLastChat: Long? = null,
        assistantId: Uuid? = null,
    ) {
        val setting = settings.getProactiveMessageSetting(assistantId)
        if (!setting.enabled) {
            cancel(context, setting.assistantId)
            return
        }
        val assistant = settings.assistants.find { it.id.toString() == setting.assistantId }
            ?: settings.getCurrentAssistant()
        val nowMillis = java.lang.System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeTargetedTrigger = prefs.getLong(KEY_TARGETED_TRIGGER_TIME, 0L)
        val companionRuntime = org.koin.core.context.GlobalContext.get().get<CompanionRuntime>()
        val snapshot = companionRuntime.snapshot(assistant.id.toString())
        val pulseInput = CompanionAutonomousPulseInput(
            setting = setting,
            snapshot = snapshot,
            minutesSinceLastChat = minutesSinceLastChat
                ?: snapshot.interactionTimeline.lastUserActivityAt
                    ?.let { ((nowMillis - it) / 60_000L).coerceAtLeast(0L) }
                ?: Long.MAX_VALUE,
            activeTargetedTriggerMillis = activeTargetedTrigger,
            nowMillis = nowMillis,
        )
        val pulsePlan = CompanionAutonomousPulsePlanner.planNext(pulseInput)
        val triggerTime = CompanionAutonomousPulsePlanner.triggerTimeMillis(pulseInput, pulsePlan)
        scheduleAt(context, setting, triggerTime, pulsePlan.reason)
        ProactiveMessageWorker.scheduleNext(context, setting, pulsePlan.delayMinutes)
    }

    private fun scheduleAt(
        context: Context,
        setting: ProactiveMessageSetting,
        triggerAtMillis: Long,
        logReason: String,
    ) {
        if (!setting.enabled || triggerAtMillis <= java.lang.System.currentTimeMillis()) return

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_NEXT_TRIGGER_TIME, triggerAtMillis)
            .putString("next_trigger_reason", logReason)
            .putString(EXTRA_ASSISTANT_ID, setting.assistantId)
            .apply()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ProactiveMessageReceiver::class.java).apply {
            action = ACTION_PROACTIVE_MESSAGE
            data = Uri.parse("rikka://proactive/autonomous/${setting.assistantId}")
            putExtra(EXTRA_ASSISTANT_ID, setting.assistantId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(REQUEST_CODE, setting.assistantId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }

        Log.d(TAG, "Scheduled autonomous proactive pulse reason=$logReason at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(triggerAtMillis))}")
    }

    fun scheduleTargeted(
        context: Context,
        setting: ProactiveMessageSetting,
        triggerAtMillis: Long,
        reason: String,
        userText: String,
        kind: String,
        assistantId: String = setting.assistantId,
        commitmentId: String? = null,
    ) {
        if (!setting.enabled || triggerAtMillis <= java.lang.System.currentTimeMillis()) return

        val preferencesEditor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_NEXT_TRIGGER_TIME, triggerAtMillis)
            .putLong(KEY_TARGETED_TRIGGER_TIME, triggerAtMillis)
            .putString(KEY_TARGETED_REASON, reason)
            .putString(KEY_TARGETED_USER_TEXT, userText)
            .putString(KEY_TARGETED_KIND, kind)
            .putString(EXTRA_ASSISTANT_ID, assistantId)
        if (commitmentId.isNullOrBlank()) {
            preferencesEditor.remove(KEY_TARGETED_COMMITMENT_ID)
        } else {
            preferencesEditor.putString(KEY_TARGETED_COMMITMENT_ID, commitmentId)
        }
        preferencesEditor.apply()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ProactiveMessageReceiver::class.java).apply {
            action = ACTION_PROACTIVE_MESSAGE
            data = Uri.parse("rikka://proactive/targeted/$assistantId/${commitmentId.orEmpty()}")
            putExtra(EXTRA_ASSISTANT_ID, assistantId)
            putExtra(EXTRA_TARGETED_REASON, reason)
            putExtra(EXTRA_TARGETED_USER_TEXT, userText)
            putExtra(EXTRA_TARGETED_KIND, kind)
            commitmentId?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_COMMITMENT_ID, it) }
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(TARGETED_REQUEST_CODE, "$assistantId:${commitmentId.orEmpty()}") ,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }

        Log.d(TAG, "Scheduled targeted proactive message kind=$kind at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(triggerAtMillis))}")
        commitmentId?.takeIf { it.isNotBlank() }?.let { id ->
            ProactiveMessageWorker.scheduleTargeted(
                context = context,
                triggerAtMillis = triggerAtMillis,
                assistantId = assistantId,
                commitmentId = id,
            )
        }
    }

    /** Schedule the nightly review that turns always-on responsibilities into real actions. */
    fun scheduleAlwaysOnAnchorReview(
        context: Context,
        settings: Settings,
        assistantId: Uuid,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val setting = settings.getProactiveMessageSetting(assistantId)
        if (!setting.enabled) return
        val triggerAtMillis = nextAlwaysOnAnchorReviewAt(nowMillis)
        val reason = "检查角色的常驻责任锚点，并在有真实证据时执行今晚需要完成的事情。"
        val userText = "夜间责任检查：读取常驻锚点、睡眠、应用使用和健康数据，完成必要的次日作息动作。"
        val intent = Intent(context, ProactiveMessageReceiver::class.java).apply {
            action = ACTION_PROACTIVE_MESSAGE
            data = Uri.parse("rikka://proactive/responsibility/${assistantId}")
            putExtra(EXTRA_ASSISTANT_ID, assistantId.toString())
            putExtra(EXTRA_TARGETED_REASON, reason)
            putExtra(EXTRA_TARGETED_USER_TEXT, userText)
            putExtra(EXTRA_TARGETED_KIND, "always_on_anchor_review")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(RESPONSIBILITY_REVIEW_REQUEST_CODE, assistantId.toString()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
        Log.d(TAG, "Scheduled responsibility review at $triggerAtMillis")
    }

    fun scheduleCommitment(
        context: Context,
        setting: ProactiveMessageSetting,
        commitment: CompanionCommitment,
    ) {
        scheduleTargeted(
            context = context,
            setting = setting,
            triggerAtMillis = recoveredCommitmentTriggerAt(
                dueAt = commitment.dueAt,
                nowMillis = System.currentTimeMillis(),
            ),
            reason = commitment.promise,
            userText = commitment.actionPlan.contextText,
            kind = commitment.actionPlan.category.ifBlank { "commitment" },
            assistantId = commitment.assistantId,
            commitmentId = commitment.id,
        )
    }

    suspend fun reconcileDurableCommitments(
        context: Context,
        settings: Settings,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        clearTargetedQueue(context)
        val runtime = org.koin.core.context.GlobalContext.get().get<CompanionRuntime>()
        repeat(MAX_RECONCILE_COMMITMENTS) {
            val commitment = runtime.nextCommitment(nowMillis) ?: return false
            if (nowMillis - commitment.dueAt > STALE_UNDELIVERED_COMMITMENT_MILLIS) {
                runtime.cancelCommitment(
                    assistantId = commitment.assistantId,
                    commitmentId = commitment.id,
                    reason = "提醒已经过期且未能送达",
                    nowMillis = nowMillis,
                )
                return@repeat
            }
            val assistantId = runCatching { Uuid.parse(commitment.assistantId) }.getOrNull()
            if (assistantId == null) {
                runtime.cancelCommitment(
                    assistantId = commitment.assistantId,
                    commitmentId = commitment.id,
                    reason = "提醒对应的角色已经不存在",
                    nowMillis = nowMillis,
                )
                return@repeat
            }
            val setting = settings.getProactiveMessageSetting(assistantId)
            if (!setting.enabled) {
                runtime.cancelCommitment(
                    assistantId = commitment.assistantId,
                    commitmentId = commitment.id,
                    reason = "这个角色没有开启主动消息，提醒已停止",
                    nowMillis = nowMillis,
                )
                return@repeat
            }
            scheduleCommitment(context, setting, commitment)
            return true
        }
        return false
    }

    fun clearTargetedQueue(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TARGETED_TRIGGER_TIME)
            .remove(KEY_TARGETED_REASON)
            .remove(KEY_TARGETED_USER_TEXT)
            .remove(KEY_TARGETED_KIND)
            .remove(KEY_TARGETED_COMMITMENT_ID)
            .remove(KEY_TARGETED_QUEUE)
            .apply()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ProactiveMessageReceiver::class.java).apply {
            action = ACTION_PROACTIVE_MESSAGE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            TARGETED_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
        ProactiveMessageWorker.cancelTargeted(context)
    }

    private const val MAX_RECONCILE_COMMITMENTS = 50
    private const val STALE_UNDELIVERED_COMMITMENT_MILLIS = 12L * 60L * 60L * 1_000L

    fun resetAssistantProjection(
        context: Context,
        settings: Settings,
        assistantId: Uuid,
    ) {
        val id = assistantId.toString()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!shouldResetProactiveProjection(prefs.getString(EXTRA_ASSISTANT_ID, null), id)) return

        clearTargetedQueue(context)
        cancel(context)
        prefs.edit()
            .remove(EXTRA_ASSISTANT_ID)
            .remove("last_triggered_time")
            .remove("next_trigger_reason")
            .apply()
        scheduleNext(
            context = context,
            settings = settings,
            assistantId = assistantId,
        )
    }

    internal fun popCurrentTargetedAndScheduleNext(
        context: Context,
        setting: ProactiveMessageSetting,
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = java.lang.System.currentTimeMillis()
        val queue = runCatching {
            (Json.parseToJsonElement(prefs.getString(KEY_TARGETED_QUEUE, "[]").orEmpty()) as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
        }.getOrNull().orEmpty()
        val remaining = queue
            .drop(1)
            .filter { item ->
                (item["triggerAtMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L) > now
            }
        val next = remaining.minByOrNull {
            it["triggerAtMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: Long.MAX_VALUE
        }
        if (next == null) {
            clearTargetedQueue(context)
            return false
        }

        prefs.edit()
            .putString(KEY_TARGETED_QUEUE, JsonArray(remaining).toString())
            .apply()
        scheduleTargeted(
            context = context,
            setting = setting,
            triggerAtMillis = next["triggerAtMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return false,
            reason = next["reason"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            userText = next["userText"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            kind = next["kind"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
        return true
    }

    fun getNextTriggerTime(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val triggerTime = prefs.getLong(KEY_NEXT_TRIGGER_TIME, 0L)
        return if (triggerTime > 0) triggerTime else null
    }

    fun cancel(context: Context, assistantId: String? = null) {
        // 清除保存的触发时间
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_NEXT_TRIGGER_TIME)
            .apply()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ProactiveMessageReceiver::class.java).apply {
            action = ACTION_PROACTIVE_MESSAGE
            assistantId?.takeIf(String::isNotBlank)?.let { id ->
                data = Uri.parse("rikka://proactive/autonomous/$id")
            }
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            assistantId?.takeIf(String::isNotBlank)?.let { requestCode(REQUEST_CODE, it) } ?: REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            Log.d(TAG, "Cancelled proactive message alarm")
        }
        PendingIntent.getBroadcast(
            context,
            assistantId?.takeIf(String::isNotBlank)?.let { requestCode(RESPONSIBILITY_REVIEW_REQUEST_CODE, it) }
                ?: RESPONSIBILITY_REVIEW_REQUEST_CODE,
            intent.apply {
                assistantId?.takeIf(String::isNotBlank)?.let { id ->
                    data = Uri.parse("rikka://proactive/responsibility/$id")
                }
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { reviewIntent ->
            alarmManager.cancel(reviewIntent)
            Log.d(TAG, "Cancelled responsibility review alarm")
        }

        // Also cancel WorkManager fallback
        ProactiveMessageWorker.cancel(context, assistantId)
        ProactiveMessageWorker.cancelTargeted(context, assistantId = assistantId)
    }

    fun resetTimer(context: Context, setting: ProactiveMessageSetting) {
        scheduleNext(context, setting)
    }

    fun triggerNow(context: Context, setting: ProactiveMessageSetting) {
        // 先安排下一次（写入SP让UI立即显示），再立即触发
        scheduleNext(context, setting)
        // 立即触发：直接启动TriggerService
        val serviceIntent = Intent(context, ProactiveMessageTriggerService::class.java).apply {
            putExtra(EXTRA_ASSISTANT_ID, setting.assistantId)
        }
        context.startForegroundService(serviceIntent)
    }
}
