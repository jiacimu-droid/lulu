package me.rerere.rikkahub.ui.pages.starwish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.ApiUsageSource
import me.rerere.rikkahub.data.ai.ApiUsageStore
import me.rerere.rikkahub.data.ai.transformers.transformMessages
import me.rerere.rikkahub.data.companion.CompanionPerceptionInput
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.companion.toPromptContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.starwish.StarWishStore
import me.rerere.rikkahub.data.starwish.StarWishTheaterGuide
import me.rerere.rikkahub.data.starwish.StarWishTheaterSeed
import org.json.JSONArray
import org.json.JSONObject

data class StarWishPlotCandidate(
    val title: String,
    val worldview: String,
    val hook: String,
    val relationshipCore: String,
    val mainLine: String,
    val hiddenLine: String,
    val foreshadowing: String,
    val emotionalArc: String,
    val proseStyle: String,
    val highlights: String,
    val overview: String,
    val chapters: List<String>,
    val wordCount: String,
) {
    fun toGuide(): StarWishTheaterGuide = StarWishTheaterGuide(
        worldview = worldview,
        overview = buildString {
            appendLine(overview.trim())
            if (relationshipCore.isNotBlank()) appendLine("\n关系主线：${relationshipCore.trim()}")
            if (mainLine.isNotBlank()) appendLine("\n明线：${mainLine.trim()}")
            if (hiddenLine.isNotBlank()) appendLine("\n暗线：${hiddenLine.trim()}")
            if (foreshadowing.isNotBlank()) appendLine("\n伏笔系统：${foreshadowing.trim()}")
            if (emotionalArc.isNotBlank()) appendLine("\n情绪曲线：${emotionalArc.trim()}")
            if (proseStyle.isNotBlank()) appendLine("\n文风：${proseStyle.trim()}")
            if (hook.isNotBlank()) appendLine("\n核心钩子：${hook.trim()}")
            if (highlights.isNotBlank()) appendLine("\n亮点与爽点：${highlights.trim()}")
        }.trim(),
        chapters = chapters,
        wordCount = wordCount,
    ).normalized()
}

