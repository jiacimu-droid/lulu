package me.rerere.rikkahub.ui.pages.cihai

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private const val READING_PREFS = "reading_documents"
private const val READING_DOCUMENT_SET = "documents"

@Composable
fun CihaiReadingPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val settingsStore = koinInject<SettingsStore>()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(READING_PREFS, 0) }
    var documents by remember { mutableStateOf(loadReadingDocuments(prefs.getStringSet(READING_DOCUMENT_SET, emptySet()).orEmpty())) }
    var selectedAssistantId by remember(settings.assistantId, settings.assistants) {
        mutableStateOf(settings.assistantId.takeIf { id -> settings.assistants.any { it.id == id } })
    }

    fun persist(next: List<ReadingDocument>) {
        documents = next
        prefs.edit().putStringSet(
            READING_DOCUMENT_SET,
            next.map { "${Uri.encode(it.name)}|${Uri.encode(it.uri)}" }.toSet(),
        ).apply()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "文档"
        if (documents.none { it.uri == uri.toString() }) {
            persist(documents + ReadingDocument(name, uri.toString()))
        }
    }

    Scaffold(containerColor = CustomColors.topBarColors.containerColor) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
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
                    Text("阅读", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("返回", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
                }
            }
            item {
                Button(onClick = { picker.launch(arrayOf("text/*", "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) }) {
                    Text("上传文档")
                }
            }
            if (settings.assistants.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(settings.assistants, key = { it.id }) { assistant ->
                            FilterChip(
                                selected = assistant.id == selectedAssistantId,
                                onClick = {
                                    selectedAssistantId = assistant.id
                                    scope.launch { settingsStore.updateAssistant(assistant.id) }
                                },
                                label = { Text(assistant.name.ifBlank { "未命名角色" }) },
                            )
                        }
                    }
                }
            }
            items(documents, key = { it.uri }) { document ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(document.name, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TextButton(
                            enabled = selectedAssistantId != null,
                            onClick = {
                                val assistantId = selectedAssistantId ?: return@TextButton
                                scope.launch {
                                    settingsStore.updateAssistant(assistantId)
                                    val conversation = Conversation.ofId(
                                        id = Uuid.random(),
                                        assistantId = assistantId,
                                        newConversation = true,
                                    )
                                    navController.navigate(Screen.Chat(conversation.id.toString(), files = listOf(document.uri)))
                                }
                            },
                        ) { Text("一起阅读") }
                        TextButton(onClick = { persist(documents.filterNot { it.uri == document.uri }) }) { Text("删除") }
                    }
                }
            }
        }
    }
}

private data class ReadingDocument(val name: String, val uri: String)

private fun loadReadingDocuments(values: Set<String>): List<ReadingDocument> = values.mapNotNull { value ->
    val parts = value.split('|', limit = 2)
    if (parts.size != 2) null else ReadingDocument(Uri.decode(parts[0]), Uri.decode(parts[1]))
}.sortedBy(ReadingDocument::name)
