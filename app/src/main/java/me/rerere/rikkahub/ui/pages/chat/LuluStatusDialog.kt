package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.companion.CompanionCommitment
import me.rerere.rikkahub.data.companion.CompanionCommitmentStatus
import me.rerere.rikkahub.data.companion.CompanionConcern
import me.rerere.rikkahub.data.companion.CompanionConcernStatus
import me.rerere.rikkahub.data.companion.CompanionSnapshot
import me.rerere.rikkahub.data.model.Assistant
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LuluStatusDialog(
    assistant: Assistant,
    snapshot: CompanionSnapshot,
    onDismissRequest: () -> Unit,
) {
    val state = snapshot.state
    val activeConcerns = snapshot.concerns
        .filter { it.status == CompanionConcernStatus.ACTIVE }
        .take(3)
    val activeCommitments = snapshot.commitments
        .filter { it.status.isVisibleInStatus() }
        .take(3)
    val stateChips = buildList {
        state.mood.takeIf(String::isNotBlank)?.let { add("心情" to it) }
        state.bodyState.takeIf(String::isNotBlank)?.let { add("身体" to it) }
        state.mindState.takeIf(String::isNotBlank)?.let { add("精神" to it) }
        state.activityMode.takeIf(String::isNotBlank)?.let { add("状态" to it) }
        snapshot.relationship.roleLabel.takeIf(String::isNotBlank)?.let { add("关系" to it) }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = assistant.name.ifBlank { "角色" },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = state.statusText.ifBlank { "正在陪着你" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    StatusSection(
                        title = "没说出口",
                        text = state.innerThought.ifBlank { "此刻还没有留下明确的心声。" },
                    )
                }
                if (stateChips.isNotEmpty()) {
                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            stateChips.forEach { (label, value) ->
                                StatusChip(label = label, value = value)
                            }
                        }
                    }
                }
                state.selfScene.takeIf(String::isNotBlank)?.let { scene ->
                    item {
                        StatusSection(title = "此刻", text = scene)
                    }
                }
                if (activeConcerns.isNotEmpty()) {
                    item { SectionTitle("正在挂心") }
                    items(activeConcerns, key = { it.id }) { concern ->
                        ConcernRow(concern)
                    }
                }
                if (activeCommitments.isNotEmpty()) {
                    item { SectionTitle("答应你的事") }
                    items(activeCommitments, key = { it.id }) { commitment ->
                        CommitmentRow(commitment)
                    }
                }
                item {
                    val updatedAt = maxOf(snapshot.updatedAt, state.updatedAt)
                    Text(
                        text = formatStateTime(updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun StatusSection(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(title)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ConcernRow(concern: CompanionConcern) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = concern.event,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        concern.goal.takeIf(String::isNotBlank)?.let { goal ->
            Text(
                text = goal,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        concern.nextPerceptionAt?.let { nextPerceptionAt ->
            Text(
                text = formatNextPerception(nextPerceptionAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun CommitmentRow(commitment: CompanionCommitment) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = commitment.promise,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = formatCommitmentTime(commitment.dueAt),
            style = MaterialTheme.typography.labelSmall,
            color = if (commitment.dueAt <= System.currentTimeMillis()) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        HorizontalDivider()
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

private fun CompanionCommitmentStatus.isVisibleInStatus(): Boolean = this in setOf(
    CompanionCommitmentStatus.PROPOSED,
    CompanionCommitmentStatus.ACTIVE,
    CompanionCommitmentStatus.DUE,
    CompanionCommitmentStatus.EXECUTING,
    CompanionCommitmentStatus.RETRY_SCHEDULED,
)

private fun formatStateTime(timeMillis: Long): String {
    if (timeMillis <= 0L) return "还没有状态记录"
    return "更新于 ${formatAbsoluteTime(timeMillis)}"
}

private fun formatNextPerception(timeMillis: Long): String = if (timeMillis <= System.currentTimeMillis()) {
    "现在该重新留意了"
} else {
    "下次留意：${formatAbsoluteTime(timeMillis)}"
}

private fun formatCommitmentTime(timeMillis: Long): String = if (timeMillis <= System.currentTimeMillis()) {
    "已到约定时间 · ${formatAbsoluteTime(timeMillis)}"
} else {
    "约定时间：${formatAbsoluteTime(timeMillis)}"
}

private fun formatAbsoluteTime(timeMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    return Instant.ofEpochMilli(timeMillis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
