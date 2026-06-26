package me.rerere.rikkahub.data.voicecall

import kotlinx.serialization.Serializable

@Serializable
data class VoiceCallSession(
    val id: String,
    val conversationId: String,
    val assistantId: String,
    val assistantName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: VoiceCallStatus = VoiceCallStatus.Active,
    val transcript: List<VoiceCallLine> = emptyList(),
    val sleepMode: Boolean = false,
)

@Serializable
enum class VoiceCallStatus {
    Active,
    Ended,
    Missed,
}

@Serializable
data class VoiceCallLine(
    val role: VoiceCallRole,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val replayable: Boolean = true,
)

@Serializable
enum class VoiceCallRole {
    User,
    Assistant,
    System,
}
