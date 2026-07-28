package me.rerere.rikkahub.ui.pages.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.rikkahub.data.db.entity.MemoryBankEntity
import me.rerere.rikkahub.utils.JsonInstant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MemoryCard(
    memory: MemoryBankEntity,
    assistantLabels: Map<String, String>,
    onDelete: () -> Unit,
    onOpenSource: (conversationId: String, nodeId: String?) -> Unit,
    onEdit: () -> Unit,
    onTogglePinned: () -> Unit,
    onCorrect: () -> Unit,
) {
    val sourceNodeId = remember(memory.sourceMessageNodeIdsJson) { memory.firstSourceNodeId() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (memory.vectorStatus) {
                "done" -> MaterialTheme.colorScheme.surface
                "pending" -> MaterialTheme.colorScheme.tertiaryContainer
                "failed" -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        color = when (memory.type) {
                            "message" -> MaterialTheme.colorScheme.primaryContainer
                            "manual" -> MaterialTheme.colorScheme.inversePrimary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        Text(
                            text = when (memory.type) {
                                "message" -> "消息"
                                "manual" -> "固定记忆"
                                else -> memory.type
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    if (memory.vectorStatus == "done") {
                        Icon(
                            HugeIcons.Database02,
                            contentDescription = "已向量化",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (memory.deprecated) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            Text(
                                text = "已失效",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }

                    if (memory.pinned) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            Text(
                                text = "置顶",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    val displayTime = memory.occurredAt ?: memory.createdAt
                    val timeStr = remember(displayTime) {
                        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                            .format(Date(displayTime))
                    }
                    Text(
                        text = "发生 $timeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (memory.assistantId != null) {
                        Text(
                            text = assistantLabels[memory.assistantId] ?: memory.assistantId.shortAssistantId(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer(modifier = Modifier.height(6.dp))

                MemoryMetaInfo(memory)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (memory.conversationId != null) {
                    TextButton(onClick = { onOpenSource(memory.conversationId, sourceNodeId) }) {
                        Text("原文")
                    }
                }
                TextButton(onClick = onTogglePinned) {
                    Text(if (memory.pinned) "取消置顶" else "置顶")
                }
                if (!memory.deprecated) {
                    TextButton(onClick = onCorrect) {
                        Text("修正")
                    }
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        HugeIcons.PencilEdit01,
                        contentDescription = "编辑",
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        HugeIcons.Delete02,
                        contentDescription = "删除",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryMetaInfo(memory: MemoryBankEntity) {
    val chips = buildList {
        memory.memoryKind?.takeIf { it.isNotBlank() }?.let { add("类型：$it") }
        add("重要度：${memory.importance}")
        add("可信度：${"%.2f".format(memory.confidence)}")
        if (memory.recallCount > 0) add("召回：${memory.recallCount}")
        memory.lastRecalledAt?.let { add("上次召回：${formatShortTime(it)}") }
        memory.sourceMessageAt?.let { add("得知于：${formatShortTime(it)}") }
        memory.extractedAt.takeIf { it > 0L }?.let { add("整理于：${formatShortTime(it)}") }
        memory.relatedMemoryIdsJson?.takeIf { it.isNotBlank() && it != "[]" }?.let { add("关联：$it") }
        memory.sourceMessageNodeIdsJson?.takeIf { it.isNotBlank() && it != "[]" }?.let { add("来源：$it") }
        memory.evidenceMessageNodeIdsJson?.takeIf { it.isNotBlank() && it != "[]" }?.let { add("证据：$it") }
        memory.supersededByMemoryId?.takeIf { it.isNotBlank() }?.let { add("修正为：#$it") }
        memory.deprecatedReason?.takeIf { it.isNotBlank() }?.let { add("原因：$it") }
        memory.correctedAt?.let { add("修正时间：${formatShortTime(it)}") }
    }
    if (chips.isEmpty()) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        chips.forEach { chip ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text(
                    text = chip,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatShortTime(timestamp: Long): String =
    SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun MemoryBankEntity.firstSourceNodeId(): String? =
    runCatching {
        JsonInstant.decodeFromString<List<String>>(sourceMessageNodeIdsJson.orEmpty()).firstOrNull()
    }.getOrNull()
