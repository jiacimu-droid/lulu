package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.companion.CompanionRelationshipState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionAuthorizedToolPolicyTest {
    private val restrictiveRelationship = CompanionRelationshipState(
        trust = 0.1f,
        boundaryConfidence = 0.1f,
        unresolvedTension = 0.9f,
    )

    @Test
    fun `authorized sms and location survive hidden relationship restrictions`() {
        val decision = CompanionIntentDecision(
            intent = CompanionIntent.OBSERVE,
            shouldMessageNow = false,
            delayMinutes = null,
            toolNames = listOf(
                "read_sms",
                "get_location",
                "explore_nearby",
                "camera_capture",
            ),
            reason = "Collect relevant context.",
            tone = "Follow persona.",
            actionToolName = "get_location",
            actionArgumentsJson = "{}",
        ).enforceRelationshipPolicy(restrictiveRelationship)

        assertEquals(
            listOf("read_sms", "get_location", "explore_nearby"),
            decision.toolNames,
        )
        assertEquals("get_location", decision.actionToolName)
    }

    @Test
    fun `camera stays protected while sms and location remain available in chat planning`() {
        val plan = CompanionChatTurnPlan(
            toolRequests = listOf(
                ProactiveToolRequest("read_sms", "Read relevant messages."),
                ProactiveToolRequest("get_location", "Use current location context."),
                ProactiveToolRequest("explore_nearby", "Find nearby places."),
                ProactiveToolRequest("camera_capture", "Look through the camera."),
            ),
        ).enforceRelationshipPolicy(
            relationship = restrictiveRelationship,
            latestUserText = "今天怎么样",
        )

        assertEquals(
            listOf("read_sms", "get_location", "explore_nearby"),
            plan.toolRequests.map { it.toolName },
        )
        assertTrue(plan.toolRequests.none { it.toolName == "camera_capture" })
    }
}
