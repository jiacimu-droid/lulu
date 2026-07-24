package me.rerere.rikkahub.ui.pages.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.CustomColors
import kotlin.random.Random

internal typealias CompanionNarrativeRequest = (
    facts: String,
    instruction: String,
    onResult: (String) -> Unit,
) -> Unit

internal typealias SharedGameCheckpoint = (
    title: String,
    summary: String,
    detailsJson: String,
) -> Unit

private data class TurtleSoupCase(
    val title: String,
    val surface: String,
    val truth: String,
    val hints: List<String>,
)

private val TURTLE_SOUP_CASES = listOf(
    TurtleSoupCase(
        title = "没有响起的闹钟",
        surface = "女孩醒来后发现闹钟没有响，却立刻知道有人进过她的房间。为什么？",
        truth = "女孩失明。她睡前把会发声的机械闹钟放在门后当作简易警报，门被推开后闹钟被撞倒并停走，所以早晨没有响。她因此判断夜里有人开过门。",
        hints = listOf("女孩看不见。", "闹钟除了报时，还被当成了别的东西。", "门的位置非常关键。"),
    ),
    TurtleSoupCase(
        title = "空白来信",
        surface = "男人每天都收到一封空白信。某天信没有寄到，他反而马上报警。为什么？",
        truth = "那些空白信是被绑架的家人约定的平安信号。只要每天按时寄出，就代表仍然安全；断信代表对方已经失去行动能力或遭遇危险，所以男人报警。",
        hints = listOf("空白并不代表没有信息。", "寄信的人处于危险中。", "按时收到本身就是暗号。"),
    ),
    TurtleSoupCase(
        title = "只点一根蜡烛",
        surface = "停电后，旅馆老板只点了一根蜡烛，所有住客却都安全离开了。为什么？",
        truth = "旅馆位于海边，那根蜡烛放在备用灯塔的透镜后，形成了临时航标。住客其实是停靠在附近船上的旅客，看到航标后安全驶离危险水域。",
        hints = listOf("住客不一定住在普通房间里。", "蜡烛的位置比数量重要。", "旅馆与海有关。"),
    ),
)

@Composable
internal fun TurtleSoupGame(
    assistantName: String,
    request: CompanionNarrativeRequest,
    checkpoint: SharedGameCheckpoint,
) {
    var caseIndex by remember { mutableIntStateOf(0) }
    var question by remember { mutableStateOf("") }
    var history by remember(caseIndex) { mutableStateOf(emptyList<Pair<String, String>>()) }
    var hintIndex by remember(caseIndex) { mutableIntStateOf(0) }
    var revealed by remember(caseIndex) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val soup = TURTLE_SOUP_CASES[caseIndex]

    GameBody(
        title = "海龟汤 · ${soup.title}",
        subtitle = "$assistantName 是主持人。真相由程序固定，角色只能依据真相回答，不会临时改题。",
    ) {
        GameResultText(soup.surface)
        if (history.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                history.takeLast(6).forEach { (ask, answer) ->
                    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("你：$ask", fontWeight = FontWeight.Medium)
                            Text("$assistantName：$answer", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("问一个只能用是/不是/无关回答的问题") },
            enabled = !busy && !revealed,
            minLines = 2,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val ask = question.trim()
                    if (ask.isEmpty()) return@Button
                    busy = true
                    question = ""
                    request(
                        """
                        海龟汤题面：${soup.surface}
                        锁定真相：${soup.truth}
                        玩家问题：$ask
                        已有问答：${history.joinToString("；") { "${it.first}→${it.second}" }}
                        """.trimIndent(),
                        "你是海龟汤主持人。必须严格依据锁定真相判断，先明确回答“是”“不是”“无关”或“部分正确”，再用符合角色人设的一句短话回应。不得泄露完整真相，不得新增或修改设定。",
                    ) { answer ->
                        history = history + (ask to answer)
                        busy = false
                    }
                },
                enabled = question.isNotBlank() && !busy && !revealed,
                modifier = Modifier.weight(1f),
            ) { Text(if (busy) "思考中" else "提问") }
            OutlinedButton(
                onClick = { hintIndex = (hintIndex + 1).coerceAtMost(soup.hints.size) },
                enabled = hintIndex < soup.hints.size && !revealed,
                modifier = Modifier.weight(1f),
            ) { Text("提示 ${hintIndex}/${soup.hints.size}") }
        }
        if (hintIndex > 0) {
            Text("提示：${soup.hints[hintIndex - 1]}", color = MaterialTheme.colorScheme.primary)
        }
        OutlinedButton(
            onClick = {
                revealed = true
                checkpoint(
                    "一起玩海龟汤：${soup.title}",
                    "用户与${assistantName}完成海龟汤《${soup.title}》，共提问 ${history.size} 次，使用提示 $hintIndex 次。",
                    "{\"game\":\"turtle_soup\",\"case\":\"${escapeJson(soup.title)}\",\"questions\":${history.size},\"hints\":$hintIndex}",
                )
            },
            enabled = !revealed,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("揭晓汤底") }
        if (revealed) {
            GameResultText("汤底：${soup.truth}")
            Button(
                onClick = {
                    caseIndex = (caseIndex + 1) % TURTLE_SOUP_CASES.size
                    question = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("下一碗汤") }
        }
    }
}

