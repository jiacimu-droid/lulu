package me.rerere.rikkahub.ui.pages.voicecall

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Call02
import me.rerere.hugeicons.stroke.Moon02
import me.rerere.hugeicons.stroke.PlayCircle
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.rikkahub.data.voicecall.VoiceCallLine
import me.rerere.rikkahub.data.voicecall.VoiceCallRole
import me.rerere.rikkahub.data.voicecall.VoiceCallSession
import me.rerere.rikkahub.ui.components.message.splitIntoVisualBubbles
import me.rerere.rikkahub.ui.theme.CustomColors

@Composable
internal fun CallContentCard(
    stage: CallStage,
    isHistoryOnly: Boolean,
    assistantName: String,
    session: VoiceCallSession,
    onStartCall: () -> Unit,
    onReplay: (VoiceCallLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.78f),
            contentColor = Color(0xFF303744),
        ),
    ) {
        when {
            stage == CallStage.Idle && !isHistoryOnly -> IdleCallPanel(
                assistantName = assistantName,
                onStartCall = onStartCall,
            )

            stage == CallStage.Connecting && !isHistoryOnly -> ConnectingPanel(assistantName = assistantName)

            else -> TranscriptList(session = session, onReplay = onReplay)
        }
    }
}

@Composable
internal fun IdleCallPanel(
    assistantName: String,
    onStartCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("随时都可以聊一会儿", style = MaterialTheme.typography.titleMedium, color = Color(0xFF303744))
        Spacer(Modifier.height(8.dp))
        Text("呼叫 $assistantName", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF747D8E))
        Spacer(Modifier.height(28.dp))
        Surface(
            modifier = Modifier.size(96.dp).clickable(onClick = onStartCall),
            shape = CircleShape,
            color = Color(0xFFDCE8FF),
            contentColor = Color(0xFF365682),
            shadowElevation = 12.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(HugeIcons.Call02, contentDescription = "开始通话", modifier = Modifier.size(42.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("开始通话", style = MaterialTheme.typography.labelLarge, color = Color(0xFF596579))
    }
}

@Composable
private fun ConnectingPanel(assistantName: String) {
    val transition = rememberInfiniteTransition(label = "calling_pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(760),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "calling_pulse_scale",
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(76.dp).scale(pulse).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                HugeIcons.Call02,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("正在呼叫 $assistantName...", style = MaterialTheme.typography.titleMedium)
        Text(
            "正在接通",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TranscriptList(
    session: VoiceCallSession,
    onReplay: (VoiceCallLine) -> Unit,
) {
    val listState = rememberLazyListState()
    val visibleTranscript = remember(session.transcript) {
        session.transcript.filter { it.role != VoiceCallRole.System }
    }
    LaunchedEffect(visibleTranscript.size) {
        if (visibleTranscript.isNotEmpty()) {
            listState.animateScrollToItem(visibleTranscript.lastIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(visibleTranscript) { line ->
            TranscriptLine(line = line, onReplay = { onReplay(line) })
        }
        if (visibleTranscript.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "等待通话内容...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptLine(
    line: VoiceCallLine,
    onReplay: () -> Unit,
) {
    val isUser = line.role == VoiceCallRole.User
    val displayText = remember(line.text, line.role) {
        if (line.role == VoiceCallRole.Assistant) line.text.cleanRoleLineForUser() else line.text
    }
    val segments = remember(displayText, line.role) {
        if (line.role == VoiceCallRole.Assistant) displayText.splitIntoVisualBubbles() else emptyList()
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = when (line.role) {
                VoiceCallRole.User -> "我"
                VoiceCallRole.Assistant -> "对方"
                VoiceCallRole.System -> "系统"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isUser && line.replayable && displayText.isNotBlank()) {
                IconButton(onClick = onReplay, modifier = Modifier.size(34.dp)) {
                    Icon(HugeIcons.PlayCircle, contentDescription = null)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (line.role == VoiceCallRole.Assistant && segments.isNotEmpty()) {
                    segments.forEach { segment ->
                        TranscriptSegmentBubble(text = segment, isUser = false)
                    }
                } else {
                    TranscriptSegmentBubble(text = displayText, isUser = isUser)
                }
            }
        }
    }
}

@Composable
private fun TranscriptSegmentBubble(
    text: String,
    isUser: Boolean,
) {
    val color = if (isUser) Color(0xFFDCE8FF) else Color(0xFFFFF9F4)
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(color)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF303744))
    }
}

@Composable
internal fun SleepModePanel(
    enabled: Boolean,
    minutes: Long,
    onEnabledChange: (Boolean) -> Unit,
    onMinutesChange: (Long) -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(HugeIcons.Moon02, contentDescription = null)
                    Text("哄睡通话模式", fontWeight = FontWeight.Medium)
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10L, 20L, 30L, 45L).forEach { item ->
                    FilterChip(
                        selected = minutes == item,
                        onClick = { onMinutesChange(item) },
                        label = { Text("${item}m") },
                    )
                }
            }
        }
    }
}

@Composable
internal fun VoiceCallHistoryItem(
    session: VoiceCallSession,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (session.sleepMode) HugeIcons.Moon02 else HugeIcons.Call02, contentDescription = null)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(session.assistantName, style = MaterialTheme.typography.titleSmall)
                Text(
                    formatTime(session.startedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${session.transcript.count { it.role != VoiceCallRole.System }} 条记录",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun MiniCallWindow(
    assistantName: String,
    stage: CallStage,
    isSpeaking: Boolean,
    onOpen: () -> Unit,
    onEnd: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(HugeIcons.Call02, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(assistantName, style = MaterialTheme.typography.labelLarge)
                Text(
                    miniStatusText(stage, isSpeaking),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEnd, modifier = Modifier.size(34.dp)) {
                Icon(HugeIcons.StopCircle, contentDescription = null)
            }
        }
    }
}
