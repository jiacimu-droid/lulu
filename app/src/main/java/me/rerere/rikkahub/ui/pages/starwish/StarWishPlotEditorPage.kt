package me.rerere.rikkahub.ui.pages.starwish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.starwish.StarWishTheaterGuide
import me.rerere.rikkahub.data.starwish.StarWishTheaterSeed

@Composable
internal fun StarWishPlotEditorPage(
    theater: StarWishTheaterSeed,
    guide: StarWishTheaterGuide,
    onSave: (StarWishTheaterGuide) -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var worldview by remember(theater.title, guide) {
        mutableStateOf(guide.worldview.ifBlank { theater.prompt })
    }
    var overview by remember(theater.title, guide) { mutableStateOf(guide.overview) }
    var wordCount by remember(theater.title, guide) { mutableStateOf(guide.wordCount) }
    var chapters by remember(theater.title, guide) {
        mutableStateOf(guide.chapters.ifEmpty { List(6) { "" } })
    }
    var savedNotice by remember(theater.title) { mutableStateOf(false) }

    fun currentGuide(): StarWishTheaterGuide = StarWishTheaterGuide(
        worldview = worldview,
        overview = overview,
        chapters = chapters,
        wordCount = wordCount,
    ).normalized()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                "剧情规划",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            PlannerSection(title = "世界观与故事设定") {
                OutlinedTextField(
                    value = worldview,
                    onValueChange = {
                        worldview = it
                        savedNotice = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 12,
                    placeholder = { Text("故事发生在哪里、人物处境、核心规则与开篇钩子") },
                )
            }
        }
        item {
            PlannerSection(title = "当前总纲") {
                OutlinedTextField(
                    value = overview,
                    onValueChange = {
                        overview = it
                        savedNotice = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 7,
                    maxLines = 18,
                    placeholder = { Text("主线、关系变化、伏笔、转折与最终高潮") },
                )
            }
        }
        item {
            PlannerSection(title = "每章建议字数") {
                OutlinedTextField(
                    value = wordCount,
                    onValueChange = {
                        wordCount = it
                        savedNotice = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("例如 1200-2200") },
                )
            }
        }
        itemsIndexed(chapters) { index, chapter ->
            PlannerSection(title = "第 ${index + 1} 章剧情指导") {
                OutlinedTextField(
                    value = chapter,
                    onValueChange = { value ->
                        chapters = chapters.toMutableList().also { it[index] = value }
                        savedNotice = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 10,
                    placeholder = { Text("本章事件、人物选择、关系变化、伏笔或高潮") },
                )
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    chapters = chapters + ""
                    savedNotice = false
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("＋ 增加一章规划")
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        onSave(currentGuide())
                        savedNotice = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存剧情规划")
                }
                OutlinedButton(
                    onClick = onRegenerate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("重新生成三套剧情方案")
                }
                if (savedNotice) {
                    Text(
                        "已保存，之后续写会使用这份规划。",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item { Row(Modifier.navigationBarsPadding()) {} }
    }
}

@Composable
private fun PlannerSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
