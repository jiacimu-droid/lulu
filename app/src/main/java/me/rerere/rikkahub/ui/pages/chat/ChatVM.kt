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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
import me.rerere.rikkahub.data.model.GroupChatMember
import me.rerere.rikkahub.data.model.GroupChatSpec
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.NodeFavoriteTarget
import me.rerere.rikkahub.data.model.groupChatSpec
import me.rerere.rikkahub.data.model.isGroupChat
import me.rerere.rikkahub.data.model.toConversationSystemPrompt
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.service.CompanionChatPort
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.writeStringPreference
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.uuid.Uuid

private const val TAG = "ChatVM"
private const val GROUP_TURN_TIMEOUT_MS = 180_000L
private const val GROUP_MIN_AUTO_TURNS = 3

class ChatVM(
    id: String,
    private val context: Application,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val chatService: CompanionChatPort,
    private val filesManager: FilesManager,
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {
    private val _conversationId: Uuid = Uuid.parse(id)
    val conversation: StateFlow<Conversation> = chatService.getConversationFlow(_conversationId)
    var chatListInitialized by mutableStateOf(false)

    val inputState = ChatInputState()

    private val _initializationJob = MutableStateFlow<Job?>(null)
    private val _groupGenerationJob = MutableStateFlow<Job?>(null)
    private val generationJob: StateFlow<Job?> = chatService
        .getGenerationJobStateFlow(_conversationId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val conversationJob: StateFlow<Job?> = combine(
        generationJob,
        _initializationJob,
        _groupGenerationJob,
    ) { activeGenerationJob, initializationJob, groupJob ->
        initializationJob?.takeIf { it.isActive }
            ?: groupJob?.takeIf { it.isActive }
            ?: activeGenerationJob
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val processingStatus: StateFlow<String?> =
        chatService.getProcessingStatusFlow(_conversationId)

    val conversationJobs = chatService
        .getConversationJobs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private lateinit var initialization: Deferred<Unit>
    private val _conversationReady = MutableStateFlow(false)
    val conversationReady: StateFlow<Boolean> = _conversationReady.asStateFlow()

    init {
        chatService.addConversationReference(_conversationId)
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
        _groupGenerationJob.value?.cancel()
        chatService.removeConversationReference(_conversationId)
        super.onCleared()
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

    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    val enableWebSearch = settings.map {
        it.enableWebSearch
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val currentChatModel = settings.map { settings ->
        settings.getCurrentChatModel()
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val errors: StateFlow<List<ChatError>> = chatService.errors

    fun dismissError(id: Uuid) = chatService.dismissError(id)

    fun clearAllErrors() = chatService.clearAllErrors()

    val generationDoneFlow: SharedFlow<Uuid> = chatService.generationDoneFlow

    val mcpManager = chatService.mcpManager

    fun updateSettings(newSettings: Settings) {
        viewModelScope.launch {
            val oldSettings = settings.value
            checkUserAvatarDelete(oldSettings, newSettings)
            settingsStore.update(newSettings)
        }
    }

    private fun checkUserAvatarDelete(oldSettings: Settings, newSettings: Settings) {
        val oldAvatar = oldSettings.displaySetting.userAvatar
        val newAvatar = newSettings.displaySetting.userAvatar
        if (oldAvatar is Avatar.Image && oldAvatar != newAvatar) {
            filesManager.deleteChatFiles(listOf(oldAvatar.url.toUri()))
        }
    }

    fun setChatModel(assistant: Assistant, model: Model) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map {
                        if (it.id == assistant.id) it.copy(chatModelId = model.id) else it
                    },
                )
            }
        }
    }

    fun handleMessageSend(content: List<UIMessagePart>, answer: Boolean = true) {
        if (content.isEmptyInputMessage()) return

        val spec = conversation.value.groupChatSpec
        if (answer && spec != null) {
            startGroupConversation(content = content, includeUserMessage = true, spec = spec)
        } else {
            launchAfterInitialization(context.getString(R.string.error_title_send_message)) {
                chatService.sendMessage(_conversationId, content, answer)
            }
        }
    }

    fun handleReplyRequest() {
        val spec = conversation.value.groupChatSpec
        if (spec != null) {
            startGroupConversation(content = emptyList(), includeUserMessage = false, spec = spec)
        } else {
            launchAfterInitialization(context.getString(R.string.error_title_generation)) {
                chatService.requestReply(_conversationId)
            }
        }
    }

    private fun startGroupConversation(
        content: List<UIMessagePart>,
        includeUserMessage: Boolean,
        spec: GroupChatSpec,
    ) {
        if (_groupGenerationJob.value?.isActive == true) return
        val job = launchAfterInitialization(context.getString(R.string.error_title_generation)) {
            if (includeUserMessage) {
                chatService.sendMessage(_conversationId, content, answer = false)
                delay(120L)
            }
            runGroupTurns(spec)
        }
        _groupGenerationJob.value = job
        job.invokeOnCompletion {
            if (_groupGenerationJob.value === job) {
                _groupGenerationJob.value = null
            }
        }
    }

    private suspend fun runGroupTurns(spec: GroupChatSpec) {
        val original = conversation.value
        val validMembers = spec.members.mapNotNull { member ->
            settings.value.assistants.firstOrNull { it.id.toString() == member.assistantId }
                ?.let { assistant -> member to assistant }
        }
        if (validMembers.size < 2) {
            throw IllegalStateException("群聊至少需要两个仍然存在的角色")
        }

        val maxTurns = spec.maxAutoTurns.coerceIn(GROUP_MIN_AUTO_TURNS, 10)
        val latestText = conversation.value.currentMessages.lastOrNull()?.toText().orEmpty()
        val targetTurns = if (maxTurns == GROUP_MIN_AUTO_TURNS) {
            maxTurns
        } else {
            GROUP_MIN_AUTO_TURNS + latestText.hashCode().absoluteValue % (maxTurns - GROUP_MIN_AUTO_TURNS + 1)
        }
        var lastSpeakerId: String? = null

        try {
            repeat(targetTurns) { turnIndex ->
                val (member, speaker) = chooseNextGroupSpeaker(
                    members = validMembers,
                    turnIndex = turnIndex,
                    lastSpeakerId = lastSpeakerId,
                )
                val speakerPrompt = buildGroupSpeakerPrompt(
                    spec = spec,
                    member = member,
                    speaker = speaker,
                    turnIndex = turnIndex,
                    targetTurns = targetTurns,
                )
                chatService.saveConversation(
                    _conversationId,
                    conversation.value.copy(
                        assistantId = speaker.id,
                        title = spec.name,
                        customSystemPrompt = speakerPrompt,
                    ),
                )

                val completed = coroutineScope {
                    val waiter = async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeoutOrNull(GROUP_TURN_TIMEOUT_MS) {
                            generationDoneFlow.first { it == _conversationId }
                        } != null
                    }
                    chatService.requestReply(_conversationId)
                    waiter.await()
                }
                if (!completed) {
                    throw IllegalStateException("${speaker.name.ifBlank { "角色" }}的群聊回复等待超时")
                }

                delay(80L)
                tagLatestGroupReply(
                    speakerName = speaker.name.ifBlank { "角色" },
                    memberTitle = member.title.ifBlank { "群成员" },
                )
                lastSpeakerId = speaker.id.toString()

                val newestText = conversation.value.currentMessages.lastOrNull()?.toText().orEmpty()
                if (turnIndex + 1 >= GROUP_MIN_AUTO_TURNS && newestText.shouldReturnGroupFloorToUser()) {
                    return@repeat
                }
            }
        } finally {
            val hostId = spec.members.firstOrNull()?.assistantId
                ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                ?: original.assistantId
            chatService.saveConversation(
                _conversationId,
                conversation.value.copy(
                    assistantId = hostId,
                    title = spec.name,
                    customSystemPrompt = spec.toConversationSystemPrompt(),
                ),
            )
        }
    }

    private fun chooseNextGroupSpeaker(
        members: List<Pair<GroupChatMember, Assistant>>,
        turnIndex: Int,
        lastSpeakerId: String?,
    ): Pair<GroupChatMember, Assistant> {
        val recentText = conversation.value.currentMessages
            .takeLast(5)
            .joinToString("\n") { it.toText() }
        val mentioned = members.filter { (_, assistant) ->
            assistant.id.toString() != lastSpeakerId &&
                assistant.name.isNotBlank() &&
                recentText.contains(assistant.name, ignoreCase = true)
        }
        val allowRepeat = turnIndex > 0 && turnIndex % 3 == 2
        val pool = when {
            mentioned.isNotEmpty() -> mentioned
            allowRepeat -> members
            else -> members.filter { (_, assistant) -> assistant.id.toString() != lastSpeakerId }
                .ifEmpty { members }
        }
        val seed = recentText.hashCode() * 31 + turnIndex * 97 + (lastSpeakerId?.hashCode() ?: 0)
        return pool[Math.floorMod(seed, pool.size)]
    }

    private fun buildGroupSpeakerPrompt(
        spec: GroupChatSpec,
        member: GroupChatMember,
        speaker: Assistant,
        turnIndex: Int,
        targetTurns: Int,
    ): String = buildString {
        appendLine(spec.toConversationSystemPrompt())
        appendLine()
        appendLine("当前轮到【${speaker.name.ifBlank { "角色" }}】发言，群头衔是【${member.title.ifBlank { "群成员" }}】。")
        appendLine("你只能以这个角色本人的人设、记忆、语气和立场发言，不能替其他群成员说话。")
        appendLine("先读完群里此前的全部内容：既可以回应用户，也可以接住、吐槽、反驳或补充前面角色的话。")
        appendLine("内容要有新的信息或情绪推进，不要换一种说法重复上一条。自然输出一到两条适合聊天气泡的消息。")
        appendLine("不要输出角色名标签、群头衔、轮次说明、导演旁白或JSON，客户端会自动标注说话人。")
        appendLine("这是自动互动的第 ${turnIndex + 1}/$targetTurns 轮。除非话题自然回到用户，否则不要刻意总结收尾。")
    }

    private suspend fun tagLatestGroupReply(
        speakerName: String,
        memberTitle: String,
    ) {
        val prefix = "【$speakerName｜$memberTitle】\n"
        chatService.updateConversationState(_conversationId) { current ->
            val nodeIndex = current.messageNodes.indexOfLast { node ->
                node.messages.getOrNull(node.selectIndex)?.role == MessageRole.ASSISTANT
            }
            if (nodeIndex < 0) return@updateConversationState current
            val node = current.messageNodes[nodeIndex]
            val message = node.messages[node.selectIndex]
            if (message.toText().startsWith("【")) return@updateConversationState current
            val nextParts = message.parts.toMutableList()
            val firstTextIndex = nextParts.indexOfFirst { it is UIMessagePart.Text }
            if (firstTextIndex >= 0) {
                val text = nextParts[firstTextIndex] as UIMessagePart.Text
                nextParts[firstTextIndex] = text.copy(text = prefix + text.text.trimStart())
            } else {
                nextParts.add(0, UIMessagePart.Text(prefix.trimEnd()))
            }
            val nextMessages = node.messages.toMutableList().apply {
                this[node.selectIndex] = message.copy(parts = nextParts)
            }
            current.copy(
                messageNodes = current.messageNodes.toMutableList().apply {
                    this[nodeIndex] = node.copy(messages = nextMessages)
                },
            )
        }
        delay(30L)
        chatService.saveConversation(_conversationId, conversation.value)
    }

    fun handleMessageEdit(parts: List<UIMessagePart>, messageId: Uuid) {
        if (parts.isEmptyInputMessage()) return
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
                keepRecentMessages,
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
            title = context.getString(R.string.error_title_operation),
        )
    }

    fun regenerateAtMessage(
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true,
    ) {
        launchAfterInitialization(context.getString(R.string.error_title_generation)) {
            chatService.regenerateAtMessage(_conversationId, message, regenerateAssistantMsg)
        }
    }

    fun handleToolApproval(
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
    ) {
        launchAfterInitialization {
            chatService.handleToolApproval(_conversationId, toolCallId, approved, reason)
        }
    }

    fun handleToolAnswer(toolCallId: String, answer: String) {
        launchAfterInitialization {
            chatService.handleToolApproval(_conversationId, toolCallId, approved = true, answer = answer)
        }
    }

    fun stopGeneration() {
        _groupGenerationJob.value?.cancel()
        viewModelScope.launch {
            generationJob.value?.cancel()
        }
    }

    fun saveConversationAsync() {
        launchAfterInitialization {
            chatService.saveConversation(_conversationId, conversation.value)
        }
    }

    fun updateTitle(title: String) {
        launchAfterInitialization {
            chatService.saveConversation(_conversationId, conversation.value.copy(title = title))
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
            val conversationFull = conversationRepo.getConversationById(conversation.id)
                ?: return@launchAfterInitialization
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
            val conversationFull = conversationRepo.getConversationById(conversation.id)
                ?: return@launchAfterInitialization
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
                        node = node,
                    ),
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
                    },
                )
            }
        }
    }
}

private fun String.shouldReturnGroupFloorToUser(): Boolean {
    val normalized = trim().lowercase()
    return listOf(
        "你觉得呢",
        "你怎么想",
        "你来说",
        "轮到你",
        "等你回复",
        "问问你",
    ).any(normalized::contains)
}

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
        chatSuggestions = if (edited.chatSuggestions != base.chatSuggestions) edited.chatSuggestions else current.chatSuggestions,
        isPinned = if (edited.isPinned != base.isPinned) edited.isPinned else current.isPinned,
        createAt = if (edited.createAt != base.createAt) edited.createAt else current.createAt,
        updateAt = if (edited.updateAt != base.updateAt) edited.updateAt else current.updateAt,
        customSystemPrompt = if (edited.customSystemPrompt != base.customSystemPrompt) {
            edited.customSystemPrompt
        } else {
            current.customSystemPrompt
        },
        newConversation = if (edited.newConversation != base.newConversation) edited.newConversation else current.newConversation,
    )
}

internal fun canRequestManualReply(conversation: Conversation): Boolean =
    conversation.isGroupChat || conversation.currentMessages.lastOrNull()?.role == MessageRole.USER
