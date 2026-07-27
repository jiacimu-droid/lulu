package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.datastore.ProactiveCallSetting
import me.rerere.rikkahub.data.datastore.ProactiveMessageSetting
import kotlin.uuid.Uuid

@Serializable
data class Assistant(
    val id: Uuid = Uuid.random(),
    val chatModelId: Uuid? = null, // 如果为null, 使用全局默认模型
    val name: String = "",
    val avatar: Avatar = Avatar.Dummy,
    val useAssistantAvatar: Boolean = false, // 使用助手头像替代模型头像
    val tags: List<Uuid> = emptyList(),
    val systemPrompt: String = "",
    val appearancePrompt: String = "",
    val faceReferenceImage: String? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val contextMessageSize: Int = 60,
    /** Number of stable message nodes required before this role summarizes a memory batch. 0 disables automatic extraction. */
    val memoryExtractionInterval: Int = 20,
    /** Number of newest logical messages kept out of long-term memory extraction. */
    val memoryExtractionProtectedRecentCount: Int = 10,
    val streamOutput: Boolean = true,
    val autoPlayVoice: Boolean = false,
    val ttsVoiceId: String = "",
    val proactiveMessageSetting: ProactiveMessageSetting = ProactiveMessageSetting(),
    val proactiveCallSetting: ProactiveCallSetting = ProactiveCallSetting(),
    val interactionProfile: AssistantInteractionProfile = AssistantInteractionProfile(),
    val enableRecentChatsReference: Boolean = false,
    val messageTemplate: String = "{{ message }}",
    val presetMessages: List<UIMessage> = emptyList(),
    val quickMessageIds: Set<Uuid> = emptySet(),
    val regexes: List<AssistantRegex> = emptyList(),
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val maxTokens: Int? = null,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val mcpServers: Set<Uuid> = emptySet(),
    val localTools: List<LocalToolOption> = listOf(LocalToolOption.TimeInfo),
    val background: String? = null,
    val backgroundOpacity: Float = 1.0f,
    val modeInjectionIds: Set<Uuid> = emptySet(),      // 关联的模式注入 ID
    val lorebookIds: Set<Uuid> = emptySet(),            // 关联的 Lorebook ID
    val enabledSkills: Set<String> = emptySet(),        // 启用的 skill 名称列表
    val enableTimeReminder: Boolean = true,             // 时间间隔提醒注入
    val allowConversationSystemPrompt: Boolean = false, // 允许对话单独重写 system prompt
    val allowSkipReply: Boolean = false,
)

/**
 * 用户可见、可编辑的角色互动规则。
 *
 * 这些字段不用固定数值约束角色，而是让人设生成器写出具体、可执行的行为描述，
 * 再作为聊天、电话、主动消息和后续追问的共同依据。
 */
@Serializable
data class AssistantInteractionProfile(
    val initiative: String = "",
    val sharingDesire: String = "",
    val responsibility: String = "",
    val followUpStyle: String = "",
    val passivity: String = "",
)

fun AssistantInteractionProfile.isBlank(): Boolean =
    initiative.isBlank() &&
        sharingDesire.isBlank() &&
        responsibility.isBlank() &&
        followUpStyle.isBlank() &&
        passivity.isBlank()

fun AssistantInteractionProfile.toPromptContext(): String {
    if (isBlank()) return ""
    return buildString {
        appendLine("<interaction_profile priority=\"authoritative\">")
        appendLine("以下是用户确认过的角色互动设定。它决定角色是否主动联系、分享、承担责任、追问或保持被动；不得用统一的热情、冷淡、恋人或管家默认值覆盖。")
        initiative.trim().takeIf(String::isNotBlank)?.let { appendLine("主动意愿：$it") }
        sharingDesire.trim().takeIf(String::isNotBlank)?.let { appendLine("分享欲：$it") }
        responsibility.trim().takeIf(String::isNotBlank)?.let { appendLine("责任感：$it") }
        followUpStyle.trim().takeIf(String::isNotBlank)?.let { appendLine("追问方式：$it") }
        passivity.trim().takeIf(String::isNotBlank)?.let { appendLine("被动倾向：$it") }
        append("</interaction_profile>")
    }
}

@Serializable
data class QuickMessage(
    val id: Uuid = Uuid.random(),
    val title: String = "",
    val content: String = "",
)

@Serializable
enum class AssistantAffectScope {
    USER,
    ASSISTANT,
}

@Serializable
data class AssistantRegex(
    val id: Uuid,
    val name: String = "",
    val enabled: Boolean = true,
    val findRegex: String = "", // 正则表达式
    val replaceString: String = "", // 替换字符串
    val affectingScope: Set<AssistantAffectScope> = setOf(),
    val visualOnly: Boolean = false, // 是否仅在视觉上影响
)

