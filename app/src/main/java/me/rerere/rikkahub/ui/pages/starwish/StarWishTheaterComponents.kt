package me.rerere.rikkahub.ui.pages.starwish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BookOpen02
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Play
import me.rerere.rikkahub.data.starwish.StarWishRules
import me.rerere.rikkahub.data.starwish.StarWishTheaterChapter
import me.rerere.rikkahub.data.starwish.StarWishTheaterGuide
import me.rerere.rikkahub.data.starwish.StarWishTheaterSeed

@Composable
internal fun TheaterDetailContent(
    theater: StarWishTheaterSeed,
    credits: Int,
    rareFragments: Int,
    chapters: List<StarWishTheaterChapter>,
    isGenerating: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
    costPerChapter: Int = StarWishRules.RARE_FRAGMENTS_PER_CHAPTER,
    fragmentLabel: String = "剧场碎片",
    onCreateChapter: (String) -> Unit,
    onDeleteChapter: (String) -> Unit,
) {
    val visibleChapters = remember(theater, chapters) { chapters.filterNot { it.isPromptPlaceholder(theater) } }
    var influence by remember(theater.title, visibleChapters.size) { mutableStateOf("") }
    var showCatalog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 178.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$fragmentLabel $rareFragments · 可兑换 $credits 章 · 已生成 ${visibleChapters.size} 章",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (visibleChapters.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.78f))) {
                        Text(
                            "还没有正文。花 $costPerChapter 个$fragmentLabel 生成第一章；之后每次再花 $costPerChapter 个$fragmentLabel 续写下一章。",
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(visibleChapters) {
                Surface(color = Color.White.copy(alpha = 0.86f), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(it.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDeleteChapter(it.id) }) {
                                Icon(HugeIcons.Delete01, contentDescription = "删除章节")
                            }
                        }
                        if (it.userInfluence.isNotBlank()) {
                            Text("你的影响：${it.userInfluence}", style = MaterialTheme.typography.bodySmall, color = StarWishColors.inkBlue)
                        }
                        Text(it.content, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Box(modifier = Modifier.align(Alignment.BottomStart)) {
            Surface(
                color = Color.White.copy(alpha = 0.92f),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 2.dp,
            ) {
                TextButton(onClick = { showCatalog = true }) {
                    Icon(HugeIcons.BookOpen02, null)
                    Spacer(Modifier.width(6.dp))
                    Text("目录")
                }
            }
            DropdownMenu(expanded = showCatalog, onDismissRequest = { showCatalog = false }) {
                if (visibleChapters.isEmpty()) {
                    DropdownMenuItem(text = { Text("暂无章节") }, onClick = { showCatalog = false })
                } else {
                    visibleChapters.forEachIndexed { index, chapter ->
                        DropdownMenuItem(
                            text = { Text(chapter.title) },
                            onClick = {
                                showCatalog = false
                                scope.launch { listState.animateScrollToItem(index + 1) }
                            },
                        )
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = StarWishColors.paper.copy(alpha = 0.96f),
            tonalElevation = 2.dp,
        ) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = influence,
                    onValueChange = { influence = it },
                    label = { Text(if (visibleChapters.isEmpty()) "给第一章一点方向（可选）" else "我想影响下一章（可选）") },
                    placeholder = { Text("例如：让露臣这章彻底低头，顺便狠狠打脸恶人") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        onCreateChapter(influence)
                        influence = ""
                    },
                    enabled = !isGenerating && rareFragments >= costPerChapter,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(HugeIcons.Play, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            isGenerating -> "生成中..."
                            visibleChapters.isEmpty() -> "生成第一章"
                            else -> "续写下一章"
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun TheaterGuideDialog(
    theaterTitle: String,
    guide: StarWishTheaterGuide,
    overviewFallback: String,
    onDismiss: () -> Unit,
    onSave: (StarWishTheaterGuide) -> Unit,
) {
    var overview by remember(guide) { mutableStateOf(guide.overview) }
    var wordCount by remember(guide) { mutableStateOf(guide.wordCount) }
    val chapters = remember(guide) {
        mutableStateListOf<String>().apply {
            addAll(guide.chapters.ifEmpty { List(6) { "" } })
        }
    }
    val chapterSnapshot = chapters.toList()
    val preview = remember(overview, wordCount, chapterSnapshot, overviewFallback) {
        val normalized = StarWishTheaterGuide(
            overview = overview,
            chapters = chapterSnapshot,
            wordCount = wordCount,
        ).normalized()
        buildString {
            appendLine("剧情介绍：")
            appendLine(normalized.overview.ifBlank { overviewFallback })
            appendLine()
            appendLine("章节规划：")
            normalized.chapters.forEachIndexed { index, chapter ->
                appendLine("第 ${index + 1} 章：${chapter.ifBlank { "可自由发挥，但必须承接总剧情介绍。" }}")
            }
            appendLine()
            appendLine("每章字数：${normalized.wordCount}")
        }.trim()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        StarWishTheaterGuide(
                            overview = overview,
                            chapters = chapters.toList(),
                            wordCount = wordCount,
                        ),
                    )
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("$theaterTitle · 剧情规划") },
        text = {
            LazyColumn(
                modifier = Modifier.height(520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = overview,
                        onValueChange = { overview = it },
                        label = { Text("剧情介绍") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = wordCount,
                        onValueChange = { wordCount = it },
                        label = { Text("每章字数") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                items(chapters.size) { index ->
                    OutlinedTextField(
                        value = chapters[index],
                        onValueChange = { chapters[index] = it },
                        label = { Text("第 ${index + 1} 章剧情") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    TextButton(onClick = { chapters.add("") }) {
                        Text("增加章节")
                    }
                }
                item {
                    Text("保存后预览", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

internal fun StarWishTheaterChapter.isPromptPlaceholder(
    seed: StarWishTheaterSeed,
): Boolean {
    val clean = content.trim()
    return clean == seed.prompt.trim() ||
        clean.startsWith("总设定：") ||
        clean.startsWith("你是一个擅长") ||
        clean.contains("硬性要求：") ||
        clean.contains("请根据下面设定生成")
}

@Composable
internal fun AddTheaterDialog(
    dialogTitle: String,
    description: String,
    promptLabel: String,
    promptRequired: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onAdd(title, prompt) }, enabled = title.isNotBlank() && (!promptRequired || prompt.isNotBlank())) {
                Text("添加")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(promptLabel) },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
