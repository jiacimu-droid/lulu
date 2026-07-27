package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.companion.CompanionConcern
import me.rerere.rikkahub.data.companion.CompanionInteractionTimeline
import me.rerere.rikkahub.data.companion.CompanionOutboundContact
import me.rerere.rikkahub.data.companion.CompanionOutboundStatus
import me.rerere.rikkahub.data.companion.CompanionRelationshipState
import me.rerere.rikkahub.data.companion.CompanionSnapshot
import me.rerere.rikkahub.data.datastore.ProactiveMessageSetting
import me.rerere.rikkahub.data.model.AssistantInteractionProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionAutonomousPulsePlannerTest {
    private val setting = ProactiveMessageSetting(
        enabled = true,
        naturalScheduling = false,
        minIntervalMinutes = 30,
        maxIntervalMinutes = 90,
    )

    @Test
    fun `active companion work makes the next perception sooner`() {
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting,
                snapshot = CompanionSnapshot.empty("assistant-a").copy(
                    concerns = listOf(
                        CompanionConcern(
                            assistantId = "assistant-a",
                            subjectKey = "health:headache",
                            event = "用户说头疼",
                            goal = "稍后重新确认状态",
                            nextPerceptionAt = 1_699_999_900_000L,
                        ),
                    ),
                ),
                minutesSinceLastChat = 50,
            ),
        )

        assertEquals(18, plan.delayMinutes)
        assertTrue(plan.reason.contains("active_work"))
    }

    @Test
    fun `concern without a perception time does not cause repeated polling`() {
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting,
                snapshot = CompanionSnapshot.empty("assistant-a").copy(
                    concerns = listOf(
                        CompanionConcern(
                            assistantId = "assistant-a",
                            subjectKey = "relationship:general",
                            event = "记得用户最近压力很大",
                            goal = "保持关心但不机械轮询",
                            nextPerceptionAt = null,
                        ),
                    ),
                ),
                minutesSinceLastChat = 30,
                nowMillis = 1_700_000_000_000L,
            ),
        )

        assertEquals(60, plan.delayMinutes)
        assertTrue(plan.reason.contains("active=0"))
    }

    @Test
    fun `long silence schedules an earlier fixed perception without assuming attachment`() {
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting,
                snapshot = CompanionSnapshot.empty("assistant-a"),
                minutesSinceLastChat = 160,
            ),
        )

        assertEquals(12, plan.delayMinutes)
        assertTrue(plan.reason.contains("long_silence"))
        assertTrue(plan.reason.contains("active=0"))
    }

    @Test
    fun `future concern does not force repeated early polling`() {
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting,
                snapshot = CompanionSnapshot.empty("assistant-a").copy(
                    concerns = listOf(
                        CompanionConcern(
                            assistantId = "assistant-a",
                            subjectKey = "exam:result",
                            event = "等待结果",
                            goal = "结果公布后再确认",
                            nextPerceptionAt = 1_700_003_600_000L,
                        ),
                    ),
                ),
                minutesSinceLastChat = 30,
                nowMillis = 1_700_000_000_000L,
            ),
        )

        assertEquals(60, plan.delayMinutes)
        assertTrue(plan.reason.contains("active=0"))
    }

    @Test
    fun `hidden relationship tension no longer slows configured interaction rhythm`() {
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting,
                snapshot = CompanionSnapshot.empty("assistant-a").copy(
                    relationship = CompanionRelationshipState(unresolvedTension = 0.8f),
                ),
                minutesSinceLastChat = 160,
            ),
        )

        assertEquals(12, plan.delayMinutes)
        assertTrue(plan.reason.contains("long_silence"))
    }

    @Test
    fun `targeted trigger keeps normal background pulse away`() {
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting,
                snapshot = CompanionSnapshot.empty("assistant-a"),
                minutesSinceLastChat = 40,
                activeTargetedTriggerMillis = 1_700_000_000_000L,
                nowMillis = 1_699_996_760_000L,
            ),
        )

        assertEquals(84, plan.delayMinutes)
        assertTrue(plan.reason.contains("targeted_active"))
    }

    @Test
    fun `far targeted trigger is not truncated by normal maximum interval`() {
        val nowMillis = 1_700_000_000_000L
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting,
                snapshot = CompanionSnapshot.empty("assistant-a"),
                minutesSinceLastChat = 40,
                activeTargetedTriggerMillis = nowMillis + 6 * 60 * 60_000L,
                nowMillis = nowMillis,
            ),
        )

        assertEquals(390, plan.delayMinutes)
        assertTrue(plan.reason.contains("targeted_active"))
    }

    @Test
    fun `busy feedback lowers interruption frequency without changing relationship`() {
        val snapshot = CompanionSnapshot.empty("assistant-a").copy(
            interactionTimeline = CompanionInteractionTimeline(
                outboundContacts = listOf(
                    CompanionOutboundContact(
                        id = "out-1",
                        status = CompanionOutboundStatus.USER_BUSY,
                        generatedAt = 100L,
                        resolvedAt = 200L,
                    ),
                ),
            ),
        )

        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting.copy(naturalScheduling = true),
                snapshot = snapshot,
                minutesSinceLastChat = 180,
                nowMillis = 1_700_000_000_000L,
                interactionProfile = AssistantInteractionProfile(initiative = "会主动联系并关心用户"),
            ),
        )

        assertTrue(plan.delayMinutes in 180..360)
        assertTrue(plan.reason.contains("feedback=USER_BUSY"))
        assertEquals(0f, snapshot.relationship.closeness)
        assertEquals(0f, snapshot.relationship.unresolvedTension)
    }

    @Test
    fun `natural scheduling stays conservative when interaction is unspecified`() {
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting.copy(
                    naturalScheduling = true,
                    minIntervalMinutes = 1,
                    maxIntervalMinutes = 2,
                ),
                snapshot = CompanionSnapshot.empty("assistant-a"),
                minutesSinceLastChat = 180,
                nowMillis = 1_700_000_000_000L,
            ),
        )

        assertTrue(plan.delayMinutes in 60..120)
        assertTrue(plan.reason.contains("initiative=UNSPECIFIED"))
    }

    @Test
    fun `high initiative role gets frequent natural perception opportunities`() {
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting.copy(naturalScheduling = true),
                snapshot = CompanionSnapshot.empty("assistant-a"),
                minutesSinceLastChat = 60,
                nowMillis = 1_700_000_000_000L,
                interactionProfile = AssistantInteractionProfile(
                    initiative = "会主动联系并询问用户正在做什么。",
                    sharingDesire = "分享欲强，会主动分享自己的发现。",
                ),
            ),
        )

        assertTrue(plan.delayMinutes in 20..45)
        assertTrue(plan.reason.contains("initiative=HIGH"))
    }

    @Test
    fun `low initiative role receives sparse natural checks`() {
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting.copy(naturalScheduling = true),
                snapshot = CompanionSnapshot.empty("assistant-a"),
                minutesSinceLastChat = 200,
                nowMillis = 1_700_000_000_000L,
                interactionProfile = AssistantInteractionProfile(
                    initiative = "很少主动，通常等用户先提起话题。",
                ),
            ),
        )

        assertTrue(plan.delayMinutes in 180..360)
        assertTrue(plan.reason.contains("initiative=LOW"))
    }

    @Test
    fun `never initiate role keeps only a sparse maintenance pulse`() {
        val plan = CompanionAutonomousPulsePlanner.planNext(
            input = CompanionAutonomousPulseInput(
                setting = setting.copy(naturalScheduling = true),
                snapshot = CompanionSnapshot.empty("assistant-a"),
                minutesSinceLastChat = 600,
                nowMillis = 1_700_000_000_000L,
                interactionProfile = AssistantInteractionProfile(
                    initiative = "绝不主动联系，除非用户明确交代了到期责任。",
                ),
            ),
        )

        assertTrue(plan.delayMinutes in 360..720)
        assertTrue(plan.reason.contains("initiative=NEVER"))
    }
}
