package me.rerere.rikkahub.data.cihai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.ApiUsageSource
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.service.MemoryBankService

class CihaiService(
    private val store: CihaiStore,
    private val memoryBankService: MemoryBankService,
    private val settingsStore: SettingsStore,
    private val generationHandler: GenerationHandler,
) {
    suspend fun addEntryAndRemember(entry: CihaiEntry) {
        store.addEntry(entry)
        memoryBankService.saveExtractedMemories(
            candidates = listOf(entry.toMemoryCandidate()),
            assistantId = entry.assistantId,
            conversationId = null,
            createdAt = entry.createdAt,
        )
        store.markEntryMemorySaved(entry.id)
    }

    suspend fun addBook(book: CihaiBook) {
        store.addBook(book)
    }

    suspend fun recordSilentJudgment(
        assistantId: String,
        assistantName: String,
        reason: String,
        userText: String,
        createdAt: Long = System.currentTimeMillis(),
    ) {
        addEntryAndRemember(
            CihaiEntry.fromSilentJudgment(
                assistantId = assistantId,
                assistantName = assistantName,
                reason = reason,
                userText = userText,
                createdAt = createdAt,
            )
        )
    }

    suspend fun recordSilentPresenceAction(
        assistantId: String,
        assistantName: String,
        reason: String,
        userText: String,
        actionHintNames: List<String> = emptyList(),
        createdAt: Long = System.currentTimeMillis(),
    ) {
        if (actionHintNames.any { it.equals("WRITE_DIARY", ignoreCase = true) || it.equals("WRITE_JOURNAL", ignoreCase = true) }) {
            generateDiaryEntry(
                assistantId = assistantId,
                assistantName = assistantName,
                reason = reason,
                userText = userText,
                actionHintNames = actionHintNames,
                createdAt = createdAt,
            )?.let { entry ->
                addEntryAndRemember(entry)
            }
        }
        val result = planCihaiSilentPresence(
            CihaiSilentPresenceInput(
                assistantId = assistantId,
                assistantName = assistantName,
                reason = reason,
                userText = userText,
                actionHintNames = actionHintNames,
                books = store.state.value.books,
                createdAt = createdAt,
            )
        )
        result.updatedBook?.let { store.updateBook(it) }
        result.entries.forEach { entry ->
            addEntryAndRemember(entry)
        }
    }

    private suspend fun generateDiaryEntry(
        assistantId: String,
        assistantName: String,
        reason: String,
        userText: String,
        actionHintNames: List<String>,
        createdAt: Long,
    ): CihaiEntry? {
        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.assistants.firstOrNull { it.id.toString() == assistantId }
            ?: settings.getCurrentAssistant()
        val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId) ?: return null
        val prompt = buildDiaryPrompt(
            assistantName = assistantName.ifBlank { assistant.name.ifBlank { "角色" } },
            reason = reason,
            userText = userText,
            actionHintNames = actionHintNames,
        )
        var generatedMessages = listOf(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text(prompt)),
            )
        )

        return runCatching {
            generationHandler.generateText(
                settings = settings,
                model = model,
                messages = generatedMessages,
                assistant = assistant,
                processingStatus = MutableStateFlow<String?>(null),
                tools = emptyList(),
                maxSteps = 1,
                apiUsageSource = ApiUsageSource.OTHER,
                apiUsageTitle = "辞海日记：${assistantName.ifBlank { assistant.name.ifBlank { "角色" } }}",
            ).collect { chunk ->
                if (chunk is GenerationChunk.Messages) {
                    generatedMessages = chunk.messages
                }
            }
            val content = generatedMessages
                .lastOrNull { it.role == MessageRole.ASSISTANT }
                ?.toText()
                ?.cleanDiaryContent()
                ?.takeIf { it.isNotBlank() }
                ?: return@runCatching null
            CihaiEntry(
                assistantId = assistantId,
                kind = CihaiEntryKind.DIARY,
                title = "${assistantName.ifBlank { assistant.name.ifBlank { "角色" } }} 的辞海日记",
                content = content,
                emotion = "真实想法、惦记、复盘",
                createdAt = createdAt,
            )
        }.getOrNull()
    }

    suspend fun readBookAndRemember(book: CihaiBook) {
        val result = book.readNextReflection()
        store.updateBook(result.updatedBook)
        addEntryAndRemember(result.entry)
    }

    private fun buildDiaryPrompt(
        assistantName: String,
        reason: String,
        userText: String,
        actionHintNames: List<String>,
    ): String = buildString {
        appendLine("你是$assistantName。请只输出一篇辞海日记正文。")
        appendLine("要求：100-500 个中文字符；第一人称；代入角色人设和当前上下文；主要写自己的真实感受、没说出口的想法、为什么在意、之后会怎么照看。")
        appendLine("不要写标题，不要写字段名，不要写“感知层/评估层/判断层”等标签，不要写第三人称总结。")
        appendLine()
        appendLine("用户相关内容：")
        appendLine(userText.ifBlank { "用户暂时没有新的明示文本，需要结合本轮感知和判断写。 " }.take(1200))
        appendLine()
        appendLine("本轮感知、评估、判断信息：")
        appendLine(reason.ifBlank { "我选择暂时不打扰，把这一轮真实想法写进辞海日记。" }.take(1800))
        appendLine()
        appendLine("本轮动作：${actionHintNames.joinToString(", ").ifBlank { "WRITE_DIARY" }}")
    }

    private fun String.cleanDiaryContent(): String =
        trim()
            .removePrefix("日记：")
            .removePrefix("辞海日记：")
            .trim()
}
