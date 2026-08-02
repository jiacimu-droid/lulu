package me.rerere.rikkahub.ui.pages.game

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import me.rerere.rikkahub.ui.context.LocalSettings
import kotlin.random.Random

private data class RoleplayWorld(
    val id: String,
    val title: String,
    val system: String,
    val premise: String,
    val accent: Color,
)

private val ROLEPLAY_WORLDS = listOf(
    RoleplayWorld(
        id = "occult_city",
        title = "雾港失踪案",
        system = "都市秘闻 · 调查恐怖 · d20",
        premise = "终年起雾的港城里，每逢午夜都会出现一条不存在的街道。最近失踪的人，都曾收到一张写着自己死亡日期的旧车票。",
        accent = Color(0xFF28C2D8),
    ),
    RoleplayWorld(
        id = "fantasy_ruin",
        title = "失落王庭",
        system = "高魔幻想 · 遗迹探索 · d20",
        premise = "沉入地底三百年的王城突然重新升起。王座仍在等待继承者，而所有进入王城的人都会逐渐忘记自己的名字。",
        accent = Color(0xFFF0A94A),
    ),
    RoleplayWorld(
        id = "space_derelict",
        title = "静默星舰",
        system = "太空惊悚 · 生存探索 · d20",
        premise = "一艘失联二十年的殖民星舰向你们发送求救信号。登舰后，主脑坚持声称船员仍全部存活。",
        accent = Color(0xFF7C8CFF),
    ),
    RoleplayWorld(
        id = "academy_secret",
        title = "第十三间教室",
        system = "校园怪谈 · 心理恐怖 · d20",
        premise = "学校平面图上只有十二间教室，但每个雨夜，走廊尽头都会出现第十三扇门。门后的课表写着你们所有人的名字。",
        accent = Color(0xFFFF4F91),
    ),
)

private data class RoleplaySave(
    val id: String,
    val title: String,
    val worldId: String,
    val assistantIds: List<String>,
    val assistantNames: List<String>,
    val scene: Int = 1,
    val hp: Int = 10,
    val sanity: Int = 10,
    val luck: Int = 3,
    val clues: Int = 0,
    val lastNarration: String,
    val log: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

private object TrpgColors {
    val backgroundTop = Color(0xFF070A16)
    val backgroundBottom = Color(0xFF15102A)
    val panel = Color(0xFF13182D)
    val panelRaised = Color(0xFF1A2140)
    val border = Color(0xFF425079)
    val text = Color(0xFFF3F5FF)
    val muted = Color(0xFFAAB2D2)
    val cyan = Color(0xFF38D9F1)
    val violet = Color(0xFFA88BFF)
    val amber = Color(0xFFFFC45B)
    val rose = Color(0xFFFF668F)
    val mint = Color(0xFF61E6B3)
}

private class RoleplaySaveStore(context: Context) {
    private val prefs = context.getSharedPreferences("formal_roleplay_campaigns", Context.MODE_PRIVATE)

    fun load(): List<RoleplaySave> = runCatching {
        val array = JSONArray(prefs.getString("saves", "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    RoleplaySave(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        worldId = item.getString("worldId"),
                        assistantIds = item.getJSONArray("assistantIds").toStringList(),
                        assistantNames = item.getJSONArray("assistantNames").toStringList(),
                        scene = item.optInt("scene", 1),
                        hp = item.optInt("hp", 10),
                        sanity = item.optInt("sanity", 10),
                        luck = item.optInt("luck", 3),
                        clues = item.optInt("clues", 0),
                        lastNarration = item.optString("lastNarration"),
                        log = item.optJSONArray("log")?.toStringList().orEmpty(),
                        updatedAt = item.optLong("updatedAt", 0L),
                    ),
                )
            }
        }.sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    fun save(saves: List<RoleplaySave>) {
        val array = JSONArray()
        saves.forEach { save ->
            array.put(JSONObject().apply {
                put("id", save.id)
                put("title", save.title)
                put("worldId", save.worldId)
                put("assistantIds", JSONArray(save.assistantIds))
                put("assistantNames", JSONArray(save.assistantNames))
                put("scene", save.scene)
                put("hp", save.hp)
                put("sanity", save.sanity)
                put("luck", save.luck)
                put("clues", save.clues)
                put("lastNarration", save.lastNarration)
                put("log", JSONArray(save.log.takeLast(80)))
                put("updatedAt", save.updatedAt)
            })
        }
        prefs.edit().putString("saves", array.toString()).apply()
    }
}

private fun JSONArray.toStringList(): List<String> = buildList {
    for (index in 0 until length()) add(optString(index))
}

