package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.NodeFavoriteTarget
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.CompanionChatPort
import me.rerere.rikkahub.ui.hooks.writeStringPreference
import me.rerere.rikkahub.ui.hooks.ChatInputState
import java.util.Locale
import kotlin.uuid.Uuid

private const val TAG = "ChatVM"

class ChatVM(
    id: String,
    private val context: Application,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val chatService: CompanionChatPort,
    private val analytics: FirebaseAnalytics,
    private val filesManager: FilesManager,
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {
    private val _conversationId: Uuid = Uuid.parse(id)
    val conversation: StateFlow<Conversation> = chatService.getConversationFlow(_conversationId)
    var chatListInitialized by mutableStateOf(false) // 聊天列表是否已经滚动到底部

    // 聊天输入状态 - 保存在 ViewModel 中避免 TransactionTooLargeException
    val inputState = ChatInputState()

    private val _initializationJob = MutableStateFlow<Job?>(null)

    // 初始化期间沿用现有 loading 通道禁用输入；加载完成后再切回模型生成任务。
    val conversationJob: StateFlow<Job?> = combine(
        chatService.getGenerationJobStateFlow(_conversationId),
        _initializationJob,
    ) { generationJob, initializationJob ->
        initializationJob?.takeIf { it.isActive } ?: generationJob
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val processingStatus: StateFlow<String?> =
        chatService
            .getProcessingStatusFlow(_conversationId)

    val conversationJobs = chatService
        .getConversationJobs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private lateinit var initialization: Deferred<Unit>
    private val _conversationReady = MutableStateFlow(false)
    val conversationReady: StateFlow<Boolean> = _conversationReady.asStateFlow()

    init {
        // 添加对话引用
        chatService.addConversationReference(_conversationId)

        // 初始化必须成为所有会修改对话的操作之前的屏障。否则旧会话仍在从数据库
        // 加载时，发送动作会基于空 Conversation.ofId 保存，直接覆盖完整历史。
        initialization = viewModelScope.async {
            try {
                chatService.initializeConversation(_conversationId)
                _conversationReady.value = true
            } finally {
                _initializationJob.value = null
            }
        }
        _initializationJob.value = initialization

        context.writeStringPreference("lastConversationId", _conversationId.toString())
    }

    override fun onCleared() {
        super.onCleared()
        // 移除对话引用
        chatService.removeConversationReference(_conversationId)
    }

    private fun launchAfterInitialization(
        title: String = context.getString(R.string.error_title_operation),
        block: suspend () -> Unit,
    ): Job = viewModelScope.launch {
        try {
            initialization.await()
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            chatService.addError(
                error = error,
                conversationId = _conversationId,
                title = title,
            )
        }
    }

    // 用户设置
    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    // 网络搜索
    val enableWebSearch = settings.map {
        it.enableWebSearch
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // 当前模型
    val currentChatModel = settings.map { settings ->
        settings.getCurrentChatModel()
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    // 错误状态
    val errors: StateFlow<List<ChatError>> = chatService.errors

    fun dismissError(id: Uuid) = chatService.dismissError(id)

    fun clearAllErrors() = chatService.clearAllErrors()

    // 生成完成
    val generationDoneFlow: SharedFlow<Uuid> = chatService.generationDoneFlow

    // MCP管理器
    val mcpManager = chatService.mcpManager

    fun updateSettings(newSettings: Settings) {
        viewModelScope.launch {
            val oldSettings = settings.value
            // 检查用户头像是否有变化，如果有则删除旧头像
            checkUserAvatarDelete(oldSettings, newSettings)
            settingsStore.update(newSettings)
        }
    }

    // 检查用户头像删除
    private fun checkUserAvatarDelete(oldSettings: Settings, newSettings: Settings) {
        val oldAvatar = oldSettings.displaySetting.userAvatar
        val newAvatar = newSettings.displaySetting.userAvatar

        if (oldAvatar is Avatar.Image && oldAvatar != newAvatar) {
            filesManager.deleteChatFiles(listOf(oldAvatar.url.toUri()))
        }
    }

    // 设置聊天模型
    fun setChatModel(assistant: Assistant, model: Model) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map {
                        if (it.id == assistant.id) {
                            it.copy(
                                chatModelId = model.id
                            )
                        } else {
                            it
                        }
                    })
            }
        }
    }

    /**
     * 处理消息发送
     *
     * @param content 消息内容
     * @param answer 是否触发消息生成，如果为false，则仅添加消息到消息列表中
     */
    fun handleMessageSend(content: List<UIMessagePart>, answer: Boolean = true) {
        if (content.isEmptyInputMessage()) return
        analytics.logEvent("ai_send_message", null)

        launchAfterInitialization(context.getString(R.string.error_title_send_message)) {
            chatService.sendMessage(_conversationId, content, answer)
        }
    }

    fun handleReplyRequest() {
        analytics.logEvent("ai_request_reply", null)
        launchAfterInitialization(context.getString(R.string.error_title_generation)) {
            chatService.requestReply(_conversationId)
        }
    }

    fun handleMessageEdit(parts: List<UIMessagePart>, messageId: Uuid) {
        if (parts.isEmptyInputMessage()) return
        analytics.logEvent("ai_edit_message", null)

        launchAfterInitialization {
            chatService.editMessage(_conversationId, messageId, parts)
        }
    }

    fun handleCompressContext(additionalPrompt: String, targetTokens: Int, keepRecentMessages: Int): Job {
        return launchAfterInitialization(context.getString(R.string.error_title_compress_conversation)) {
            chatService.compressConversation(
                _conversationId,
                conversation.value,
                additionalPrompt,
                targetTokens,
                keepRecentMessages
            ).onFailure {
                chatService.addError(it, title = context.getString(R.string.error_title_compress_conversation))
            }
        }
    }

    suspend fun forkMessage(message: UIMessage): Conversation {
        initialization.await()
        return chatService.forkConversationAtMessage(_conversationId, message.id)
    }

    fun deleteMessage(message: UIMessage) {
        launchAfterInitialization {
            chatService.deleteMessage(_conversationId, message)
        }
    }

    fun showDeleteBlockedWhileGeneratingError() {
        chatService.addError(
            error = IllegalStateException("请先停止生成再删除消息"),
            conversationId = _conversationId,
            title = context.getString(R.string.error_title_operation)
        )
    }

    fun regenerateAtMessage(
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true
    ) {
        analytics.logEvent("ai_regenerate_at_message", null)
        launchAfterInitialization(context.getString(R.string.error_title_generation)) {
            chatService.regenerateAtMessage(_conversationId, message, regenerateAssistantMsg)
        }
    }

    fun handleToolApproval(
        toolCallId: String,
        approved: Boolean,
        reason: String = ""
    ) {
        analytics.logEvent("ai_tool_approval", null)
        launchAfterInitialization {
            chatService.handleToolApproval(_conversationId, toolCallId, approved, reason)
        }
    }

    fun handleToolAnswer(
        toolCallId: String,
        answer: String,
    ) {
        analytics.logEvent("ai_tool_answer", null)
        launchAfterInitialization {
            chatService.handleToolApproval(_conversationId, toolCallId, approved = true, answer = answer)
        }
    }

    fun stopGeneration() {
        viewModelScope.launch {
            conversationJob.value?.cancel()
        }
    }

    fun saveConversationAsync() {
        launchAfterInitialization {
            // 读取发生在初始化屏障之后，禁止把初始化前的空快照写回数据库。
            chatService.saveConversation(_conversationId, conversation.value)
        }
    }

    fun updateTitle(title: String) {
        launchAfterInitialization {
            val updatedConversation = conversation.value.copy(title = title)
            chatService.saveConversation(_conversationId, updatedConversation)
        }
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            conversationRepo.deleteConversation(conversation)
        }
    }

    fun updatePinnedStatus(conversation: Conversation) {
        viewModelScope.launch {
            conversationRepo.togglePinStatus(conversation.id)
        }
    }

    fun moveConversationToAssistant(conversation: Conversation, targetAssistantId: Uuid) {
        launchAfterInitialization {
            val conversationFull = conversationRepo.getConversationById(conversation.id) ?: return@launchAfterInitialization
            val updatedConversation = conversationFull.copy(assistantId = targetAssistantId)
            if (conversation.id == _conversationId) {
                chatService.saveConversation(_conversationId, updatedConversation)
                settingsStore.updateAssistant(targetAssistantId)
            } else {
                conversationRepo.updateConversation(updatedConversation)
            }
        }
    }

    fun translateMessage(message: UIMessage, targetLanguage: Locale) {
        launchAfterInitialization {
            chatService.translateMessage(_conversationId, message, targetLanguage)
        }
    }

    fun generateTitle(conversation: Conversation, force: Boolean = false) {
        launchAfterInitialization(context.getString(R.string.error_title_generate_title)) {
            val conversationFull = conversationRepo.getConversationById(conversation.id) ?: return@launchAfterInitialization
            chatService.generateTitle(_conversationId, conversationFull, force)
        }
    }

    fun clearTranslationField(messageId: Uuid) {
        launchAfterInitialization {
            chatService.clearTranslationField(_conversationId, messageId)
        }
    }

    fun updateConversation(newConversation: Conversation) {
        val baseConversation = conversation.value
        launchAfterInitialization {
            chatService.updateConversationState(_conversationId) { currentConversation ->
                mergeConversationEdit(
                    base = baseConversation,
                    edited = newConversation,
                    current = currentConversation,
                )
            }
        }
    }

    fun toggleMessageFavorite(node: MessageNode) {
        launchAfterInitialization {
            val currentlyFavorited = favoriteRepository.isNodeFavorited(_conversationId, node.id)
            if (currentlyFavorited) {
                favoriteRepository.removeNodeFavorite(_conversationId, node.id)
            } else {
                favoriteRepository.addNodeFavorite(
                    NodeFavoriteTarget(
                        conversationId = _conversationId,
                        conversationTitle = conversation.value.title,
                        nodeId = node.id,
                        node = node
                    )
                )
            }

            chatService.updateConversationState(_conversationId) { currentConversation ->
                currentConversation.copy(
                    messageNodes = currentConversation.messageNodes.map { existingNode ->
                        if (existingNode.id == node.id) {
                            MessageNode(
                                id = existingNode.id,
                                messages = existingNode.messages,
                                selectIndex = existingNode.selectIndex,
                                isFavorite = !currentlyFavorited,
                            )
                        } else {
                            existingNode
                        }
                    }
                )
            }
        }
    }
}

