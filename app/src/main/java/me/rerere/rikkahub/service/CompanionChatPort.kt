package me.rerere.rikkahub.service

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.model.Conversation
import java.util.Locale
import kotlin.uuid.Uuid

/**
 * Stable boundary used by presentation code.
 *
 * ChatService remains the legacy implementation while its responsibilities are
 * moved into smaller collaborators. Keeping the UI on this port prevents the
 * giant service from leaking more methods into screens during the migration.
 */
interface CompanionChatPort {
    val errors: StateFlow<List<ChatError>>
    val generationDoneFlow: SharedFlow<Uuid>
    val mcpManager: McpManager

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation>
    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?>
    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?>
    fun getConversationJobs(): Flow<Map<Uuid, Job?>>
    fun addConversationReference(conversationId: Uuid)
    fun removeConversationReference(conversationId: Uuid)
    suspend fun initializeConversation(conversationId: Uuid)
    fun dismissError(id: Uuid)
    fun clearAllErrors()
    fun addError(
        error: Throwable,
        conversationId: Uuid? = null,
        title: String? = null,
        solution: ChatErrorSolution? = null,
    )
    fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean = true)
    fun requestReply(conversationId: Uuid)
    suspend fun editMessage(conversationId: Uuid, messageId: Uuid, parts: List<UIMessagePart>)
    suspend fun compressConversation(
        conversationId: Uuid,
        conversation: Conversation,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int,
    ): Result<Unit>
    suspend fun forkConversationAtMessage(conversationId: Uuid, messageId: Uuid): Conversation
    suspend fun deleteMessage(conversationId: Uuid, message: UIMessage)
    fun regenerateAtMessage(
        conversationId: Uuid,
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true,
    )
    fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        answer: String? = null,
    )
    suspend fun saveConversation(conversationId: Uuid, conversation: Conversation)
    fun translateMessage(conversationId: Uuid, message: UIMessage, targetLanguage: Locale)
    suspend fun generateTitle(conversationId: Uuid, conversation: Conversation, force: Boolean = false)
    fun clearTranslationField(conversationId: Uuid, messageId: Uuid)
    fun updateConversationState(conversationId: Uuid, transform: (Conversation) -> Conversation)
}

class DefaultCompanionChatPort(
    private val chatService: ChatService,
) : CompanionChatPort {
    override val errors: StateFlow<List<ChatError>> get() = chatService.errors
    override val generationDoneFlow: SharedFlow<Uuid> get() = chatService.generationDoneFlow
    override val mcpManager: McpManager get() = chatService.mcpManager

    override fun getConversationFlow(conversationId: Uuid) = chatService.getConversationFlow(conversationId)
    override fun getGenerationJobStateFlow(conversationId: Uuid) = chatService.getGenerationJobStateFlow(conversationId)
    override fun getProcessingStatusFlow(conversationId: Uuid) = chatService.getProcessingStatusFlow(conversationId)
    override fun getConversationJobs() = chatService.getConversationJobs()
    override fun addConversationReference(conversationId: Uuid) = chatService.addConversationReference(conversationId)
    override fun removeConversationReference(conversationId: Uuid) = chatService.removeConversationReference(conversationId)
    override suspend fun initializeConversation(conversationId: Uuid) = chatService.initializeConversation(conversationId)
    override fun dismissError(id: Uuid) = chatService.dismissError(id)
    override fun clearAllErrors() = chatService.clearAllErrors()
    override fun addError(error: Throwable, conversationId: Uuid?, title: String?, solution: ChatErrorSolution?) =
        chatService.addError(error, conversationId, title, solution)
    override fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean) =
        chatService.sendMessage(conversationId, content, answer)
    override fun requestReply(conversationId: Uuid) = chatService.requestReply(conversationId)
    override suspend fun editMessage(conversationId: Uuid, messageId: Uuid, parts: List<UIMessagePart>) =
        chatService.editMessage(conversationId, messageId, parts)
    override suspend fun compressConversation(
        conversationId: Uuid,
        conversation: Conversation,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int,
    ) = chatService.compressConversation(
        conversationId,
        conversation,
        additionalPrompt,
        targetTokens,
        keepRecentMessages,
    )
    override suspend fun forkConversationAtMessage(conversationId: Uuid, messageId: Uuid) =
        chatService.forkConversationAtMessage(conversationId, messageId)
    override suspend fun deleteMessage(conversationId: Uuid, message: UIMessage) =
        chatService.deleteMessage(conversationId, message)
    override fun regenerateAtMessage(conversationId: Uuid, message: UIMessage, regenerateAssistantMsg: Boolean) =
        chatService.regenerateAtMessage(conversationId, message, regenerateAssistantMsg)
    override fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String,
        answer: String?,
    ) = chatService.handleToolApproval(conversationId, toolCallId, approved, reason, answer)
    override suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) =
        chatService.saveConversation(conversationId, conversation)
    override fun translateMessage(conversationId: Uuid, message: UIMessage, targetLanguage: Locale) =
        chatService.translateMessage(conversationId, message, targetLanguage)
    override suspend fun generateTitle(conversationId: Uuid, conversation: Conversation, force: Boolean) =
        chatService.generateTitle(conversationId, conversation, force)
    override fun clearTranslationField(conversationId: Uuid, messageId: Uuid) =
        chatService.clearTranslationField(conversationId, messageId)
    override fun updateConversationState(conversationId: Uuid, transform: (Conversation) -> Conversation) =
        chatService.updateConversationState(conversationId, transform)
}
