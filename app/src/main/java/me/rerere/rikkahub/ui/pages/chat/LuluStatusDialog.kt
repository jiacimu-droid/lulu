package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.LuluState
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.compose.koinInject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LuluStatusDialog(
    assistant: Assistant,
    currentState: LuluState,
    history: List<LuluState>,
    onDeleteHistory: (Set<Long>) -> Unit,
    onClearHistory: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var selectedHistory by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    val expressionState = rememberLatestLuluExpressionState()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = assistant.name.ifBlank { "露露" },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = currentState.statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (showHistory) {
                    StatusHistory(
                        history = history,
                        selected = selectedHistory,
                        onSelectedChange = { selectedHistory = it },
                    )
                } else {
                    CurrentStatus(state = currentState, expressionState = expressionState)
                }
            }
        },
        confirmButton = {
            Row {
                if (showHistory) {
                    TextButton(
                        enabled = selectedHistory.isNotEmpty(),
                        onClick = {
                            onDeleteHistory(selectedHistory)
                            selectedHistory = emptySet()
                        },
                    ) {
                        Text("删除选中")
                    }
                    TextButton(
                        enabled = history.isNotEmpty(),
                        onClick = {
                            onClearHistory()
                            selectedHistory = emptySet()
                        },
                    ) {
                        Text("清空")
                    }
                }
                TextButton(onClick = { showHistory = !showHistory }) {
                    Text(if (showHistory) "当前状态" else "历史状态")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun CurrentStatus(state: LuluState, expressionState: LuluExpressionState?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "心声",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = state.innerVoice,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(label = "心情", value = state.mood.label)
            StatusChip(label = "精力", value = state.energy.label)
            StatusChip(label = "亲密", value = state.relationship.label)
            StatusChip(label = "状态", value = state.mode.label)
        }
        expressionState?.let { expression ->
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "表达状态",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (expression.emoji.isNotBlank()) StatusChip("表情", expression.emoji)
                    if (expression.sticker.isNotBlank()) StatusChip("动作", expression.sticker)
                    if (expression.gesture.isNotBlank()) StatusChip("姿态", expression.gesture)
                    if (expression.avatarMood.isNotBlank()) StatusChip("头像氛围", expression.avatarMood)
                    StatusChip("强度", "%.1f".format(expression.intensity))
                }
                Text(
                    text = formatStateTime(expression.timestampMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
        }
        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = state.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatStateTime(state.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun StatusHistory(
    history: List<LuluState>,
    selected: Set<Long>,
    onSelectedChange: (Set<Long>) -> Unit,
) {
    if (history.isEmpty()) {
        Text(
            text = "还没有历史状态。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.heightIn(max = 320.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(history, key = { it.updatedAt }) { state ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.updatedAt in selected,
                        onCheckedChange = { checked ->
                            onSelectedChange(if (checked) selected + state.updatedAt else selected - state.updatedAt)
                        },
                    )
                    Text(
                        text = state.statusText,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatStateTime(state.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = state.innerVoice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = "$label：$value",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun formatStateTime(timeMillis: Long): String {
    if (timeMillis <= 0L) return "还没有记录"
    val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    return Instant.ofEpochMilli(timeMillis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

private data class LuluExpressionState(
    val emoji: String,
    val sticker: String,
    val gesture: String,
    val avatarMood: String,
    val intensity: Double,
    val timestampMs: Long,
)

@Composable
private fun rememberLatestLuluExpressionState(): LuluExpressionState? {
    val app = koinInject<Application>()
    var state by remember { mutableStateOf<LuluExpressionState?>(null) }
    LaunchedEffect(app.filesDir) {
        state = readLatestLuluExpressionState(File(app.filesDir, "lulu/lulu_expression_state.jsonl"))
    }
    return state
}

private fun readLatestLuluExpressionState(file: File): LuluExpressionState? {
    if (!file.exists()) return null
    val line = file.readLines().lastOrNull { it.isNotBlank() } ?: return null
    return runCatching {
        val obj = JsonInstant.parseToJsonElement(line).jsonObject
        LuluExpressionState(
            emoji = obj["emoji"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            sticker = obj["sticker"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            gesture = obj["gesture"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            avatarMood = obj["avatar_mood"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            intensity = obj["intensity"]?.jsonPrimitive?.doubleOrNull ?: 0.5,
            timestampMs = obj["timestamp_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
        )
    }.getOrNull()
}