class StarWishPlotGeneratorVM(
    private val store: StarWishStore,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val apiUsageStore: ApiUsageStore,
    private val companionRuntime: CompanionRuntime,
) : ViewModel() {
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()
    private val _candidates = MutableStateFlow<List<StarWishPlotCandidate>>(emptyList())
    val candidates = _candidates.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun clear() {
        _candidates.value = emptyList()
        _error.value = null
    }

    fun generate(existingTitle: String?, existingPremise: String?, direction: String) {
        if (_isGenerating.value) return
        viewModelScope.launch {
            try {
                _isGenerating.value = true
                _error.value = null
                _candidates.value = generateCandidates(existingTitle, existingPremise, direction.trim())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _error.value = e.message ?: "剧情方案生成失败"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun applyToExisting(theaterTitle: String, candidate: StarWishPlotCandidate) {
        viewModelScope.launch {
            store.update { current ->
                current.copy(theaterGuides = current.theaterGuides + (theaterTitle to candidate.toGuide()))
            }
        }
    }

    fun createFromCandidate(candidate: StarWishPlotCandidate) {
        viewModelScope.launch {
            val cleanTitle = candidate.title.trim().ifBlank { "未命名小剧场" }
            val seed = StarWishTheaterSeed(
                id = "generated-${System.currentTimeMillis()}-${cleanTitle.hashCode()}",
                title = cleanTitle,
                prompt = candidate.worldview.ifBlank { candidate.overview }.trim(),
                createdAt = System.currentTimeMillis(),
            )
            store.update { current ->
                current.copy(
                    customTheaters = current.customTheaters + seed,
                    theaterGuides = current.theaterGuides + (cleanTitle to candidate.toGuide()),
                )
            }
        }
    }

    private suspend fun generateCandidates(existingTitle: String?, existingPremise: String?, direction: String): List<StarWishPlotCandidate> {
        val settings = settingsStore.settingsFlow.first()
        val selectedModel: Model? = settings.theaterModelId?.let { settings.findModelById(it) }
        val model = selectedModel?.takeIf { it.type == ModelType.CHAT }
            ?: error("请先在默认模型里设置“小剧场模型”。")
        val providerSetting = model.findProvider(settings.providers)
            ?: error("小剧场模型没有找到对应提供商。")
        val provider = providerManager.getProviderByType(providerSetting)
        val assistant = settings.getCurrentAssistant()
        val companionContext = companionRuntime.perception(
            CompanionPerceptionInput(
                assistantId = assistant.id.toString(),
                assistantName = assistant.name,
                persona = assistant.systemPrompt,
                nowMillis = System.currentTimeMillis(),
            ),
        ).toPromptContext()
        val task = buildString {
            appendLine("你是成熟的长篇类型小说总策划。一次设计3套差异明显、能够真正展开成小说的小剧场方案。")
            appendLine("核心陪伴角色：${assistant.name.ifBlank { "当前角色" }}。必须遵守角色人设、关系边界与语言习惯。")
            if (!existingTitle.isNullOrBlank()) appendLine("正在为已有小说《$existingTitle》重新规划；新方案不能只换名字。")
            if (!existingPremise.isNullOrBlank()) appendLine("原始世界观与当前规划：$existingPremise")
            appendLine(if (direction.isBlank()) "用户未指定方向，请主动创造新鲜但自洽的故事。" else "用户最高优先级偏好：$direction")
            appendLine()
            appendLine("三套方案核心驱动力必须不同：关系变化、共同秘密/契约、身份反转/循环、冒险悬疑、轻喜剧治愈、甜中带刀、强强对抗等轮换。不得三套都依赖反派。")
            appendLine("外部冲突只负责施压，人物选择、关系变化和未知探索才是核心。")
            appendLine("用成熟类型文学技法：具体场景、动作与可回收细节；不要模仿或复刻任何具体作者的独特文风。")
            appendLine()
            appendLine("每套必须详细设计：")
            appendLine("1. 世界规则、人物处境和一眼想读的开篇钩子。")
            appendLine("2. 明线目标、暗线真相、关系主线、阶段性矛盾和最终选择。")
            appendLine("3. 至少4个伏笔，逐项写清埋设章节、表面含义、真实含义、回收章节和回收效果。")
            appendLine("4. 读者情绪曲线：好奇、心动/爽点、压迫或误会、中段高潮、反转、终局释放与余韵。")
            appendLine("5. 6章详细规划，每章写具体事件、人物主动选择、关系变化、埋伏笔/收伏笔、章节结尾钩子；每章不少于100字。")
            appendLine("6. 文风应描述可执行技法，例如镜头距离、五感密度、对白节奏、留白、意象、心理描写比例；不要只写‘唯美’‘细腻’。")
            appendLine()
            appendLine("只输出JSON，不要Markdown代码块，不要解释。顶层必须是数组，恰好3个对象。字段严格如下：")
            appendLine("[{\"title\":\"\",\"worldview\":\"\",\"hook\":\"\",\"relationshipCore\":\"\",\"mainLine\":\"\",\"hiddenLine\":\"\",\"foreshadowing\":\"\",\"emotionalArc\":\"\",\"proseStyle\":\"\",\"highlights\":\"\",\"overview\":\"\",\"wordCount\":\"1600-2600\",\"chapters\":[\"\",\"\",\"\",\"\",\"\",\"\"]}]")
        }
        val messages = transformMessages(
            messages = listOf(
                UIMessage.system(buildString {
                    appendLine("人物关系与用户探索感优先。方案要像上帝视角的小说设计，不是宣传简介。严格返回可解析JSON。")
                    if (assistant.systemPrompt.isNotBlank()) appendLine("角色人设：\n${assistant.systemPrompt}")
                    if (assistant.appearancePrompt.isNotBlank()) appendLine("角色外貌：\n${assistant.appearancePrompt}")
                }.trim()),
                companionContext.takeIf(String::isNotBlank)?.let(UIMessage::system),
                UIMessage.user(task),
            ).filterNotNull(),
            assistant = assistant,
            modeInjections = settings.modeInjections,
            lorebooks = settings.lorebooks,
        )
        val chunk = provider.generateText(
            providerSetting = providerSetting,
            messages = messages,
            params = TextGenerationParams(
                model = model,
                temperature = 0.92f,
                topP = 0.95f,
                maxTokens = 7000,
                reasoningLevel = ReasoningLevel.OFF,
            ),
        )
        chunk.usage?.let { usage ->
            apiUsageStore.record(
                source = ApiUsageSource.OTHER,
                title = "星愿馆：剧情生成器",
                model = model.displayName.ifBlank { model.modelId },
                provider = providerSetting.name.ifBlank { providerSetting.id.toString() },
                usage = usage,
            )
        }
        val text = chunk.choices.firstOrNull()?.message?.toText()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: error("剧情生成器没有返回内容。")
        val candidates = parseJsonCandidates(text).ifEmpty { parseLooseCandidates(text) }
        return candidates.take(3).takeIf { it.isNotEmpty() }
            ?: error("剧情已经生成，但格式仍无法识别。请保留错误日志后再试一次。")
    }

    private fun parseJsonCandidates(raw: String): List<StarWishPlotCandidate> = runCatching {
        var clean = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
            .replace('“', '"').replace('”', '"')
            .replace(Regex(",\\s*([}\\]])"), "$1")
        val firstArray = clean.indexOf('[')
        val lastArray = clean.lastIndexOf(']')
        if (firstArray >= 0 && lastArray > firstArray) clean = clean.substring(firstArray, lastArray + 1)
        val array = JSONArray(clean)
        buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                candidateFromJson(obj)?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private fun candidateFromJson(obj: JSONObject): StarWishPlotCandidate? {
        fun text(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
            obj.optString(key).trim().takeIf(String::isNotBlank)
        }.orEmpty()
        val chaptersArray = obj.optJSONArray("chapters") ?: obj.optJSONArray("章节")
        val chapters = buildList {
            if (chaptersArray != null) {
                for (i in 0 until chaptersArray.length()) add(chaptersArray.optString(i).trim())
            }
        }
        val title = text("title", "标题")
        val overview = text("overview", "总览", "总纲")
        if (title.isBlank() || overview.isBlank() || chapters.count(String::isNotBlank) < 4) return null
        return StarWishPlotCandidate(
            title = title,
            worldview = text("worldview", "世界观", "世界设定"),
            hook = text("hook", "钩子"),
            relationshipCore = text("relationshipCore", "关系主线"),
            mainLine = text("mainLine", "明线"),
            hiddenLine = text("hiddenLine", "暗线"),
            foreshadowing = text("foreshadowing", "伏笔", "伏笔系统"),
            emotionalArc = text("emotionalArc", "情绪曲线"),
            proseStyle = text("proseStyle", "文风", "叙事风格"),
            highlights = text("highlights", "亮点", "爽点"),
            overview = overview,
            chapters = chapters,
            wordCount = text("wordCount", "字数").ifBlank { "1600-2600" },
        )
    }

    private fun parseLooseCandidates(text: String): List<StarWishPlotCandidate> {
        val blocks = text.split(Regex("(?:^|\\n)\\s*(?:={2,}|#{1,4})?\\s*方案\\s*[一二三123]\\s*(?:={2,})?", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)))
            .map(String::trim).filter(String::isNotBlank)
        return blocks.mapNotNull { block ->
            fun value(vararg labels: String): String {
                val allLabels = listOf("标题", "世界观", "钩子", "关系主线", "明线", "暗线", "伏笔系统", "情绪曲线", "文风", "亮点", "总览", "总纲", "字数") + (1..12).map { "第${it}章" }
                val labelPattern = labels.joinToString("|") { Regex.escape(it) }
                val nextPattern = allLabels.filterNot { it in labels }.joinToString("|") { Regex.escape(it) }
                return Regex("(?:^|\\n)\\s*(?:[-*#]+\\s*)?(?:$labelPattern)[：:]\\s*([\\s\\S]*?)(?=\\n\\s*(?:[-*#]+\\s*)?(?:$nextPattern)[：:]|$)", RegexOption.IGNORE_CASE)
                    .find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            }
            val chapters = (1..12).map { value("第${it}章") }.takeWhile { it.isNotBlank() }.ifEmpty { (1..6).map { value("第${it}章") } }
            val title = value("标题")
            val overview = value("总览", "总纲")
            if (title.isBlank() || overview.isBlank() || chapters.count(String::isNotBlank) < 4) null else StarWishPlotCandidate(
                title, value("世界观"), value("钩子"), value("关系主线"), value("明线"), value("暗线"), value("伏笔系统"), value("情绪曲线"), value("文风"), value("亮点"), overview, chapters, value("字数").ifBlank { "1600-2600" },
            )
        }
    }
}
