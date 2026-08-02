package me.rerere.rikkahub.ui.pages.game

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
        system = "现代都市 · 克苏鲁式调查 · d20",
        premise = "终年起雾的港城里，每逢午夜都会有一条不存在的街道出现。最近失踪的人，都曾收到一张写着自己死亡日期的旧车票。",
        accent = Color(0xFF375768),
    ),
    RoleplayWorld(
        id = "fantasy_ruin",
        title = "失落王庭",
        system = "高魔幻想 · 遗迹探索 · d20",
        premise = "沉入地底三百年的王城突然重新升起。王座仍在等待继承者，而所有进入王城的人都会逐渐忘记自己的名字。",
        accent = Color(0xFF6B4C2F),
    ),
    RoleplayWorld(
        id = "space_derelict",
        title = "静默星舰",
        system = "太空惊悚 · 生存探索 · d20",
        premise = "一艘失联二十年的殖民星舰向你们发送求救信号。登舰后，主脑坚持声称船员仍全部存活。",
        accent = Color(0xFF39476A),
    ),
    RoleplayWorld(
        id = "academy_secret",
        title = "第十三间教室",
        system = "校园怪谈 · 关系悬疑 · d20",
        premise = "学校平面图上只有十二间教室，但每个雨夜，走廊尽头都会出现第十三扇门。门后的课表写着你们所有人的名字。",
        accent = Color(0xFF704155),
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

    if (activeSave == null) {
        RoleplayArchive(
            saves = saves,
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
            text = { Text("《${target.title}》的探索记录会被永久删除。") },
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
    onOpen: (RoleplaySave) -> Unit,
    onCreate: () -> Unit,
    onDelete: (RoleplaySave) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF151311)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("战役档案", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF2E7D5))
            Spacer(Modifier.height(4.dp))
            Text("选择一段尚未结束的冒险，或者开启新的世界。", color = Color(0xFFB9AA95))
        }
        item {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("＋ 新建跑团存档") }
        }
        if (saves.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF24201C)) {
                    Text("还没有战役。新建存档后，世界观、队伍、线索和判定都会分别保存。", modifier = Modifier.padding(20.dp), color = Color(0xFFD4C7B4))
                }
            }
        } else {
            items(saves, key = { it.id }) { save ->
                val world = ROLEPLAY_WORLDS.firstOrNull { it.id == save.worldId }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(save) },
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF24201C),
                    tonalElevation = 2.dp,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(save.title, fontWeight = FontWeight.Bold, color = Color(0xFFF2E7D5))
                            Text("场景 ${save.scene}", color = world?.accent ?: Color(0xFFC69A68))
                        }
                        Text(world?.system.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB9AA95))
                        Text("队伍：${save.assistantNames.joinToString("、").ifBlank { "独自探索" }}", color = Color(0xFFD4C7B4))
                        Text("生命 ${save.hp}　理智 ${save.sanity}　幸运 ${save.luck}　线索 ${save.clues}", style = MaterialTheme.typography.labelMedium, color = Color(0xFFC69A68))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onDelete(save) }) { Text("删除存档", color = Color(0xFFCF8E83)) }
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
                    OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("存档名称（可留空）") })
                }
                item { Text("选择世界", fontWeight = FontWeight.SemiBold) }
                items(ROLEPLAY_WORLDS) { world ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { selectedWorld = world },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selectedWorld.id == world.id) world.accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(world.title, fontWeight = FontWeight.Bold)
                            Text(world.system, style = MaterialTheme.typography.labelMedium)
                            Text(world.premise, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { Text("选择同行角色（最多 3 位）", fontWeight = FontWeight.SemiBold) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        assistantChoices.take(4).forEach { (id, name) ->
                            FilterChip(
                                selected = id in selectedAssistants,
                                onClick = {
                                    selectedAssistants = if (id in selectedAssistants) selectedAssistants - id
                                    else if (selectedAssistants.size < 3) selectedAssistants + id else selectedAssistants
                                },
                                label = { Text(name) },
                            )
                        }
                    }
                }
                if (assistantChoices.size > 4) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            assistantChoices.drop(4).forEach { (id, name) ->
                                FilterChip(
                                    selected = id in selectedAssistants,
                                    onClick = {
                                        selectedAssistants = if (id in selectedAssistants) selectedAssistants - id
                                        else if (selectedAssistants.size < 3) selectedAssistants + id else selectedAssistants
                                    },
                                    label = { Text(name) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(title.trim(), selectedWorld, selectedAssistants) }, enabled = selectedAssistants.isNotEmpty()) { Text("开始战役") }
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
    var action by remember(save.id) { mutableStateOf("") }
    var busy by remember(save.id) { mutableStateOf(false) }
    var lastRoll by remember(save.id) { mutableIntStateOf(0) }
    var lastDifficulty by remember(save.id) { mutableIntStateOf(0) }
    var lastOutcome by remember(save.id) { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF171513), Color(0xFF211B18)))),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(world.accent.copy(alpha = 0.28f)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = onBack) { Text("← 档案") }
            Column(Modifier.weight(1f)) {
                Text(save.title, color = Color(0xFFF4E8D7), fontWeight = FontWeight.Bold)
                Text(world.system, color = Color(0xFFBEAF9B), style = MaterialTheme.typography.labelSmall)
            }
            Text("场景 ${save.scene}", color = Color(0xFFE2B77E))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusRune("生命", save.hp, Color(0xFF9E4B47), Modifier.weight(1f))
                    StatusRune("理智", save.sanity, Color(0xFF586B91), Modifier.weight(1f))
                    StatusRune("幸运", save.luck, Color(0xFF9C7A3F), Modifier.weight(1f))
                    StatusRune("线索", save.clues, Color(0xFF527A62), Modifier.weight(1f))
                }
            }
            item {
                Text("同行者：${save.assistantNames.joinToString("、")}", color = Color(0xFFD9CBB8), style = MaterialTheme.typography.labelLarge)
            }
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF2A2521)) {
                    Text(save.lastNarration, modifier = Modifier.padding(18.dp), color = Color(0xFFF0E4D3), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (lastRoll > 0) {
                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = world.accent.copy(alpha = 0.22f)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("d20：$lastRoll / 难度 $lastDifficulty", color = Color(0xFFF2E7D5), fontWeight = FontWeight.Bold)
                            Text(lastOutcome, color = Color(0xFFE2B77E))
                        }
                    }
                }
            }
            if (save.log.isNotEmpty()) {
                item { Text("探索记录", color = Color(0xFFBEAF9B), fontWeight = FontWeight.SemiBold) }
                items(save.log.takeLast(8)) { entry ->
                    Text("• $entry", color = Color(0xFFCFC0AD), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Surface(color = Color(0xFF29231F), shadowElevation = 10.dp) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    value = action,
                    onValueChange = { action = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("描述你的行动") },
                    placeholder = { Text("调查、交涉、潜行、战斗，或者任何你能想到的行动") },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !busy,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (save.luck <= 0) return@OutlinedButton
                            val reroll = Random.nextInt(1, 21)
                            lastRoll = reroll
                            lastOutcome = if (reroll >= lastDifficulty) "幸运改写：成功" else "幸运耗尽：仍失败"
                            onUpdate(save.copy(luck = save.luck - 1, updatedAt = System.currentTimeMillis()))
                        },
                        enabled = lastRoll > 0 && save.luck > 0 && !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("消耗幸运重投") }
                    Button(
                        onClick = {
                            val cleanAction = action.trim()
                            if (cleanAction.isBlank()) return@Button
                            val roll = Random.nextInt(1, 21)
                            val difficulty = Random.nextInt(8, 16)
                            val success = roll >= difficulty
                            lastRoll = roll
                            lastDifficulty = difficulty
                            lastOutcome = when {
                                roll == 20 -> "大成功"
                                roll == 1 -> "大失败"
                                success -> "成功"
                                else -> "失败"
                            }
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
                                "你是严谨但有戏剧张力的跑团主持人。必须接受骰子结果，描写环境变化、行动后果和至少一名同行角色符合人设的反应。不要替玩家决定下一步，不要直接完结。结尾给出2至3个可选行动，但允许玩家自由输入。控制在350至600字。",
                            ) { narration ->
                                val hpLoss = if (!success && roll <= 5) 2 else if (!success) 1 else 0
                                val sanityLoss = if (!success && world.id in listOf("occult_city", "space_derelict")) 1 else 0
                                val clueGain = if (success) 1 else 0
                                val updated = save.copy(
                                    scene = save.scene + 1,
                                    hp = (save.hp - hpLoss).coerceAtLeast(0),
                                    sanity = (save.sanity - sanityLoss).coerceAtLeast(0),
                                    clues = save.clues + clueGain,
                                    lastNarration = narration.ifBlank { "判定已经发生，但主持叙事暂时中断。你仍可以继续行动。" },
                                    log = (save.log + "场景${save.scene}：$cleanAction｜d20=$roll/$difficulty $lastOutcome").takeLast(80),
                                    updatedAt = System.currentTimeMillis(),
                                )
                                onUpdate(updated)
                                checkpoint(
                                    "跑团《${save.title}》场景 ${save.scene}",
                                    "用户与$party在${world.title}中执行“$cleanAction”，d20=$roll，结果为$lastOutcome。",
                                    "{\"game\":\"formal_trpg\",\"campaign\":\"${save.id}\",\"scene\":${save.scene},\"roll\":$roll,\"difficulty\":$difficulty}",
                                )
                                busy = false
                            }
                        },
                        enabled = action.isNotBlank() && !busy && save.hp > 0 && save.sanity > 0,
                        modifier = Modifier.weight(1.4f),
                    ) { Text(if (busy) "主持人叙事中…" else "掷 d20 并行动") }
                }
                if (save.hp <= 0 || save.sanity <= 0) {
                    Text("本次探索已经抵达危险结局。你仍可以返回档案保留这段记录。", color = Color(0xFFCF8E83))
                }
            }
        }
    }
}

@Composable
private fun StatusRune(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.24f)) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), color = Color(0xFFF3E8D8), fontWeight = FontWeight.Bold)
            Text(label, color = Color(0xFFC8BAA6), style = MaterialTheme.typography.labelSmall)
        }
    }
}
