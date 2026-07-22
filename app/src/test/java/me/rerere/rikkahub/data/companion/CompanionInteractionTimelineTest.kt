package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompanionInteractionTimelineTest {
    @Test
    fun `outbound delivery never resets user activity clock`() {
        val initial = CompanionInteractionTimeline(lastUserActivityAt = 100L)
        val reduced = reduceCompanionInteractionTimeline(
            current = initial,
            events = listOf(
                CompanionInteractionEvent(
                    kind = CompanionInteractionEventKind.OUTBOUND_GENERATED,
                    occurredAt = 200L,
                    contactId = "out-1",
                ),
                CompanionInteractionEvent(
                    kind = CompanionInteractionEventKind.OUTBOUND_SENT,
                    occurredAt = 210L,
                    contactId = "out-1",
                ),
                CompanionInteractionEvent(
                    kind = CompanionInteractionEventKind.OUTBOUND_DELIVERED,
                    occurredAt = 220L,
                    contactId = "out-1",
                ),
            ),
        )

        assertEquals(100L, reduced.lastUserActivityAt)
        assertEquals(220L, reduced.lastOutboundAt)
        assertEquals(CompanionOutboundStatus.DELIVERED, reduced.outboundContacts.single().status)
    }

    @Test
    fun `busy reply resolves latest outbound without punitive state`() {
        val delivered = reduceCompanionInteractionTimeline(
            current = CompanionInteractionTimeline(),
            events = listOf(
                CompanionInteractionEvent(
                    kind = CompanionInteractionEventKind.OUTBOUND_DELIVERED,
                    occurredAt = 100L,
                    contactId = "out-1",
                ),
            ),
        )
        val reduced = reduceCompanionInteractionTimeline(
            current = delivered,
            events = userReplyInteractionEvents("我现在在忙，晚点再说", 200L),
        )

        assertEquals(200L, reduced.lastUserActivityAt)
        assertEquals(200L, reduced.lastUserReplyAt)
        assertEquals(CompanionOutboundStatus.USER_BUSY, reduced.outboundContacts.single().status)
        assertEquals("user_busy", reduced.outboundContacts.single().result)
    }

    @Test
    fun `ordinary assistant clock is independent from outbound clock`() {
        val reduced = reduceCompanionInteractionTimeline(
            current = CompanionInteractionTimeline(),
            events = listOf(
                CompanionInteractionEvent(
                    kind = CompanionInteractionEventKind.ORDINARY_ASSISTANT,
                    occurredAt = 300L,
                    sourceMessageId = "reply-1",
                ),
            ),
        )

        assertEquals(300L, reduced.lastOrdinaryAssistantAt)
        assertNull(reduced.lastOutboundAt)
        assertNull(reduced.lastUserActivityAt)
    }

    @Test
    fun `opening a notification updates the matching outbound contact`() {
        val delivered = reduceCompanionInteractionTimeline(
            current = CompanionInteractionTimeline(),
            events = listOf(
                CompanionInteractionEvent(
                    kind = CompanionInteractionEventKind.OUTBOUND_DELIVERED,
                    occurredAt = 100L,
                    contactId = "out-1",
                ),
            ),
        )

        val opened = reduceCompanionInteractionTimeline(
            current = delivered,
            events = listOf(
                CompanionInteractionEvent(
                    kind = CompanionInteractionEventKind.OUTBOUND_OPENED,
                    occurredAt = 150L,
                    contactId = "out-1",
                ),
            ),
        )

        assertEquals(150L, opened.lastOpenedAt)
        assertEquals(150L, opened.outboundContacts.single().openedAt)
        assertEquals(CompanionOutboundStatus.OPENED, opened.outboundContacts.single().status)
    }

    @Test
    fun `completion reply resolves latest reminder without requiring a contact id`() {
        val delivered = reduceCompanionInteractionTimeline(
            current = CompanionInteractionTimeline(),
            events = listOf(
                CompanionInteractionEvent(
                    kind = CompanionInteractionEventKind.OUTBOUND_DELIVERED,
                    occurredAt = 100L,
                    contactId = "reminder-1",
                ),
            ),
        )

        val completed = reduceCompanionInteractionTimeline(
            current = delivered,
            events = userReplyInteractionEvents("已经做完了", 200L),
        )

        assertEquals(CompanionOutboundStatus.REMINDER_COMPLETED, completed.outboundContacts.single().status)
        assertEquals("user_reported_completion", completed.outboundContacts.single().result)
        assertEquals(200L, completed.outboundContacts.single().resolvedAt)
    }

    @Test
    fun `explicit topic change closes the latest outbound without penalty`() {
        val delivered = reduceCompanionInteractionTimeline(
            current = CompanionInteractionTimeline(),
            events = listOf(
                CompanionInteractionEvent(
                    kind = CompanionInteractionEventKind.OUTBOUND_DELIVERED,
                    occurredAt = 100L,
                    contactId = "out-1",
                ),
            ),
        )

        val changed = reduceCompanionInteractionTimeline(
            current = delivered,
            events = userReplyInteractionEvents("换个话题吧", 200L),
        )

        assertEquals(CompanionOutboundStatus.TOPIC_CHANGED, changed.outboundContacts.single().status)
        assertEquals("user_changed_topic", changed.outboundContacts.single().result)
    }
}
