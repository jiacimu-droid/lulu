package me.rerere.rikkahub.ui.pages.starwish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.ApiUsageSource
import me.rerere.rikkahub.data.ai.ApiUsageStore
import me.rerere.rikkahub.data.ai.transformers.transformMessages
import me.rerere.rikkahub.data.companion.CompanionLifeEvent
import me.rerere.rikkahub.data.companion.CompanionLifeEventSource
import me.rerere.rikkahub.data.companion.CompanionLifeEventStatus
import me.rerere.rikkahub.data.companion.CompanionLifeEventType
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.companion.CompanionTurnMutation
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.starwish.StarWishRules
import me.rerere.rikkahub.data.starwish.StarWishState
import me.rerere.rikkahub.data.starwish.StarWishStore
import me.rerere.rikkahub.data.starwish.StarWishTheaterChapter
import me.rerere.rikkahub.data.starwish.StarWishTheaterGuide
import me.rerere.rikkahub.data.starwish.StarWishTheaterSeed

class StarWishVM(
    private val store: StarWishStore,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val apiUsageStore: ApiUsageStore,
    private val companionRuntime: CompanionRuntime,
) : ViewModel() {
    val state: StateFlow<StarWishState> = store.state
    private val _isGeneratingChapter = MutableStateFlow(false)
    val isGeneratingChapter = _isGeneratingChapter.asStateFlow()
    private val _chapterError = MutableStateFlow<String?>(null)
    val chapterError = _chapterError.asStateFlow()

    fun addCustomTheater(title: String, prompt: String) {
        val cleanTitle = title.trim()
        val cleanPrompt = prompt.trim()
        if (cleanTitle.isEmpty() || cleanPrompt.isEmpty()) return
        viewModelScope.launch {
            store.update { current ->
                if (current.customTheaters.any { it.title == cleanTitle }) current else current.copy(
                    customTheaters = current.customTheaters + StarWishTheaterSeed(
                        id = "custom-${System.currentTimeMillis()}-${cleanTitle.hashCode()}",
                        title = cleanTitle,
                        prompt = cleanPrompt,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun deleteTheater(title: String) = update { current ->
        current.copy(
            customTheaters = current.customTheaters.filterNot { it.title == title },
            theaterChapters = current.theaterChapters - title,
            theaterGuides = current.theaterGuides - title,
        )
    }

    fun deleteChapter(theater: String, chapterId: String) = update { current ->
        val chapters = current.theaterChapters[theater].orEmpty()
            .filterNot { it.id == chapterId }
            .mapIndexed { index, chapter -> chapter.copy(chapter = index + 1, title = "第 ${index + 1} 章") }
        current.copy(theaterChapters = current.theaterChapters + (theater to chapters))
    }

    fun saveTheaterGuide(title: String, guide: StarWishTheaterGuide) = update {
        it.copy(theaterGuides = it.theaterGuides + (title to guide.normalized()))
    }

    fun createNextChapter(theater: String, influence: String = "") {
        viewModelScope.launch {
            try {
                _isGeneratingChapter.value = true
                _chapterError.value = null
                val snapshot = state.value
                val seed = snapshot.customTheaters.firstOrNull { it.title == theater }
                    ?: error("小剧场不存在")
                val chapters = snapshot.theaterChapters[theater].orEmpty()
                val nextChapter = chapters.size + 1
                val generated = generateChapter(
                    seed = seed,
                    chapters = chapters,
                    nextChapter = nextChapter,
                    influence = influence.trim(),
                    guide = snapshot.theaterGuides[theater] ?: StarWishRules.defaultTheaterGuide(seed),
                )
                val chapter = StarWishTheaterChapter(
                    id = "theater-${System.currentTimeMillis()}-${theater.hashCode()}",
                    theater = theater,
                    chapter = nextChapter,
                    title = "第 $nextChapter 章",
                    content = generated.content,
                    userInfluence = influence.trim(),
                    createdAt = System.currentTimeMillis(),
                )
                store.update { current ->
                    current.copy(theaterChapters = current.theaterChapters + (theater to (current.theaterChapters[theater].orEmpty() + chapter)))
                }
                val now = System.currentTimeMillis()
                companionRuntime.applyTurn(
                    CompanionTurnMutation(
                        assistantId = generated.assistantId,
                        lifeEvents = listOf(
                            CompanionLifeEvent(
                                id = "starwish:${chapter.id}",
                                assistantId = generated.assistantId,
                                type = CompanionLifeEventType.TOOL_ACTION,
                                status = CompanionLifeEventStatus.COMPLETED,
                                title = "写入了小剧场新章节",
                                summary = "参与完成《${chapter.theater}》第 ${chapter.chapter} 章。",
                                source = CompanionLifeEventSource.AGENT,
                                evidenceReference = chapter.id,
                                importance = 3,
                                startedAt = now,
                                endedAt = now,
                                createdAt = now,
                            ),
                        ),
                        nowMillis = now,
                    ),
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _chapterError.value = error.message ?: "小剧场生成失败"
            } finally {
                _isGeneratingChapter.value = false
            }
        }
    }

    private suspend fun generateChapter(
        seed: StarWishTheaterSeed,
        chapters: List<StarWishTheaterChapter>,
        nextChapter: Int,
        influence: String,
        guide: StarWishTheaterGuide,
    ): GeneratedChapter {
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.assistants.firstOrNull { it.id == settings.assistantId }
            ?: error("请先创建并选择角色")
        val model = settings.theaterModelId?.let { settings.findModelById(it) }
            ?.takeIf { it.type == ModelType.CHAT }
            ?: error("请先设置小剧场模型")
        val providerSetting = model.findProvider(settings.providers)
            ?: error("小剧场模型没有对应提供商")
        val provider = providerManager.getProviderByType(providerSetting)
        val messages = transformMessages(
            messages = listOf(
                UIMessage.system(buildString {
                    appendLine("你正在创作同一本连续小说，只输出本章正文。")
                    appendLine("核心角色：${assistant.name.ifBlank { "当前角色" }}")
                    if (assistant.systemPrompt.isNotBlank()) appendLine(assistant.systemPrompt)
                }),
                UIMessage.user(StarWishRules.theaterChapterPrompt(seed, chapters, nextChapter, influence, guide)),
            ),
            assistant = assistant,
            modeInjections = settings.modeInjections,
            lorebooks = settings.lorebooks,
        )
        val chunk = provider.generateText(
            providerSetting = providerSetting,
            messages = messages,
            params = TextGenerationParams(
                model = model,
                temperature = 0.82f,
                topP = 0.93f,
                maxTokens = 4200,
                reasoningLevel = ReasoningLevel.OFF,
            ),
        )
        chunk.usage?.let { usage ->
            apiUsageStore.record(
                source = ApiUsageSource.OTHER,
                title = "星愿馆：小剧场",
                model = model.displayName.ifBlank { model.modelId },
                provider = providerSetting.name.ifBlank { providerSetting.id.toString() },
                usage = usage,
            )
        }
        val content = chunk.choices.firstOrNull()?.message?.toText()?.trim()
            ?.takeIf(String::isNotBlank)
            ?: error("小剧场模型没有返回正文")
        return GeneratedChapter(content, assistant.id.toString())
    }

    private fun update(transform: (StarWishState) -> StarWishState) {
        viewModelScope.launch { store.update(transform) }
    }

    private data class GeneratedChapter(val content: String, val assistantId: String)
}