@Composable
internal fun FormalRoleplayCampaignGame(
    request: CompanionNarrativeRequest,
    checkpoint: SharedGameCheckpoint,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val store = remember { RoleplaySaveStore(context) }
    var saves by remember { mutableStateOf(store.load()) }
    var activeSaveId by remember { mutableStateOf<String?>(null) }
    var showCreator by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<RoleplaySave?>(null) }
    val activeSave = saves.firstOrNull { it.id == activeSaveId }

    fun persist(updated: List<RoleplaySave>) {
        saves = updated.sortedByDescending { it.updatedAt }
        store.save(saves)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TrpgColors.backgroundTop, TrpgColors.backgroundBottom))),
    ) {
        if (activeSave == null) {
            RoleplayArchive(
                saves = saves,
                onExit = onExit,
                onOpen = { activeSaveId = it.id },
                onCreate = { showCreator = true },
                onDelete = { deleteTarget = it },
            )
        } else {
            RoleplayTable(
                save = activeSave,
                world = ROLEPLAY_WORLDS.firstOrNull { it.id == activeSave.worldId } ?: ROLEPLAY_WORLDS.first(),
                request = request,
                onBack = { activeSaveId = null },
                onUpdate = { changed -> persist(saves.map { if (it.id == changed.id) changed else it }) },
                checkpoint = checkpoint,
            )
        }
    }

    if (showCreator) {
        RoleplayCreator(
            assistantChoices = settings.assistants.map { it.id.toString() to it.name.ifBlank { "未命名角色" } },
            onDismiss = { showCreator = false },
            onCreate = { title, world, selected ->
                val selectedNames = selected.mapNotNull { id ->
                    settings.assistants.firstOrNull { it.id.toString() == id }?.name?.ifBlank { "未命名角色" }
                }
                val save = RoleplaySave(
                    id = "campaign-${System.currentTimeMillis()}",
                    title = title.ifBlank { world.title },
                    worldId = world.id,
                    assistantIds = selected,
                    assistantNames = selectedNames,
                    lastNarration = world.premise,
                )
                persist(listOf(save) + saves)
                activeSaveId = save.id
                showCreator = false
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除跑团存档？") },
            text = { Text("《${target.title}》的全部探索记录都会删除。") },
            confirmButton = {
                TextButton(onClick = {
                    persist(saves.filterNot { it.id == target.id })
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun RoleplayArchive(
    saves: List<RoleplaySave>,
    onExit: () -> Unit,
    onOpen: (RoleplaySave) -> Unit,
    onCreate: () -> Unit,
    onDelete: (RoleplaySave) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onExit) { Text("← 返回", color = TrpgColors.cyan) }
                Text("战役档案", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = TrpgColors.text)
                Text("TRPG", color = TrpgColors.violet, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("＋ 新建跑团存档") }
        }
        if (saves.isEmpty()) {
            item {
                NeonPanel(TrpgColors.violet) {
                    Text("暂无战役档案", color = TrpgColors.text, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            items(saves, key = { it.id }) { save ->
                val world = ROLEPLAY_WORLDS.firstOrNull { it.id == save.worldId } ?: ROLEPLAY_WORLDS.first()
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(save) },
                    shape = RoundedCornerShape(22.dp),
                    color = TrpgColors.panel,
                    border = BorderStroke(1.dp, world.accent.copy(alpha = 0.56f)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(save.title, color = TrpgColors.text, fontWeight = FontWeight.Bold)
                            Text("SCENE ${save.scene}", color = world.accent, fontWeight = FontWeight.Bold)
                        }
                        Text(world.system, color = TrpgColors.muted, style = MaterialTheme.typography.bodySmall)
                        Text(save.assistantNames.joinToString(" · ").ifBlank { "独自探索" }, color = TrpgColors.text)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            MiniBadge("HP ${save.hp}", TrpgColors.rose)
                            MiniBadge("SAN ${save.sanity}", TrpgColors.violet)
                            MiniBadge("LUCK ${save.luck}", TrpgColors.amber)
                            MiniBadge("CLUE ${save.clues}", TrpgColors.cyan)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onDelete(save) }) { Text("删除档案", color = TrpgColors.rose) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleplayCreator(
    assistantChoices: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onCreate: (String, RoleplayWorld, List<String>) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var selectedWorld by remember { mutableStateOf(ROLEPLAY_WORLDS.first()) }
    var selectedAssistants by remember { mutableStateOf(emptyList<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("建立新战役") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("存档名称（可留空）") },
                    )
                }
                items(ROLEPLAY_WORLDS) { world ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { selectedWorld = world },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedWorld.id == world.id) world.accent.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(world.title, fontWeight = FontWeight.Bold)
                            Text(world.system, style = MaterialTheme.typography.labelMedium)
                            Text(world.premise, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                items(assistantChoices.chunked(3)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (id, name) ->
                            FilterChip(
                                selected = id in selectedAssistants,
                                onClick = {
                                    selectedAssistants = if (id in selectedAssistants) {
                                        selectedAssistants - id
                                    } else if (selectedAssistants.size < 3) {
                                        selectedAssistants + id
                                    } else {
                                        selectedAssistants
                                    }
                                },
                                label = { Text(name) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title.trim(), selectedWorld, selectedAssistants) },
                enabled = selectedAssistants.isNotEmpty(),
            ) { Text("开始战役") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RoleplayTable(
    save: RoleplaySave,
    world: RoleplayWorld,
    request: CompanionNarrativeRequest,
    onBack: () -> Unit,
    onUpdate: (RoleplaySave) -> Unit,
    checkpoint: SharedGameCheckpoint,
) {
    val scope = rememberCoroutineScope()
    var action by remember(save.id) { mutableStateOf("") }
    var busy by remember(save.id) { mutableStateOf(false) }
    var rolling by remember(save.id) { mutableStateOf(false) }
    var animatedDie by remember(save.id) { mutableIntStateOf(20) }
    var lastRoll by remember(save.id) { mutableIntStateOf(0) }
    var lastDifficulty by remember(save.id) { mutableIntStateOf(0) }
    var lastOutcome by remember(save.id) { mutableStateOf("") }

    fun runAction(cleanAction: String) {
        if (cleanAction.isBlank() || busy || rolling) return
        rolling = true
        scope.launch {
            repeat(12) {
                animatedDie = Random.nextInt(1, 21)
                delay(75L + it * 7L)
            }
            val roll = Random.nextInt(1, 21)
            val difficulty = Random.nextInt(8, 16)
            val success = roll >= difficulty
            animatedDie = roll
            lastRoll = roll
            lastDifficulty = difficulty
            lastOutcome = when {
                roll == 20 -> "大成功"
                roll == 1 -> "大失败"
                success -> "成功"
                else -> "失败"
            }
            rolling = false
            busy = true
            action = ""
            val party = save.assistantNames.joinToString("、")
            val styleDirection = when (world.id) {
                "academy_secret" -> "本世界必须恐怖加辣：强化雨声、灯光、气味、温度、触感、远近声源等五感；用熟悉事物的细微异常制造心理压迫；允许短暂寂静、错觉、被注视感与不可靠细节。恐怖来自逐步逼近和未知，不要只靠血腥。"
                "occult_city" -> "强调潮湿雾气、旧城区声音、都市传闻和逐渐失真的现实感。"
                "space_derelict" -> "强调失重、金属回声、生命维持系统噪声、幽闭感和宇宙尺度的孤独。"
                else -> "强调遗迹尺度、魔法异象、历史残响与危险中的壮丽感。"
            }
            request(
                """
                跑团世界：${world.title}
                世界设定：${world.premise}
                当前场景编号：${save.scene}
                玩家本人：用户。叙事中的“你”只能指用户本人。
                同行角色：$party。同行角色不是用户，也不是主持人。
                当前状态：生命${save.hp}，理智${save.sanity}，幸运${save.luck}，线索${save.clues}
                上一幕：${save.lastNarration}
                用户本轮行动：$cleanAction
                d20真实结果：$roll，难度：$difficulty，判定：$lastOutcome
                最近记录：${save.log.takeLast(5).joinToString("；")}
                风格要求：$styleDirection
                """.trimIndent(),
                """
                你是长篇沉浸式跑团主持人兼小说叙事者。严格服从骰子结果，不得偷偷改判定。
                视角必须清楚：用第二人称“你”称呼用户；同行角色必须直接写名字。不要用含混的“我抬头提醒你”，除非那句话明确处于某位角色的引号对白中。
                每轮写约800至1400个汉字，即使用户动作很小，也要通过环境、五感、心理压力、微动作、空间变化和细节伏笔把这一刻写得充分。
                同行角色必须真实参与：至少安排两次有意义的同行互动，可包含对白、主动观察、保护、争执、试探、分工、恐惧反应或对用户选择的情绪反馈。互动必须符合各自人设，不能只写“他跟在后面”。
                故事要像主角小队小说：用户是可行动的核心，但同行角色有自己的判断、发现和关系变化。外部谜团与角色关系同时推进。
                失败不等于什么都没发生；失败应带来代价、误导、暴露、压力或更危险的新信息。成功也要留下后续风险和伏笔。
                不替用户决定下一步，不擅自写用户已经答应、逃跑、拥抱或攻击。结尾自然给出2至3个可选方向，同时允许自由行动。不要写规则说明、幕后分析或字数提示。
                """.trimIndent(),
            ) { narration ->
                val hpLoss = if (!success && roll <= 5) 2 else if (!success) 1 else 0
                val sanityLoss = if (!success && world.id in listOf("academy_secret", "occult_city", "space_derelict")) 1 else 0
                val updated = save.copy(
                    scene = save.scene + 1,
                    hp = (save.hp - hpLoss).coerceAtLeast(0),
                    sanity = (save.sanity - sanityLoss).coerceAtLeast(0),
                    clues = save.clues + if (success) 1 else 0,
                    lastNarration = narration.ifBlank { "主持叙事暂时中断，但本次判定已经被记录。" },
                    log = (save.log + "场景${save.scene}：$cleanAction｜d20=$roll/$difficulty $lastOutcome").takeLast(80),
                    updatedAt = System.currentTimeMillis(),
                )
                onUpdate(updated)
                checkpoint(
                    "跑团《${save.title}》场景 ${save.scene}",
                    "用户与${party}在${world.title}中执行“$cleanAction”，d20=$roll，结果为$lastOutcome。",
                    "{\"game\":\"formal_trpg\",\"campaign\":\"${save.id}\",\"scene\":${save.scene},\"roll\":$roll,\"difficulty\":$difficulty}",
                )
                busy = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            NeonPanel(world.accent) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) { Text("← 战役档案", color = TrpgColors.cyan) }
                    Text("SCENE ${save.scene}", color = world.accent, fontWeight = FontWeight.Bold)
                }
                Text(save.title, color = TrpgColors.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(save.assistantNames.joinToString(" · "), color = TrpgColors.text)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusRune("生命", save.hp, TrpgColors.rose, Modifier.weight(1f))
                StatusRune("理智", save.sanity, TrpgColors.violet, Modifier.weight(1f))
                StatusRune("幸运", save.luck, TrpgColors.amber, Modifier.weight(1f))
                StatusRune("线索", save.clues, TrpgColors.cyan, Modifier.weight(1f))
            }
        }
        item {
            NeonPanel(world.accent) {
                Text(save.lastNarration, color = TrpgColors.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (rolling) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = TrpgColors.violet.copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, TrpgColors.violet),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("D20", color = TrpgColors.violet, fontWeight = FontWeight.Black)
                        Text(animatedDie.toString(), color = TrpgColors.text, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
                        Text("骰子正在滚动……", color = TrpgColors.muted)
                    }
                }
            }
        } else if (lastRoll > 0) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = if (lastOutcome.contains("成功")) TrpgColors.mint.copy(alpha = 0.16f) else TrpgColors.rose.copy(alpha = 0.16f),
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("d20  $lastRoll / DC $lastDifficulty", color = TrpgColors.text, fontWeight = FontWeight.Bold)
                        Text(lastOutcome, color = if (lastOutcome.contains("成功")) TrpgColors.mint else TrpgColors.rose, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (save.log.isNotEmpty()) {
            item {
                NeonPanel {
                    save.log.takeLast(4).reversed().forEach {
                        Text("• $it", color = TrpgColors.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            NeonPanel(TrpgColors.violet) {
                OutlinedTextField(
                    value = action,
                    onValueChange = { action = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("你要做什么？") },
                    placeholder = { Text("调查、交涉、潜行、战斗，或任何自由行动") },
                    minLines = 3,
                    maxLines = 7,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TrpgColors.text,
                        unfocusedTextColor = TrpgColors.text,
                        cursorColor = TrpgColors.cyan,
                        focusedBorderColor = TrpgColors.violet,
                        unfocusedBorderColor = TrpgColors.border,
                        focusedLabelColor = TrpgColors.violet,
                        unfocusedLabelColor = TrpgColors.muted,
                        focusedPlaceholderColor = TrpgColors.muted,
                        unfocusedPlaceholderColor = TrpgColors.muted,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (save.luck > 0) {
                                onUpdate(save.copy(luck = save.luck - 1, updatedAt = System.currentTimeMillis()))
                            }
                        },
                        enabled = lastRoll > 0 && save.luck > 0 && !busy && !rolling,
                        modifier = Modifier.weight(1f),
                    ) { Text("消耗幸运") }
                    Button(
                        onClick = { runAction(action.trim()) },
                        enabled = action.isNotBlank() && !busy && !rolling && save.hp > 0 && save.sanity > 0,
                        modifier = Modifier.weight(1.35f),
                    ) {
                        Text(
                            when {
                                rolling -> "掷骰中…"
                                busy -> "主持人叙事中…"
                                else -> "掷 d20 并行动"
                            },
                        )
                    }
                }
                if (save.hp <= 0 || save.sanity <= 0) {
                    Text("探索已抵达危险结局，档案仍会保留。", color = TrpgColors.rose)
                }
            }
        }
    }
}

@Composable
private fun NeonPanel(
    accent: Color = TrpgColors.border,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = TrpgColors.panel.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.52f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            content = content,
        )
    }
}

@Composable
private fun MiniBadge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.16f)) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatusRune(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), color = TrpgColors.text, fontWeight = FontWeight.Black)
            Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}
