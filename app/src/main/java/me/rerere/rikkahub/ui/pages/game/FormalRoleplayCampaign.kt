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
import me.rerere.rikkahub.ui.context.LocalSettings
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

private data class RoleplayWorld(
    val id: String,
    val title: String,
    val system: String,
    val premise: String,
    val narrativeStyle: String,
    val accent: Color,
    val sanityRisk: Boolean = false,
)

private val ROLEPLAY_WORLDS = listOf(
    RoleplayWorld("occult_city", "雾港失踪案", "都市秘闻 · 调查恐怖 · d20", "终年起雾的港城里，每逢午夜都会出现一条不存在的街道。最近失踪的人，都曾收到一张写着自己死亡日期的旧车票。", "都市怪谈与硬派调查风。强化潮湿雾气、旧楼回声、路灯死角、港口腥味和城市传闻；现实应从细微处逐渐失真。线索必须可追踪，恐怖来自你们发现城市一直在观察自己。同行角色要有不同推理、怀疑与保护反应。", Color(0xFF28C2D8), true),
    RoleplayWorld("fantasy_ruin", "失落王庭", "高魔幻想 · 遗迹史诗 · d20", "沉入地底三百年的王城突然重新升起。王座仍在等待继承者，而所有进入王城的人都会逐渐忘记自己的名字。", "史诗奇幻与遗迹冒险风。描写宏大尺度、古老魔法、失落礼仪、历史残响和壮丽危险；战斗之外要有谜题、阵营、古代誓约与身份诱惑。同行角色会因权力、记忆与牺牲产生立场变化，关系线与王庭秘密同步推进。", Color(0xFFF0A94A)),
    RoleplayWorld("space_derelict", "静默星舰", "太空惊悚 · 生存悬疑 · d20", "一艘失联二十年的殖民星舰向你们发送求救信号。登舰后，主脑坚持声称船员仍全部存活。", "冷峻科幻惊悚风。强调失重、金属回声、红色警报、维生系统噪声、舷窗外的绝对黑暗和宇宙尺度孤独。信息应通过终端、监控、残缺录音和错误人工智能逐步拼合。同行角色要真实讨论资源、风险与是否信任主脑。", Color(0xFF7C8CFF), true),
    RoleplayWorld("academy_secret", "第十三间教室", "校园怪谈 · 心理恐怖 · d20", "学校平面图上只有十二间教室，但每个雨夜，走廊尽头都会出现第十三扇门。门后的课表写着你们所有人的名字。", "高浓度校园心理恐怖。强化雨声、灯管嗡鸣、粉笔灰、潮湿校服、冷空气、脚步远近错位和熟悉场景的细微异常；允许寂静、错觉、被注视感、不可靠记忆和无法确认的人影。恐怖要逐步逼近，不靠单纯血腥。同行角色必须出现恐惧、逞强、保护、怀疑或依赖等真实反应。", Color(0xFFFF4F91), true),
    RoleplayWorld("romance_target_me", "全员都在攻略我", "恋爱修罗场 · 被攻略 · 关系判定", "你进入一档无法退出的沉浸式恋爱实验。同行角色都收到秘密任务：在七天内让你主动选择他，但每个人隐藏的真实目的并不相同。", "高张力恋爱修罗场与被攻略风。重点写眼神停顿、距离变化、话里有话、吃醋、试探、偏爱、误会和公开场合下的暗流。同行角色必须主动制定各自攻略方式，既有甜蜜心动也有竞争和秘密。不要让外部反派抢走关系主线；每次行动都应推动至少一条关系或揭开一个真实目的。", Color(0xFFFF6FAE)),
    RoleplayWorld("romance_i_target", "心动对象观察日志", "主动攻略 · 都市恋爱 · d20", "你获得一本会显示‘心动波动’的观察日志，却看不到具体数值。只有通过行动、对话与共同经历，才能判断谁正在对你动心。", "细腻都市恋爱与主动攻略风。文笔要克制、暧昧、生活化，重视聊天节奏、微表情、未说出口的话和日常陪伴。骰子决定行动是否自然、是否被误解或意外制造心动。同行角色不能轻易表白，要通过持续互动形成未知探索感。", Color(0xFFFFB26B)),
    RoleplayWorld("system_mission", "系统说今天必须心动", "系统任务 · 轻喜剧 · 恋爱冒险", "一个不太靠谱的系统绑定了你们，天天发布离谱任务：交换身份、假装情侣、在敌人面前演戏、说出一句无法撤回的真心话。失败惩罚通常比任务更荒唐。", "快节奏轻喜剧与系统文风。系统提示要简短、有梗、偶尔故障，但不能替玩家决定行动。重点写任务引发的尴尬、误会、嘴硬、互相拆台和突然心动；笑点来自角色性格碰撞，不要只靠网络段子。关键场景仍要有真情绪和关系推进。", Color(0xFFFFD84D)),
    RoleplayWorld("palace_scheme", "今夜谁在宫门外", "古风宫廷 · 权谋关系 · d20", "新帝登基后的第一个雪夜，宫门外出现一具没有影子的尸体。你们被卷入储位、旧案与禁军之间的秘密角力。", "古风权谋与克制情感风。语言雅致但清楚，重视礼法、身份、称谓、试探、沉默和一句话中的多重含义。阴谋必须可推理，角色不会无缘无故降智。同行者之间既可能结盟也可能互相隐瞒，感情线通过危险中的选择与信任变化展开。", Color(0xFFD8A45B)),
    RoleplayWorld("cyber_memory", "霓虹雨中的假记忆", "赛博都市 · 身份悬疑 · d20", "在可以购买记忆的城市里，你们发现彼此都拥有同一段童年，但那段童年只可能属于一个人。", "赛博朋克悬疑与身份关系风。强化霓虹雨、广告噪声、义体触觉、数据残影和贫富割裂；用黑客行动、记忆交易与企业追踪推进谜团。核心不是打公司，而是谁的记忆被改写、同行者之间还能否相信彼此。", Color(0xFF37E3B5), true),
    RoleplayWorld("apocalypse_store", "废土便利店最后营业日", "末日生存 · 公路治愈 · d20", "世界毁灭后的第九年，你们经营着荒原上最后一家便利店。某天，一位客人用一张灾难发生前的崭新车票购买了最后一盒草莓糖。", "末日公路、资源生存与温柔治愈并存。描写风沙、废墟、旧商品、微小食物带来的慰藉和人与人之间艰难建立的信任。危险真实但不要持续压抑；允许幽默、日常经营、互相照顾和偶尔灿烂的希望。", Color(0xFFFF8A5B)),
    RoleplayWorld("cultivation_comedy", "小师弟把魔尊契约当话本", "仙侠轻喜剧 · 契约冒险 · d20", "一纸写错名字的上古契约，把你们与刚苏醒的魔尊绑在一起。解除契约需要完成九项试炼，但第一项竟是‘让契约双方真心称赞对方一次’。", "仙侠冒险与欢喜冤家轻喜剧。保持东方奇幻意象、门派规矩、法器和秘境奇观，同时让笑点来自契约限制、角色嘴硬和身份反差。战斗、修炼和情感变化相互服务，避免一直打怪升级。", Color(0xFF8BE0C5)),
    RoleplayWorld("time_loop_date", "约会结束前世界会重启", "时间循环 · 恋爱悬疑 · d20", "每天晚上十一点五十九分，世界都会回到你们第一次见面的早晨。只有同行小队保留记忆，而某个人似乎每次都在偷偷改变结局。", "时间循环、恋爱悬疑与渐进反转。重复场景要通过细节偏差、记忆累积和关系变化产生新鲜感；每次循环都应揭开一点秘密，同时让角色对死亡、重逢、遗忘和选择产生不同心理。浪漫来自共同记得，而不是无条件甜宠。", Color(0xFFA98CFF), true),
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
                add(RoleplaySave(
                    id = item.getString("id"), title = item.getString("title"), worldId = item.getString("worldId"),
                    assistantIds = item.getJSONArray("assistantIds").toStringList(), assistantNames = item.getJSONArray("assistantNames").toStringList(),
                    scene = item.optInt("scene", 1), hp = item.optInt("hp", 10), sanity = item.optInt("sanity", 10),
                    luck = item.optInt("luck", 3), clues = item.optInt("clues", 0), lastNarration = item.optString("lastNarration"),
                    log = item.optJSONArray("log")?.toStringList().orEmpty(), updatedAt = item.optLong("updatedAt", 0L),
                ))
            }
        }.sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    fun save(saves: List<RoleplaySave>) {
        val array = JSONArray()
        saves.forEach { save -> array.put(JSONObject().apply {
            put("id", save.id); put("title", save.title); put("worldId", save.worldId)
            put("assistantIds", JSONArray(save.assistantIds)); put("assistantNames", JSONArray(save.assistantNames))
            put("scene", save.scene); put("hp", save.hp); put("sanity", save.sanity); put("luck", save.luck); put("clues", save.clues)
            put("lastNarration", save.lastNarration); put("log", JSONArray(save.log.takeLast(80))); put("updatedAt", save.updatedAt)
        }) }
        prefs.edit().putString("saves", array.toString()).apply()
    }
}

