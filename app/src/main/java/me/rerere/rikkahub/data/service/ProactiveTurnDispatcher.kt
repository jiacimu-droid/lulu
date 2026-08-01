package me.rerere.rikkahub.data.service

import android.content.Context
import android.content.Intent
import android.os.Build
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getProactiveMessageSetting
import java.util.UUID
import kotlin.uuid.Uuid

private const val EXECUTION_PREFS = "proactive_execution_leases"
private const val EXECUTION_LEASE_MILLIS = 10L * 60L * 1000L

/**
 * Resolves a proactive target and atomically leases one execution per assistant.
 * Alarm, Worker and in-app triggers all pass through this gate before the generation service starts.
 */
class ProactiveTurnDispatcher(
    private val settingsStore: SettingsStore,
) {
    suspend fun dispatch(
        context: Context,
        assistantId: String?,
        commitmentId: String?,
        triggerId: String = UUID.randomUUID().toString(),
        targetedReason: String? = null,
        targetedUserText: String? = null,
        targetedKind: String? = null,
    ): ProactiveTurnDispatchResult {
        val targeted = !commitmentId.isNullOrBlank() ||
            !targetedReason.isNullOrBlank() ||
            !targetedUserText.isNullOrBlank() ||
            !targetedKind.isNullOrBlank()
        val parsedAssistantId = assistantId
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            ?: if (targeted) {
                return ProactiveTurnDispatchResult.InvalidTarget("Invalid assistant id for targeted turn")
            } else {
                null
            }
        val settings = settingsStore.settingsFlow.value
        val proactiveSetting = settings.getProactiveMessageSetting(parsedAssistantId)
        if (!proactiveSetting.enabled) {
            return ProactiveTurnDispatchResult.Disabled
        }

        val resolvedAssistantId = proactiveSetting.assistantId
        val executionId = claimExecutionLease(
            context = context,
            assistantId = resolvedAssistantId,
            triggerId = triggerId,
        ) ?: return ProactiveTurnDispatchResult.Busy(
            assistantId = resolvedAssistantId,
            executionId = activeExecutionId(context, resolvedAssistantId).orEmpty(),
        )

        return runCatching {
            val intent = Intent(context, ProactiveMessageTriggerService::class.java).apply {
                putExtra(ProactiveMessageService.EXTRA_ASSISTANT_ID, resolvedAssistantId)
                putExtra(EXTRA_PROACTIVE_EXECUTION_ID, executionId)
                putExtra(EXTRA_PROACTIVE_TRIGGER_ID, triggerId)
                commitmentId?.takeIf { it.isNotBlank() }?.let {
                    putExtra(ProactiveMessageService.EXTRA_COMMITMENT_ID, it)
                }
                targetedReason?.takeIf { it.isNotBlank() }?.let {
                    putExtra(ProactiveMessageService.EXTRA_TARGETED_REASON, it)
                }
                targetedUserText?.takeIf { it.isNotBlank() }?.let {
                    putExtra(ProactiveMessageService.EXTRA_TARGETED_USER_TEXT, it)
                }
                targetedKind?.takeIf { it.isNotBlank() }?.let {
                    putExtra(ProactiveMessageService.EXTRA_TARGETED_KIND, it)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            ProactiveTurnDispatchResult.Dispatched(
                assistantId = resolvedAssistantId,
                targeted = targeted,
                executionId = executionId,
            )
        }.getOrElse { error ->
            releaseExecutionLease(context, resolvedAssistantId, executionId)
            throw error
        }
    }
}

internal const val EXTRA_PROACTIVE_EXECUTION_ID = "proactive_execution_id"
internal const val EXTRA_PROACTIVE_TRIGGER_ID = "proactive_trigger_id"

internal fun claimExecutionLease(
    context: Context,
    assistantId: String,
    triggerId: String,
    nowMillis: Long = System.currentTimeMillis(),
): String? {
    val prefs = context.getSharedPreferences(EXECUTION_PREFS, Context.MODE_PRIVATE)
    val key = "lease:${assistantId.trim()}"
    synchronized(ProactiveExecutionLeaseLock) {
        val current = prefs.getString(key, null)?.decodeLease()
        if (current != null && current.expiresAt > nowMillis) {
            return null
        }
        val executionId = "${assistantId.trim()}:${nowMillis}:${triggerId.hashCode()}"
        val lease = ProactiveExecutionLease(
            executionId = executionId,
            triggerId = triggerId,
            expiresAt = nowMillis + EXECUTION_LEASE_MILLIS,
        )
        val saved = prefs.edit().putString(key, lease.encode()).commit()
        return executionId.takeIf { saved }
    }
}

internal fun releaseExecutionLease(
    context: Context,
    assistantId: String,
    executionId: String,
) {
    val prefs = context.getSharedPreferences(EXECUTION_PREFS, Context.MODE_PRIVATE)
    val key = "lease:${assistantId.trim()}"
    synchronized(ProactiveExecutionLeaseLock) {
        val current = prefs.getString(key, null)?.decodeLease()
        if (current?.executionId == executionId) {
            prefs.edit().remove(key).commit()
        }
    }
}

private fun activeExecutionId(context: Context, assistantId: String): String? {
    val prefs = context.getSharedPreferences(EXECUTION_PREFS, Context.MODE_PRIVATE)
    return prefs.getString("lease:${assistantId.trim()}", null)?.decodeLease()?.executionId
}

private object ProactiveExecutionLeaseLock

private data class ProactiveExecutionLease(
    val executionId: String,
    val triggerId: String,
    val expiresAt: Long,
) {
    fun encode(): String = listOf(executionId, triggerId, expiresAt.toString()).joinToString("|")
}

private fun String.decodeLease(): ProactiveExecutionLease? {
    val parts = split('|', limit = 3)
    if (parts.size != 3) return null
    return ProactiveExecutionLease(
        executionId = parts[0],
        triggerId = parts[1],
        expiresAt = parts[2].toLongOrNull() ?: return null,
    )
}

sealed interface ProactiveTurnDispatchResult {
    data class Dispatched(
        val assistantId: String,
        val targeted: Boolean,
        val executionId: String,
    ) : ProactiveTurnDispatchResult

    data class Busy(
        val assistantId: String,
        val executionId: String,
    ) : ProactiveTurnDispatchResult

    data object Disabled : ProactiveTurnDispatchResult
    data class InvalidTarget(val reason: String) : ProactiveTurnDispatchResult
}
