package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.rikkahub.utils.JsonInstant

private const val GROUP_CHAT_PREFIX = "<!--lulu-group-chat:"
private const val GROUP_CHAT_SUFFIX = ":lulu-group-chat-->"

@Serializable
data class GroupChatMember(
    val assistantId: String,
    val title: String = "群成员",
)

@Serializable
data class GroupChatSpec(
    val name: String,
    val ownerId: String = "user",
    val members: List<GroupChatMember>,
    val maxAutoTurns: Int = 6,
) {
    init {
        require(name.isNotBlank())
        require(members.size >= 2)
    }
}

fun GroupChatSpec.toConversationSystemPrompt(): String {
    val encoded = JsonInstant.encodeToString(this)
    return "$GROUP_CHAT_PREFIX$encoded$GROUP_CHAT_SUFFIX\n" +
        "这是多人群聊。所有角色都能看到群内此前的全部消息，也可以回应、吐槽、反驳或接续其他角色。" +
        "发言顺序不固定，应根据各自人设、当前话题和互动价值自然决定；允许同一角色再次发言，也允许某个角色保持沉默。" +
        "不要代替其他成员发言，不要输出说话人标签以外的旁白。"
}

fun String?.decodeGroupChatSpec(): GroupChatSpec? {
    val text = this ?: return null
    val start = text.indexOf(GROUP_CHAT_PREFIX)
    if (start < 0) return null
    val payloadStart = start + GROUP_CHAT_PREFIX.length
    val end = text.indexOf(GROUP_CHAT_SUFFIX, payloadStart)
    if (end <= payloadStart) return null
    return runCatching {
        JsonInstant.decodeFromString<GroupChatSpec>(text.substring(payloadStart, end))
    }.getOrNull()
}

val Conversation.groupChatSpec: GroupChatSpec?
    get() = customSystemPrompt.decodeGroupChatSpec()

val Conversation.isGroupChat: Boolean
    get() = groupChatSpec != null
