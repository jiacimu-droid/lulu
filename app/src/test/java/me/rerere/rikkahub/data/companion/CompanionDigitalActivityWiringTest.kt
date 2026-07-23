package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionDigitalActivityWiringTest {
    private val registry = CompanionDigitalActivityExecutorRegistry()

    @Test
    fun everyRegisteredActivityHasExactlyOneUsableExecutor() {
        val audit = registry.audit()

        assertTrue(audit.issues.joinToString("\n"), audit.isComplete)
        assertEquals(CompanionDigitalActivityRegistry.definitions.keys, audit.executableKinds)
    }

    @Test
    fun everyUsesModelActivityIsBackedByAModelCapableExecutor() {
        val modelActivities = CompanionDigitalActivityRegistry.definitions.values
            .filter(CompanionDigitalActivityDefinition::usesModel)

        assertFalse(modelActivities.isEmpty())
        modelActivities.forEach { definition ->
            val executor = registry.requireExecutor(definition.kind)
            assertTrue(
                "${definition.kind} declares usesModel=true but ${executor.id} cannot generate an artifact",
                executor.supportsModelGeneration,
            )
        }
    }

    @Test
    fun evidenceBoundActivitiesRejectClaimsWithoutARealReference() {
        val evidenceKinds = CompanionDigitalActivityRegistry.definitions.values
            .filter(CompanionDigitalActivityDefinition::requiresEvidenceReference)

        assertFalse(evidenceKinds.isEmpty())
        evidenceKinds.forEach { definition ->
            val error = registry.requireExecutor(definition.kind).validate(
                request = CompanionDigitalActivityRequest(
                    assistantId = "role-1",
                    kind = definition.kind,
                    title = "activity",
                    summary = "result",
                    evidenceReference = null,
                ),
                definition = definition,
            )
            assertTrue("${definition.kind} accepted missing evidence", !error.isNullOrBlank())
        }
    }
}