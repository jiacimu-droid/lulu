package me.rerere.rikkahub.ui.pages.chat

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft02
import me.rerere.hugeicons.stroke.Call02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Setting07
import me.rerere.hugeicons.stroke.TransactionHistory
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.companion.CompanionPersistedState
import me.rerere.rikkahub.data.companion.CompanionSnapshot
import me.rerere.rikkahub.data.companion.CompanionStore
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.groupChatSpec
import me.rerere.rikkahub.data.model.toConversationSystemPrompt
import me.rerere.rikkahub.service.ChatError
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.utils.base64Decode
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

@Composable
fun ChatPage(id: Uuid, text: String?, files: List<Uri>, nodeId: Uuid? = null, autoStartVoice: Boolean = false) {
    val vm: ChatVM = koinViewModel(parameters = { parametersOf(id.toString()) })
    val filesManager: FilesManager = koinInject()
    val companionStore: CompanionStore = koinInject()
    val navController = LocalNavController.current

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val enableWebSearch by vm.enableWebSearch.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val companionState by companionStore.state.collectAsStateWithLifecycle()
    val inputState = vm.inputState

    LaunchedEffect(files, text) {
        if (files.isNotEmpty()) {
            val localFiles = filesManager.createChatFilesByContents(files)
            val contentTypes = files.mapNotNull(filesManager::getFileMimeType)
            inputState.messageContent = buildList {
                localFiles.forEachIndexed { index, file ->
                    when {
                        contentTypes.getOrNull(index)?.startsWith("image/") == true -> add(UIMessagePart.Image(file.toString()))
                        contentTypes.getOrNull(index)?.startsWith("video/") == true -> add(UIMessagePart.Video(file.toString()))
                        contentTypes.getOrNull(index)?.startsWith("audio/") == true -> add(UIMessagePart.Audio(file.toString()))
                        else -> add(
                            UIMessagePart.Document(
                                url = file.toString(),
                                fileName = files.getOrNull(index)?.lastPathSegment?.substringAfterLast('/') ?: "文档",
                                mime = contentTypes.getOrNull(index) ?: "application/octet-stream",
                            ),
                        )
                    }
                }
            }
        }
        text?.base64Decode()?.takeIf(String::isNotEmpty)?.let(inputState::setMessageText)
    }

    val chatListState = rememberLazyListState()
    LaunchedEffect(nodeId, conversation.messageNodes.size) {
        if (!vm.chatListInitialized && conversation.messageNodes.isNotEmpty()) {
            if (nodeId != null) {
                val index = conversation.messageNodes.indexOfFirst { it.id == nodeId }
                if (index >= 0) chatListState.scrollToItem(index)
            } else {
                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
            }
            vm.chatListInitialized = true
        }
    }

    ChatPageContent(
        inputState = inputState,
        loadingJob = loadingJob,
        processingStatus = processingStatus,
        setting = setting,
        companionState = companionState,
        conversation = conversation,
        navController = navController,
        vm = vm,
        chatListState = chatListState,
        enableWebSearch = enableWebSearch,
        currentChatModel = currentChatModel,
        autoStartVoice = autoStartVoice,
        errors = errors,
        onDismissError = vm::dismissError,
        onClearAllErrors = vm::clearAllErrors,
    )
}

