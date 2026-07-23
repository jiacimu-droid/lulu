package me.rerere.rikkahub.data.service

import android.content.Context
import android.content.Intent
import android.os.Build
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getProactiveMessageSetting
import kotlin.uuid.Uuid

/**
 * Delivery boundary for proactive turns.
 *
 * Scheduling policy stays in the scheduler, generation stays in
 * ProactiveMessageService, and this class owns only resolving the requested role
 * and handing the work to the Android service. This prevents workers from
 * accumulating generation and commitment logic.
 */
class ProactiveTurnDispatcher(
    private val settingsStore: SettingsStore,
) {
    suspend fun dispatch(
        context: Context,
        assistantId: String?,
        commitmentId: String?,
    ): ProactiveTurnDispatchResult {
        val targeted = !assistantId.isNullOrBlank() && !commitmentId.isNullOrBlank()
        val parsedAssistantId = assistantId
            ?.takeIf(String::isNotBlank)
            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            ?: if (targeted) {
                return ProactiveTurnDispatchResult.InvalidTarget("Invalid assistant id for targeted turn")
            } else {
                null
            }
        val settings = settingsStore.settingsFlow.value
        val proactiveSetting = settings.getProactiveMessageSetting(parsedAssistantId)
        if (!targeted && !proactiveSetting.enabled) {
            return ProactiveTurnDispatchResult.Disabled
        }

        val intent = Intent(context, ProactiveMessageTriggerService::class.java).apply {
            putExtra(ProactiveMessageService.EXTRA_ASSISTANT_ID, proactiveSetting.assistantId)
            commitmentId?.takeIf(String::isNotBlank)?.let {
                putExtra(ProactiveMessageService.EXTRA_COMMITMENT_ID, it)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        return ProactiveTurnDispatchResult.Dispatched(
            assistantId = proactiveSetting.assistantId,
            targeted = targeted,
        )
    }
}

sealed interface ProactiveTurnDispatchResult {
    data class Dispatched(
        val assistantId: String,
        val targeted: Boolean,
    ) : ProactiveTurnDispatchResult

    data object Disabled : ProactiveTurnDispatchResult
    data class InvalidTarget(val reason: String) : ProactiveTurnDispatchResult
}