fun String.replaceRegexes(
    assistant: Assistant?,
    scope: AssistantAffectScope,
    visual: Boolean = false
): String {
    if (assistant == null) return this
    if (assistant.regexes.isEmpty()) return this
    return assistant.regexes.fold(this) { acc, regex ->
        if (regex.enabled && regex.visualOnly == visual && regex.affectingScope.contains(scope)) {
            try {
                val result = acc.replace(
                    regex = Regex(regex.findRegex),
                    replacement = regex.replaceString,
                )
                // println("Regex: ${regex.findRegex} -> ${result}")
                result
            } catch (e: Exception) {
                e.printStackTrace()
                // 如果正则表达式格式错误，返回原字符串
                acc
            }
        } else {
            acc
        }
    }
}

/**
 * 注入位置
 */
@Serializable
enum class InjectionPosition {
    @SerialName("before_system_prompt")
    BEFORE_SYSTEM_PROMPT,   // 系统提示词之前

    @SerialName("after_system_prompt")
    AFTER_SYSTEM_PROMPT,    // 系统提示词之后（最常用）

    @SerialName("top_of_chat")
    TOP_OF_CHAT,            // 对话最开头（第一条用户消息之前）

    @SerialName("bottom_of_chat")
    BOTTOM_OF_CHAT,         // 最新消息之前（当前用户输入之前）

    @SerialName("at_depth")
    AT_DEPTH,               // 在指定深度位置插入（从最新消息往前数）
}

/**
 * 提示词注入
 *
 * - ModeInjection: 基于模式开关的注入（如学习模式）
 * - RegexInjection: 基于正则匹配的注入（Lorebook）
 */
@Serializable
sealed class PromptInjection {
    abstract val id: Uuid
    abstract val name: String
    abstract val enabled: Boolean
    abstract val priority: Int
    abstract val position: InjectionPosition
    abstract val content: String
    abstract val injectDepth: Int  // 当 position 为 AT_DEPTH 时使用，表示从最新消息往前数的位置
    abstract val role: MessageRole  // 注入角色：USER 或 ASSISTANT

    /**
     * 模式注入 - 基于开关状态触发
     */
    @Serializable
    @SerialName("mode")
    data class ModeInjection(
        override val id: Uuid = Uuid.random(),
        override val name: String = "",
        override val enabled: Boolean = true,
        override val priority: Int = 0,
        override val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        override val content: String = "",
        override val injectDepth: Int = 4,
        override val role: MessageRole = MessageRole.USER,
    ) : PromptInjection()

    /**
     * 正则注入 - 基于内容匹配触发（世界书）
     */
    @Serializable
    @SerialName("regex")
    data class RegexInjection(
        override val id: Uuid = Uuid.random(),
        override val name: String = "",
        override val enabled: Boolean = true,
        override val priority: Int = 0,
        override val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        override val content: String = "",
        override val injectDepth: Int = 4,
        override val role: MessageRole = MessageRole.USER,
        val keywords: List<String> = emptyList(),  // 触发关键词
        val useRegex: Boolean = false,             // 使用正则匹配
        val caseSensitive: Boolean = false,        // 大小写敏感
        val scanDepth: Int = 4,                    // 扫描最近N条消息
        val constantActive: Boolean = false,       // 常驻激活（无需匹配）
    ) : PromptInjection()
}

/**
 * Lorebook - 组织管理多个 RegexInjection
 */
@Serializable
data class Lorebook(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val globalApply: Boolean = false,
    val entries: List<PromptInjection.RegexInjection> = emptyList(),
)

/**
 * 检查 RegexInjection 是否被触发
 *
 * @param context 要扫描的上下文文本
 * @return 是否触发
 */
fun PromptInjection.RegexInjection.isTriggered(context: String): Boolean {
    if (!enabled) return false
    if (constantActive) return true
    if (keywords.isEmpty()) return false

    return keywords.any { keyword ->
        if (useRegex) {
            try {
                val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex(keyword, options).containsMatchIn(context)
            } catch (e: Exception) {
                false
            }
        } else {
            if (caseSensitive) {
                context.contains(keyword)
            } else {
                context.contains(keyword, ignoreCase = true)
            }
        }
    }
}

/**
 * 从消息列表中提取用于匹配的上下文文本
 *
 * @param messages 消息列表
 * @param scanDepth 扫描深度（最近N条消息）
 * @return 拼接的文本内容
 */
fun extractContextForMatching(
    messages: List<UIMessage>,
    scanDepth: Int
): String {
    return messages
        .takeLast(scanDepth)
        .joinToString("\n") { it.toText() }
}

/**
 * 获取所有被触发的注入，按优先级排序
 *
 * @param injections 所有注入规则
 * @param context 上下文文本
 * @return 被触发的注入列表，按优先级降序排列
 */
fun getTriggeredInjections(
    injections: List<PromptInjection.RegexInjection>,
    context: String
): List<PromptInjection.RegexInjection> {
    return injections
        .filter { it.isTriggered(context) }
        .sortedByDescending { it.priority }
}
