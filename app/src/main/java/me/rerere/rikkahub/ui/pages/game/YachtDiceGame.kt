package me.rerere.rikkahub.ui.pages.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Refresh03
import kotlin.random.Random

@Composable
internal fun YachtDiceGame(
    assistantName: String,
    onRoundCompleted: (
        round: Int,
        userCategory: String,
        roleCategory: String,
        userScore: Int,
        roleScore: Int,
        outcome: String,
        userTotal: Int,
        roleTotal: Int,
    ) -> Unit,
) {
    var dice by remember { mutableStateOf(List(5) { 0 }) }
    var heldIndices by remember { mutableStateOf(emptySet<Int>()) }
    var rollsUsed by remember { mutableIntStateOf(0) }
    var round by remember { mutableIntStateOf(1) }
    var userScores by remember { mutableStateOf(emptyMap<YachtCategory, Int>()) }
    var roleScores by remember { mutableStateOf(emptyMap<YachtCategory, Int>()) }
    var roundMessage by remember { mutableStateOf("先掷骰子，再选择一个还没使用的计分项。") }

    val gameOver = userScores.size == YachtCategory.entries.size
    val userTotal = userScores.values.sum()
    val roleTotal = roleScores.values.sum()

    fun resetTurn() {
        dice = List(5) { 0 }
        heldIndices = emptySet()
        rollsUsed = 0
    }

    fun resetGame() {
        userScores = emptyMap()
        roleScores = emptyMap()
        round = 1
        roundMessage = "先掷骰子，再选择一个还没使用的计分项。"
        resetTurn()
    }

    fun rollDice() {
        if (gameOver || rollsUsed >= 3) return
        dice = dice.mapIndexed { index, value ->
            if (index in heldIndices && value != 0) value else Random.nextInt(1, 7)
        }
        rollsUsed += 1
        roundMessage = if (rollsUsed < 3) {
            "点骰子可以保留，再掷 ${3 - rollsUsed} 次；也可以现在计分。"
        } else {
            "三次机会已经用完，请选择计分项。"
        }
    }

    fun score(category: YachtCategory) {
        if (gameOver || rollsUsed == 0 || category in userScores) return
        val userScore = yachtScore(category, dice)
        val roleTurn = chooseRoleYachtTurn(roleScores.keys)
        val nextUserScores = userScores + (category to userScore)
        val nextRoleScores = roleScores + (roleTurn.category to roleTurn.score)
        val nextUserTotal = nextUserScores.values.sum()
        val nextRoleTotal = nextRoleScores.values.sum()
        val outcome = when {
            userScore > roleTurn.score -> "本轮用户领先"
            userScore < roleTurn.score -> "本轮角色领先"
            else -> "本轮同分"
        }

        userScores = nextUserScores
        roleScores = nextRoleScores
        roundMessage = "你把 ${category.label} 记为 $userScore 分；$assistantName 把 ${roleTurn.category.label} 记为 ${roleTurn.score} 分。"
        onRoundCompleted(
            round,
            category.label,
            roleTurn.category.label,
            userScore,
            roleTurn.score,
            outcome,
            nextUserTotal,
            nextRoleTotal,
        )
        round += 1
        resetTurn()
    }

    GameBody(
        title = "快艇骰子",
        subtitle = "五颗骰子，每轮最多掷三次。点骰子保留，再把结果填进一个计分项；你和 $assistantName 各自完成整张计分表。",
    ) {
        YachtScoreSummary(
            round = round.coerceAtMost(YachtCategory.entries.size),
            userTotal = userTotal,
            roleTotal = roleTotal,
            assistantName = assistantName,
        )

        if (!gameOver) {
            Text(
                "第 ${round.coerceAtMost(YachtCategory.entries.size)} / ${YachtCategory.entries.size} 轮 · 已掷 $rollsUsed / 3 次",
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                dice.forEachIndexed { index, value ->
                    val held = index in heldIndices
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = rollsUsed > 0 && rollsUsed < 3) {
                                heldIndices = if (held) heldIndices - index else heldIndices + index
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = if (held) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        tonalElevation = if (held) 4.dp else 1.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (value == 0) "—" else value.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            Text(
                if (heldIndices.isEmpty()) "没有保留骰子" else "已保留第 ${heldIndices.sorted().joinToString("、") { (it + 1).toString() }} 颗骰子",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = ::rollDice,
                enabled = rollsUsed < 3,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(if (rollsUsed == 0) HugeIcons.Play else HugeIcons.Refresh03, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (rollsUsed == 0) "掷骰子" else "重掷未保留骰子")
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        ) {
            Text(roundMessage, modifier = Modifier.padding(12.dp))
        }

        YachtScoreCard(
            dice = dice,
            rollsUsed = rollsUsed,
            userScores = userScores,
            roleScores = roleScores,
            assistantName = assistantName,
            enabled = !gameOver,
            onScore = ::score,
        )

        if (gameOver) {
            val finalOutcome = when {
                userTotal > roleTotal -> "用户胜"
                userTotal < roleTotal -> "角色胜"
                else -> "平局"
            }
            GameResultText("整局结束：$finalOutcome · 你 $userTotal 分，$assistantName $roleTotal 分")
            Button(onClick = ::resetGame, modifier = Modifier.fillMaxWidth()) {
                Icon(HugeIcons.Refresh03, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("重新开一局")
            }
        }
    }
}

@Composable
private fun YachtScoreSummary(
    round: Int,
    userTotal: Int,
    roleTotal: Int,
    assistantName: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        YachtTotalTile("轮次", "$round/${YachtCategory.entries.size}", Modifier.weight(1f))
        YachtTotalTile("你", "$userTotal 分", Modifier.weight(1f))
        YachtTotalTile(assistantName, "$roleTotal 分", Modifier.weight(1f))
    }
}

@Composable
private fun YachtTotalTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(value, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun YachtScoreCard(
    dice: List<Int>,
    rollsUsed: Int,
    userScores: Map<YachtCategory, Int>,
    roleScores: Map<YachtCategory, Int>,
    assistantName: String,
    enabled: Boolean,
    onScore: (YachtCategory) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("计分项", modifier = Modifier.weight(1.4f), fontWeight = FontWeight.SemiBold)
            Text("你", modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
            Text(assistantName, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center, maxLines = 1)
        }
        YachtCategory.entries.forEach { category ->
            val userScore = userScores[category]
            val roleScore = roleScores[category]
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Column(Modifier.weight(1.4f)) {
                        Text(category.label, fontWeight = FontWeight.Medium)
                        Text(category.hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (userScore != null) {
                        Text(userScore.toString(), modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    } else {
                        OutlinedButton(
                            onClick = { onScore(category) },
                            enabled = enabled && rollsUsed > 0,
                            modifier = Modifier.weight(0.7f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Text(if (rollsUsed > 0) yachtScore(category, dice).toString() else "—")
                        }
                    }
                    Text(roleScore?.toString() ?: "—", modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

internal fun yachtScore(category: YachtCategory, dice: List<Int>): Int {
    if (dice.size != 5 || dice.any { it !in 1..6 }) return 0
    val counts = dice.groupingBy { it }.eachCount()
    val sortedCounts = counts.values.sorted()
    val unique = dice.toSet()
    return when (category) {
        YachtCategory.ONES -> dice.filter { it == 1 }.sum()
        YachtCategory.TWOS -> dice.filter { it == 2 }.sum()
        YachtCategory.THREES -> dice.filter { it == 3 }.sum()
        YachtCategory.FOURS -> dice.filter { it == 4 }.sum()
        YachtCategory.FIVES -> dice.filter { it == 5 }.sum()
        YachtCategory.SIXES -> dice.filter { it == 6 }.sum()
        YachtCategory.CHOICE -> dice.sum()
        YachtCategory.FOUR_OF_A_KIND -> if (counts.values.any { it >= 4 }) dice.sum() else 0
        YachtCategory.FULL_HOUSE -> if (sortedCounts == listOf(2, 3)) dice.sum() else 0
        YachtCategory.SMALL_STRAIGHT -> if (
            setOf(1, 2, 3, 4).all(unique::contains) ||
            setOf(2, 3, 4, 5).all(unique::contains) ||
            setOf(3, 4, 5, 6).all(unique::contains)
        ) 15 else 0
        YachtCategory.LARGE_STRAIGHT -> if (
            unique == setOf(1, 2, 3, 4, 5) || unique == setOf(2, 3, 4, 5, 6)
        ) 30 else 0
        YachtCategory.YACHT -> if (counts.values.any { it == 5 }) 50 else 0
    }
}

internal fun chooseRoleYachtTurn(usedCategories: Set<YachtCategory>): RoleYachtTurn {
    val available = YachtCategory.entries.filterNot(usedCategories::contains)
    require(available.isNotEmpty()) { "No yacht categories left" }
    return List(8) {
        val dice = simulateRoleDice()
        val category = available.maxByOrNull { yachtScore(it, dice) } ?: available.first()
        RoleYachtTurn(dice, category, yachtScore(category, dice))
    }.maxByOrNull { it.score } ?: RoleYachtTurn(List(5) { 1 }, available.first(), yachtScore(available.first(), List(5) { 1 }))
}

private fun simulateRoleDice(): List<Int> {
    var dice = List(5) { Random.nextInt(1, 7) }
    repeat(2) {
        val held = chooseRoleHeldIndices(dice)
        dice = dice.mapIndexed { index, value -> if (index in held) value else Random.nextInt(1, 7) }
    }
    return dice
}

private fun chooseRoleHeldIndices(dice: List<Int>): Set<Int> {
    val counts = dice.groupingBy { it }.eachCount()
    val repeatedValue = counts.maxByOrNull { it.value }?.takeIf { it.value >= 2 }?.key
    if (repeatedValue != null) {
        return dice.indices.filterTo(mutableSetOf()) { dice[it] == repeatedValue }
    }

    val unique = dice.toSet()
    val bestStraight = listOf(
        setOf(1, 2, 3, 4),
        setOf(2, 3, 4, 5),
        setOf(3, 4, 5, 6),
    ).maxByOrNull { target -> target.count(unique::contains) }
    if (bestStraight != null && bestStraight.count(unique::contains) >= 3) {
        val keptValues = mutableSetOf<Int>()
        return dice.indices.filterTo(mutableSetOf()) { index ->
            dice[index] in bestStraight && keptValues.add(dice[index])
        }
    }
    return dice.indices.filterTo(mutableSetOf()) { dice[it] >= 5 }
}

internal data class RoleYachtTurn(
    val dice: List<Int>,
    val category: YachtCategory,
    val score: Int,
)

internal enum class YachtCategory(
    val label: String,
    val hint: String,
) {
    ONES("一", "所有 1 点相加"),
    TWOS("二", "所有 2 点相加"),
    THREES("三", "所有 3 点相加"),
    FOURS("四", "所有 4 点相加"),
    FIVES("五", "所有 5 点相加"),
    SIXES("六", "所有 6 点相加"),
    CHOICE("全选", "五颗骰子点数总和"),
    FOUR_OF_A_KIND("四条", "至少四颗相同，取总和"),
    FULL_HOUSE("葫芦", "三颗相同加两颗相同"),
    SMALL_STRAIGHT("小顺", "任意连续四个点数，15 分"),
    LARGE_STRAIGHT("大顺", "连续五个点数，30 分"),
    YACHT("快艇", "五颗完全相同，50 分"),
}