@Composable
private fun ChatPageContent(
    inputState: ChatInputState,
    loadingJob: Job?,
    processingStatus: String?,
    setting: Settings,
    companionState: CompanionPersistedState,
    conversation: Conversation,
    navController: Navigator,
    vm: ChatVM,
    chatListState: LazyListState,
    enableWebSearch: Boolean,
    currentChatModel: Model?,
    autoStartVoice: Boolean,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var previewMode by rememberSaveable { mutableStateOf(false) }
    var showGroupSettings by rememberSaveable { mutableStateOf(false) }
    val hazeState = rememberHazeState()
    val imeVisible = WindowInsets.isImeVisible
    val groupSpec = conversation.groupChatSpec
    val assistant = setting.getAssistantById(conversation.assistantId) ?: setting.getCurrentAssistant()
    val companionSnapshot = companionState.snapshots.firstOrNull { it.assistantId == assistant.id.toString() }
        ?: CompanionSnapshot.empty(assistant.id.toString())

    TTSAutoPlay(setting = setting, conversation = conversation, loading = loadingJob != null)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        AssistantBackground(setting = setting)
        Scaffold(
            topBar = {
                TopBar(
                    settings = setting,
                    assistant = assistant,
                    companionSnapshot = companionSnapshot,
                    navController = navController,
                    previewMode = previewMode,
                    groupName = groupSpec?.name,
                    groupMemberCount = groupSpec?.members?.size?.plus(1),
                    customSystemPrompt = conversation.customSystemPrompt,
                    allowConversationSystemPrompt = groupSpec == null && assistant.allowConversationSystemPrompt,
                    onConversationSystemPromptChange = { newPrompt ->
                        vm.updateConversation(conversation.copy(customSystemPrompt = newPrompt))
                        vm.saveConversationAsync()
                    },
                    onClickMenu = { previewMode = !previewMode },
                    onGroupSettings = if (groupSpec != null) ({ showGroupSettings = true }) else null,
                    onStartVoiceCall = if (groupSpec == null) ({
                        navController.navigate(Screen.VoiceCall(conversation.id.toString(), conversation.assistantId.toString()))
                    }) else null,
                    onOpenVoiceCallHistory = if (groupSpec == null) ({
                        navController.navigate(Screen.VoiceCallHistory(conversation.id.toString(), conversation.assistantId.toString()))
                    }) else null,
                )
            },
            bottomBar = {
                ChatInput(
                    modifier = if (imeVisible) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier,
                    state = inputState,
                    loading = loadingJob != null,
                    settings = setting,
                    conversation = conversation,
                    mcpManager = vm.mcpManager,
                    hazeState = hazeState,
                    autoStartVoice = autoStartVoice && groupSpec == null,
                    onCancelClick = vm::stopGeneration,
                    enableSearch = enableWebSearch,
                    onToggleSearch = { vm.updateSettings(setting.copy(enableWebSearch = !enableWebSearch)) },
                    canReplyToCurrentConversation = canRequestManualReply(conversation),
                    onSendClick = {
                        if (currentChatModel == null) {
                            toaster.show("请先选择模型", type = ToastType.Error)
                            return@ChatInput
                        }
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(inputState.getContents(), inputState.editingMessage!!)
                        } else {
                            vm.handleMessageSend(inputState.getContents(), answer = false)
                            scope.launch { chatListState.requestScrollToItem(conversation.currentMessages.size + 5) }
                        }
                        inputState.clearInput()
                    },
                    onReplyClick = {
                        if (currentChatModel == null) {
                            toaster.show("请先选择模型", type = ToastType.Error)
                            return@ChatInput
                        }
                        when {
                            inputState.isEditing() -> {
                                vm.handleMessageEdit(inputState.getContents(), inputState.editingMessage!!)
                                inputState.clearInput()
                            }
                            !inputState.isEmpty() -> {
                                vm.handleMessageSend(inputState.getContents(), answer = true)
                                inputState.clearInput()
                            }
                            else -> vm.handleReplyRequest()
                        }
                        scope.launch { chatListState.requestScrollToItem(conversation.currentMessages.size + 5) }
                    },
                    onVoiceMessage = { url, duration, transcript ->
                        if (currentChatModel == null) {
                            toaster.show("请先选择模型", type = ToastType.Error)
                            return@ChatInput
                        }
                        vm.handleMessageSend(listOf(UIMessagePart.VoiceMessage(url, duration, transcript)))
                    },
                    onUpdateChatModel = { vm.setChatModel(setting.getCurrentAssistant(), it) },
                    onUpdateAssistant = { updated ->
                        vm.updateSettings(setting.copy(assistants = setting.assistants.map { if (it.id == updated.id) updated else it }))
                    },
                    onUpdateSearchService = { vm.updateSettings(setting.copy(searchServiceSelected = it)) },
                    onCompressContext = vm::handleCompressContext,
                )
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            ChatList(
                innerPadding = innerPadding,
                conversation = conversation,
                state = chatListState,
                loading = loadingJob != null,
                processingStatus = processingStatus,
                previewMode = previewMode,
                settings = setting,
                currentCompanionDescription = companionSnapshot.state.selfScene,
                hazeState = hazeState,
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                onRegenerate = vm::regenerateAtMessage,
                onEdit = {
                    inputState.editingMessage = it.id
                    inputState.setContents(it.parts)
                },
                onForkMessage = {
                    scope.launch { navController.navigate(Screen.Chat(vm.forkMessage(it).id.toString())) }
                },
                onDelete = { if (loadingJob != null) vm.showDeleteBlockedWhileGeneratingError() else vm.deleteMessage(it) },
                onUpdateMessage = { newNode ->
                    vm.updateConversation(conversation.copy(messageNodes = conversation.messageNodes.map { if (it.id == newNode.id) newNode else it }))
                    vm.saveConversationAsync()
                },
                onTranslate = vm::translateMessage,
                onClearTranslation = { vm.clearTranslationField(it.id) },
                onJumpToMessage = {
                    previewMode = false
                    scope.launch { chatListState.animateScrollToItem(it) }
                },
                onToolApproval = vm::handleToolApproval,
                onToolAnswer = vm::handleToolAnswer,
                onToggleFavorite = vm::toggleMessageFavorite,
            )
        }
    }

    if (showGroupSettings && groupSpec != null) {
        GroupChatSettingsDialog(
            spec = groupSpec,
            assistants = setting.assistants,
            onDismiss = { showGroupSettings = false },
            onSave = { updated ->
                vm.updateConversation(
                    conversation.copy(
                        title = updated.name,
                        assistantId = updated.members.firstOrNull()?.assistantId
                            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                            ?: conversation.assistantId,
                        customSystemPrompt = updated.toConversationSystemPrompt(),
                    ),
                )
                vm.saveConversationAsync()
                showGroupSettings = false
            },
            onDeleteGroup = {
                vm.stopGeneration()
                vm.deleteConversation(conversation)
                showGroupSettings = false
                navController.popBackStack()
            },
        )
    }
}

