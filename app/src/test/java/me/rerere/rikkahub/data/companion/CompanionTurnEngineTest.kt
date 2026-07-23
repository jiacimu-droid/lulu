package me.rerere.rikkahub.data.companion

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionTurnEngineTest {
    @Test
    fun executeUsesTheSingleOrderedLifecycleAndDefersMaintenance() = runBlocking {
        val calls = mutableListOf<String>()
        var maintenanceRan = false
        val engine = CompanionTurnEngine()

        val result = engine.execute(
            request = CompanionTurnRequest(
                assistantId = "role-1",
                conversationId = "conversation-1",
                entryPoint = CompanionTurnEntryPoint.CHAT,
                userText = "hello",
            ),
            prepareContext = {
                calls += "prepare"
                "context"
            },
            decide = { context ->
                calls += "decide:$context"
                "decision"
            },
            generate = { context, decision ->
                calls += "generate:$context:$decision"
                "reply"
            },
            executeTools = { context, decision, generated ->
                calls += "tools:$context:$decision:$generated"
                listOf("tool-result")
            },
            persist = { input ->
                calls += "persist:${input.generated}:${input.tools.single()}"
                "persisted"
            },
            maintain = { input ->
                calls += "maintain:${input.persisted}"
                maintenanceRan = true
            },
        )

        assertEquals(
            listOf(
                "prepare",
                "decide:context",
                "generate:context:decision",
                "tools:context:decision:reply",
                "persist:reply:tool-result",
            ),
            calls,
        )
        assertFalse(maintenanceRan)
        assertEquals(
            listOf(
                CompanionTurnStage.PREPARE_CONTEXT,
                CompanionTurnStage.DECIDE,
                CompanionTurnStage.GENERATE,
                CompanionTurnStage.EXECUTE_TOOLS,
                CompanionTurnStage.PERSIST,
            ),
            result.foregroundTrace.map(CompanionTurnStageRecord::stage),
        )
        assertTrue(result.foregroundTrace.all { it.status == CompanionTurnStageStatus.COMPLETED })
        assertEquals("persisted", result.persisted)
        assertTrue(result.traceId.isNotBlank())

        result.runMaintenance()

        assertTrue(maintenanceRan)
        assertEquals("maintain:persisted", calls.last())
    }

    @Test
    fun everyCompanionSurfaceIsRepresentedByTheSharedContract() {
        assertEquals(
            setOf(
                CompanionTurnEntryPoint.CHAT,
                CompanionTurnEntryPoint.VOICE_CALL,
                CompanionTurnEntryPoint.PROACTIVE,
                CompanionTurnEntryPoint.GAME,
            ),
            CompanionTurnEntryPoint.values().toSet(),
        )
    }

    @Test
    fun neutralEventsStoreEvidenceAndResponsibilityWithoutRoleWording() {
        val event = CompanionNeutralEvent(
            type = "commitment_execution",
            assistantId = "role-1",
            occurredAtMillis = 1234L,
            status = CompanionNeutralEventStatus.COMPLETED,
            actor = CompanionEventActor.ASSISTANT,
            subjectKey = "wake-supervision",
            evidenceReferences = listOf("alarm:42", "message:84"),
            responsibility = CompanionResponsibility(
                owner = CompanionEventActor.ASSISTANT,
                beneficiary = CompanionEventActor.USER,
                actionType = "wake_user",
                dueAtMillis = 1200L,
                recurrence = "daily",
            ),
            attributes = mapOf("result" to "delivered"),
        )

        assertEquals("commitment_execution", event.type)
        assertEquals(listOf("alarm:42", "message:84"), event.evidenceReferences)
        assertEquals("wake_user", event.responsibility?.actionType)
        assertEquals(CompanionNeutralEventStatus.COMPLETED, event.status)
        assertNotNull(event.responsibility)
    }
}