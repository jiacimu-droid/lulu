package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.rerere.rikkahub.utils.JsonInstant

private const val GROUP_CHAT_PREFIX = "<!--lulu-group-chat:"
private const val GROUP_CHAT_SUFFIX = ":lulu-group-chat-->"

@Serializable
enum class GroupChatRole {
    OWNER,
    ADMIN,
    MEMBER,
}

@Serializable
data class GroupChatMember(
    val assistantId: String,
    val title: String = "群成员",
    val role: GroupChatRole = GroupChatRole.MEMBER,
)

@Serializable
data class GroupChatSpec(
    val name: String,
    val ownerId: String = "user",
    val userTitle: String = "群主",
    val members: List<GroupChatMember>,
    val maxAutoTurns: Int = 6,
    val allowCharacterInitiatedTurns: Boolean = true,
) {
    init {
        require(name.isNotBlank())
        require(members.size >= 2)
        require(maxAutoTurns in 3..10)
        require(members.map { it.assistantId }.distinct().size == members.size)
    }

    fun normalized(): GroupChatSpec = copy(
        name = name.trim(),
        userTitle = userTitle.trim().ifBlank { "群主" },
        members = members.distinctBy { it.assistantId }.map { member ->
            member.copy(title = member.title.trim().ifBlank { "群成员" })
        },
        maxAutoTurns = maxAutoTurns.coerceIn(3, 10),
    )

    fun updateMemberTitle(assistantId: String, title: String): GroupChatSpec = copy(
        members = members.map { member ->
            if (member.assistantId == assistantId) {
                member.copy(title = title.trim().ifBlank { "群成员" })
            } else {
                member
            }
        },
    ).normalized()

    fun removeMember(assistantId: String): GroupChatSpec? {
        val remaining = members.filterNot { it.assistantId == assistantId }
        return if (remaining.size >= 2) copy(members = remaining).normalized() else null
    }

    fun transferOwnershipTo(assistantId: String): GroupChatSpec = copy(
        ownerId = assistantId,
        members = members.map { member ->
            member.copy(
                role = if (member.assistantId == assistantId) GroupChatRole.OWNER else GroupChatRole.MEMBER,
            )
        },
    ).normalized()
}

fun GroupChatSpec.toConversationSystemPrompt(): String {
    val normalized = normalized()
    val encoded = JsonInstant.encodeToString(normalized)
    return "$GROUP_CHAT_PREFIX$encoded$GROUP_CHAT_SUFFIX\n" +
        "这是多人群聊。所有角色都能看到群内此前的全部消息，也可以回应、吐槽、反驳、补充或接续其他角色。" +
        "发言顺序不固定，应根据各自人设、关系、当前话题和互动价值自然决定；允许同一角色再次发言，也允许某个角色保持沉默。" +
        "每个角色只能代表自己发言，不得替其他成员说话，不得把群聊写成旁白式剧本。"
}

fun String?.decodeGroupChatSpec(): GroupChatSpec? {
    val text = this ?: return null
    val start = text.indexOf(GROUP_CHAT_PREFIX)
    if (start < 0) return null
    val payloadStart = start + GROUP_CHAT_PREFIX.length
    val end = text.indexOf(GROUP_CHAT_SUFFIX, payloadStart)
    if (end <= payloadStart) return null
    return runCatching {
        JsonInstant.decodeFromString<GroupChatSpec>(text.substring(payloadStart, end)).normalized()
    }.getOrNull()
}

val Conversation.groupChatSpec: GroupChatSpec?
    get() = customSystemPrompt.decodeGroupChatSpec()

val Conversation.isGroupChat: Boolean
    get() = groupChatSpec != null