/**
 * Applies only the fields that actually changed relative to the UI snapshot the
 * edit started from. This keeps a late database load from being replaced by an
 * earlier empty snapshot while still allowing intentional edits after loading.
 */
internal fun mergeConversationEdit(
    base: Conversation,
    edited: Conversation,
    current: Conversation,
): Conversation {
    if (base.id != current.id || edited.id != current.id) return current
    return current.copy(
        assistantId = if (edited.assistantId != base.assistantId) edited.assistantId else current.assistantId,
        title = if (edited.title != base.title) edited.title else current.title,
        messageNodes = if (edited.messageNodes != base.messageNodes) edited.messageNodes else current.messageNodes,
        chatSuggestions = if (edited.chatSuggestions != base.chatSuggestions) {
            edited.chatSuggestions
        } else {
            current.chatSuggestions
        },
        isPinned = if (edited.isPinned != base.isPinned) edited.isPinned else current.isPinned,
        createAt = if (edited.createAt != base.createAt) edited.createAt else current.createAt,
        updateAt = if (edited.updateAt != base.updateAt) edited.updateAt else current.updateAt,
        customSystemPrompt = if (edited.customSystemPrompt != base.customSystemPrompt) {
            edited.customSystemPrompt
        } else {
            current.customSystemPrompt
        },
        newConversation = if (edited.newConversation != base.newConversation) {
            edited.newConversation
        } else {
            current.newConversation
        },
    )
}

internal fun canRequestManualReply(conversation: Conversation): Boolean =
    conversation.currentMessages.lastOrNull()?.role == MessageRole.USER