private val RAPPORT_QUESTIONS = listOf(
    "如果我今天情绪很差，我更希望你怎么陪我？",
    "我们突然多出一个完全空闲的下午，最可能一起做什么？",
    "我嘴上说“没事”的时候，真正需要的是什么？",
    "如果只能保存一段共同回忆，你觉得我会留下哪一段？",
    "我遇到压力时，更像是立刻解决、先逃一会儿，还是找人说说？",
    "哪一种小事最容易让我感受到被在意？",
    "我们意见不一致时，你觉得我最在乎的是什么？",
    "如果给我们的关系取一个天气，你会选什么？为什么？",
)

@Composable
internal fun RapportQuizGame(
    assistantName: String,
    request: CompanionNarrativeRequest,
    checkpoint: SharedGameCheckpoint,
) {
    var index by remember { mutableIntStateOf(0) }
    var userAnswer by remember { mutableStateOf("") }
    var roleAnswer by remember { mutableStateOf<String?>(null) }
    var score by remember { mutableIntStateOf(0) }
    var answered by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val question = RAPPORT_QUESTIONS[index % RAPPORT_QUESTIONS.size]

    GameBody(
        title = "默契问答 · 第 ${answered + 1} 题",
        subtitle = "你先写下答案，$assistantName 会读取自己的人设、记忆和对你的印象后独立作答。",
    ) {
        GameResultText(question)
        Text("当前默契：$score / $answered", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = userAnswer,
            onValueChange = { userAnswer = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("你的答案（提交前角色看不到）") },
            enabled = roleAnswer == null && !busy,
            minLines = 3,
        )
        if (roleAnswer == null) {
            Button(
                onClick = {
                    val answer = userAnswer.trim()
                    if (answer.isEmpty()) return@Button
                    busy = true
                    request(
                        """
                        默契问题：$question
                        用户已经秘密写下答案，但你不能看到答案内容。
                        请基于你的人设、长期记忆、对用户的印象和共同经历，独立写出你的答案。
                        """.trimIndent(),
                        "先用“我的答案：”给出清晰答案，再用 1-2 句解释你为什么这样判断。不要声称看见了用户的秘密答案，不要为了迎合而猜一个万能答案。",
                    ) { answerFromRole ->
                        roleAnswer = answerFromRole
                        busy = false
                    }
                },
                enabled = userAnswer.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "$assistantName 正在作答" else "同时公开答案") }
        } else {
            Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("你的答案", fontWeight = FontWeight.SemiBold)
                    Text(userAnswer)
                    Text("$assistantName 的答案", fontWeight = FontWeight.SemiBold)
                    Text(roleAnswer.orEmpty())
                }
            }
            Text("由你判断这一题是否有默契：", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val nextAnswered = answered + 1
                        score += 1
                        checkpoint(
                            "默契问答：答对彼此",
                            "用户认为第 ${nextAnswered} 题与${assistantName}很有默契。问题：$question",
                            "{\"game\":\"rapport_quiz\",\"question\":\"${escapeJson(question)}\",\"match\":true}",
                        )
                        answered = nextAnswered
                        index = (index + 1) % RAPPORT_QUESTIONS.size
                        userAnswer = ""
                        roleAnswer = null
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("很默契 +1") }
                OutlinedButton(
                    onClick = {
                        val nextAnswered = answered + 1
                        checkpoint(
                            "默契问答：发现不同",
                            "用户发现自己与${assistantName}在第 ${nextAnswered} 题的答案不同。问题：$question",
                            "{\"game\":\"rapport_quiz\",\"question\":\"${escapeJson(question)}\",\"match\":false}",
                        )
                        answered = nextAnswered
                        index = (index + 1) % RAPPORT_QUESTIONS.size
                        userAnswer = ""
                        roleAnswer = null
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("不太一样") }
            }
        }
    }
}

private val RPG_OPENING_CHOICES = listOf(
    "检查门缝和地面的痕迹",
    "直接敲门，假装自己迷路了",
    "绕到旧宅后方寻找别的入口",
)