@Composable
private fun TopBar(
    settings: Settings,
    assistant: Assistant,
    companionSnapshot: CompanionSnapshot,
    navController: Navigator,
    previewMode: Boolean,
    groupName: String?,
    groupMemberCount: Int?,
    customSystemPrompt: String?,
    allowConversationSystemPrompt: Boolean,
    onConversationSystemPromptChange: (String?) -> Unit,
    onClickMenu: () -> Unit,
    onGroupSettings: (() -> Unit)?,
    onStartVoiceCall: (() -> Unit)?,
    onOpenVoiceCallHistory: (() -> Unit)?,
) {
    var showLuluStatus by rememberSaveable { mutableStateOf(false) }
    var showConversationSystemPrompt by rememberSaveable { mutableStateOf(false) }
    val assistantDefaultName = stringResource(R.string.assistant_page_default_assistant)
    val isGroup = groupName != null

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(HugeIcons.ArrowLeft02, contentDescription = null)
            }
        },
        title = {
            val model = settings.getCurrentChatModel()
            val provider = model?.findProvider(settings.providers, checkOverwrite = false)
            Row {
                UIAvatar(
                    name = groupName ?: assistant.name.ifBlank { assistantDefaultName },
                    value = assistant.avatar,
                    modifier = Modifier.size(40.dp),
                    onClick = { if (isGroup) onGroupSettings?.invoke() else showLuluStatus = true },
                )
                androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = groupName ?: assistant.name.ifBlank { assistantDefaultName },
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (isGroup) {
                            "${groupMemberCount ?: 0}人群聊"
                        } else if (model != null && provider != null) {
                            "${model.displayName} (${provider.name})"
                        } else {
                            ""
                        },
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        color = LocalContentColor.current.copy(0.65f),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    )
                }
            }
        },
        actions = {
            if (onGroupSettings != null) {
                IconButton(onClick = onGroupSettings) {
                    Icon(HugeIcons.Setting07, contentDescription = "群聊设置")
                }
            } else if (allowConversationSystemPrompt) {
                IconButton(onClick = { showConversationSystemPrompt = true }) {
                    Icon(HugeIcons.Setting07, contentDescription = stringResource(R.string.chat_page_conversation_system_prompt))
                }
            }
            onStartVoiceCall?.let { action ->
                IconButton(onClick = action) { Icon(HugeIcons.Call02, contentDescription = "电话") }
            }
            onOpenVoiceCallHistory?.let { action ->
                IconButton(onClick = action) { Icon(HugeIcons.TransactionHistory, contentDescription = "电话历史") }
            }
            IconButton(onClick = onClickMenu) {
                Icon(if (previewMode) HugeIcons.Cancel01 else HugeIcons.LeftToRightListBullet, "Chat Options")
            }
        },
    )
    if (showLuluStatus) {
        LuluStatusDialog(assistant, companionSnapshot, onDismissRequest = { showLuluStatus = false })
    }
    ConversationSystemPromptDialog(
        visible = showConversationSystemPrompt,
        customSystemPrompt = customSystemPrompt,
        onSystemPromptChange = onConversationSystemPromptChange,
        onDismissRequest = { showConversationSystemPrompt = false },
    )
}
