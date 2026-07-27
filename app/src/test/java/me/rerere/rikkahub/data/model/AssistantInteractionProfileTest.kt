package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantInteractionProfileTest {
    @Test
    fun `blank interaction remains backward compatible`() {
        val profile = AssistantInteractionProfile()

        assertTrue(profile.isBlank())
        assertEquals(AssistantInitiativeLevel.UNSPECIFIED, profile.initiativeLevel())
        assertEquals("", profile.toPromptContext())
    }

    @Test
    fun `explicit never initiative wins over caring language`() {
        val profile = AssistantInteractionProfile(
            initiative = "绝不主动联系，只在用户先开口时回应。",
            responsibility = "仍会认真完成用户明确交代的责任。",
        )

        assertEquals(AssistantInitiativeLevel.NEVER, profile.initiativeLevel())
    }

    @Test
    fun `low initiative wins over sharing or responsibility phrases`() {
        val profile = AssistantInteractionProfile(
            initiative = "很少主动，通常等待用户先开口。",
            sharingDesire = "偶尔会主动分享重要发现。",
            responsibility = "会主动监督已经答应的事项。",
        )

        assertEquals(AssistantInitiativeLevel.LOW, profile.initiativeLevel())
    }

    @Test
    fun `normal initiative wording is not promoted to high`() {
        val profile = AssistantInteractionProfile(
            initiative = "通常会主动联系，但只在有合适话题或明确关心理由时开口。",
        )

        assertEquals(AssistantInitiativeLevel.NORMAL, profile.initiativeLevel())
    }

    @Test
    fun `active caring profile becomes high initiative`() {
        val profile = AssistantInteractionProfile(
            initiative = "会主动联系并关心用户现在的状态。",
            sharingDesire = "分享欲强，会主动分享自己的日常。",
        )

        assertEquals(AssistantInitiativeLevel.HIGH, profile.initiativeLevel())
        val prompt = profile.toPromptContext()
        assertTrue(prompt.contains("<interaction_profile"))
        assertTrue(prompt.contains("主动意愿"))
        assertTrue(prompt.contains("分享欲"))
        assertTrue(prompt.contains("用户确认过"))
    }
}
