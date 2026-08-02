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

data class StarWishPlotCandidate(
    val title: String,
    val hook: String,
    val relationshipCore: String,
    val highlights: String,
    val overview: String,
    val chapters: List<String>,
    val wordCount: String,
) {
    fun toGuide(): StarWishTheaterGuide = StarWishTheaterGuide(
        overview = buildString {
            appendLine(overview.trim())
            if (relationshipCore.isNotBlank()) appendLine("\n关系主线：${relationshipCore.trim()}")
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
                prompt = candidate.overview.trim(),
                createdAt = System.currentTimeMillis(),
            )
            store.update { current ->
                current.copy(
                    customTheaters = current.customTheaters + seed,
                    theaterGuides = current.theaterGuides + (cleanTitle to candidate.toGuide()),
                    hiddenTheaterTitles = current.hiddenTheaterTitles - cleanTitle,
                )
            }
        }
    }

    private suspend fun generateCandidates(
        existingTitle: String?,
        existingPremise: String?,
        direction: String,
    ): List<StarWishPlotCandidate> {
        val settings = settingsStore.settingsFlow.first()
        val selectedModel: Model? = settings.theaterModelId?.let { settings.findModelById(it) }
        val model = selectedModel
            ?.takeIf { it.type == ModelType.CHAT }
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
            appendLine("你是一名擅长商业网文、角色关系和情绪张力的剧情策划。请一次提出 3 套差异显著、真正有阅读吸引力的小剧场方案。")
            appendLine("核心陪伴角色：${assistant.name.ifBlank { "当前角色" }}。必须尊重角色人设、关系边界与语言习惯。")
            if (!existingTitle.isNullOrBlank()) appendLine("这是为已有小说《$existingTitle》重新规划走向。")
            if (!existingPremise.isNullOrBlank()) appendLine("原始设定可借鉴但不必拘泥：$existingPremise")
            if (direction.isNotBlank()) appendLine("用户给出的偏好方向：$direction")
            else appendLine("用户没有给方向，请主动创造意外但合理的好故事。")
            appendLine()
            appendLine("三套方案必须使用不同的核心驱动力，优先从下列类型中轮换，不得三套都写成打反派：")
            appendLine("1. 双人关系变化、暧昧拉扯、信任建立、误会与和解、身份差带来的张力。")
            appendLine("2. 共同秘密、契约、同居或同行、日常细节中逐步升级的情感。")
            appendLine("3. 身份反转、时间循环、记忆错位、立场冲突、不得不合作。")
            appendLine("4. 冒险、悬疑或反派线，但外部事件必须服务于人物关系，而不是只打怪升级。")
            appendLine("5. 轻喜剧、治愈、甜中带刀、酸涩克制或高张力强强对抗等不同情绪风格。")
            appendLine()
            appendLine("每套都要有：一眼想点开的世界观钩子；明确的双人关系主线；持续升级的矛盾；伏笔与回收；至少一个意外反转；中段高潮；终局高潮；独特爽点或心动点。")
            appendLine("避免空泛词语，章节规划必须写出具体事件、人物选择和关系变化。规划 6 章，每章 2—4 句。")
            appendLine()
            appendLine("严格按以下纯文本格式输出，不要写额外说明：")
            appendLine("===方案1===")
            appendLine("标题：")
            appendLine("钩子：")
            appendLine("关系主线：")
            appendLine("亮点：")
            appendLine("总览：")
            appendLine("字数：1200-2200")
            appendLine("第1章：")
            appendLine("第2章：")
            appendLine("第3章：")
            appendLine("第4章：")
            appendLine("第5章：")
            appendLine("第6章：")
            appendLine("===方案2===（同格式）")
            appendLine("===方案3===（同格式）")
        }

        val messages = transformMessages(
            messages = listOf(
                UIMessage.system(buildString {
                    appendLine("你必须把人物关系变化放在故事核心。外部反派和任务只能作为压力装置，不得喧宾夺主。")
                    if (assistant.systemPrompt.isNotBlank()) {
                        appendLine("角色人设：")
                        appendLine(assistant.systemPrompt)
                    }
                    if (assistant.appearancePrompt.isNotBlank()) {
                        appendLine("角色外貌：")
                        appendLine(assistant.appearancePrompt)
                    }
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
                temperature = 1.05f,
                topP = 0.98f,
                maxTokens = 4200,
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
        return parseCandidates(text).takeIf { it.isNotEmpty() }
            ?: error("剧情方案格式解析失败，请重新生成一次。")
    }

    private fun parseCandidates(text: String): List<StarWishPlotCandidate> {
        val blocks = text.split(Regex("===\\s*方案\\s*\\d+\\s*===", RegexOption.IGNORE_CASE))
            .map(String::trim)
            .filter(String::isNotBlank)
        return blocks.mapNotNull { block ->
            fun value(label: String, nextLabels: List<String>): String {
                val end = nextLabels.joinToString("|") { Regex.escape(it) }
                val pattern = if (end.isBlank()) {
                    Regex("${Regex.escape(label)}[：:]\\s*([\\s\\S]*)")
                } else {
                    Regex("${Regex.escape(label)}[：:]\\s*([\\s\\S]*?)(?=\\n(?:$end)[：:]|$)")
                }
                return pattern.find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            }
            val labels = listOf("标题", "钩子", "关系主线", "亮点", "总览", "字数") + (1..6).map { "第${it}章" }
            val title = value("标题", labels.drop(1))
            val hook = value("钩子", labels.drop(2))
            val relation = value("关系主线", labels.drop(3))
            val highlights = value("亮点", labels.drop(4))
            val overview = value("总览", labels.drop(5))
            val wordCount = value("字数", labels.drop(6)).ifBlank { "1200-2200" }
            val chapters = (1..6).map { index ->
                val label = "第${index}章"
                value(label, labels.dropWhile { it != label }.drop(1))
            }
            if (title.isBlank() || overview.isBlank() || chapters.count(String::isNotBlank) < 4) null
            else StarWishPlotCandidate(title, hook, relation, highlights, overview, chapters, wordCount)
        }
    }
}
