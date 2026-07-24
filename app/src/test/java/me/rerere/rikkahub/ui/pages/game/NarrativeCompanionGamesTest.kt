package me.rerere.rikkahub.ui.pages.game

import org.junit.Assert.assertEquals
import org.junit.Test

class NarrativeCompanionGamesTest {
    @Test
    fun adventureChoicesAreParsedFromRequiredFormat() {
        assertEquals(
            listOf("检查钟楼", "寻找研究员", "立刻撤退"),
            parseAdventureChoices("场景继续。\n选项：检查钟楼｜寻找研究员｜立刻撤退"),
        )
    }

    @Test
    fun adventureChoicesAreLimitedToThree() {
        assertEquals(
            listOf("A", "B", "C"),
            parseAdventureChoices("选项：A|B|C|D"),
        )
    }

    @Test
    fun newGameWireNamesRemainStable() {
        assertEquals(QuickCompanionGame.TURTLE_SOUP, QuickCompanionGame.fromWireName("turtle_soup"))
        assertEquals(QuickCompanionGame.RAPPORT_QUIZ, QuickCompanionGame.fromWireName("rapport_quiz"))
        assertEquals(QuickCompanionGame.ROLEPLAY_ADVENTURE, QuickCompanionGame.fromWireName("trpg"))
    }
}
