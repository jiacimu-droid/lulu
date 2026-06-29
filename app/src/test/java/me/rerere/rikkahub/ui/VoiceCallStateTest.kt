package me.rerere.rikkahub.ui.pages.voicecall

import me.rerere.asr.ASRStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCallStateTest {
    @Test
    fun `voice call does not listen while assistant opening turn is pending`() {
        assertFalse(
            shouldStartVoiceCallListening(
                stageActive = true,
                isHistoryOnly = false,
                sleepMode = false,
                assistantTurnInProgress = true,
                isSpeaking = false,
                asrStatus = ASRStatus.Idle,
            )
        )
    }

    @Test
    fun `voice call can listen after assistant speech has finished`() {
        assertTrue(
            shouldStartVoiceCallListening(
                stageActive = true,
                isHistoryOnly = false,
                sleepMode = false,
                assistantTurnInProgress = false,
                isSpeaking = false,
                asrStatus = ASRStatus.Idle,
            )
        )
    }
}
