package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.GroupChatMember
import me.rerere.rikkahub.data.model.GroupChatRole
import me.rerere.rikkahub.data.model.GroupChatSpec

@Composable
internal fun GroupChatSettingsDialog(
    spec: GroupChatSpec,
    assistants: List<Assistant>,
    onDismiss: () -> Unit,
    onSave: (GroupChatSpec) -> Unit,
    onDeleteGroup: () -> Unit,
) {
    var name by remember(spec) { mutableStateOf(spec.name) }
    var userTitle by remember(spec) { mutableStateOf(spec.userTitle) }
    var maxTurns by remember(spec) { mutableStateOf(spec.maxAutoTurns.toString()) }
    var members by remember(spec) { mutableStateOf(spec.members) }
    var ownerId by remember(spec) { mutableStateOf(spec.ownerId) }
    var confirmDelete by remember { mutableStateOf(false) }

    val selectedIds = members.mapTo(mutableSetOf()) { it.assistantId }
    val canSave = name.isNotBlank() && members.size >= 2

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("群聊设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("群名称") },
                )
                OutlinedTextField(
                    value = userTitle,
                    onValueChange = { userTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("我的群头衔") },
                )
                OutlinedTextField(
                    value = maxTurns,
                    onValueChange = { value -> maxTurns = value.filter { char -> char.isDigit() }.take(2) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("每次自动互动轮数（3～10）") },
                )
                Text("群成员与头衔", style = MaterialTheme.typography.titleSmall)
                assistants.forEach { assistant ->
                    val id = assistant.id.toString()
                    val checked = id in selectedIds
                    val existing = members.firstOrNull { it.assistantId == id }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                members = if (checked) {
                                    if (members.size > 2) members.filterNot { it.assistantId == id } else members
                                } else {
                                    members + GroupChatMember(assistantId = id)
                                }
                            }
                            .padding(vertical = 2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    members = if (checked) {
                                        if (members.size > 2) members.filterNot { it.assistantId == id } else members
                                    } else {
                                        members + GroupChatMember(assistantId = id)
                                    }
                                },
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(assistant.name.ifBlank { "角色" }, modifier = Modifier.weight(1f))
                            if (ownerId == id) {
                                Text("群主", color = MaterialTheme.colorScheme.primary)
                            } else if (checked) {
                                TextButton(onClick = { ownerId = id }) { Text("转让群主") }
                            }
                        }
                        if (existing != null) {
                            OutlinedTextField(
                                value = existing.title,
                                onValueChange = { title ->
                                    members = members.map { member ->
                                        if (member.assistantId == id) member.copy(title = title) else member
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(start = 42.dp),
                                singleLine = true,
                                label = { Text("${assistant.name.ifBlank { "角色" }}的群头衔") },
                            )
                        }
                    }
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Text("退出并删除这个群聊", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val normalizedMembers = members.map { member ->
                        member.copy(
                            role = if (member.assistantId == ownerId) GroupChatRole.OWNER else GroupChatRole.MEMBER,
                        )
                    }
                    onSave(
                        spec.copy(
                            name = name,
                            ownerId = ownerId,
                            userTitle = userTitle,
                            members = normalizedMembers,
                            maxAutoTurns = maxTurns.toIntOrNull()?.coerceIn(3, 10) ?: 6,
                        ).normalized(),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("退出群聊") },
            text = { Text("退出后会删除本机上的这个群聊和聊天记录，无法撤销。") },
            confirmButton = {
                TextButton(onClick = onDeleteGroup) {
                    Text("退出并删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}
