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

internal fun buildTargetedProactiveWorkSpec(
    triggerAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    assistantId: String,
    commitmentId: String,
): TargetedProactiveWorkSpec = TargetedProactiveWorkSpec(
    uniqueWorkName = "targeted_proactive_message_work:${assistantId.trim()}:${commitmentId.trim()}",
    delayMillis = (triggerAtMillis - nowMillis).coerceAtLeast(0L),
    assistantId = assistantId.trim(),
    commitmentId = commitmentId.trim(),
)

/**
 * WorkManager-based fallback for proactive message scheduling.
 * More reliable than AlarmManager on devices with aggressive battery optimization.
 */
class ProactiveMessageWorker(
    context: Context,
    params: WorkerParameters
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
            val delayMinutes = Random.nextInt(minMinutes, maxMinutes + 1)
            scheduleNext(context, setting, delayMinutes)
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

            val workRequest = OneTimeWorkRequestBuilder<ProactiveMessageWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(ProactiveMessageService.EXTRA_ASSISTANT_ID, setting.assistantId)
                        .build(),
                )
                .addTag(assistantWorkTag(setting.assistantId))
                .setInitialDelay(safeDelayMinutes.toLong(), TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    autonomousWorkName(setting.assistantId),
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

            // Also save trigger time to SharedPreferences for UI display
            val triggerTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(safeDelayMinutes.toLong())
            context.getSharedPreferences("proactive_message_prefs", Context.MODE_PRIVATE)
                .edit()
                .putLong("next_trigger_time", triggerTime)
                .putString(ProactiveMessageService.EXTRA_ASSISTANT_ID, setting.assistantId)
                .apply()

            Log.d(TAG, "Scheduled WorkManager proactive message in $safeDelayMinutes minutes")
        }

        fun cancel(context: Context, assistantId: String? = null) {
            val workManager = WorkManager.getInstance(context)
            if (assistantId.isNullOrBlank()) {
                // Compatibility cleanup for schedules created by older app versions.
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

            val inputData = Data.Builder()
                .putString(ProactiveMessageService.EXTRA_ASSISTANT_ID, spec.assistantId)
                .putString(ProactiveMessageService.EXTRA_COMMITMENT_ID, spec.commitmentId)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<ProactiveMessageWorker>()
                .setInputData(inputData)
                .addTag(assistantWorkTag(spec.assistantId))
                .setInitialDelay(spec.delayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                spec.uniqueWorkName,
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )
            Log.d(TAG, "Scheduled targeted WorkManager fallback commitment=${spec.commitmentId}")
        }

        fun cancelTargeted(context: Context, assistantId: String? = null, commitmentId: String? = null) {
            val workManager = WorkManager.getInstance(context)
            if (!assistantId.isNullOrBlank() && !commitmentId.isNullOrBlank()) {
                workManager.cancelUniqueWork("$TARGETED_UNIQUE_WORK_NAME:${assistantId.trim()}:${commitmentId.trim()}")
            } else if (!assistantId.isNullOrBlank()) {
                workManager.cancelAllWorkByTag(assistantWorkTag(assistantId))
            } else {
                // Compatibility cleanup for the pre-per-role targeted fallback.
                workManager.cancelUniqueWork(TARGETED_UNIQUE_WORK_NAME)
            }
            Log.d(TAG, "Cancelled targeted WorkManager fallback")
        }

        /**
         * Check if exact alarm permission is granted (Android 12+)
         */
        fun canScheduleExactAlarms(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return true
            }
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }

        /**
         * Check if app is ignoring battery optimizations
         */
        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "ProactiveMessageWorker triggered")

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
        val isTargeted = scheduledAssistantId != null && targetedCommitmentId != null
        val scheduledAssistantUuid = scheduledAssistantId
            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            ?: if (isTargeted) return Result.failure() else null
        val proactiveSetting = settings.getProactiveMessageSetting(scheduledAssistantUuid)

        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ProactiveMessage::WorkerWakeLock"
        )
        wakeLock.acquire(5 * 60 * 1000L)

        try {
            return when (
                val dispatch = dispatcher.dispatch(
                    context = applicationContext,
                    assistantId = scheduledAssistantId,
                    commitmentId = targetedCommitmentId,
                )
            ) {
                ProactiveTurnDispatchResult.Disabled -> {
                    Log.d(TAG, "Proactive message disabled, skipping")
                    Result.success()
                }
                is ProactiveTurnDispatchResult.InvalidTarget -> {
                    Log.e(TAG, dispatch.reason)
                    Result.failure()
                }
                is ProactiveTurnDispatchResult.Dispatched -> {
                    if (!dispatch.targeted) {
                        scheduleNext(applicationContext, proactiveSetting)
                    }
                    Result.success()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ProactiveMessageWorker failed", e)
            if (!isTargeted) {
                scheduleNext(applicationContext, proactiveSetting)
            }
            return Result.retry()
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
}
