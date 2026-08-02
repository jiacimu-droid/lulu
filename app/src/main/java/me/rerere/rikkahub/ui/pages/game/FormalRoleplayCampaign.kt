package me.rerere.rikkahub.ui.pages.game

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import me.rerere.rikkahub.ui.context.LocalSettings
import org.json.JSONArray
import org.json.JSONObject

private object TrpgColors {
    val void = Color(0xFF080B18)
    val deep = Color(0xFF11172B)
    val panel = Color(0xFF171F38)
    val panelHigh = Color(0xFF202A49)
    val violet = Color(0xFF9D7BFF)
    val cyan = Color(0xFF48D7E8)
    val amber = Color(0xFFFFC15B)
    val rose = Color(0xFFFF7A90)
    val mint = Color(0xFF68E0A5)
    val text = Color(0xFFF6F1FF)
    val muted = Color(0xFFAEB9D8)
    val border = Color(0xFF344163)
    val archiveBrush = Brush.verticalGradient(listOf(Color(0xFF070A16), Color(0xFF12152F), Color(0xFF091420)))
    val tableBrush = Brush.verticalGradient(listOf(Color(0xFF080B18), Color(0xFF15162E), Color(0xFF071822)))
}

private data class RoleplayWorld(
    val id: String,
    val title: String,
    val system: String,
    val premise: String,
    val accent: Color,
)

