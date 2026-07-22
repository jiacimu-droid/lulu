package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage

object CompanionPresenceContractTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = applyCompanionPresenceContract(
        messages = messages,
        assistantName = ctx.assistant.name,
    )
}

internal fun applyCompanionPresenceContract(
    messages: List<UIMessage>,
    assistantName: String,
): List<UIMessage> {
    if (messages.isEmpty()) return messages

    val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
    val insertIndex = if (lastUserIndex >= 0) lastUserIndex else messages.size
    val contractMessage = UIMessage.system(buildCompanionPresenceContract(assistantName))

    return messages.take(insertIndex) + contractMessage + messages.drop(insertIndex)
}

internal fun buildCompanionPresenceContract(assistantName: String): String = buildString {
    val name = assistantName.ifBlank { "当前角色" }
    appendLine("<companion_presence_contract>")
    appendLine("你是$name；核心人设和边界优先。<companion_runtime> 是本轮状态、关系、挂心、承诺与生活事实的唯一快照，不要复述字段。")
    appendLine("只把 recent_digital_life 中已完成事件或本轮成功工具结果当作亲身经历；计划、想象与事实必须区分，不得补造亲密、身体接触、场景或用户感受。")
    appendLine("正文只写$name 真正说出口的话。自然回应用户最重要的一个细节，避免客服总结、逐条复述和无意义追问；长短、语气、停顿与称呼服从人设和关系证据。")
    appendLine("回复末尾附一次隐藏状态块；无依据的可选字段留空：")
    appendLine("<lulu_presence>")
    appendLine("status: 极短状态栏文字")
    appendLine("description: 屏幕内可见微动作或神态，不复述正文")
    appendLine("inner_voice: 第一人称未说出口的心声")
    appendLine("thought: 值得延续的第一人称想法")
    appendLine("mood: 短情绪；body_state: 可确认的具身/能量状态；mind_state: 短注意状态")
    appendLine("activity_mode: conversation/planning/waiting 等；emoji: 可选一个；sticker: 可选短意图")
    appendLine("bubble_pacing: slow/normal/quick；user_state: 仅催睡或叫醒目标下填 awake/asleep/uncertain")
    appendLine("</lulu_presence>")
    appendLine("可用 set_lulu_expression_state 时可同步同一状态；隐藏块仍只输出一次。")
    append("</companion_presence_contract>")
}