private fun JSONArray.toStringList(): List<String> = buildList { for (index in 0 until length()) add(optString(index)) }

@Composable
internal fun FormalRoleplayCampaignGame(request: CompanionNarrativeRequest, checkpoint: SharedGameCheckpoint, onExit: () -> Unit) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val store = remember { RoleplaySaveStore(context) }
    var saves by remember { mutableStateOf(store.load()) }
    var activeSaveId by remember { mutableStateOf<String?>(null) }
    var showCreator by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<RoleplaySave?>(null) }
    val activeSave = saves.firstOrNull { it.id == activeSaveId }
    fun persist(updated: List<RoleplaySave>) { saves = updated.sortedByDescending { it.updatedAt }; store.save(saves) }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(TrpgColors.backgroundTop, TrpgColors.backgroundBottom)))) {
        if (activeSave == null) RoleplayArchive(saves, onExit, { activeSaveId = it.id }, { showCreator = true }, { deleteTarget = it })
        else RoleplayTable(activeSave, ROLEPLAY_WORLDS.firstOrNull { it.id == activeSave.worldId } ?: ROLEPLAY_WORLDS.first(), request, { activeSaveId = null }, { changed -> persist(saves.map { if (it.id == changed.id) changed else it }) }, checkpoint)
    }
    if (showCreator) RoleplayCreator(settings.assistants.map { it.id.toString() to it.name.ifBlank { "未命名角色" } }, { showCreator = false }) { title, world, selected ->
        val names = selected.mapNotNull { id -> settings.assistants.firstOrNull { it.id.toString() == id }?.name?.ifBlank { "未命名角色" } }
        val save = RoleplaySave("campaign-${System.currentTimeMillis()}", title.ifBlank { world.title }, world.id, selected, names, lastNarration = world.premise)
        persist(listOf(save) + saves); activeSaveId = save.id; showCreator = false
    }
    deleteTarget?.let { target -> AlertDialog(
        onDismissRequest = { deleteTarget = null }, title = { Text("删除跑团存档？") }, text = { Text("《${target.title}》的全部探索记录都会删除。") },
        confirmButton = { TextButton(onClick = { persist(saves.filterNot { it.id == target.id }); deleteTarget = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
    ) }
}

@Composable
private fun RoleplayArchive(saves: List<RoleplaySave>, onExit: () -> Unit, onOpen: (RoleplaySave) -> Unit, onCreate: () -> Unit, onDelete: (RoleplaySave) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExit) { Text("← 返回", color = TrpgColors.cyan) }
            Text("战役档案", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = TrpgColors.text)
            Text("TRPG", color = TrpgColors.violet, fontWeight = FontWeight.Bold)
        } }
        item { Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("＋ 新建跑团存档") } }
        if (saves.isEmpty()) item { NeonPanel(TrpgColors.violet) { Text("暂无战役档案", color = TrpgColors.text, fontWeight = FontWeight.Bold) } }
        else items(saves, key = { it.id }) { save ->
            val world = ROLEPLAY_WORLDS.firstOrNull { it.id == save.worldId } ?: ROLEPLAY_WORLDS.first()
            Surface(Modifier.fillMaxWidth().clickable { onOpen(save) }, RoundedCornerShape(22.dp), TrpgColors.panel, border = BorderStroke(1.dp, world.accent.copy(alpha = 0.56f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(save.title, color = TrpgColors.text, fontWeight = FontWeight.Bold); Text("SCENE ${save.scene}", color = world.accent, fontWeight = FontWeight.Bold) }
                    Text(world.system, color = TrpgColors.muted, style = MaterialTheme.typography.bodySmall)
                    Text(save.assistantNames.joinToString(" · ").ifBlank { "独自探索" }, color = TrpgColors.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { MiniBadge("HP ${save.hp}", TrpgColors.rose); MiniBadge("SAN ${save.sanity}", TrpgColors.violet); MiniBadge("LUCK ${save.luck}", TrpgColors.amber); MiniBadge("CLUE ${save.clues}", TrpgColors.cyan) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { onDelete(save) }) { Text("删除档案", color = TrpgColors.rose) } }
                }
            }
        }
    }
}

