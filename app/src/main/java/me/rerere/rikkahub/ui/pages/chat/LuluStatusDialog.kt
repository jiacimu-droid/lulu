package me.rerere.rikkahub.ui.pages.chat

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.LuluState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LuluStatusDialog(
    assistant: Assistant,
    currentState: LuluState,
    history: List<LuluState>,
    onDismissRequest: () -> Unit,
) {
    var showHistory by rememberSaveable { mutableStateOf(false) }

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
                    StatusHistory(history = history)
                } else {
                    CurrentStatus(state = currentState)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showHistory = !showHistory }) {
                Text(if (showHistory) "当前状态" else "历史状态")
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
private fun CurrentStatus(state: LuluState) {
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
private fun StatusHistory(history: List<LuluState>) {
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
        items(history) { state ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.statusText,
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
