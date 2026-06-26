package me.rerere.rikkahub.data.voicecall

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File
import kotlin.uuid.Uuid

class VoiceCallRepository(
    private val context: Context,
) {
    private val storageFile: File
        get() = File(context.filesDir, "voice_call_sessions.json")

    fun getSessions(): List<VoiceCallSession> {
        return runCatching {
            if (!storageFile.exists()) return emptyList()
            JsonInstant.decodeFromString<List<VoiceCallSession>>(storageFile.readText())
        }.getOrDefault(emptyList())
            .sortedByDescending { it.startedAt }
    }

    fun getSession(id: String): VoiceCallSession? {
        return getSessions().firstOrNull { it.id == id }
    }

    fun createSession(
        conversationId: String,
        assistantId: String,
        assistantName: String,
        initialLines: List<VoiceCallLine> = emptyList(),
    ): VoiceCallSession {
        val now = System.currentTimeMillis()
        val session = VoiceCallSession(
            id = Uuid.random().toString(),
            conversationId = conversationId,
            assistantId = assistantId,
            assistantName = assistantName,
            startedAt = now,
            transcript = initialLines.ifEmpty {
                listOf(
                    VoiceCallLine(
                        role = VoiceCallRole.System,
                        text = "Voice call started",
                        timestamp = now,
                    )
                )
            },
        )
        upsertSession(session)
        return session
    }

    fun upsertSession(session: VoiceCallSession) {
        val sessions = getSessions().filterNot { it.id == session.id } + session
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(JsonInstant.encodeToString(sessions.sortedByDescending { it.startedAt }))
    }

    fun appendLine(sessionId: String, line: VoiceCallLine) {
        val session = getSession(sessionId) ?: return
        upsertSession(session.copy(transcript = session.transcript + line))
    }

    fun endSession(sessionId: String) {
        val session = getSession(sessionId) ?: return
        if (session.status == VoiceCallStatus.Ended) return
        upsertSession(
            session.copy(
                status = VoiceCallStatus.Ended,
                endedAt = System.currentTimeMillis(),
                transcript = session.transcript + VoiceCallLine(
                    role = VoiceCallRole.System,
                    text = "Voice call ended",
                ),
            )
        )
    }
}