@Composable
private fun RoleplayCreator(assistantChoices: List<Pair<String, String>>, onDismiss: () -> Unit, onCreate: (String, RoleplayWorld, List<String>) -> Unit) {
    var title by remember { mutableStateOf("") }
    var selectedWorld by remember { mutableStateOf(ROLEPLAY_WORLDS.first()) }
    var selectedAssistants by remember { mutableStateOf(emptyList<String>()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("建立新战役") }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("存档名称（可留空）") }) }
        item { Text("选择世界（${ROLEPLAY_WORLDS.size}个）", fontWeight = FontWeight.SemiBold) }
        items(ROLEPLAY_WORLDS, key = { it.id }) { world -> Surface(Modifier.fillMaxWidth().clickable { selectedWorld = world }, RoundedCornerShape(16.dp), if (selectedWorld.id == world.id) world.accent.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(world.title, fontWeight = FontWeight.Bold); Text(world.system, style = MaterialTheme.typography.labelMedium); Text(world.premise, style = MaterialTheme.typography.bodySmall) }
        } }
        item { Text("选择同行角色（最多3位）", fontWeight = FontWeight.SemiBold) }
        items(assistantChoices.chunked(3)) { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { (id, name) -> FilterChip(id in selectedAssistants, { selectedAssistants = if (id in selectedAssistants) selectedAssistants - id else if (selectedAssistants.size < 3) selectedAssistants + id else selectedAssistants }, label = { Text(name) }) } } }
    } }, confirmButton = { Button({ onCreate(title.trim(), selectedWorld, selectedAssistants) }, enabled = selectedAssistants.isNotEmpty()) { Text("开始战役") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun RoleplayTable(save: RoleplaySave, world: RoleplayWorld, request: CompanionNarrativeRequest, onBack: () -> Unit, onUpdate: (RoleplaySave) -> Unit, checkpoint: SharedGameCheckpoint) {
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
            repeat(12) { animatedDie = Random.nextInt(1, 21); delay(75L + it * 7L) }
            val roll = Random.nextInt(1, 21); val difficulty = Random.nextInt(8, 16); val success = roll >= difficulty
            animatedDie = roll; lastRoll = roll; lastDifficulty = difficulty
            lastOutcome = when { roll == 20 -> "大成功"; roll == 1 -> "大失败"; success -> "成功"; else -> "失败" }
            rolling = false; busy = true; action = ""; val party = save.assistantNames.joinToString("、")
            request("""
                跑团世界：${world.title}
                玩法标签：${world.system}
                世界设定：${world.premise}
                当前场景编号：${save.scene}
                玩家本人：用户。叙事中的“你”只能指用户本人。
                同行角色：$party。同行角色不是用户，也不是主持人。
                当前状态：生命${save.hp}，理智${save.sanity}，幸运${save.luck}，线索${save.clues}
                上一幕：${save.lastNarration}
                用户本轮行动：$cleanAction
                d20真实结果：$roll，难度：$difficulty，判定：$lastOutcome
                最近记录：${save.log.takeLast(5).joinToString("；")}
                本世界专属文风：${world.narrativeStyle}
            """.trimIndent(), """
                你是长篇沉浸式跑团主持人兼小说叙事者。严格服从骰子结果，不得偷偷改判定。
                视角必须清楚：用第二人称“你”称呼用户；同行角色必须直接写名字。不要用含混的“我抬头提醒你”，除非那句话明确处于某位角色的引号对白中。
                每轮写约800至1400个汉字，即使用户动作很小，也要通过环境、五感、心理、微动作、空间变化、对白和细节伏笔把这一刻写充分。
                同行角色必须真实参与：至少安排两次有意义的同行互动，可包含对白、主动观察、保护、争执、试探、分工、恐惧反应、吃醋、心动或对用户选择的情绪反馈。互动要符合各自人设，不能只写“他跟在后面”。
                故事要像主角小队小说：用户是可行动核心，但同行角色有自己的判断、秘密、发现和关系变化。世界主线与人物关系同时推进。
                失败不等于什么都没发生；失败应带来代价、误导、暴露、尴尬、压力或更危险的新信息。成功也要留下风险、伏笔或新的关系变化。
                服从本世界专属文风，不同世界不得使用同一种叙事口吻。恋爱世界重关系与未知探索；轻喜剧世界要好笑但不降智；恐怖世界重压迫与感官；权谋世界重逻辑和言外之意。
                不替用户决定下一步，不擅自写用户已经答应、逃跑、拥抱、告白或攻击。结尾自然给出2至3个可选方向，同时允许自由行动。不要写规则说明、幕后分析或字数提示。
            """.trimIndent()) { narration ->
                val hpLoss = if (!success && roll <= 5) 2 else if (!success) 1 else 0
                val sanityLoss = if (!success && world.sanityRisk) 1 else 0
                val updated = save.copy(scene = save.scene + 1, hp = (save.hp - hpLoss).coerceAtLeast(0), sanity = (save.sanity - sanityLoss).coerceAtLeast(0), clues = save.clues + if (success) 1 else 0, lastNarration = narration.ifBlank { "主持叙事暂时中断，但本次判定已经被记录。" }, log = (save.log + "场景${save.scene}：$cleanAction｜d20=$roll/$difficulty $lastOutcome").takeLast(80), updatedAt = System.currentTimeMillis())
                onUpdate(updated)
                checkpoint("跑团《${save.title}》场景 ${save.scene}", "用户与${party}在${world.title}中执行“$cleanAction”，d20=$roll，结果为$lastOutcome。", "{\"game\":\"formal_trpg\",\"campaign\":\"${save.id}\",\"scene\":${save.scene},\"roll\":$roll,\"difficulty\":$difficulty}")
                busy = false
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().imePadding().navigationBarsPadding(), contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { NeonPanel(world.accent) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("← 战役档案", color = TrpgColors.cyan) }; Text("SCENE ${save.scene}", color = world.accent, fontWeight = FontWeight.Bold) }; Text(save.title, color = TrpgColors.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text(world.system, color = world.accent, style = MaterialTheme.typography.labelLarge); Text(save.assistantNames.joinToString(" · "), color = TrpgColors.text) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatusRune("生命", save.hp, TrpgColors.rose, Modifier.weight(1f)); StatusRune("理智", save.sanity, TrpgColors.violet, Modifier.weight(1f)); StatusRune("幸运", save.luck, TrpgColors.amber, Modifier.weight(1f)); StatusRune("线索", save.clues, TrpgColors.cyan, Modifier.weight(1f)) } }
        item { NeonPanel(world.accent) { Text(save.lastNarration, color = TrpgColors.text, style = MaterialTheme.typography.bodyLarge) } }
        if (rolling) item { Surface(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), TrpgColors.violet.copy(alpha = 0.20f), border = BorderStroke(1.dp, TrpgColors.violet)) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("D20", color = TrpgColors.violet, fontWeight = FontWeight.Black); Text(animatedDie.toString(), color = TrpgColors.text, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black); Text("骰子正在滚动……", color = TrpgColors.muted) } } }
        else if (lastRoll > 0) item { Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), if (lastOutcome.contains("成功")) TrpgColors.mint.copy(alpha = 0.16f) else TrpgColors.rose.copy(alpha = 0.16f)) { Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("d20  $lastRoll / DC $lastDifficulty", color = TrpgColors.text, fontWeight = FontWeight.Bold); Text(lastOutcome, color = if (lastOutcome.contains("成功")) TrpgColors.mint else TrpgColors.rose, fontWeight = FontWeight.Bold) } } }
        if (save.log.isNotEmpty()) item { NeonPanel { save.log.takeLast(4).reversed().forEach { Text("• $it", color = TrpgColors.muted, style = MaterialTheme.typography.bodySmall) } } }
        item { NeonPanel(TrpgColors.violet) {
            OutlinedTextField(action, { action = it }, Modifier.fillMaxWidth(), label = { Text("你要做什么？") }, placeholder = { Text("调查、交涉、潜行、战斗、攻略，或任何自由行动") }, minLines = 3, maxLines = 7, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TrpgColors.text, unfocusedTextColor = TrpgColors.text, cursorColor = TrpgColors.cyan, focusedBorderColor = TrpgColors.violet, unfocusedBorderColor = TrpgColors.border, focusedLabelColor = TrpgColors.violet, unfocusedLabelColor = TrpgColors.muted, focusedPlaceholderColor = TrpgColors.muted, unfocusedPlaceholderColor = TrpgColors.muted))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(onClick = { if (save.luck > 0) onUpdate(save.copy(luck = save.luck - 1, updatedAt = System.currentTimeMillis())) }, enabled = lastRoll > 0 && save.luck > 0 && !busy && !rolling, modifier = Modifier.weight(1f)) { Text("消耗幸运", color = Color.White) }
                Button(onClick = { runAction(action.trim()) }, enabled = action.isNotBlank() && !busy && !rolling && save.hp > 0 && save.sanity > 0, modifier = Modifier.weight(1.35f)) { Text(when { rolling -> "掷骰中…"; busy -> "主持人叙事中…"; else -> "掷 d20 并行动" }, color = Color.White) }
            }
            if (save.hp <= 0 || save.sanity <= 0) Text("探索已抵达危险结局，档案仍会保留。", color = TrpgColors.rose)
        } }
    }
}

@Composable
private fun NeonPanel(accent: Color = TrpgColors.border, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = TrpgColors.panel.copy(alpha = 0.95f), border = BorderStroke(1.dp, accent.copy(alpha = 0.52f))) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content) }
}

@Composable
private fun MiniBadge(text: String, color: Color) { Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.16f)) { Text(text, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) } }

@Composable
private fun StatusRune(label: String, value: Int, color: Color, modifier: Modifier = Modifier) { Surface(modifier, RoundedCornerShape(16.dp), color.copy(alpha = 0.18f), border = BorderStroke(1.dp, color.copy(alpha = 0.45f))) { Column(Modifier.padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value.toString(), color = TrpgColors.text, fontWeight = FontWeight.Black); Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) } } }
