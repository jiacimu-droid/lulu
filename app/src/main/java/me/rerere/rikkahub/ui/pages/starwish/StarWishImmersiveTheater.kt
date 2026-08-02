package me.rerere.rikkahub.ui.pages.starwish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.data.starwish.StarWishTheaterChapter
import me.rerere.rikkahub.data.starwish.StarWishTheaterSeed

@Composable
internal fun StarWishImmersiveTheaterContent(
    theater: StarWishTheaterSeed,
    chapters: List<StarWishTheaterChapter>,
    rareFragments: Int,
    isGenerating: Boolean,
    error: String?,
    costPerChapter: Int,
    showChapterNavigation: Boolean,
    onDismissChapterNavigation: () -> Unit,
    onCreateChapter: (String) -> Unit,
    onDeleteChapter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val readableChapters = remember(chapters) {
        chapters
            .filter { it.content.isNotBlank() }
            .sortedBy { it.chapter }
    }
    var selectedChapterId by rememberSaveable(theater.title) {
        mutableStateOf(readableChapters.lastOrNull()?.id)
    }
    var knownChapterCount by rememberSaveable(theater.title) {
        mutableIntStateOf(readableChapters.size)
    }
    var influence by rememberSaveable(theater.title) { mutableStateOf("") }

    LaunchedEffect(readableChapters.map { it.id }) {
        val latestChapter = readableChapters.lastOrNull()
        when {
            readableChapters.size > knownChapterCount -> selectedChapterId = latestChapter?.id
            selectedChapterId == null -> selectedChapterId = latestChapter?.id
            readableChapters.none { it.id == selectedChapterId } -> selectedChapterId = latestChapter?.id
        }
        knownChapterCount = readableChapters.size
    }

    val selectedChapter = readableChapters.firstOrNull { it.id == selectedChapterId }
        ?: readableChapters.lastOrNull()
    val canGenerate = rareFragments >= costPerChapter && !isGenerating

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 190.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (selectedChapter == null) {
                item {
                    Column(
                        modifier = Modifier.fillParentMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("这本书还没有正文", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "在下方写下你想影响的剧情，然后生成第一章。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    Text(
                        selectedChapter.title.ifBlank { "第 ${selectedChapter.chapter} 章" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (selectedChapter.userInfluence.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "你的影响：${selectedChapter.userInfluence}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    Text(
                        selectedChapter.content,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            lineHeight = 30.sp,
                        ),
                    )
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onDeleteChapter(selectedChapter.id) }) {
                            Text("删除本章", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                OutlinedTextField(
                    value = influence,
                    onValueChange = { influence = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("我想影响下一章") },
                    placeholder = { Text("可留空，让剧情自然发展") },
                    minLines = 1,
                    maxLines = 3,
                )
                Button(
                    onClick = {
                        onCreateChapter(influence.trim())
                        influence = ""
                    },
                    enabled = canGenerate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        when {
                            isGenerating -> "续写中"
                            readableChapters.isEmpty() -> "生成第一章"
                            else -> "续写下一章"
                        },
                    )
                }
                when {
                    error?.isNotBlank() == true -> Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    !canGenerate && !isGenerating -> Text(
                        "需要 $costPerChapter 枚剧场碎片，当前 $rareFragments 枚",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (showChapterNavigation) {
        AlertDialog(
            onDismissRequest = onDismissChapterNavigation,
            title = { Text("章节导航") },
            text = {
                if (readableChapters.isEmpty()) {
                    Text("还没有可阅读的章节。")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(readableChapters, key = { it.id }) { chapter ->
                            TextButton(
                                onClick = {
                                    selectedChapterId = chapter.id
                                    onDismissChapterNavigation()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    chapter.title.ifBlank { "第 ${chapter.chapter} 章" },
                                    modifier = Modifier.weight(1f),
                                )
                                if (chapter.id == selectedChapter?.id) {
                                    Text("正在阅读", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissChapterNavigation) { Text("关闭") }
            },
        )
    }
}
