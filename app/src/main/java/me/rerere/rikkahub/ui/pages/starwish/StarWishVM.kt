package me.rerere.rikkahub.ui.pages.starwish

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
import me.rerere.rikkahub.data.companion.CompanionLifeEvent
import me.rerere.rikkahub.data.companion.CompanionLifeEventSource
import me.rerere.rikkahub.data.companion.CompanionLifeEventStatus
import me.rerere.rikkahub.data.companion.CompanionLifeEventType
import me.rerere.rikkahub.data.companion.CompanionPerceptionInput
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.companion.CompanionTurnMutation
import me.rerere.rikkahub.data.companion.toPromptContext
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.starwish.StarWishGeneratedImage
import me.rerere.rikkahub.data.starwish.StarWishImageLaunch
import me.rerere.rikkahub.data.starwish.StarWishOutfitPrompts
import me.rerere.rikkahub.data.starwish.StarWishRules
import me.rerere.rikkahub.data.starwish.StarWishState
import me.rerere.rikkahub.data.starwish.StarWishStore
import me.rerere.rikkahub.data.starwish.StarWishTheaterChapter
import me.rerere.rikkahub.data.starwish.StarWishTheaterGuide
import me.rerere.rikkahub.data.starwish.StarWishTheaterSeed
import me.rerere.rikkahub.data.starwish.StarWishVideoItem
import me.rerere.rikkahub.data.study.StudyStore

