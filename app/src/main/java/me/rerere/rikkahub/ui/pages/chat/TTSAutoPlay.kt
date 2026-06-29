package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.components.message.buildSpeakableMessageText
import me.rerere.rikkahub.ui.context.LocalTTSState
import kotlin.uuid.Uuid

@Composable
fun TTSAutoPlay(vm: ChatVM, setting: Settings, conversation: Conversation) {
    val assistant = remember(setting.assistants, conversation.assistantId) {
        setting.assistants.firstOrNull { it.id == conversation.assistantId }
    }
    if (assistant?.autoPlayVoice != true) return

    val tts = LocalTTSState.current
    val isAvailable by tts.isAvailable.collectAsState()
    var lastSpokenMessageId by remember(conversation.id) {
        mutableStateOf(conversation.messageNodes.latestAssistantMessageId())
    }
    val target = remember(conversation.messageNodes, lastSpokenMessageId) {
        findAutoPlayTTSMessage(
            nodes = conversation.messageNodes,
            lastSpokenMessageId = lastSpokenMessageId,
        )
    }

    LaunchedEffect(target?.id, isAvailable, setting.displaySetting.ttsOnlyReadQuoted) {
        if (!isAvailable || target == null) return@LaunchedEffect
        val text = buildSpeakableMessageText(
            message = target,
            onlyReadQuoted = setting.displaySetting.ttsOnlyReadQuoted,
        ) ?: return@LaunchedEffect
        lastSpokenMessageId = target.id
        tts.speak(text)
    }
}

private fun List<MessageNode>.latestAssistantMessageId(): Uuid? =
    asReversed()
        .map { it.currentMessage }
        .firstOrNull { it.role == MessageRole.ASSISTANT }
        ?.id

internal fun findAutoPlayTTSMessage(
    nodes: List<MessageNode>,
    lastSpokenMessageId: Uuid?,
): UIMessage? {
    val message = nodes
        .asReversed()
        .map { it.currentMessage }
        .firstOrNull { it.role == MessageRole.ASSISTANT && it.toText().isNotBlank() }
        ?: return null
    return message.takeIf { it.id != lastSpokenMessageId }
}
