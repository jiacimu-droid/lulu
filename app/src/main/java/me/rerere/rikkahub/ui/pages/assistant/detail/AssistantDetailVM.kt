package me.rerere.rikkahub.ui.pages.assistant.detail

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.ApiUsageSource
import me.rerere.rikkahub.data.ai.ApiUsageStore
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.SkillMetadata
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantInteractionProfile
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.service.AssistantInteractionResetService
import kotlin.uuid.Uuid

private const val TAG = "AssistantDetailVM"

class AssistantDetailVM(
    private val id: String,
    private val settingsStore: SettingsStore,
    private val interactionResetService: AssistantInteractionResetService,
    private val filesManager: FilesManager,
    private val skillManager: SkillManager,
    private val providerManager: ProviderManager,
    private val apiUsageStore: ApiUsageStore,
) : ViewModel() {
    private val assistantId = Uuid.parse(id)

    private val _skills = MutableStateFlow<List<SkillMetadata>>(emptyList())
    val skills = _skills.asStateFlow()

    private val _historyClearState = MutableStateFlow<HistoryClearState>(HistoryClearState.Idle)
    val historyClearState = _historyClearState.asStateFlow()

    private val _interactionGenerationState = MutableStateFlow<InteractionGenerationState>(InteractionGenerationState.Idle)
    val interactionGenerationState = _interactionGenerationState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _skills.value = skillManager.listSkills()
        }
    }

    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    val mcpServerConfigs = settingsStore
        .settingsFlow.map { settings ->
            settings.mcpServers
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val assistant: StateFlow<Assistant> = settingsStore
        .settingsFlow
        .map { settings ->
            settings.assistants.find { it.id == assistantId } ?: Assistant()
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = Assistant()
        )

    val providers = settingsStore
        .settingsFlow
        .map { settings ->
            settings.providers
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val tags = settingsStore
        .settingsFlow
        .map { settings ->
            settings.assistantTags
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    fun updateTags(tagIds: List<Uuid>, tags: List<Tag>) {
        viewModelScope.launch {
            val settings = settings.value
            settingsStore.update(
                settings = settings.copy(
                    assistantTags = tags
                )
            )
            update(
                assistant.value.copy(
                    tags = tagIds.toList()
                )
            )
            Log.d(TAG, "updateTags: ${tagIds.joinToString(",")}")
            cleanupUnusedTags()
        }
    }

    fun cleanupUnusedTags() {
        viewModelScope.launch {
            val settings = settings.value
            val validTagIds = settings.assistantTags.map { it.id }.toSet()

            // 清理 assistant 中的无效 tag id
            val cleanedAssistants = settings.assistants.map { assistant ->
                val validTags = assistant.tags.filter { tagId ->
                    validTagIds.contains(tagId)
                }
                if (validTags.size != assistant.tags.size) {
                    assistant.copy(tags = validTags)
                } else {
                    assistant
                }
            }

            // 获取清理后的 assistant 中使用的 tag id
            val usedTagIds = cleanedAssistants.flatMap { it.tags }.toSet()

            // 清理未使用的 tags
            val cleanedTags = settings.assistantTags.filter { tag ->
                usedTagIds.contains(tag.id)
            }

            // 检查是否需要更新
            val needUpdateAssistants = cleanedAssistants != settings.assistants
            val needUpdateTags = cleanedTags.size != settings.assistantTags.size

            if (needUpdateAssistants || needUpdateTags) {
                settingsStore.update(
                    settings = settings.copy(
                        assistants = cleanedAssistants,
                        assistantTags = cleanedTags
                    )
                )
            }
        }
    }

    fun update(assistant: Assistant) {
        viewModelScope.launch {
            val settings = settings.value
            settingsStore.update(
                settings = settings.copy(
                    assistants = settings.assistants.map {
                        if (it.id == assistant.id) {
                            checkAvatarDelete(old = it, new = assistant) // 删除旧头像
                            checkBackgroundDelete(old = it, new = assistant) // 删除旧背景
                            checkFaceReferenceDelete(old = it, new = assistant)
                            assistant
                        } else {
                            it
                        }
                    })
            )
        }
    }

    fun generateInteractionProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_interactionGenerationState.value is InteractionGenerationState.Running) return@launch
            val currentAssistant = assistant.value
            _interactionGenerationState.value = InteractionGenerationState.Running
            _interactionGenerationState.value = runCatching {
                require(currentAssistant.systemPrompt.isNotBlank()) {
                    "请先填写角色资料 / 系统人设，再生成互动设定。"
                }
                val currentSettings = settingsStore.settingsFlow.value
                val model = currentAssistant.chatModelId
                    ?.let { currentSettings.findModelById(it) }
                    ?.takeIf { it.type == ModelType.CHAT }
                    ?: currentSettings.getCurrentChatModel()
                val providerSetting = model.findProvider(currentSettings.providers)
                    ?: error("当前聊天模型没有找到对应提供商。")
                val provider = providerManager.getProviderByType(providerSetting)
                val chunk = provider.generateText(
                    providerSetting = providerSetting,
                    messages = listOf(
                        UIMessage.system(INTERACTION_PROFILE_SYSTEM_PROMPT),
                        UIMessage.user(buildInteractionProfilePrompt(currentAssistant)),
                    ),
                    params = TextGenerationParams(
                        model = model,
                        temperature = 0.2f,
                        topP = 0.8f,
                        maxTokens = 900,
                        reasoningLevel = ReasoningLevel.OFF,
                    ),
                )
                chunk.usage?.let { usage ->
                    apiUsageStore.record(
                        source = ApiUsageSource.OTHER,
                        title = "角色互动设定：${currentAssistant.name.ifBlank { "当前角色" }}",
                        model = model.displayName.ifBlank { model.modelId },
                        provider = providerSetting.name.ifBlank { providerSetting.id.toString() },
                        usage = usage,
                    )
                }
                val raw = chunk.choices.firstOrNull()?.message?.toText().orEmpty()
                val profile = parseAssistantInteractionProfile(raw)
                update(currentAssistant.copy(interactionProfile = profile))
                InteractionGenerationState.Success
            }.getOrElse { error ->
                Log.e(TAG, "Failed to generate interaction profile", error)
                InteractionGenerationState.Failed(error.message ?: "生成失败，请检查模型和 API 设置。")
            }
        }
    }

    fun clearAssistantHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_historyClearState.value is HistoryClearState.Running) return@launch
            _historyClearState.value = HistoryClearState.Running
            _historyClearState.value = runCatching {
                interactionResetService.clearAssistantRecords(assistantId)
            }.fold(
                onSuccess = { HistoryClearState.Success },
                onFailure = { error -> HistoryClearState.Failed(error.message ?: "清除失败，请重试") },
            )
        }
    }

    fun setFaceReferenceImage(assistant: Assistant, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            filesManager.createChatFilesByContents(listOf(uri)).firstOrNull()?.let { localUri ->
                update(assistant.copy(faceReferenceImage = localUri.toString()))
            }
        }
    }

    fun checkAvatarDelete(old: Assistant, new: Assistant) {
        if (old.avatar is Avatar.Image && old.avatar != new.avatar) {
            filesManager.deleteChatFiles(listOf(old.avatar.url.toUri()))
        }
    }

    fun checkBackgroundDelete(old: Assistant, new: Assistant) {
        val oldBackground = old.background
        val newBackground = new.background

        if (oldBackground != null && oldBackground != newBackground) {
            try {
                val oldUri = oldBackground.toUri()
                if (oldUri.scheme == "content" || oldUri.scheme == "file") {
                    filesManager.deleteChatFiles(listOf(oldUri))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete background file: $oldBackground", e)
            }
        }
    }

    fun checkFaceReferenceDelete(old: Assistant, new: Assistant) {
        val oldFace = old.faceReferenceImage
        val newFace = new.faceReferenceImage

        if (oldFace != null && oldFace != newFace) {
            try {
                filesManager.deleteChatFiles(listOf(oldFace.toUri()))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete face reference file: $oldFace", e)
            }
        }
    }
}

