package me.rerere.rikkahub.ui.pages.study

import me.rerere.rikkahub.data.study.StudyDrawResult
import me.rerere.rikkahub.data.study.StudyRarity
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawRevealFlowTest {
    @Test
    fun rainbowResultStartsWithVideoBeforeCardReveal() {
        val results = listOf(draw(StudyRarity.Rainbow), draw(StudyRarity.Normal))

        val start = DrawRevealFlow.start(results)

        assertEquals(DrawRevealPhase.RainbowVideo, start.phase)
        assertEquals(0, start.index)

        val revealed = DrawRevealFlow.videoFinished(start, results)
        assertEquals(DrawRevealPhase.Card, revealed.phase)
        assertEquals(0, revealed.index)
    }

    @Test
    fun nextMovesToFollowingCardOrVideoGate() {
        val results = listOf(draw(StudyRarity.Normal), draw(StudyRarity.Rainbow), draw(StudyRarity.Rare))

        val start = DrawRevealFlow.start(results)
        assertEquals(DrawRevealPhase.Card, start.phase)
        assertEquals(0, start.index)

        val rainbowGate = DrawRevealFlow.next(start, results)
        assertEquals(DrawRevealPhase.RainbowVideo, rainbowGate.phase)
        assertEquals(1, rainbowGate.index)

        val rainbowCard = DrawRevealFlow.videoFinished(rainbowGate, results)
        val rareCard = DrawRevealFlow.next(rainbowCard, results)
        assertEquals(DrawRevealPhase.Card, rareCard.phase)
        assertEquals(2, rareCard.index)
    }

    @Test
    fun skipShowsSummary() {
        val results = listOf(draw(StudyRarity.Rainbow), draw(StudyRarity.Epic))

        val skipped = DrawRevealFlow.skip(DrawRevealFlow.start(results))

        assertEquals(DrawRevealPhase.Summary, skipped.phase)
        assertEquals(results.lastIndex, skipped.index)
    }

    private fun draw(rarity: StudyRarity) = StudyDrawResult(
        rarity = rarity,
        fragmentKey = rarity.name,
        title = "${rarity.label}碎片",
    )
}
