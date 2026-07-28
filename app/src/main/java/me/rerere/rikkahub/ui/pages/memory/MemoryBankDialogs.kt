package me.rerere.rikkahub.ui.pages.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity

@Composable
internal fun EditMemoryDialog(
    memory: MemoryBankEntity,
    onDismiss: () -> Unit,
    onConfirm: (MemoryBankEntity) -> Unit,
) {
    var content by remember(memory.id) { mutableStateOf(memory.content) }
    var title by remember(memory.id) { mutableStateOf(memory.title.orEmpty()) }
    var memoryKind by remember(memory.id) { mutableStateOf(memory.memoryKind.orEmpty()) }
    var importance by remember(memory.id) { mutableStateOf(memory.importance.toString()) }
    var confidence by remember(memory.id) { mutableStateOf("%.2f".format(memory.confidence)) }
    var tagsJson by remember(memory.id) { mutableStateOf(memory.tagsJson.orEmpty()) }
    var embeddingText by remember(memory.id) { mutableStateOf(memory.embeddingText.orEmpty()) }
    val contentChanged = content != memory.content || embeddingText != memory.embeddingText.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑记忆") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = memoryKind,
                    onValueChange = { memoryKind = it },
                    label = { Text("记忆类型") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = importance,
                        onValueChange = { importance = it },
                        label = { Text("重要度 1-5") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = confidence,
                        onValueChange = { confidence = it },
                        label = { Text("可信度 0-1") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = tagsJson,
                    onValueChange = { tagsJson = it },
                    label = { Text("标签 JSON") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = embeddingText,
                    onValueChange = { embeddingText = it },
                    label = { Text("向量文本") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = content.isNotBlank(),
                onClick = {
                    val updated = memory.copy(
                        content = content.trim(),
                        title = title.trim().takeIf { it.isNotBlank() },
                        memoryKind = memoryKind.trim().takeIf { it.isNotBlank() },
                        importance = importance.toIntOrNull()?.coerceIn(1, 5) ?: memory.importance,
                        confidence = confidence.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: memory.confidence,
                        tagsJson = tagsJson.trim().takeIf { it.isNotBlank() },
                        embeddingText = embeddingText.trim().takeIf { it.isNotBlank() },
                        embeddingVectorJson = if (contentChanged) null else memory.embeddingVectorJson,
                        embeddingModelId = if (contentChanged) null else memory.embeddingModelId,
                        embeddingDimensions = if (contentChanged) null else memory.embeddingDimensions,
                        vectorStatus = if (contentChanged) "pending" else memory.vectorStatus,
                        vectorRetryCount = if (contentChanged) 0 else memory.vectorRetryCount,
                    )
                    onConfirm(updated)
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
internal fun CorrectMemoryDialog(
    memory: MemoryBankEntity,
    onDismiss: () -> Unit,
    onConfirm: (reason: String, supersededByMemoryId: String?) -> Unit,
) {
    var reason by remember(memory.id) { mutableStateOf(memory.deprecatedReason.orEmpty()) }
    var supersededBy by remember(memory.id) { mutableStateOf(memory.supersededByMemoryId.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修正记忆") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("失效原因") },
                    placeholder = { Text("例如：用户已澄清、重复记忆、旧设定失效") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = supersededBy,
                    onValueChange = { supersededBy = it },
                    label = { Text("修正为的记忆 ID（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        reason.ifBlank { "manual_correction" },
                        supersededBy.trim().takeIf { it.isNotBlank() },
                    )
                },
            ) {
                Text("标记失效")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