private const val INTERACTION_PROFILE_SYSTEM_PROMPT = """
你是角色互动设定分析器。你只分析输入的人设，不扮演角色，也不生成对用户说的话。
必须尊重人设差异：高冷、被动、寡言或明确不主动的角色可以很少甚至绝不主动；热情、恋人、管家、照顾型角色可以更主动，但不能凭标签擅自增加不存在的亲密、监督或占有欲。
只返回一个 JSON 对象，不要 Markdown，不要解释。字段必须恰好为：initiative、sharingDesire、responsibility、followUpStyle、passivity。
每个字段用一到三句简洁中文写成可执行的互动规则，明确触发条件、表达方式和克制边界。不要给数值评分，不要使用空泛词语。
"""

internal fun buildInteractionProfilePrompt(assistant: Assistant): String = buildString {
    appendLine("请根据下面完整人设生成角色的互动设定。")
    appendLine("角色名：${assistant.name.ifBlank { "未命名角色" }}")
    appendLine("<persona>")
    appendLine(assistant.systemPrompt.trim())
    appendLine("</persona>")
    assistant.appearancePrompt.trim().takeIf(String::isNotBlank)?.let {
        appendLine("<appearance>")
        appendLine(it)
        appendLine("</appearance>")
    }
    assistant.messageTemplate.trim()
        .takeIf { it.isNotBlank() && it != "{{ message }}" }
        ?.let {
            appendLine("<message_style>")
            appendLine(it)
            appendLine("</message_style>")
        }
    appendLine("initiative 要说明角色是否会主动联系、通常因为什么开口，以及什么情况下不会主动。")
    appendLine("sharingDesire 要说明角色会不会分享自己的想法、经历、发现或日常，以及分享方式。")
    appendLine("responsibility 要说明角色对承诺、照看、监督和用户状态承担到什么程度；人设没有这些倾向时必须明确写低。")
    appendLine("followUpStyle 要说明用户没回复、回答含糊或事情未完成时，角色是否追问、怎样追问、何时停止。")
    appendLine("passivity 要说明角色在哪些情况下等待用户先开口、保持沉默或隐藏真实关心。")
}

