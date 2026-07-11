package me.rerere.rikkahub.ui.pages.cihai

import me.rerere.rikkahub.data.companion.CompanionRelationshipEvent
import me.rerere.rikkahub.data.companion.CompanionRelationshipEventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompanionRelationshipTimelineTest {
    @Test
    fun `timeline orders newest events first and explains dimension changes`() {
        val items = buildCompanionRelationshipTimeline(
            listOf(
                event(
                    id = "older",
                    kind = CompanionRelationshipEventKind.MEANINGFUL_DISCLOSURE,
                    evidence = "你认真说了最近真正担心的事",
                    trustDelta = 0.02f,
                    closenessDelta = 0.03f,
                    createdAt = 100L,
                ),
                event(
                    id = "newer",
                    kind = CompanionRelationshipEventKind.COMMITMENT_FULFILLED,
                    evidence = "message delivered",
                    reliabilityDelta = 0.03f,
                    createdAt = 200L,
                ),
            ),
        )

        assertEquals(listOf("newer", "older"), items.map { it.id })
        assertEquals("答应的事已经做到", items.first().title)
        assertEquals("说到做到 +3", items.first().deltaText)
        assertEquals("你认真说了最近真正担心的事", items.last().detail)
        assertEquals("信任 +2 · 亲近 +3", items.last().deltaText)
    }

    @Test
    fun `timeline hides technical failure evidence`() {
        val item = buildCompanionRelationshipTimeline(
            listOf(
                event(
                    id = "failed",
                    kind = CompanionRelationshipEventKind.COMMITMENT_FAILED,
                    evidence = "provider unavailable: API request failed",
                    reliabilityDelta = -0.03f,
                    createdAt = 200L,
                ),
            ),
        ).single()

        assertEquals("答应的事没有按计划完成", item.title)
        assertNull(item.detail)
        assertEquals("说到做到 -3", item.deltaText)
    }

    private fun event(
        id: String,
        kind: CompanionRelationshipEventKind,
        evidence: String,
        trustDelta: Float = 0f,
        closenessDelta: Float = 0f,
        reliabilityDelta: Float = 0f,
        createdAt: Long,
    ) = CompanionRelationshipEvent(
        id = id,
        assistantId = "lulu",
        sourceId = "source-$id",
        kind = kind,
        trustDelta = trustDelta,
        closenessDelta = closenessDelta,
        reliabilityDelta = reliabilityDelta,
        evidence = evidence,
        createdAt = createdAt,
    )
}
