package me.rerere.rikkahub.ui.pages.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionGameEnginesTest {
    @Test
    fun `gomoku detects horizontal five`() {
        val board = MutableList(15 * 15) { 0 }
        (3..7).forEach { column -> board[index(7, column)] = 1 }

        assertEquals(1, gomokuWinner(board, index(7, 7)))
    }

    @Test
    fun `gomoku detects diagonal five`() {
        val board = MutableList(15 * 15) { 0 }
        repeat(5) { offset -> board[index(4 + offset, 6 + offset)] = 2 }

        assertEquals(2, gomokuWinner(board, index(8, 10)))
    }

    @Test
    fun `gomoku role takes an immediate winning move`() {
        val board = MutableList(15 * 15) { 0 }
        (4..7).forEach { column -> board[index(5, column)] = 2 }
        board[index(8, 8)] = 1

        val move = chooseGomokuMove(board, roleStone = 2)

        assertTrue(move == index(5, 3) || move == index(5, 8))
        val completed = board.toMutableList().also { it[move] = 2 }
        assertEquals(2, gomokuWinner(completed, move))
    }

    @Test
    fun `gomoku role blocks an immediate user win`() {
        val board = MutableList(15 * 15) { 0 }
        (5..8).forEach { column -> board[index(9, column)] = 1 }
        board[index(7, 7)] = 2

        val move = chooseGomokuMove(board, roleStone = 2)

        assertTrue(move == index(9, 4) || move == index(9, 9))
    }

    @Test
    fun `yacht scoring covers the main combinations`() {
        assertEquals(50, yachtScore(YachtCategory.YACHT, listOf(6, 6, 6, 6, 6)))
        assertEquals(18, yachtScore(YachtCategory.FULL_HOUSE, listOf(3, 3, 3, 4, 4)))
        assertEquals(15, yachtScore(YachtCategory.SMALL_STRAIGHT, listOf(1, 2, 3, 4, 6)))
        assertEquals(30, yachtScore(YachtCategory.LARGE_STRAIGHT, listOf(2, 3, 4, 5, 6)))
        assertEquals(21, yachtScore(YachtCategory.FOUR_OF_A_KIND, listOf(4, 4, 4, 4, 5)))
    }

    @Test
    fun `role yacht turn never reuses a filled category`() {
        val onlyCategoryLeft = YachtCategory.YACHT
        val used = YachtCategory.entries.filterNot { it == onlyCategoryLeft }.toSet()

        val turn = chooseRoleYachtTurn(used)

        assertEquals(onlyCategoryLeft, turn.category)
        assertEquals(5, turn.dice.size)
        assertTrue(turn.dice.all { it in 1..6 })
        assertEquals(yachtScore(turn.category, turn.dice), turn.score)
    }

    private fun index(row: Int, column: Int): Int = row * 15 + column
}
