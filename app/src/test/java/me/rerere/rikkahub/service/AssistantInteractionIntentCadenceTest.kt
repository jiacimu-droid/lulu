package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.companion.CompanionPerceptionAssembler
import me.rerere.rikkahub.data.companion.CompanionPerceptionInput
import me.rerere.rikkahub.data.companion.CompanionSnapshot
import me.rerere.rikkahub.data.model.AssistantInteractionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantInteractionIntentCadenceTest {
    @Test
    fun `normal initiative is not treated as high initiative by fallback`() {
        val profile = AssistantInteractionProfile(
            initiative = "通常会主动联系，但只在有合适话题或明确关心理由时开口。",
        )
        val perception = CompanionPerceptionAssembler.assemble(
            input = CompanionPerceptionInput(
                assistantId = "assistant-a",
                assistantName = "Role A",
                persona = "A moderately proactive role",
                nowMillis = 1_000L,
            ),
            snapshot = CompanionSnapshot.empty("assistant-a"),
        )

        val early = CompanionIntentFallbackPlanner.plan(
            input = CompanionIntentInput(
                perception = perception,
                mode = CompanionDecisionMode.BACKGROUND,
                minutesSinceLastChat = 60L,
            ),
            interactionProfile = profile,
        )
        val due = CompanionIntentFallbackPlanner.plan(
            input = CompanionIntentInput(
                perception = perception,
                mode = CompanionDecisionMode.BACKGROUND,
                minutesSinceLastChat = 90L,
            ),
            interactionProfile = profile,
        )

        assertEquals(CompanionIntent.WAIT, early.intent)
        assertFalse(early.shouldMessageNow)
        assertEquals(CompanionIntent.REACH_OUT, due.intent)
        assertTrue(due.shouldMessageNow)
    }
}