private val ROLEPLAY_WORLDS = listOf(
    RoleplayWorld("occult_city", "雾港失踪案", "现代都市 · 神秘调查 · d20", "终年起雾的港城里，每逢午夜都会出现一条不存在的街道。最近失踪的人，都曾收到写着自己死亡日期的旧车票。", TrpgColors.cyan),
    RoleplayWorld("fantasy_ruin", "失落王庭", "高魔幻想 · 遗迹探索 · d20", "沉入地底三百年的王城突然重新升起。王座仍在等待继承者，而所有进入王城的人都会逐渐忘记自己的名字。", TrpgColors.amber),
    RoleplayWorld("space_derelict", "静默星舰", "太空惊悚 · 生存探索 · d20", "一艘失联二十年的殖民星舰重新发出求救信号。登舰后，主脑坚持声称船员仍全部存活。", TrpgColors.violet),
    RoleplayWorld("academy_secret", "第十三间教室", "校园怪谈 · 关系悬疑 · d20", "学校平面图上只有十二间教室，但每个雨夜，走廊尽头都会出现第十三扇门。门后的课表写着你们所有人的名字。", TrpgColors.rose),
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

    Box(Modifier.fillMaxSize().background(if (activeSave == null) TrpgColors.archiveBrush else TrpgColors.tableBrush)) {
        if (activeSave == null) {
            RoleplayArchive(saves, { activeSaveId = it.id }, { showCreator = true }, { deleteTarget = it })
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
                val selectedNames = selected.mapNotNull { id -> settings.assistants.firstOrNull { it.id.toString() == id }?.name?.ifBlank { "未命名角色" } }
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
            text = { Text("《${target.title}》的探索记录会被永久删除。") },
            confirmButton = { TextButton(onClick = { persist(saves.filterNot { it.id == target.id }); deleteTarget = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun RoleplayArchive(
    saves: List<RoleplaySave>,
    onOpen: (RoleplaySave) -> Unit,
    onCreate: () -> Unit,
    onDelete: (RoleplaySave) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Surface(shape = RoundedCornerShape(26.dp), color = TrpgColors.panel.copy(alpha = 0.92f)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("TRPG CAMPAIGNS", color = TrpgColors.cyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("战役档案", color = TrpgColors.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("你的每个世界、队伍、骰运和秘密都会独立保存。", color = TrpgColors.muted)
                }
            }
        }
        item { Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("＋ 开启新战役") } }
        if (saves.isEmpty()) {
            item { NeonPanel { Text("档案柜还没有记录。建立第一支队伍，选择世界，然后让骰子决定故事。", color = TrpgColors.muted) } }
        } else {
            items(saves, key = { it.id }) { save ->
                val world = ROLEPLAY_WORLDS.firstOrNull { it.id == save.worldId } ?: ROLEPLAY_WORLDS.first()
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(save) },
                    shape = RoundedCornerShape(22.dp),
                    color = TrpgColors.panel,
                    border = androidx.compose.foundation.BorderStroke(1.dp, world.accent.copy(alpha = 0.5f)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(save.title, color = TrpgColors.text, fontWeight = FontWeight.Bold)
                            Text("SCENE ${save.scene}", color = world.accent, fontWeight = FontWeight.Bold)
                        }
                        Text(world.system, color = TrpgColors.muted, style = MaterialTheme.typography.bodySmall)
                        Text("队伍｜${save.assistantNames.joinToString(" · ").ifBlank { "独自探索" }}", color = TrpgColors.text)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                item { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("存档名称（可留空）") }) }
                item { Text("选择世界", fontWeight = FontWeight.SemiBold) }
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
                item { Text("选择同行角色（最多 3 位）", fontWeight = FontWeight.SemiBold) }
                items(assistantChoices.chunked(3)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (id, name) ->
                            FilterChip(
                                selected = id in selectedAssistants,
                                onClick = { selectedAssistants = if (id in selectedAssistants) selectedAssistants - id else if (selectedAssistants.size < 3) selectedAssistants + id else selectedAssistants },
                                label = { Text(name) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onCreate(title.trim(), selectedWorld, selectedAssistants) }, enabled = selectedAssistants.isNotEmpty()) { Text("开始战役") } },
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
    var action by remember(save.id) { mutableStateOf("") }
    var busy by remember(save.id) { mutableStateOf(false) }
    var lastRoll by remember(save.id) { mutableIntStateOf(0) }
    var lastDifficulty by remember(save.id) { mutableIntStateOf(0) }
    var lastOutcome by remember(save.id) { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = TrpgColors.panel, border = androidx.compose.foundation.BorderStroke(1.dp, world.accent.copy(alpha = 0.55f))) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack) { Text("← 档案柜", color = TrpgColors.cyan) }
                        Text("SCENE ${save.scene}", color = world.accent, fontWeight = FontWeight.Bold)
                    }
                    Text(save.title, color = TrpgColors.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(world.system, color = TrpgColors.muted)
                    Text(save.assistantNames.joinToString(" · "), color = TrpgColors.text)
                }
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
            NeonPanel(accent = world.accent) {
                Text("主持叙事", color = world.accent, fontWeight = FontWeight.Bold)
                Text(save.lastNarration, color = TrpgColors.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (save.log.isNotEmpty()) {
            item {
                NeonPanel {
                    Text("调查日志", color = TrpgColors.cyan, fontWeight = FontWeight.Bold)
                    save.log.takeLast(5).reversed().forEach { Text("• $it", color = TrpgColors.muted, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (lastRoll > 0) {
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = if (lastOutcome.contains("成功")) TrpgColors.mint.copy(alpha = 0.16f) else TrpgColors.rose.copy(alpha = 0.16f)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("d20  $lastRoll / DC $lastDifficulty", color = TrpgColors.text, fontWeight = FontWeight.Bold)
                        Text(lastOutcome, color = if (lastOutcome.contains("成功")) TrpgColors.mint else TrpgColors.rose, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            NeonPanel(accent = TrpgColors.violet) {
                OutlinedTextField(
                    value = action,
                    onValueChange = { action = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("你要做什么？") },
                    placeholder = { Text("调查、交涉、潜行、战斗，或者任何自由行动") },
                    minLines = 2,
                    maxLines = 5,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedButton(
                        onClick = { if (save.luck > 0) onUpdate(save.copy(luck = save.luck - 1, updatedAt = System.currentTimeMillis())) },
                        enabled = lastRoll > 0 && save.luck > 0 && !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("消耗幸运") }
                    Button(
                        onClick = {
                            val cleanAction = action.trim()
                            if (cleanAction.isBlank()) return@Button
                            val roll = Random.nextInt(1, 21)
                            val difficulty = Random.nextInt(8, 16)
                            val success = roll >= difficulty
                            lastRoll = roll
                            lastDifficulty = difficulty
                            lastOutcome = when { roll == 20 -> "大成功"; roll == 1 -> "大失败"; success -> "成功"; else -> "失败" }
                            busy = true
                            action = ""
                            val party = save.assistantNames.joinToString("、")
                            request(
                                """
                                正式跑团世界：${world.title}
                                世界设定：${world.premise}
                                当前场景：${save.scene}
                                玩家队伍：用户、$party
                                当前状态：生命${save.hp}，理智${save.sanity}，幸运${save.luck}，线索${save.clues}
                                最近叙事：${save.lastNarration}
                                玩家行动：$cleanAction
                                d20真实结果：$roll，难度：$difficulty，判定：$lastOutcome
                                最近记录：${save.log.takeLast(5).joinToString("；")}
                                """.trimIndent(),
                                "你是严谨而富有戏剧张力的跑团主持人。必须接受骰子结果，描写环境变化、行动后果和至少一名同行角色符合人设的反应。不要替玩家决定下一步，不要直接完结。结尾给出2至3个可选行动，但允许玩家自由输入。控制在350至600字。",
                            ) { narration ->
                                val hpLoss = if (!success && roll <= 5) 2 else if (!success) 1 else 0
                                val sanityLoss = if (!success && world.id in listOf("occult_city", "space_derelict")) 1 else 0
                                val updated = save.copy(
                                    scene = save.scene + 1,
                                    hp = (save.hp - hpLoss).coerceAtLeast(0),
                                    sanity = (save.sanity - sanityLoss).coerceAtLeast(0),
                                    clues = save.clues + if (success) 1 else 0,
                                    lastNarration = narration.ifBlank { "判定已经发生，但主持叙事暂时中断。你仍可以继续行动。" },
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
                        },
                        enabled = action.isNotBlank() && !busy && save.hp > 0 && save.sanity > 0,
                        modifier = Modifier.weight(1.3f),
                    ) { Text(if (busy) "主持人叙事中…" else "掷 d20 行动") }
                }
                if (save.hp <= 0 || save.sanity <= 0) Text("探索抵达危险结局，档案仍会保留。", color = TrpgColors.rose)
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun NeonPanel(accent: Color = TrpgColors.border, content: @Composable Column.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = TrpgColors.panel.copy(alpha = 0.94f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@Composable
private fun MiniBadge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.16f)) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusRune(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.18f), border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.45f))) {
        Column(Modifier.padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), color = TrpgColors.text, fontWeight = FontWeight.Black)
            Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}
