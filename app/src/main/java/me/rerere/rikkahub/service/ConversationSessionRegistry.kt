package me.rerere.rikkahub.service

import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/** Owns conversation-session creation, reference counting, jobs and idle cleanup. */
internal class ConversationSessionRegistry(
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
) {
    private val sessions = ConcurrentHashMap<Uuid, ConversationSession>()
    private val version = MutableStateFlow(0L)

    fun getOrCreate(conversationId: Uuid): ConversationSession =
        sessions.computeIfAbsent(conversationId) { id ->
            val settings = settingsStore.settingsFlow.value
            ConversationSession(
                id = id,
                initial = Conversation.ofId(
                    id = id,
                    assistantId = settings.getCurrentAssistant().id,
                ),
                scope = appScope,
                onIdle = ::removeIfIdle,
            ).also {
                version.value++
                Log.i(TAG, "createSession: $id (total: ${sessions.size + 1})")
            }
        }

    fun acquire(conversationId: Uuid) {
        getOrCreate(conversationId).acquire()
    }

    fun release(conversationId: Uuid) {
        sessions[conversationId]?.release()
    }

    fun launchWithReference(
        conversationId: Uuid,
        block: suspend () -> Unit,
    ): Job = appScope.launch {
        acquire(conversationId)
        try {
            block()
        } finally {
            release(conversationId)
        }
    }

    fun conversationFlow(conversationId: Uuid): StateFlow<Conversation> =
        getOrCreate(conversationId).state

    fun generationJobFlow(conversationId: Uuid): Flow<Job?> =
        sessions[conversationId]?.generationJob ?: flowOf(null)

    fun currentGenerationJob(conversationId: Uuid): Job? =
        sessions[conversationId]?.getJob()

    fun processingStatusFlow(conversationId: Uuid): StateFlow<String?> =
        sessions[conversationId]?.processingStatus ?: MutableStateFlow(null)

    fun activeJobs(): Flow<Map<Uuid, Job?>> = version.flatMapLatest {
        val current = sessions.values.toList()
        if (current.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(current.map { session ->
                session.generationJob.map { job -> session.id to job }
            }) { pairs ->
                pairs.filter { it.second != null }.toMap()
            }
        }
    }

    fun cleanup() {
        sessions.values.forEach(ConversationSession::cleanup)
        sessions.clear()
        version.value++
    }

    private fun removeIfIdle(conversationId: Uuid) {
        val session = sessions[conversationId] ?: return
        if (session.isInUse) {
            Log.d(TAG, "removeSession: skipped $conversationId (still in use)")
            return
        }
        if (sessions.remove(conversationId, session)) {
            session.cleanup()
            version.value++
            Log.i(TAG, "removeSession: $conversationId (remaining: ${sessions.size})")
        }
    }

    private companion object {
        const val TAG = "ConversationSessionRegistry"
    }
}
