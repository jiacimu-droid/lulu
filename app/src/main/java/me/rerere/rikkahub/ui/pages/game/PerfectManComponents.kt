package me.rerere.rikkahub.ui.pages.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Sparkles
import me.rerere.hugeicons.stroke.Voice
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.theme.CustomColors

@Composable
internal fun PerfectManRoundHeader(
    round: Int,
    phase: PerfectManRoundPhase,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("第 $round 轮", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                if (phase == PerfectManRoundPhase.UserGuesses) "对面描述，我来猜分。" else "我来描述，对面猜分。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun PerfectManPlayerSelector(
    selectedPlayer: Assistant?,
    assistants: List<Assistant>,
    onSelect: (String?) -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("选择玩家", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Text(
                    selectedPlayer?.name?.takeIf { it.isNotBlank() } ?: "未选择角色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(assistants, key = { it.id.toString() }) { assistant ->
                    FilterChip(
                        selected = selectedPlayer?.id == assistant.id,
                        onClick = { onSelect(assistant.id.toString()) },
                        label = {
                            Text(
                                assistant.name.ifBlank { "玩家" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun PerfectManOpponentSeatCard(
    line: String,
    speakingEnabled: Boolean,
    onSpeak: () -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = PerfectManComponentColors.accent.copy(alpha = 0.16f),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(HugeIcons.MagicWand01, contentDescription = null, tint = PerfectManComponentColors.accent)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("对面玩家", style = MaterialTheme.typography.labelLarge, color = PerfectManComponentColors.accent)
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onSpeak, enabled = speakingEnabled && line.isNotBlank()) {
                Icon(HugeIcons.VolumeHigh, contentDescription = "播放对面玩家的话")
            }
        }
    }
}

@Composable
internal fun PerfectManVoiceSettingsCard(
    opponentVoiceEnabled: Boolean,
    onOpponentVoiceEnabledChange: (Boolean) -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(HugeIcons.VolumeHigh, contentDescription = null, tint = PerfectManComponentColors.accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("对方语音", fontWeight = FontWeight.SemiBold)
            }
            Switch(checked = opponentVoiceEnabled, onCheckedChange = onOpponentVoiceEnabledChange)
        }
    }
}

@Composable
internal fun PerfectManActionCard(
    phase: PerfectManRoundPhase,
    score: Int,
    promptReady: Boolean,
    result: PerfectManRoundResult?,
    description: String,
    onDescriptionChange: (String) -> Unit,
    onExample: () -> Unit,
    guessText: String,
    onGuessTextChange: (String) -> Unit,
    listeningTarget: PerfectManVoiceInputTarget?,
    isListening: Boolean,
    onVoiceDescription: () -> Unit,
    onVoiceGuess: () -> Unit,
    isGenerating: Boolean,
    onStartPrompt: () -> Unit,
    onSubmitDescription: () -> Unit,
    onSubmitGuess: () -> Unit,
    onNextRound: () -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                perfectManActionTitle(phase, promptReady),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (phase == PerfectManRoundPhase.PartnerGuesses) {
                Surface(
                    color = PerfectManComponentColors.accent.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("本轮分数", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$score / 10", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    }
                }
            }

            when {
                result != null -> {
                    PerfectManResultInline(result = result)
                    Button(onClick = onNextRound, modifier = Modifier.fillMaxWidth()) {
                        Icon(HugeIcons.Refresh03, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("下一轮")
                    }
                }

                phase == PerfectManRoundPhase.UserGuesses && !promptReady -> {
                    Button(
                        onClick = onStartPrompt,
                        enabled = !isGenerating,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(HugeIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isGenerating) "对面正在想" else "开始")
                    }
                }

                phase == PerfectManRoundPhase.UserGuesses -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = guessText,
                            onValueChange = onGuessTextChange,
                            modifier = Modifier.weight(1f),
                            label = { Text("0-10 分") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        IconButton(onClick = onVoiceGuess) {
                            Icon(
                                HugeIcons.Voice,
                                contentDescription = if (
                                    listeningTarget == PerfectManVoiceInputTarget.Guess && isListening
                                ) {
                                    "停止听写"
                                } else {
                                    "语音猜分"
                                },
                            )
                        }
                    }
                    Button(
                        onClick = onSubmitGuess,
                        enabled = !isGenerating && guessText.trim().toFloatOrNull() != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(HugeIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isGenerating) "对面正在回应" else "发送分数")
                    }
                }

                else -> {
                    OutlinedTextField(
                        value = description,
                        onValueChange = onDescriptionChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        label = { Text("这是一个满分男，但是...") },
                        placeholder = { Text("例如：10天不洗脚，也不洗澡。") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(onClick = onVoiceDescription, modifier = Modifier.weight(1f)) {
                            Icon(HugeIcons.Voice, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (listeningTarget == PerfectManVoiceInputTarget.Flaw && isListening) {
                                    "停止听写"
                                } else {
                                    "语音输入"
                                },
                            )
                        }
                        OutlinedButton(onClick = onExample, modifier = Modifier.weight(1f)) {
                            Icon(HugeIcons.Sparkles, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("随机缺点")
                        }
                    }
                    Button(
                        onClick = onSubmitDescription,
                        enabled = !isGenerating && description.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(HugeIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isGenerating) "对方正在想" else "发送给对方")
                    }
                }
            }
        }
    }
}

@Composable
private fun PerfectManResultInline(result: PerfectManRoundResult) {
    Surface(
        color = if (result.success) {
            PerfectManComponentColors.success.copy(alpha = 0.14f)
        } else {
            PerfectManComponentColors.soft.copy(alpha = 0.18f)
        },
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (result.success) "差值 ${result.diff} 分，算默契。" else "差值 ${result.diff} 分。",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "真实分：${result.score}，猜分：${result.guess}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private object PerfectManComponentColors {
    val accent = Color(0xFF8B3D5E)
    val success = Color(0xFF2E8B68)
    val soft = Color(0xFF6F6A87)
}
