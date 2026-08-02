package me.rerere.rikkahub.ui.pages.starwish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun StarWishPlotGeneratorDialog(
    existingTitle: String?,
    existingPremise: String?,
    candidates: List<StarWishPlotCandidate>,
    isGenerating: Boolean,
    error: String?,
    onGenerate: (String) -> Unit,
    onApply: (StarWishPlotCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    var direction by remember(existingTitle) { mutableStateOf("") }
    var expandedIndex by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existingTitle == null) "剧情生成器" else "重新规划《$existingTitle》")
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        if (existingTitle == null) {
                            "露露会一次写出三套不同方向的小说方案。你不需要先想好，也可以留空让它自由发挥。"
                        } else {
                            "新方案不会立即覆盖现在的大纲。先生成三套候选，只有你点“采用”后才会替换。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = direction,
                        onValueChange = { direction = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("可选的引导方向") },
                        placeholder = { Text("例如：更暧昧、强强对抗、不要反派、甜中带刀……") },
                        minLines = 2,
                        maxLines = 4,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onGenerate(direction.trim()) },
                        enabled = !isGenerating,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        } else {
                            Text(if (candidates.isEmpty()) "生成三套剧情" else "换一批剧情")
                        }
                    }
                    if (!error.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }

                itemsIndexed(candidates) { index, candidate ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 2.dp,
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "方案 ${index + 1} · ${candidate.title}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(candidate.hook, style = MaterialTheme.typography.bodyMedium)
                            if (candidate.relationshipCore.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "关系主线：${candidate.relationshipCore}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (candidate.highlights.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "亮点：${candidate.highlights}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        expandedIndex = if (expandedIndex == index) null else index
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(if (expandedIndex == index) "收起大纲" else "查看大纲")
                                }
                                Button(
                                    onClick = { onApply(candidate) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("采用这套")
                                }
                            }
                            if (expandedIndex == index) {
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(10.dp))
                                Text(candidate.overview, style = MaterialTheme.typography.bodyMedium)
                                candidate.chapters.forEachIndexed { chapterIndex, chapter ->
                                    if (chapter.isNotBlank()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "第 ${chapterIndex + 1} 章",
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(chapter, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
