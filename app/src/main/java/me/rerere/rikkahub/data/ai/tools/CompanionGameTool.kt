package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.random.Random

fun createCompanionGameTool(
    assistantId: String,
    clockMillis: () -> Long = System::currentTimeMillis,
): Tool = Tool(
    name = "play_companion_game",
    description = "Play one real app-local mini-game as the configured character. Available games are yacht_dice and gomoku. The engine returns actual moves and results. Never claim a game happened unless this tool succeeds.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("game") {
                    put("type", JsonPrimitive("string"))
                    put("enum", buildJsonArray {
                        CompanionGameKind.entries.forEach { add(JsonPrimitive(it.wireName)) }
                    })
                    put("description", JsonPrimitive("The mini-game the character chooses to play."))
                }
                putJsonObject("strategy") {
                    put("type", JsonPrimitive("string"))
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("curious"))
                        add(JsonPrimitive("careful"))
                        add(JsonPrimitive("bold"))
                    })
                    put("description", JsonPrimitive("The character's chosen play style."))
                }
            },
        )
    },
    execute = { input ->
        val nowMillis = clockMillis()
        val game = input.jsonObject["game"]?.jsonPrimitive?.contentOrNull
            ?.lowercase()
            ?.let { wireName -> CompanionGameKind.entries.firstOrNull { it.wireName == wireName } }
            ?: CompanionGameKind.GOMOKU
        val strategy = input.jsonObject["strategy"]?.jsonPrimitive?.contentOrNull
            ?.lowercase()
            ?.takeIf { value -> value in CompanionGameStrategy.entries.map { it.wireName } }
            ?: CompanionGameStrategy.CURIOUS.wireName
        val selectedStrategy = CompanionGameStrategy.entries.first { it.wireName == strategy }
        val seed = (assistantId.hashCode() * 31) xor nowMillis.hashCode()
        val sessionId = UUID.nameUUIDFromBytes(
            "$assistantId|${game.wireName}|$nowMillis".toByteArray(StandardCharsets.UTF_8),
        ).toString()
        val details = when (game) {
            CompanionGameKind.YACHT_DICE -> {
                val result = playCompanionDiceDuel(seed)
                buildJsonObject {
                    put("role_roll", result.roleMove.toInt())
                    put("opponent_roll", result.opponentMove.toInt())
                    put("outcome", result.outcome)
                    put("result", result.resultText)
                }
            }
            CompanionGameKind.GOMOKU -> {
                val result = playCompanionGomoku(selectedStrategy, seed)
                buildJsonObject {
                    put("outcome", result.outcome)
                    put("result", result.resultText)
                    put("moves", buildJsonArray {
                        result.moves.forEach { move ->
                            add(buildJsonObject {
                                put("player", move.player)
                                put("cell", move.cell)
                            })
                        }
                    })
                }
            }
        }
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("game", game.wireName)
                    put("session_id", sessionId)
                    put("played_at", nowMillis)
                    details.forEach { (key, value) -> put(key, value) }
                }.toString(),
            ),
        )
    },
)

internal enum class CompanionGameKind(val wireName: String) {
    YACHT_DICE("yacht_dice"),
    GOMOKU("gomoku"),
}

internal enum class CompanionGameStrategy(val wireName: String) {
    CURIOUS("curious"),
    CAREFUL("careful"),
    BOLD("bold"),
}

internal data class CompanionSimpleGameResult(
    val roleMove: String,
    val opponentMove: String,
    val outcome: String,
    val resultText: String,
)

internal data class CompanionGomokuMove(
    val player: String,
    val cell: Int,
)

internal data class CompanionGomokuResult(
    val outcome: String,
    val moves: List<CompanionGomokuMove>,
    val resultText: String,
)

internal fun playCompanionDiceDuel(seed: Int): CompanionSimpleGameResult {
    val random = Random(seed)
    val roleRoll = random.nextInt(1, 7)
    val opponentRoll = random.nextInt(1, 7)
    val outcome = when {
        roleRoll > opponentRoll -> "win"
        roleRoll < opponentRoll -> "lose"
        else -> "draw"
    }
    return CompanionSimpleGameResult(
        roleMove = roleRoll.toString(),
        opponentMove = opponentRoll.toString(),
        outcome = outcome,
        resultText = "角色掷出 $roleRoll，对手掷出 $opponentRoll，本局结果为 $outcome。",
    )
}

internal fun playCompanionGomoku(
    strategy: CompanionGameStrategy,
    seed: Int,
): CompanionGomokuResult {
    val random = Random(seed)
    val board = MutableList<String?>(81) { null }
    val moves = mutableListOf<CompanionGomokuMove>()
    var current = "role"
    while (moves.size < 81 && gomokuWinner(board) == null) {
        val open = board.indices.filter { board[it] == null }
        val mark = if (current == "role") "X" else "O"
        val opponentMark = if (mark == "X") "O" else "X"
        val winning = open.firstOrNull { cell ->
            board.toMutableList().also { it[cell] = mark }.let(::gomokuWinner) == mark
        }
        val blocking = open.firstOrNull { cell ->
            board.toMutableList().also { it[cell] = opponentMark }.let(::gomokuWinner) == opponentMark
        }
        val cell = when {
            winning != null -> winning
            strategy == CompanionGameStrategy.CAREFUL && blocking != null -> blocking
            40 in open -> 40
            blocking != null -> blocking
            else -> open.minByOrNull { candidate ->
                val row = candidate / 9
                val column = candidate % 9
                kotlin.math.abs(row - 4) + kotlin.math.abs(column - 4) + random.nextInt(3)
            } ?: open.random(random)
        }
        board[cell] = mark
        moves += CompanionGomokuMove(current, cell)
        current = if (current == "role") "opponent" else "role"
    }
    val winner = gomokuWinner(board)
    val outcome = when (winner) {
        "X" -> "win"
        "O" -> "lose"
        else -> "draw"
    }
    return CompanionGomokuResult(
        outcome = outcome,
        moves = moves,
        resultText = "五子棋完整结束，本局结果为 $outcome。",
    )
}

internal fun gomokuWinner(board: List<String?>): String? =
    GOMOKU_LINES.firstNotNullOfOrNull { line ->
        board[line[0]]?.takeIf { mark -> line.all { board[it] == mark } }
    }

private val GOMOKU_LINES: List<List<Int>> = buildList {
    for (row in 0 until 9) for (column in 0..4) add((0..4).map { row * 9 + column + it })
    for (column in 0 until 9) for (row in 0..4) add((0..4).map { (row + it) * 9 + column })
    for (row in 0..4) for (column in 0..4) add((0..4).map { (row + it) * 9 + column + it })
    for (row in 0..4) for (column in 4 until 9) add((0..4).map { (row + it) * 9 + column - it })
}
