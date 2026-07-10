package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.companion.CompanionPerceptionAssembler
import me.rerere.rikkahub.data.companion.CompanionPerceptionInput
import me.rerere.rikkahub.data.companion.CompanionRelationshipState
import me.rerere.rikkahub.data.companion.CompanionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionIntentPlannerTest {
    @Test
    fun `foreground fallback waits without inventing role behavior`() {
        val decision = CompanionIntentFallbackPlanner.plan(
            CompanionIntentInput(
                perception = perception(),
                mode = CompanionDecisionMode.FOREGROUND,
                minutesSinceLastChat = 0L,
            ),
        )

        assertEquals(CompanionIntent.WAIT, decision.intent)
        assertFalse(decision.shouldMessageNow)
        assertEquals(null, decision.delayMinutes)
        assertFalse(decision.reason.contains("露露"))
    }

    @Test
    fun `background fallback may reach out after long silence without persona keyword matching`() {
        val decision = CompanionIntentFallbackPlanner.plan(
            CompanionIntentInput(
                perception = perception(availableTools = setOf("get_battery_info", "get_app_usage")),
                mode = CompanionDecisionMode.BACKGROUND,
                minutesSinceLastChat = 180L,
            ),
        )

        assertEquals(CompanionIntent.REACH_OUT, decision.intent)
        assertTrue(decision.shouldMessageNow)
        assertEquals(listOf("get_battery_info", "get_app_usage"), decision.toolNames)
    }

    @Test
    fun `background fallback respects unresolved relationship tension`() {
        val tenseSnapshot = CompanionSnapshot.empty("assistant-a").copy(
            relationship = CompanionRelationshipState(unresolvedTension = 0.8f),
        )
        val packet = CompanionPerceptionAssembler.assemble(
            input = CompanionPerceptionInput(
                assistantId = "assistant-a",
                assistantName = "Role A",
                persona = "Any role persona",
                nowMillis = 1_000L,
            ),
            snapshot = tenseSnapshot,
        )

        val decision = CompanionIntentFallbackPlanner.plan(
            CompanionIntentInput(
                perception = packet,
                mode = CompanionDecisionMode.BACKGROUND,
                minutesSinceLastChat = 180L,
            ),
        )

        assertEquals(CompanionIntent.WAIT, decision.intent)
    }

    @Test
    fun `model parser accepts legacy labels but returns role neutral intent`() {
        val decision = CompanionIntentModelPlanner.parsePlan(
            rawText = """
                {
                  "intent": "check_context",
                  "shouldMessageNow": false,
                  "delayMinutes": 99999,
                  "toolNames": ["camera_capture", "unknown", "camera_capture"],
                  "reason": "Recheck current context before deciding.",
                  "tone": "follow persona",
                  "innerThought": "I should look again before speaking."
                }
            """.trimIndent(),
            availableToolNames = setOf("camera_capture"),
        )

        assertEquals(CompanionIntent.OBSERVE, decision?.intent)
        assertEquals(24 * 60, decision?.delayMinutes)
        assertEquals(listOf("camera_capture"), decision?.toolNames)
        assertTrue(decision?.fromModel == true)
    }

    @Test
    fun `model prompt contains persona and unified runtime truth`() {
        val prompt = CompanionIntentModelPlanner.buildPrompt(
            CompanionIntentInput(
                perception = perception(persona = "A restrained housekeeper persona"),
                mode = CompanionDecisionMode.BACKGROUND,
                minutesSinceLastChat = 45L,
            ),
        )

        assertTrue(prompt.contains("A restrained housekeeper persona"))
        assertTrue(prompt.contains("<companion_runtime"))
        assertTrue(prompt.contains("FOLLOW_UP, STAY_AVAILABLE, REACH_OUT, OBSERVE, WAIT"))
        assertFalse(prompt.contains("study-supervisor"))
    }

    private fun perception(
        persona: String = "Any role persona",
        availableTools: Set<String> = emptySet(),
    ) = CompanionPerceptionAssembler.assemble(
        input = CompanionPerceptionInput(
            assistantId = "assistant-a",
            assistantName = "Role A",
            persona = persona,
            availableToolNames = availableTools,
            nowMillis = 1_000L,
        ),
        snapshot = CompanionSnapshot.empty("assistant-a"),
    )
}
