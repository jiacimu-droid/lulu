package me.rerere.rikkahub.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Single alarm entry point for every proactive trigger.
 *
 * AlarmManager never starts the generation service directly. It only submits the trigger to
 * [ProactiveTurnDispatcher], which owns the per-assistant execution lease shared with Worker and
 * in-app launches.
 */
class UnifiedProactiveAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ProactiveMessageService.ACTION_PROACTIVE_MESSAGE) return

        val pendingResult = goAsync()
        val applicationContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val assistantId = intent.getStringExtra(ProactiveMessageService.EXTRA_ASSISTANT_ID)
                val commitmentId = intent.getStringExtra(ProactiveMessageService.EXTRA_COMMITMENT_ID)
                val targetedReason = intent.getStringExtra(ProactiveMessageService.EXTRA_TARGETED_REASON)
                val targetedUserText = intent.getStringExtra(ProactiveMessageService.EXTRA_TARGETED_USER_TEXT)
                val targetedKind = intent.getStringExtra(ProactiveMessageService.EXTRA_TARGETED_KIND)
                val triggerIdentity = buildString {
                    append("alarm:")
                    append(assistantId.orEmpty())
                    append(':')
                    append(commitmentId.orEmpty())
                    append(':')
                    append(targetedKind.orEmpty())
                    append(':')
                    append(intent.dataString.orEmpty())
                }

                val dispatcher = GlobalContext.get().get<ProactiveTurnDispatcher>()
                when (
                    val result = dispatcher.dispatch(
                        context = applicationContext,
                        assistantId = assistantId,
                        commitmentId = commitmentId,
                        triggerId = triggerIdentity,
                        targetedReason = targetedReason,
                        targetedUserText = targetedUserText,
                        targetedKind = targetedKind,
                    )
                ) {
                    is ProactiveTurnDispatchResult.Dispatched -> {
                        Log.d(TAG, "Alarm trigger dispatched execution=${result.executionId}")
                    }
                    is ProactiveTurnDispatchResult.Busy -> {
                        Log.d(TAG, "Alarm trigger merged into active execution=${result.executionId}")
                    }
                    ProactiveTurnDispatchResult.Disabled -> {
                        Log.d(TAG, "Alarm trigger ignored because proactive messaging is disabled")
                    }
                    is ProactiveTurnDispatchResult.InvalidTarget -> {
                        Log.w(TAG, "Alarm trigger ignored: ${result.reason}")
                    }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to dispatch proactive alarm through unified gate", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "UnifiedProactiveAlarm"
    }
}
