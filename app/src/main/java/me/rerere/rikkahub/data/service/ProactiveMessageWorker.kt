package me.rerere.rikkahub.data.service

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getProactiveMessageSetting
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.uuid.Uuid

internal data class TargetedProactiveWorkSpec(
    val uniqueWorkName: String,
    val delayMillis: Long,
    val assistantId: String,
    val commitmentId: String,
) {
    val isTargeted: Boolean
        get() = assistantId.isNotBlank() && commitmentId.isNotBlank()
}

private const val FALLBACK_GRACE_MILLIS = 2L * 60L * 1000L
private const val INPUT_PROJECTED_TRIGGER_AT = "projected_trigger_at"
private const val KEY_NEXT_TRIGGER_TIME = "next_trigger_time"
private const val KEY_LAST_TRIGGERED_TIME = "last_triggered_time"

internal fun buildTargetedProactiveWorkSpec(
    triggerAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    assistantId: String,
    commitmentId: String,
): TargetedProactiveWorkSpec = TargetedProactiveWorkSpec(
    uniqueWorkName = "targeted_proactive_message_work:${assistantId.trim()}:${commitmentId.trim()}",
    delayMillis = (triggerAtMillis - nowMillis).coerceAtLeast(0L) + FALLBACK_GRACE_MILLIS,
    assistantId = assistantId.trim(),
    commitmentId = commitmentId.trim(),
)

/**
 * WorkManager is a recovery path, never a second primary trigger.
 * AlarmManager gets the first delivery attempt. This worker waits for a grace
 * period and verifies the exact projection is still pending before dispatching.
 */
class ProactiveMessageWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ProactiveMessageWorker"
        private const val UNIQUE_WORK_NAME = "proactive_message_work"
        private const val TARGETED_UNIQUE_WORK_NAME = "targeted_proactive_message_work"

        private fun autonomousWorkName(assistantId: String): String =
            "$UNIQUE_WORK_NAME:${assistantId.trim()}"

        private fun assistantWorkTag(assistantId: String): String =
            "proactive_assistant:${assistantId.trim()}"

        fun scheduleNext(context: Context, setting: me.rerere.rikkahub.data.datastore.ProactiveMessageSetting) {
            if (!setting.enabled) {
                cancel(context, setting.assistantId)
                return
            }
            val minMinutes = if (setting.naturalScheduling) 45 else setting.minIntervalMinutes.coerceAtLeast(1)
            val maxMinutes = if (setting.naturalScheduling) 90 else setting.maxIntervalMinutes.coerceAtLeast(minMinutes)
            scheduleNext(context, setting, Random.nextInt(minMinutes, maxMinutes + 1))
        }

        fun scheduleNext(
            context: Context,
            setting: me.rerere.rikkahub.data.datastore.ProactiveMessageSetting,
            delayMinutes: Int,
        ) {
            if (!setting.enabled) {
                cancel(context, setting.assistantId)
                return
            }
            val safeDelayMinutes = delayMinutes.coerceAtLeast(1)
            val projectedTriggerAt = System.currentTimeMillis() +
                TimeUnit.MINUTES.toMillis(safeDelayMinutes.toLong())

            val workRequest = OneTimeWorkRequestBuilder<ProactiveMessageWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(ProactiveMessageService.EXTRA_ASSISTANT_ID, setting.assistantId)
                        .putLong(INPUT_PROJECTED_TRIGGER_AT, projectedTriggerAt)
                        .build(),
                )
                .addTag(assistantWorkTag(setting.assistantId))
                .setInitialDelay(
                    TimeUnit.MINUTES.toMillis(safeDelayMinutes.toLong()) + FALLBACK_GRACE_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                autonomousWorkName(setting.assistantId),
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )

            // Scheduler owns the canonical trigger projection. Do not overwrite it
            // with a second independently calculated WorkManager timestamp.
            Log.d(TAG, "Scheduled delayed autonomous fallback in $safeDelayMinutes minutes + grace")
        }

        fun cancel(context: Context, assistantId: String? = null) {
            val workManager = WorkManager.getInstance(context)
            if (assistantId.isNullOrBlank()) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            } else {
                workManager.cancelAllWorkByTag(assistantWorkTag(assistantId))
            }
            Log.d(TAG, "Cancelled WorkManager proactive message assistant=$assistantId")
        }

        fun scheduleTargeted(
            context: Context,
            triggerAtMillis: Long,
            assistantId: String,
            commitmentId: String,
        ) {
            val spec = buildTargetedProactiveWorkSpec(
                triggerAtMillis = triggerAtMillis,
                assistantId = assistantId,
                commitmentId = commitmentId,
            )
            if (!spec.isTargeted) return

            val workRequest = OneTimeWorkRequestBuilder<ProactiveMessageWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(ProactiveMessageService.EXTRA_ASSISTANT_ID, spec.assistantId)
                        .putString(ProactiveMessageService.EXTRA_COMMITMENT_ID, spec.commitmentId)
                        .putLong(INPUT_PROJECTED_TRIGGER_AT, triggerAtMillis)
                        .build(),
                )
                .addTag(assistantWorkTag(spec.assistantId))
                .setInitialDelay(spec.delayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                spec.uniqueWorkName,
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )
            Log.d(TAG, "Scheduled delayed targeted fallback commitment=${spec.commitmentId}")
        }

        fun cancelTargeted(context: Context, assistantId: String? = null, commitmentId: String? = null) {
            val workManager = WorkManager.getInstance(context)
            if (!assistantId.isNullOrBlank() && !commitmentId.isNullOrBlank()) {
                workManager.cancelUniqueWork("$TARGETED_UNIQUE_WORK_NAME:${assistantId.trim()}:${commitmentId.trim()}")
            } else if (!assistantId.isNullOrBlank()) {
                workManager.cancelAllWorkByTag(assistantWorkTag(assistantId))
            } else {
                workManager.cancelUniqueWork(TARGETED_UNIQUE_WORK_NAME)
            }
            Log.d(TAG, "Cancelled targeted WorkManager fallback")
        }

        fun canScheduleExactAlarms(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            return (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        }

        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            return (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        }
    }

    override suspend fun doWork(): Result {
        val koin = org.koin.core.context.GlobalContext.get()
        val settingsStore = koin.get<SettingsStore>()
        val dispatcher = koin.get<ProactiveTurnDispatcher>()
        val settings = settingsStore.settingsFlow.first()
        val scheduledAssistantId = inputData
            .getString(ProactiveMessageService.EXTRA_ASSISTANT_ID)
            ?.takeIf(String::isNotBlank)
        val targetedCommitmentId = inputData
            .getString(ProactiveMessageService.EXTRA_COMMITMENT_ID)
            ?.takeIf(String::isNotBlank)
        val projectedTriggerAt = inputData.getLong(INPUT_PROJECTED_TRIGGER_AT, 0L)
        val isTargeted = scheduledAssistantId != null && targetedCommitmentId != null
        val scheduledAssistantUuid = scheduledAssistantId
            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            ?: if (isTargeted) return Result.failure() else null
        val proactiveSetting = settings.getProactiveMessageSetting(scheduledAssistantUuid)

        val prefs = applicationContext.getSharedPreferences(
            ProactiveMessageService.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val canonicalTriggerAt = if (isTargeted) {
            prefs.getLong(ProactiveMessageService.KEY_TARGETED_TRIGGER_TIME, 0L)
        } else {
            prefs.getLong(KEY_NEXT_TRIGGER_TIME, 0L)
        }
        val lastTriggeredAt = prefs.getLong(KEY_LAST_TRIGGERED_TIME, 0L)
        val projectionMatches = projectedTriggerAt > 0L && canonicalTriggerAt == projectedTriggerAt
        val primaryAlreadyRan = lastTriggeredAt >= projectedTriggerAt && projectedTriggerAt > 0L

        if (!projectionMatches || primaryAlreadyRan) {
            Log.d(
                TAG,
                "Fallback stale or primary already delivered; skip assistant=$scheduledAssistantId " +
                    "projected=$projectedTriggerAt canonical=$canonicalTriggerAt last=$lastTriggeredAt",
            )
            return Result.success()
        }

        if (isTargeted) {
            val projectedAssistantId = prefs.getString(ProactiveMessageService.EXTRA_ASSISTANT_ID, null)
            val projectedCommitmentId = prefs.getString(
                ProactiveMessageService.KEY_TARGETED_COMMITMENT_ID,
                null,
            )
            if (projectedAssistantId != scheduledAssistantId || projectedCommitmentId != targetedCommitmentId) {
                Log.d(TAG, "Targeted fallback identity is stale; skipping commitment=$targetedCommitmentId")
                return Result.success()
            }
        }

        val wakeLock = (applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ProactiveMessage::WorkerWakeLock")
        wakeLock.acquire(5 * 60 * 1000L)

        try {
            return when (
                val dispatch = dispatcher.dispatch(
                    context = applicationContext,
                    assistantId = scheduledAssistantId,
                    commitmentId = targetedCommitmentId,
                    triggerId = buildTriggerId(scheduledAssistantId, targetedCommitmentId, projectedTriggerAt),
                )
            ) {
                ProactiveTurnDispatchResult.Disabled -> Result.success()
                is ProactiveTurnDispatchResult.Busy -> {
                    Log.d(TAG, "Another proactive turn owns the lease: ${dispatch.executionId}")
                    Result.success()
                }
                is ProactiveTurnDispatchResult.InvalidTarget -> Result.failure()
                is ProactiveTurnDispatchResult.Dispatched -> Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ProactiveMessageWorker failed", e)
            return Result.retry()
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }
}

private fun buildTriggerId(
    assistantId: String?,
    commitmentId: String?,
    projectedTriggerAt: Long,
): String = listOf(
    assistantId.orEmpty(),
    commitmentId.orEmpty(),
    projectedTriggerAt.toString(),
).joinToString(":")
