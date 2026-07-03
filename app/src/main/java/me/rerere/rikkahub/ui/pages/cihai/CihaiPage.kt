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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.cihai.CihaiBook
import me.rerere.rikkahub.data.cihai.CihaiEntry
import me.rerere.rikkahub.data.cihai.CihaiEntryKind
import me.rerere.rikkahub.data.cihai.CihaiService
import me.rerere.rikkahub.data.cihai.CihaiStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.living.LivingPresenceStore
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CihaiPage(onBack: () -> Unit) {
    val settings = LocalSettings.current
    val store = koinInject<CihaiStore>()
    val service = koinInject<CihaiService>()
    val livingPresenceStore = koinInject<LivingPresenceStore>()
    val state by store.state.collectAsState()
    val livingPresenceState by livingPresenceStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val fallbackAssistant = settings.getCurrentAssistant()
    val selectedAssistantId = state.selectedAssistantId
        .takeIf { id -> settings.assistants.any { it.id.toString() == id } }
        ?: fallbackAssistant.id.toString()
    val selectedAssistant = settings.assistants.firstOrNull { it.id.toString() == selectedAssistantId }
        ?: fallbackAssistant
    val livingIntentCards = remember(livingPresenceState.activeIntents, selectedAssistantId) {
        buildLivingIntentCards(
            intents = livingPresenceState.activeIntents,
            selectedAssistantId = selectedAssistantId,
        )
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        CihaiEntryKind.INNER_JOURNAL,
        CihaiEntryKind.ACTION_LOG,
        CihaiEntryKind.READING_NOTE,
        CihaiEntryKind.REFLECTION,
    )

    LaunchedEffect(selectedAssistantId) {
        if (state.selectedAssistantId != selectedAssistantId) {
            store.selectAssistant(selectedAssistantId)
        }
    }

    Scaffold(containerColor = CustomColors.topBarColors.containerColor) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "露露日记",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "返回",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
                )
            }
            Text(
                text = "露露和其他角色没说出口的话、行动记录、阅读感悟和反思总结都会在这里留下来，并进入向量记忆和图谱记忆。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AssistantSelector(
                selectedAssistantId = selectedAssistantId,
                onSelect = { id -> scope.launch { store.selectAssistant(id) } },
            )
            Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    UIAvatar(
                        name = selectedAssistant.name,
                        value = selectedAssistant.avatar,
                        modifier = Modifier.size(48.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selectedAssistant.name.ifBlank { "当前角色" }, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "当前查看这个角色的活人感日记。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, kind ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(kind.label, maxLines = 1) },
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (livingIntentCards.isNotEmpty()) {
                    item {
                        LivingIntentPanel(cards = livingIntentCards)
                    }
                }
                item {
                    CihaiComposer(
                        assistantId = selectedAssistantId,
                        kind = tabs[selectedTab],
                        service = service,
                    )
                }
                if (tabs[selectedTab] == CihaiEntryKind.READING_NOTE) {
                    item {
                        ReadingImportCard(
                            assistantId = selectedAssistantId,
                            service = service,
                        )
                    }
                    items(
                        state.books.filter { it.assistantId == selectedAssistantId },
                        key = { it.id },
                    ) { book ->
                        BookCard(
                            book = book,
                            onRead = {
                                scope.launch {
                                    service.readBookAndRemember(book)
                                }
                            },
                        )
                    }
                }
                items(
                    state.entries.filter {
                        it.assistantId == selectedAssistantId && it.kind == tabs[selectedTab]
                    },
                    key = { it.id },
                ) { entry ->
                    EntryCard(entry)
                }
            }
        }
    }
}

@Composable
private fun LivingIntentPanel(cards: List<LivingIntentCardModel>) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("挂在心里的事", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "这些不是随机主动消息，而是角色正在滚动判断、克制、观察和等待的事件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            cards.forEach { card ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = card.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = card.statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Text(
                            text = card.nextEvaluateText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(card.bdiLine, style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = card.hypothesesLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = card.cadenceLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${card.countLine}\n${card.emotionLine}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        card.capabilityLine?.let { capabilityLine ->
                            Text(
                                text = capabilityLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantSelector(
    selectedAssistantId: String,
    onSelect: (String) -> Unit,
) {
    val settings = LocalSettings.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(settings.assistants, key = { it.id.toString() }) { assistant ->
            FilterChip(
                selected = assistant.id.toString() == selectedAssistantId,
                onClick = { onSelect(assistant.id.toString()) },
                label = { Text(assistant.name.ifBlank { "角色" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun CihaiComposer(
    assistantId: String,
    kind: CihaiEntryKind,
    service: CihaiService,
) {
    val scope = rememberCoroutineScope()
    var title by remember(kind, assistantId) { mutableStateOf(defaultTitle(kind)) }
    var content by remember(kind, assistantId) { mutableStateOf("") }
    var emotion by remember(kind, assistantId) { mutableStateOf("") }
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("新增${kind.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("标题") },
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 7,
                label = { Text("内容") },
            )
            OutlinedTextField(
                value = emotion,
                onValueChange = { emotion = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("情绪 / 心理状态") },
            )
            Button(
                onClick = {
                    val entry = CihaiEntry(
                        assistantId = assistantId,
                        kind = kind,
                        title = title,
                        content = content,
                        emotion = emotion,
                    )
                    scope.launch {
                        service.addEntryAndRemember(entry)
                        content = ""
                        emotion = ""
                    }
                },
                enabled = content.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("写入日记")
            }
        }
    }
}

@Composable
private fun ReadingImportCard(
    assistantId: String,
    service: CihaiService,
) {
    val scope = rememberCoroutineScope()
    var title by remember(assistantId) { mutableStateOf("") }
    var content by remember(assistantId) { mutableStateOf("") }
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("阅读材料", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("书名 / 文章名") },
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
                label = { Text("粘贴内容") },
            )
            Button(
                onClick = {
                    scope.launch {
                        service.addBook(
                            CihaiBook(
                                assistantId = assistantId,
                                title = title,
                                content = content,
                            )
                        )
                        service.addEntryAndRemember(
                            CihaiEntry(
                                assistantId = assistantId,
                                kind = CihaiEntryKind.READING_NOTE,
                                title = "收到阅读材料：《$title》",
                                content = "我把《$title》放进辞海阅读室。用户不在的时候，我可以阅读它、写感悟，并把理解沉淀进记忆。",
                                sourceTitle = title,
                            )
                        )
                        title = ""
                        content = ""
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("放入阅读")
            }
        }
    }
}

@Composable
private fun EntryCard(entry: CihaiEntry) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = if (entry.memorySaved) "已入记忆" else "待记忆",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Text(entry.content, style = MaterialTheme.typography.bodyMedium)
            if (entry.emotion.isNotBlank()) {
                Text(
                    text = "情绪：${entry.emotion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatTime(entry.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BookCard(
    book: CihaiBook,
    onRead: () -> Unit,
) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = book.content.take(140),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "进度 ${book.progressPercent}% · ${formatTime(book.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRead,
                enabled = book.progressPercent < 100,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(if (book.progressPercent < 100) "读一段" else "已读完")
            }
        }
    }
}

private fun defaultTitle(kind: CihaiEntryKind): String = when (kind) {
    CihaiEntryKind.INNER_JOURNAL -> "没说出口的话"
    CihaiEntryKind.ACTION_LOG -> "我刚刚做了什么"
    CihaiEntryKind.READING_NOTE -> "阅读感悟"
    CihaiEntryKind.REFLECTION -> "这次我学到的事"
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