@Composable
internal fun RoleplayAdventureGame(
    assistantName: String,
    request: CompanionNarrativeRequest,
    checkpoint: SharedGameCheckpoint,
) {
    var chapter by remember { mutableIntStateOf(1) }
    var hp by remember { mutableIntStateOf(4) }
    var clues by remember { mutableIntStateOf(0) }
    var customAction by remember { mutableStateOf("") }
    var choices by remember { mutableStateOf(RPG_OPENING_CHOICES) }
    var narration by remember {
        mutableStateOf("雨夜里，你和${assistantName}站在一座废弃天文台前。三天前失踪的研究员，最后一条讯息只有一句：不要相信会倒着走的钟。")
    }
    var log by remember { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }
    val finished = hp <= 0 || chapter > 6

    fun takeAction(action: String) {
        if (action.isBlank() || busy || finished) return
        val roll = Random.nextInt(1, 21)
        val difficulty = 9 + (chapter % 4)
        val success = roll >= difficulty
        val nextHp = if (success) hp else (hp - 1).coerceAtLeast(0)
        val nextClues = if (success) clues + 1 else clues
        busy = true
        customAction = ""
        request(
            """
            轻量跑团世界：雨夜废弃天文台，失踪研究员留下“不要相信会倒着走的钟”。
            玩家与同伴：用户和$assistantName。
            当前章节：$chapter/6；体力：$hp/4；线索：$clues。
            上一幕：$narration
            玩家行动：$action
            程序掷骰：d20=$roll，难度=$difficulty，判定=${if (success) "成功" else "失败"}。
            判定后的状态：体力=$nextHp/4，线索=$nextClues。
            """.trimIndent(),
            "你同时扮演同伴角色和跑团主持人。严格接受程序给出的掷骰与状态，不得重掷或篡改。用 2-4 个短段落描述行动结果，让你的角色自然参与并说话；最后另起一行给出恰好三个可执行选项，格式必须为“选项：A｜B｜C”。不要替用户决定。",
        ) { result ->
            val parsedChoices = parseAdventureChoices(result)
            narration = result.substringBeforeLast("选项：", result).trim().ifBlank { result }
            choices = parsedChoices.ifEmpty { defaultAdventureChoices(chapter, success) }
            log = (log + "第 $chapter 幕：$action · d20=$roll/${difficulty} · ${if (success) "成功" else "失败"}").takeLast(6)
            hp = nextHp
            clues = nextClues
            checkpoint(
                "跑团第 $chapter 幕",
                "用户与${assistantName}在废弃天文台冒险：选择“$action”，掷出 $roll，判定${if (success) "成功" else "失败"}。当前体力 $nextHp，线索 $nextClues。",
                "{\"game\":\"roleplay_adventure\",\"chapter\":$chapter,\"roll\":$roll,\"difficulty\":$difficulty,\"success\":$success,\"hp\":$nextHp,\"clues\":$nextClues,\"action\":\"${escapeJson(action)}\"}",
            )
            chapter += 1
            busy = false
        }
    }

    GameBody(
        title = "轻量跑团 · 倒走的钟",
        subtitle = "$assistantName 既是你的同伴，也会主持其他 NPC 和世界。行动自由输入，判定由本地 d20 引擎完成。",
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("章节", "${chapter.coerceAtMost(6)}/6")
            StatusChip("体力", "$hp/4")
            StatusChip("线索", "$clues")
        }
        GameResultText(
            when {
                hp <= 0 -> "你们没能撑到天文台最深处。本次冒险结束，但留下的线索可以成为下一次故事的开端。"
                chapter > 6 -> "钟楼停止倒转。你们带着 $clues 条线索走出天文台，本次冒险告一段落。"
                else -> narration
            },
        )
        if (log.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("冒险记录", fontWeight = FontWeight.SemiBold)
                log.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (!finished) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { choice ->
                    OutlinedButton(
                        onClick = { takeAction(choice) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                    ) { Text(choice) }
                }
            }
            OutlinedTextField(
                value = customAction,
                onValueChange = { customAction = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("或者自由描述你的行动") },
                enabled = !busy,
                minLines = 2,
            )
            Button(
                onClick = { takeAction(customAction.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = customAction.isNotBlank() && !busy,
            ) { Text(if (busy) "$assistantName 正在推进剧情" else "执行自由行动并掷骰") }
        } else {
            Button(
                onClick = {
                    chapter = 1
                    hp = 4
                    clues = 0
                    narration = "雨夜里，你和${assistantName}再次站在废弃天文台前。这一次，钟声比记忆中更早响起。"
                    choices = RPG_OPENING_CHOICES
                    log = emptyList()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("重新开团") }
        }
    }
}

@Composable
private fun StatusChip(label: String, value: String) {
    FilterChip(
        selected = true,
        onClick = {},
        label = { Text("$label $value") },
    )
}

internal fun parseAdventureChoices(text: String): List<String> {
    val tail = text.substringAfterLast("选项：", "")
    return tail.split('｜', '|')
        .map { it.trim().removePrefix("A.").removePrefix("B.").removePrefix("C.").trim() }
        .filter { it.isNotBlank() }
        .take(3)
}

private fun defaultAdventureChoices(chapter: Int, success: Boolean): List<String> = when {
    !success -> listOf("先处理伤势并观察周围", "让同伴掩护，继续追踪", "暂时撤回安全区域")
    chapter <= 2 -> listOf("研究发现的线索", "追赶刚刚闪过的人影", "寻找通往钟楼的楼梯")
    chapter <= 4 -> listOf("质问守钟人", "拆开倒走的钟", "根据线索寻找研究员")
    else -> listOf("关闭天文台主机", "救出研究员", "带着证据立刻离开")
}

private fun escapeJson(value: String): String = value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
