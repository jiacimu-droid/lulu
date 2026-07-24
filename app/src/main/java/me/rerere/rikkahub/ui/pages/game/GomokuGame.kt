package me.rerere.rikkahub.ui.pages.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Refresh03
import kotlin.math.abs

private const val GOMOKU_SIZE = 15
private const val GOMOKU_CELLS = GOMOKU_SIZE * GOMOKU_SIZE
private const val USER_STONE = 1
private const val ROLE_STONE = 2

@Composable
internal fun GomokuGame(
    assistantName: String,
    onCompleted: (outcome: String, moves: Int, board: List<Int>) -> Unit,
) {
    var board by remember { mutableStateOf(List(GOMOKU_CELLS) { 0 }) }
    var result by remember { mutableStateOf<String?>(null) }
    var moveCount by remember { mutableIntStateOf(0) }

    fun reset() {
        board = List(GOMOKU_CELLS) { 0 }
        result = null
        moveCount = 0
    }

    fun finish(outcome: String, finalBoard: List<Int>, finalMoveCount: Int) {
        board = finalBoard
        moveCount = finalMoveCount
        result = outcome
        onCompleted(outcome, finalMoveCount, finalBoard)
    }

    fun choose(cell: Int) {
        if (result != null || board[cell] != 0) return

        val afterUser = board.toMutableList().also { it[cell] = USER_STONE }
        val userMoveCount = moveCount + 1
        if (gomokuWinner(afterUser, cell) == USER_STONE) {
            finish("用户胜", afterUser, userMoveCount)
            return
        }
        if (afterUser.none { it == 0 }) {
            finish("平局", afterUser, userMoveCount)
            return
        }

        val roleCell = chooseGomokuMove(afterUser, ROLE_STONE)
        val afterRole = afterUser.toMutableList().also { it[roleCell] = ROLE_STONE }
        val finalMoveCount = userMoveCount + 1
        when {
            gomokuWinner(afterRole, roleCell) == ROLE_STONE -> finish("角色胜", afterRole, finalMoveCount)
            afterRole.none { it == 0 } -> finish("平局", afterRole, finalMoveCount)
            else -> {
                board = afterRole
                moveCount = finalMoveCount
            }
        }
    }

    GameBody(
        title = "五子棋",
        subtitle = "你执黑子先手，$assistantName 执白子。棋步由本地规则引擎执行，角色只负责思考和回应。",
    ) {
        Text(
            text = result?.let { "本局结束：$it" } ?: "轮到你落子 · 已走 $moveCount 手",
            color = if (result == null) MaterialTheme.colorScheme.onSurfaceVariant else GameColors.accent,
            fontWeight = if (result == null) FontWeight.Normal else FontWeight.SemiBold,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFD8B77C),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                repeat(GOMOKU_SIZE) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        repeat(GOMOKU_SIZE) { column ->
                            val cell = row * GOMOKU_SIZE + column
                            val stone = board[cell]
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(2.dp))
                                    .clickable(enabled = result == null && stone == 0) { choose(cell) },
                                color = Color(0xFFE4C68E),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = when (stone) {
                                            USER_STONE -> "●"
                                            ROLE_STONE -> "○"
                                            else -> "·"
                                        },
                                        color = when (stone) {
                                            USER_STONE -> Color(0xFF201B18)
                                            ROLE_STONE -> Color.White
                                            else -> Color(0xFF856B45).copy(alpha = 0.48f)
                                        },
                                        fontSize = 12.sp,
                                        lineHeight = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            "角色会优先完成自己的五连，也会拦截你下一手就能形成的五连；中盘会围绕已有棋形进攻。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (result != null) {
            GameResultText(result.orEmpty())
            Button(onClick = ::reset, modifier = Modifier.fillMaxWidth()) {
                Icon(HugeIcons.Refresh03, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text("再来一局", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

internal fun chooseGomokuMove(board: List<Int>, roleStone: Int): Int {
    require(board.size == GOMOKU_CELLS) { "Gomoku board must be 15×15" }
    val userStone = if (roleStone == USER_STONE) ROLE_STONE else USER_STONE
    val open = board.indices.filter { board[it] == 0 }
    require(open.isNotEmpty()) { "No open gomoku cells" }

    if (board.all { it == 0 }) return gomokuIndex(GOMOKU_SIZE / 2, GOMOKU_SIZE / 2)

    open.firstOrNull { cell ->
        board.toMutableList().also { it[cell] = roleStone }.let { gomokuWinner(it, cell) == roleStone }
    }?.let { return it }

    open.firstOrNull { cell ->
        board.toMutableList().also { it[cell] = userStone }.let { gomokuWinner(it, cell) == userStone }
    }?.let { return it }

    val candidates = open.filter { cell ->
        val row = cell / GOMOKU_SIZE
        val column = cell % GOMOKU_SIZE
        (-2..2).any { dr ->
            (-2..2).any { dc ->
                if (dr == 0 && dc == 0) return@any false
                val nr = row + dr
                val nc = column + dc
                nr in 0 until GOMOKU_SIZE && nc in 0 until GOMOKU_SIZE && board[gomokuIndex(nr, nc)] != 0
            }
        }
    }.ifEmpty { open }

    return candidates.maxByOrNull { cell ->
        val attack = gomokuPlacementScore(board, cell, roleStone)
        val defence = gomokuPlacementScore(board, cell, userStone)
        val row = cell / GOMOKU_SIZE
        val column = cell % GOMOKU_SIZE
        val centerBias = GOMOKU_SIZE - abs(row - GOMOKU_SIZE / 2) - abs(column - GOMOKU_SIZE / 2)
        attack * 2 + defence + centerBias
    } ?: open.first()
}

internal fun gomokuWinner(board: List<Int>, lastMove: Int): Int? {
    if (lastMove !in board.indices) return null
    val stone = board[lastMove]
    if (stone == 0) return null
    val row = lastMove / GOMOKU_SIZE
    val column = lastMove % GOMOKU_SIZE
    return stone.takeIf { candidate ->
        GOMOKU_DIRECTIONS.any { (dr, dc) ->
            1 + countDirection(board, row, column, dr, dc, candidate) +
                countDirection(board, row, column, -dr, -dc, candidate) >= 5
        }
    }
}

private fun gomokuPlacementScore(board: List<Int>, cell: Int, stone: Int): Int {
    val row = cell / GOMOKU_SIZE
    val column = cell % GOMOKU_SIZE
    return GOMOKU_DIRECTIONS.sumOf { (dr, dc) ->
        val forward = countDirection(board, row, column, dr, dc, stone)
        val backward = countDirection(board, row, column, -dr, -dc, stone)
        val length = 1 + forward + backward
        val openEnds = listOf(
            row + (forward + 1) * dr to column + (forward + 1) * dc,
            row - (backward + 1) * dr to column - (backward + 1) * dc,
        ).count { (r, c) ->
            r in 0 until GOMOKU_SIZE && c in 0 until GOMOKU_SIZE && board[gomokuIndex(r, c)] == 0
        }
        when {
            length >= 5 -> 100_000
            length == 4 && openEnds == 2 -> 18_000
            length == 4 -> 7_000
            length == 3 && openEnds == 2 -> 2_000
            length == 3 -> 600
            length == 2 && openEnds == 2 -> 180
            else -> length * 12 + openEnds * 5
        }
    }
}

private fun countDirection(
    board: List<Int>,
    row: Int,
    column: Int,
    rowDelta: Int,
    columnDelta: Int,
    stone: Int,
): Int {
    var count = 0
    var currentRow = row + rowDelta
    var currentColumn = column + columnDelta
    while (
        currentRow in 0 until GOMOKU_SIZE &&
        currentColumn in 0 until GOMOKU_SIZE &&
        board[gomokuIndex(currentRow, currentColumn)] == stone
    ) {
        count += 1
        currentRow += rowDelta
        currentColumn += columnDelta
    }
    return count
}

private fun gomokuIndex(row: Int, column: Int): Int = row * GOMOKU_SIZE + column

private val GOMOKU_DIRECTIONS = listOf(
    0 to 1,
    1 to 0,
    1 to 1,
    1 to -1,
)
