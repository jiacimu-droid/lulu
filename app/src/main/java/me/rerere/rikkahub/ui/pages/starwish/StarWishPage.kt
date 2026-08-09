package me.rerere.rikkahub.ui.pages.starwish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft02
import me.rerere.hugeicons.stroke.BookOpen02
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.starwish.StarWishRules
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun StarWishPage(
    vm: StarWishVM = koinViewModel(),
    plotVM: StarWishPlotGeneratorVM = koinViewModel(),
) {
    val navController = LocalNavController.current
    val state by vm.state.collectAsStateWithLifecycle()
    val plotCandidates by plotVM.candidates.collectAsStateWithLifecycle()
    val isGeneratingPlot by plotVM.isGenerating.collectAsStateWithLifecycle()
    val plotError by plotVM.error.collectAsStateWithLifecycle()
    var showPlotGenerator by remember { mutableStateOf(false) }
    var customTitle by remember { mutableStateOf("") }
    var customPrompt by remember { mutableStateOf("") }

    val theaters = state.customTheaters

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton() },
                title = { Text("小剧场") },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = StarWishColors.paper,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding + PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Button(
                    onClick = {
                        plotVM.clear()
                        showPlotGenerator = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("生成小剧场")
                }
            }
            item {
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = { customPrompt = it },
                    label = { Text("故事设定") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
            item {
                Button(
                    onClick = {
                        vm.addCustomTheater(customTitle, customPrompt)
                        customTitle = ""
                        customPrompt = ""
                    },
                    enabled = customTitle.isNotBlank() && customPrompt.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("添加")
                }
            }
            items(theaters, key = { it.id }) { theater ->
                val chapterCount = state.theaterChapters[theater.title].orEmpty().count { it.content.isNotBlank() }
                StarWishListRow(
                    title = theater.title,
                    subtitle = if (chapterCount == 0) "尚未开篇" else "$chapterCount 章",
                    unlocked = true,
                    progress = if (chapterCount == 0) 0f else 1f,
                    icon = HugeIcons.BookOpen02,
                    onClick = { navController.navigate(Screen.StarWishTheater(theater.title)) },
                    onDelete = { vm.deleteTheater(theater.title) },
                )
            }
        }
    }

    if (showPlotGenerator) {
        StarWishPlotGeneratorDialog(
            existingTitle = null,
            existingPremise = null,
            candidates = plotCandidates,
            isGenerating = isGeneratingPlot,
            error = plotError,
            onGenerate = { direction -> plotVM.generate(null, null, direction) },
            onApply = { candidate ->
                plotVM.createFromCandidate(candidate)
                showPlotGenerator = false
            },
            onDismiss = { showPlotGenerator = false },
        )
    }
}

@Composable
fun StarWishTheaterPage(
    theaterTitle: String,
    vm: StarWishVM = koinViewModel(),
    plotVM: StarWishPlotGeneratorVM = koinViewModel(),
) {
    val navController = LocalNavController.current
    val state by vm.state.collectAsStateWithLifecycle()
    val isGeneratingChapter by vm.isGeneratingChapter.collectAsStateWithLifecycle()
    val chapterError by vm.chapterError.collectAsStateWithLifecycle()
    val plotCandidates by plotVM.candidates.collectAsStateWithLifecycle()
    val isGeneratingPlot by plotVM.isGenerating.collectAsStateWithLifecycle()
    val plotError by plotVM.error.collectAsStateWithLifecycle()
    val theater = state.customTheaters.firstOrNull { it.title == theaterTitle }
    val currentGuide = theater?.let { state.theaterGuides[it.title] ?: StarWishRules.defaultTheaterGuide(it) }
    var showMenu by remember(theaterTitle) { mutableStateOf(false) }
    var showPlotGenerator by remember(theaterTitle) { mutableStateOf(false) }
    var showPlotEditor by remember(theaterTitle) { mutableStateOf(false) }
    var showChapterNavigation by remember(theaterTitle) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (showPlotEditor) "剧情规划" else theaterTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    if (showPlotEditor) {
                        IconButton(onClick = { showPlotEditor = false }) {
                            Icon(HugeIcons.ArrowLeft02, contentDescription = "返回阅读")
                        }
                    } else {
                        BackButton()
                    }
                },
                actions = {
                    if (theater != null && !showPlotEditor) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(HugeIcons.MoreVertical, contentDescription = "更多")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("剧情规划") },
                                    onClick = {
                                        showMenu = false
                                        showPlotEditor = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("章节导航") },
                                    onClick = {
                                        showMenu = false
                                        showChapterNavigation = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("删除小剧场", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        vm.deleteTheater(theater.title)
                                        navController.popBackStack()
                                    },
                                )
                            }
                        }
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = StarWishColors.paper,
    ) { padding ->
        when {
            theater == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("找不到这个小剧场")
                }
            }
            showPlotEditor && currentGuide != null -> {
                StarWishPlotEditorPage(
                    theater = theater,
                    guide = currentGuide,
                    onSave = { guide -> vm.saveTheaterGuide(theater.title, guide) },
                    onRegenerate = {
                        plotVM.clear()
                        showPlotGenerator = true
                    },
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                StarWishImmersiveTheaterContent(
                    theater = theater,
                    chapters = state.theaterChapters[theater.title].orEmpty(),
                    isGenerating = isGeneratingChapter,
                    error = chapterError,
                    showChapterNavigation = showChapterNavigation,
                    onDismissChapterNavigation = { showChapterNavigation = false },
                    onCreateChapter = { influence -> vm.createNextChapter(theater.title, influence) },
                    onDeleteChapter = { chapterId -> vm.deleteChapter(theater.title, chapterId) },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    if (theater != null && showPlotGenerator) {
        val premise = currentGuide?.worldview?.ifBlank { theater.prompt } ?: theater.prompt
        StarWishPlotGeneratorDialog(
            existingTitle = theater.title,
            existingPremise = premise,
            candidates = plotCandidates,
            isGenerating = isGeneratingPlot,
            error = plotError,
            onGenerate = { direction -> plotVM.generate(theater.title, premise, direction) },
            onApply = { candidate ->
                plotVM.applyToExisting(theater.title, candidate)
                showPlotGenerator = false
                showPlotEditor = true
            },
            onDismiss = { showPlotGenerator = false },
        )
    }
}