internal fun parseAssistantInteractionProfile(rawText: String): AssistantInteractionProfile {
    val trimmed = rawText.trim()
    val jsonPayload = trimmed.substringAfter("```json", trimmed)
        .substringAfter("```", trimmed)
        .substringBeforeLast("```", trimmed)
        .let { candidate ->
            val start = candidate.indexOf('{')
            val end = candidate.lastIndexOf('}')
            if (start >= 0 && end > start) candidate.substring(start, end + 1) else candidate
        }
    val root = Json.parseToJsonElement(jsonPayload) as? JsonObject
        ?: error("互动设定返回格式不是 JSON 对象。")
    fun required(name: String): String = root[name]
        ?.jsonPrimitive
        ?.contentOrNull
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.take(800)
        ?: error("互动设定缺少字段：$name")
    return AssistantInteractionProfile(
        initiative = required("initiative"),
        sharingDesire = required("sharingDesire"),
        responsibility = required("responsibility"),
        followUpStyle = required("followUpStyle"),
        passivity = required("passivity"),
    )
}

sealed interface HistoryClearState {
    data object Idle : HistoryClearState
    data object Running : HistoryClearState
    data object Success : HistoryClearState
    data class Failed(val message: String) : HistoryClearState
}

sealed interface InteractionGenerationState {
    data object Idle : InteractionGenerationState
    data object Running : InteractionGenerationState
    data object Success : InteractionGenerationState
    data class Failed(val message: String) : InteractionGenerationState
}