class StarWishVM(
    private val store: StarWishStore,
    private val studyStore: StudyStore,
    private val genMediaRepository: GenMediaRepository,
    private val filesManager: FilesManager,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val apiUsageStore: ApiUsageStore,
    private val companionRuntime: CompanionRuntime,
) : ViewModel() {
    val state: StateFlow<StarWishState> = store.state
    val studyState = studyStore.state
    private val _videoPlayback = MutableSharedFlow<StarWishVideoItem>(extraBufferCapacity = 8)
    val videoPlayback = _videoPlayback
    private val _videoMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val videoMessage = _videoMessage
    private val _generatedImages = MutableStateFlow<List<StarWishGeneratedImage>>(emptyList())
    val generatedImages = _generatedImages.asStateFlow()
    private val _isGeneratingChapter = MutableStateFlow(false)
    val isGeneratingChapter = _isGeneratingChapter.asStateFlow()
    private val _chapterError = MutableStateFlow<String?>(null)
    val chapterError = _chapterError.asStateFlow()

    init {
        viewModelScope.launch {
            state.collectLatest {
                refreshGeneratedImages()
            }
        }
    }

    fun savePrompts(outfit: String, prompts: StarWishOutfitPrompts) {
        viewModelScope.launch {
            store.update {
                it.copy(customOutfitPrompts = it.customOutfitPrompts + (outfit to prompts))
            }
        }
    }

    fun recordImageLaunch(outfit: String, prompt: String) {
        viewModelScope.launch {
            val launch = StarWishImageLaunch(
                id = "image-${System.currentTimeMillis()}-${outfit.hashCode()}",
                outfit = outfit,
                prompt = prompt,
                createdAt = System.currentTimeMillis(),
            )
            store.update {
                it.copy(imageLaunches = (listOf(launch) + it.imageLaunches).take(80))
            }
        }
    }

    fun refreshGeneratedImages() {
        viewModelScope.launch {
            val current = state.value
            val launches = current.imageLaunches.filterNot { it.id in current.hiddenImageLaunchIds }
            val imagesDir = filesManager.getImagesDir()
            val media = genMediaRepository.listAllMedia()
            val hiddenMedia = media.filter { it.id in current.hiddenGeneratedImageIds }
            if (hiddenMedia.isNotEmpty()) {
                hiddenMedia.forEach { entity ->
                    runCatching {
                        File(imagesDir, entity.path.removePrefix("images/")).delete()
                    }
                }
                genMediaRepository.deleteMediaByIds(hiddenMedia.map { it.id })
                store.update { state ->
                    state.copy(hiddenGeneratedImageIds = state.hiddenGeneratedImageIds - hiddenMedia.map { it.id }.toSet())
                }
            }
            val hiddenIds = hiddenMedia.map { it.id }.toSet()
            _generatedImages.value = media.mapNotNull { entity ->
                if (entity.id in current.hiddenGeneratedImageIds || entity.id in hiddenIds) return@mapNotNull null
                val launch = launches.firstOrNull { it.prompt == entity.prompt }
                StarWishGeneratedImage(
                    id = entity.id,
                    outfit = launch?.outfit ?: "生成图库",
                    filePath = File(imagesDir, entity.path.removePrefix("images/")).absolutePath,
                    prompt = entity.prompt,
                    createdAt = entity.createAt,
                    fromStarWish = launch != null,
                )
            }
        }
    }

    fun importVideo(uri: Uri) {
        viewModelScope.launch {
            val localUri = filesManager.createChatFilesByContents(listOf(uri)).firstOrNull()
            if (localUri == null) {
                _videoMessage.tryEmit("视频导入失败")
                return@launch
            }
            val sourceName = filesManager.getFileNameFromUri(uri) ?: "星愿视频"
            val item = StarWishVideoItem(
                id = "custom-video-${System.currentTimeMillis()}-${sourceName.hashCode()}",
                title = sourceName.substringBeforeLast('.').ifBlank { "星愿视频" },
                uri = localUri.toString(),
                builtIn = false,
                createdAt = System.currentTimeMillis(),
            )
            store.update { current ->
                current.copy(customVideos = current.customVideos + item)
            }
            _videoMessage.tryEmit("已加入视频柜，使用视频碎片后可解锁")
        }
    }

    fun unlockNextVideoOrPlayRandom() {
        viewModelScope.launch {
            val currentStarWish = state.value
            val visibleVideos = StarWishRules.allVideos(currentStarWish.customVideos)
                .filterNot { it.id in currentStarWish.hiddenVideoIds }
            val hasLockedVideo = visibleVideos.any { it.id !in currentStarWish.unlockedVideoIds }
            var result = StarWishRules.unlockNextVideo(currentStarWish, studyState.value, Random.Default)
            val video = result.video
            if (video == null) {
                _videoMessage.tryEmit(if (visibleVideos.isEmpty()) "先上传或内置视频后再解锁" else "还需要 1 枚视频碎片")
                return@launch
            }
            if (result.consumedFragment) {
                studyStore.update { currentStudy ->
                    result = StarWishRules.unlockNextVideo(currentStarWish, currentStudy, Random.Default)
                    result.studyState
                }
                if (!result.consumedFragment || result.video == null) {
                    _videoMessage.tryEmit("还需要 1 枚视频碎片")
                    return@launch
                }
                store.update { result.starWishState }
                _videoMessage.tryEmit("已解锁：${result.video!!.title}")
            } else if (hasLockedVideo) {
                _videoMessage.tryEmit("还需要 1 枚视频碎片")
                return@launch
            }
            _videoPlayback.emit(result.video ?: video)
        }
    }

    fun playVideo(video: StarWishVideoItem) {
        viewModelScope.launch {
            if (video.id in state.value.unlockedVideoIds) {
                _videoPlayback.emit(video)
            } else {
                _videoMessage.tryEmit("这个视频还没有解锁")
            }
        }
    }

    fun deleteVideo(video: StarWishVideoItem) {
        viewModelScope.launch {
            if (!video.builtIn) {
                runCatching { filesManager.deleteChatFiles(listOf(video.uri.toUri())) }
            }
            store.update { current ->
                current.copy(
                    customVideos = current.customVideos.filterNot { it.id == video.id },
                    hiddenVideoIds = current.hiddenVideoIds + video.id,
                    unlockedVideoIds = current.unlockedVideoIds - video.id,
                )
            }
        }
    }

    fun createNextChapter(theater: String, influence: String = "") {
        viewModelScope.launch {
            try {
                _isGeneratingChapter.value = true
                _chapterError.value = null
                val seed = StarWishRules.allTheaters(state.value.customTheaters).firstOrNull { it.title == theater } ?: return@launch
                val study = studyState.value
                if (study.inventory.theaterFragments < StarWishRules.RARE_FRAGMENTS_PER_CHAPTER) return@launch
                val chapters = state.value.theaterChapters[theater].orEmpty().filterNot { it.isPromptPlaceholder(seed) }
                val guide = state.value.theaterGuides[theater] ?: StarWishRules.defaultTheaterGuide(seed)
                val nextChapter = chapters.size + 1
                val generated = generateTheaterChapterContent(seed, chapters, nextChapter, influence.trim(), guide)
                studyStore.update { current ->
                    current.copy(
                        inventory = current.inventory.copy(
                            theaterFragments = (current.inventory.theaterFragments - StarWishRules.RARE_FRAGMENTS_PER_CHAPTER).coerceAtLeast(0),
                        ),
                    )
                }
                var createdChapter: StarWishTheaterChapter? = null
                store.update { current ->
                    val latestChapters = current.theaterChapters[theater].orEmpty().filterNot { it.isPromptPlaceholder(seed) }
                    val chapter = StarWishTheaterChapter(
                        id = "theater-${System.currentTimeMillis()}-${theater.hashCode()}",
                        theater = theater,
                        chapter = nextChapter,
                        title = "第 $nextChapter 章",
                        content = generated.content,
                        userInfluence = influence.trim(),
                        createdAt = System.currentTimeMillis(),
                    )
                    createdChapter = chapter
                    current.copy(theaterChapters = current.theaterChapters + (theater to (latestChapters + chapter)))
                }
                createdChapter?.let { chapter ->
                    val nowMillis = System.currentTimeMillis()
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
                                    summary = "参与完成《${chapter.theater}》第 ${chapter.chapter} 章《${chapter.title}》。",
                                    source = CompanionLifeEventSource.AGENT,
                                    evidenceReference = chapter.id,
                                    importance = 3,
                                    startedAt = nowMillis,
                                    endedAt = nowMillis,
                                    createdAt = nowMillis,
                                ),
                            ),
                            nowMillis = nowMillis,
                        ),
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _chapterError.value = e.message ?: "小剧场生成失败"
            } finally {
                _isGeneratingChapter.value = false
            }
        }
    }

    fun deleteChapter(theater: String, chapterId: String) {
        viewModelScope.launch {
            store.update { current ->
                val updated = current.theaterChapters[theater].orEmpty()
                    .filterNot { it.id == chapterId }
                    .mapIndexed { index, chapter ->
                        chapter.copy(
                            chapter = index + 1,
                            title = "第 ${index + 1} 章",
                        )
                    }
                current.copy(theaterChapters = current.theaterChapters + (theater to updated))
            }
        }
    }

    fun saveTheaterGuide(title: String, guide: StarWishTheaterGuide) {
        viewModelScope.launch {
            store.update { current ->
                current.copy(theaterGuides = current.theaterGuides + (title to guide.normalized()))
            }
        }
    }

    fun rememberSection(section: String) {
        viewModelScope.launch {
            store.update { it.copy(lastSection = section) }
        }
    }

    fun deleteScroll(title: String, legacyOutfit: String? = null) {
        viewModelScope.launch {
            store.update { current ->
                val keys = setOfNotNull(title, legacyOutfit)
                current.copy(
                    hiddenScrollTitles = current.hiddenScrollTitles + title,
                    customOutfitPrompts = current.customOutfitPrompts - keys,
                    imageLaunches = current.imageLaunches.filterNot { it.outfit in keys },
                )
            }
        }
    }

    fun deleteImageLaunch(id: String) {
        viewModelScope.launch {
            store.update { current ->
                current.copy(hiddenImageLaunchIds = current.hiddenImageLaunchIds + id)
            }
        }
    }

    fun deleteGeneratedImage(id: Int) {
        viewModelScope.launch {
            store.update { current ->
                current.copy(hiddenGeneratedImageIds = current.hiddenGeneratedImageIds + id)
            }
        }
    }

    fun deleteTheater(title: String) {
        viewModelScope.launch {
            store.update { current ->
                current.copy(
                    hiddenTheaterTitles = current.hiddenTheaterTitles + title,
                    customTheaters = current.customTheaters.filterNot { it.title == title },
                    theaterChapters = current.theaterChapters - title,
                    theaterGuides = current.theaterGuides - title,
                )
            }
        }
    }

    private suspend fun generateTheaterChapterContent(
        seed: StarWishTheaterSeed,
        chapters: List<StarWishTheaterChapter>,
        nextChapter: Int,
        influence: String,
        guide: StarWishTheaterGuide,
    ): GeneratedTheaterChapterContent {
        val settings = settingsStore.settingsFlow.first()
        val selectedModel: Model? = settings.theaterModelId?.let { settings.findModelById(it) }
        val model = selectedModel
            ?.takeIf { selected -> selected.type == ModelType.CHAT }
            ?: error("请先在默认模型里设置“小剧场模型”，用来生成小剧场正文。")
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
        val basePrompt = StarWishRules.theaterChapterPrompt(seed, chapters, nextChapter, influence, guide)
        val previous = chapters.lastOrNull()
        val continuityPrompt = buildString {
            appendLine(basePrompt)
            appendLine()
            appendLine("【续写优先级与连续性协议】")
            appendLine("1. 用户在‘影响下一章’中写下的内容拥有最高剧情优先级。只要不直接违背角色底层人设与安全边界，就先服从用户选择。")
            appendLine("2. 剧情大纲是导航和伏笔地图，不是不可偏离的铁轨。若用户行为与本章规划冲突，应让主线延迟、改道、拆分或通过后续事件重新汇合，不得无视用户行为硬拉回原计划。")
            appendLine("3. 新章必须从上一章最后一个有效状态继续。禁止重新演一遍上一章已经完成的动作、对白、到达、拥抱、战斗、决定或发现。")
            appendLine("4. 开篇前先在内部确认：人物当前所在位置、姿势、距离、情绪、已知信息、正在进行但尚未完成的动作、已埋未收的伏笔。不要把这份检查过程输出给读者。")
            appendLine("5. 若需要时间跳跃或场景切换，必须用自然过渡明确交代经过，不能突然重置人物状态。")
            appendLine("6. 续写应像连续小说而不是独立短篇：承接上一章语气、节奏、关系温度和未完句意，同时推进新的事件。")
            appendLine("7. 文风采用成熟类型小说技法：具体意象、五感、空间关系、动作细节、心理变化、对白潜台词、留白与节奏变化。不要只做事件流水账，也不要堆砌空泛形容词。")
            appendLine("8. 伏笔必须有生命周期：新埋伏笔要有可识别载体；回收旧伏笔时要让读者产生‘原来如此’而不是生硬解释。明线、暗线与关系线至少推进其中两条。")
            if (influence.isNotBlank()) {
                appendLine()
                appendLine("【本章用户最高优先级影响】")
                appendLine(influence)
            }
            if (previous != null) {
                appendLine()
                appendLine("【上一章末尾连续性锚点】")
                appendLine("上一章编号：${previous.chapter}")
                appendLine("上一章用户影响：${previous.userInfluence.ifBlank { "无" }}")
                appendLine("上一章最后约1200字（新章必须从这里之后继续，禁止重演）：")
                appendLine(previous.content.takeLast(1200))
            }
        }.trim()
        val messages = transformMessages(
            messages = listOf(
                UIMessage.system(buildString {
                    appendLine(
                        "小剧场中的核心陪伴角色是 ${assistant.name.ifBlank { "当前角色" }}，" +
                            "必须遵守其人设、关系边界与语言习惯。",
                    )
                    appendLine("你正在写同一本连续小说。输出只能是本章正文，不得输出大纲、分析、连续性检查、写作说明或JSON。")
                    appendLine("不得模仿具体在世作者的独特文风；应使用类型文学的通用高水平技法，形成自然、细腻、有意境但清晰可读的中文叙事。")
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
                UIMessage.user(continuityPrompt),
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
            ?.takeIf { it.isNotBlank() }
            ?: error("小剧场 API 没有返回正文。")
        return GeneratedTheaterChapterContent(
            content = content,
            assistantId = assistant.id.toString(),
        )
    }

    private data class GeneratedTheaterChapterContent(
        val content: String,
        val assistantId: String,
    )

    fun addCustomTheater(title: String, prompt: String) {
        val cleanTitle = title.trim()
        val cleanPrompt = prompt.trim()
        if (cleanTitle.isBlank() || cleanPrompt.isBlank()) return
        viewModelScope.launch {
            val seed = StarWishTheaterSeed(
                id = "custom-${System.currentTimeMillis()}-${cleanTitle.hashCode()}",
                title = cleanTitle,
                prompt = cleanPrompt,
                createdAt = System.currentTimeMillis(),
            )
            store.update { current ->
                current.copy(customTheaters = current.customTheaters + seed)
            }
        }
    }
}
