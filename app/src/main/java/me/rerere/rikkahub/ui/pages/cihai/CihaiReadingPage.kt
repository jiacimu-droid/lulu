package me.rerere.rikkahub.ui.pages.cihai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import me.rerere.rikkahub.data.starwish.StarWishRules
import me.rerere.rikkahub.data.starwish.StarWishStore
import me.rerere.rikkahub.data.starwish.StarWishTheaterChapter
import me.rerere.rikkahub.data.starwish.StarWishTheaterSeed
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

@Composable
fun CihaiReadingPage(onBack: () -> Unit) {
    val starWishStore = koinInject<StarWishStore>()
    val state by starWishStore.state.collectAsState()
    val readableTheaters = remember(state) {
        StarWishRules.allTheaters(state.customTheaters).mapNotNull { seed ->
            val chapters = state.theaterChapters[seed.title].orEmpty()
                .filter { it.content.isNotBlank() }
                .sortedBy { it.chapter }
            if (chapters.isEmpty()) null else TheaterReading(seed, chapters)
        }
    }
    var selectedTitle by remember(readableTheaters.map { it.seed.title }) { mutableStateOf<String?>(null) }
    val selected = readableTheaters.firstOrNull { it.seed.title == selectedTitle }
        ?: readableTheaters.firstOrNull()

    LaunchedEffect(readableTheaters.map { it.seed.title }) {
        if (selectedTitle == null || readableTheaters.none { it.seed.title == selectedTitle }) {
            selectedTitle = readableTheaters.randomOrNull()?.seed?.title
        }
    }

    Scaffold(containerColor = CustomColors.topBarColors.containerColor) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "阅读小剧场",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "女主角是我；名字含露的角色是他。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "返回",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
                    )
                }
            }

            if (readableTheaters.isEmpty()) {
                item {
                    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
                        Text(
                            text = "还没有可阅读的小剧场。先去星愿馆生成至少一章正文，这里就会随机挑一部给你读。",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                        )
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selected?.seed?.title.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "已生成 ${selected?.chapters?.size ?: 0} 章",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = {
                                selectedTitle = readableTheaters
                                    .filterNot { it.seed.title == selectedTitle }
                                    .ifEmpty { readableTheaters }
                                    .random(Random(System.currentTimeMillis()))
                                    .seed
                                    .title
                            },
                        ) {
                            Text("随机换一本")
                        }
                    }
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(readableTheaters, key = { it.seed.title }) { theater ->
                            FilterChip(
                                selected = theater.seed.title == selected?.seed?.title,
                                onClick = { selectedTitle = theater.seed.title },
                                label = {
                                    Text(theater.seed.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                            )
                        }
                    }
                }

                selected?.let { theater ->
                    items(theater.chapters, key = { it.id }) { chapter ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = chapter.title.ifBlank { "第 ${chapter.chapter} 章" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = chapter.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class TheaterReading(
    val seed: StarWishTheaterSeed,
    val chapters: List<StarWishTheaterChapter>,
)
