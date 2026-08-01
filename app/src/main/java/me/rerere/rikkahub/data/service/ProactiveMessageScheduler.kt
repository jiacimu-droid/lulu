package me.rerere.rikkahub.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.companion.CompanionCommitment
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.datastore.ProactiveMessageSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getProactiveMessageSetting
import org.koin.core.context.GlobalContext

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

    private fun alarmIntent(
        context: Context,
        data: Uri,
        assistantId: String,
        reason: String? = null,
        userText: String? = null,
        kind: String? = null,
        commitmentId: String? = null,
    ): Intent = Intent(context, UnifiedProactiveAlarmReceiver::class.java).apply {
        action = ACTION_PROACTIVE_MESSAGE
        this.data = data
        putExtra(EXTRA_ASSISTANT_ID, assistantId)
        reason?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_TARGETED_REASON, it) }
        userText?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_TARGETED_USER_TEXT, it) }
        kind?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_TARGETED_KIND, it) }
        commitmentId?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_COMMITMENT_ID, it) }
    }

    private fun scheduleAlarm(
        context: Context,
        triggerAtMillis: Long,
        requestCode: Int,
        intent: Intent,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                Log.w(TAG, "Exact alarm permission not granted, using inexact alarm")
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun scheduleNext(context: Context, setting: ProactiveMessageSetting) {
        if (!setting.enabled) {
            cancel(context, setting.assistantId)
            return
        }
        val minMinutes = if (setting.naturalScheduling) 45 else setting.minIntervalMinutes.coerceAtLeast(1)
        val maxMinutes = if (setting.naturalScheduling) 90 else setting.maxIntervalMinutes.coerceAtLeast(minMinutes)
        val delayMinutes = Random.nextInt(minMinutes, maxMinutes + 1)
        val triggerTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes.toLong())

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_NEXT_TRIGGER_TIME, triggerTime)
            .putString(EXTRA_ASSISTANT_ID, setting.assistantId)
            .apply()

        scheduleAlarm(
            context = context,
            triggerAtMillis = triggerTime,
            requestCode = requestCode(REQUEST_CODE, setting.assistantId),
            intent = alarmIntent(
                context = context,
                data = Uri.parse("rikka://proactive/autonomous/${setting.assistantId}"),
                assistantId = setting.assistantId,
            ),
        )
        Log.d(TAG, "Scheduled proactive message in $delayMinutes minutes")
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
        val nowMillis = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeTargetedTrigger = prefs.getLong(KEY_TARGETED_TRIGGER_TIME, 0L)
        val companionRuntime = GlobalContext.get().get<CompanionRuntime>()
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
        if (!setting.enabled || triggerAtMillis <= System.currentTimeMillis()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_NEXT_TRIGGER_TIME, triggerAtMillis)
            .putString("next_trigger_reason", logReason)
            .putString(EXTRA_ASSISTANT_ID, setting.assistantId)
            .apply()
        scheduleAlarm(
            context = context,
            triggerAtMillis = triggerAtMillis,
            requestCode = requestCode(REQUEST_CODE, setting.assistantId),
            intent = alarmIntent(
                context = context,
                data = Uri.parse("rikka://proactive/autonomous/${setting.assistantId}"),
                assistantId = setting.assistantId,
            ),
        )
        Log.d(TAG, "Scheduled autonomous proactive pulse reason=$logReason at $triggerAtMillis")
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
        if (!setting.enabled || triggerAtMillis <= System.currentTimeMillis()) return
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

        scheduleAlarm(
            context = context,
            triggerAtMillis = triggerAtMillis,
            requestCode = requestCode(TARGETED_REQUEST_CODE, "$assistantId:${commitmentId.orEmpty()}"),
            intent = alarmIntent(
                context = context,
                data = Uri.parse("rikka://proactive/targeted/$assistantId/${commitmentId.orEmpty()}"),
                assistantId = assistantId,
                reason = reason,
                userText = userText,
                kind = kind,
                commitmentId = commitmentId,
            ),
        )
        Log.d(TAG, "Scheduled targeted proactive message kind=$kind at $triggerAtMillis")
        commitmentId?.takeIf { it.isNotBlank() }?.let { id ->
            ProactiveMessageWorker.scheduleTargeted(
                context = context,
                triggerAtMillis = triggerAtMillis,
                assistantId = assistantId,
                commitmentId = id,
            )
        }
    }

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
        scheduleAlarm(
            context = context,
            triggerAtMillis = triggerAtMillis,
            requestCode = requestCode(RESPONSIBILITY_REVIEW_REQUEST_CODE, assistantId.toString()),
            intent = alarmIntent(
                context = context,
                data = Uri.parse("rikka://proactive/responsibility/$assistantId"),
                assistantId = assistantId.toString(),
                reason = reason,
                userText = userText,
                kind = "always_on_anchor_review",
            ),
        )
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
        val runtime = GlobalContext.get().get<CompanionRuntime>()
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
        scheduleNext(context = context, settings = settings, assistantId = assistantId)
    }

    internal fun popCurrentTargetedAndScheduleNext(
        context: Context,
        setting: ProactiveMessageSetting,
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val queue = runCatching {
            (Json.parseToJsonElement(prefs.getString(KEY_TARGETED_QUEUE, "[]").orEmpty()) as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
        }.getOrNull().orEmpty()
        val remaining = queue.drop(1).filter { item ->
            (item["triggerAtMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L) > now
        }
        val next = remaining.minByOrNull {
            it["triggerAtMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: Long.MAX_VALUE
        }
        if (next == null) {
            clearTargetedQueue(context)
            return false
        }
        prefs.edit().putString(KEY_TARGETED_QUEUE, JsonArray(remaining).toString()).apply()
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
        val triggerTime = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_NEXT_TRIGGER_TIME, 0L)
        return triggerTime.takeIf { it > 0L }
    }

    fun cancel(context: Context, assistantId: String? = null) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_NEXT_TRIGGER_TIME)
            .apply()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val identities = buildList {
            assistantId?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        identities.forEach { id ->
            listOf(
                Triple(REQUEST_CODE, "rikka://proactive/autonomous/$id", requestCode(REQUEST_CODE, id)),
                Triple(RESPONSIBILITY_REVIEW_REQUEST_CODE, "rikka://proactive/responsibility/$id", requestCode(RESPONSIBILITY_REVIEW_REQUEST_CODE, id)),
            ).forEach { (_, uri, code) ->
                val intent = Intent(context, UnifiedProactiveAlarmReceiver::class.java).apply {
                    action = ACTION_PROACTIVE_MESSAGE
                    data = Uri.parse(uri)
                }
                PendingIntent.getBroadcast(
                    context,
                    code,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )?.let { alarmManager.cancel(it) }
            }
        }
        ProactiveMessageWorker.cancel(context, assistantId)
        ProactiveMessageWorker.cancelTargeted(context, assistantId = assistantId)
    }

    fun resetTimer(context: Context, setting: ProactiveMessageSetting) {
        scheduleNext(context, setting)
    }

    fun triggerNow(context: Context, setting: ProactiveMessageSetting) {
        scheduleNext(context, setting)
        val applicationContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                GlobalContext.get().get<ProactiveTurnDispatcher>().dispatch(
                    context = applicationContext,
                    assistantId = setting.assistantId,
                    commitmentId = null,
                    triggerId = "manual:${setting.assistantId}:${System.currentTimeMillis()}",
                )
            }.onFailure { error ->
                Log.e(TAG, "Failed to dispatch manual proactive trigger", error)
            }
        }
    }
}
