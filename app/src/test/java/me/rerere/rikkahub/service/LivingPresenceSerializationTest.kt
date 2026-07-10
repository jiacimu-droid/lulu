package me.rerere.rikkahub.service

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.living.LivingPresenceState
import me.rerere.rikkahub.data.living.withoutAssistant
import org.junit.Assert.assertEquals
import org.junit.Test

class LivingPresenceSerializationTest {
    @Test
    fun `living presence state serializes active intents`() {
        val json = Json { ignoreUnknownKeys = true }
        val intent = RollingJudgmentLoop.createIntent(
            assistantId = "assistant-1",
            assistantName = "露露",
            userText = "我先忙一下",
            assistantText = "好，我在这里等你。",
            nowMillis = 1_700_000_000_000L,
        )

        val encoded = json.encodeToString(LivingPresenceState(activeIntents = listOf(intent)))
        val decoded = json.decodeFromString<LivingPresenceState>(encoded)

        assertEquals(1, decoded.activeIntents.size)
        assertEquals("assistant-1", decoded.activeIntents.single().assistantId)
        assertEquals(intent.kind, decoded.activeIntents.single().kind)
        assertEquals(intent.evaluationCadence.delaysMinutes, decoded.activeIntents.single().evaluationCadence.delaysMinutes)
    }

    @Test
    fun `clearing assistant intents preserves other roles`() {
        val target = RollingJudgmentLoop.createIntent(
            assistantId = "assistant-a",
            assistantName = "甲",
            userText = "我先忙一下",
            assistantText = "好。",
            nowMillis = 1_700_000_000_000L,
        )
        val other = RollingJudgmentLoop.createIntent(
            assistantId = "assistant-b",
            assistantName = "乙",
            userText = "晚点提醒我",
            assistantText = "记住了。",
            nowMillis = 1_700_000_000_100L,
        )
        val state = LivingPresenceState(
            activeIntents = listOf(target, other),
            archivedIntents = listOf(target.copy(id = "archived-target"), other.copy(id = "archived-other")),
        )

        val cleared = state.withoutAssistant("assistant-a")

        assertEquals(listOf("assistant-b"), cleared.activeIntents.map { it.assistantId })
        assertEquals(listOf("assistant-b"), cleared.archivedIntents.map { it.assistantId })
    }
}
