package me.rerere.rikkahub.ui.pages.starwish

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiImage
import me.rerere.hugeicons.stroke.BookOpen02
import me.rerere.hugeicons.stroke.Image03
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Play
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.starwish.StarWishOutfitPrompts
import me.rerere.rikkahub.data.starwish.StarWishRules
import me.rerere.rikkahub.data.starwish.StarWishScroll
import me.rerere.rikkahub.data.starwish.StarWishVideoItem
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

internal enum class StarWishSection(val label: String) {
    Scrolls("画卷"),
    Theaters("小剧场"),
    Video("视频"),
}

private enum class ScrollSubsection(val label: String) {
    Images("图片"),
    Prompts("提示词"),
}

@Composable
fun StarWishPage(
    vm: StarWishVM = koinViewModel(),
    plotVM: StarWishPlotGeneratorVM = koinViewModel(),
) {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val state by vm.state.collectAsStateWithLifecycle()
    val generatedImages by vm.generatedImages.collectAsStateWithLifecycle()
    val studyState by vm.studyState.collectAsStateWithLifecycle()
    val plotCandidates by plotVM.candidates.collectAsStateWithLifecycle()
    val isGeneratingPlot by plotVM.isGenerating.collectAsStateWithLifecycle()
    val plotError by plotVM.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedVideo by remember { mutableStateOf<StarWishVideoItem?>(null) }
    var section by remember { mutableStateOf(StarWishSection.Scrolls) }
    var scrollSubsection by remember { mutableStateOf(ScrollSubsection.Prompts) }
    var selectedScroll by remember { mutableStateOf<Pair<String, StarWishScroll>?>(null) }
    var showPlotGenerator by remember { mutableStateOf(false) }
    val companionAssistant = remember(settings.assistants, settings.assistantId, studyState.selectedAssistantId) {
        val selected = studyState.selectedAssistantId
        settings.assistants.firstOrNull { it.id.toString() == selected }
            ?: settings.getCurrentAssistant()
    }

    LaunchedEffect(state.lastSection) {
        section = StarWishSection.entries.firstOrNull { it.name == state.lastSection } ?: StarWishSection.Scrolls
    }
    LaunchedEffect(Unit) {
        vm.videoPlayback.collect { selectedVideo = it }
    }
    LaunchedEffect(Unit) {
        vm.videoMessage.collect { snackbarHostState.showSnackbar(it) }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshGeneratedImages()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(vm::importVideo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton() },
                title = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StarWishSection.entries.forEach { item ->
                            FilterChip(
                                selected = section == item,
                                onClick = {
                                    section = item
                                    vm.rememberSection(item.name)
                                },
                                label = { Text(item.label, maxLines = 1) },
                            )
                        }
                    }
                },
                actions = {
                    if (section == StarWishSection.Theaters) {
                        Text(
                            "碎片 ${studyState.inventory.theaterFragments}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = StarWishColors.paper,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(StarWishColors.paper),
            contentPadding = padding + PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (section) {
                StarWishSection.Scrolls -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScrollSubsection.entries.forEach {
                                FilterChip(
                                    selected = scrollSubsection == it,
                                    onClick = { scrollSubsection = it },
                                    label = { Text(it.label) },
                                )
                            }
                        }
                    }
                    when (scrollSubsection) {
                        ScrollSubsection.Images -> {
                            val visibleLaunches = state.imageLaunches.filterNot { it.id in state.hiddenImageLaunchIds }
                            if (visibleLaunches.isEmpty() && generatedImages.isEmpty()) {
                                item {
                                    StarWishEmptyCard(
                                        title = "还没有画卷图片",
                                        subtitle = "点亮画卷后选择独美或互动生成，完成的图片会留在这里。",
                                        icon = HugeIcons.Image03,
                                        onClick = { scrollSubsection = ScrollSubsection.Prompts },
                                    )
                                }
                            } else {
                                item {
                                    Button(onClick = vm::refreshGeneratedImages, modifier = Modifier.fillMaxWidth()) {
                                        Text("刷新图片")
                                    }
                                }
                                if (generatedImages.isNotEmpty()) {
                                    items(generatedImages) { image ->
                                        StarWishGeneratedImageRow(image = image, onDelete = { vm.deleteGeneratedImage(image.id) })
                                    }
                                } else {
                                    items(visibleLaunches) { launch ->
                                        StarWishImageLaunchRow(launch = launch, onDelete = { vm.deleteImageLaunch(launch.id) })
                                    }
                                }
                                item {
                                    StarWishEmptyCard(
                                        title = "查看图片库",
                                        subtitle = "已生成图片会保存在生图页图库里。",
                                        icon = HugeIcons.AiImage,
                                        onClick = { navController.navigate(Screen.ImageGen()) },
                                    )
                                }
                            }
                        }
                        ScrollSubsection.Prompts -> {
                            val scrollEntries = StudyRules.outfitNames
                                .map { outfit -> outfit to StarWishRules.scrollForOutfit(outfit) }
                                .filterNot { (_, scroll) -> scroll.title in state.hiddenScrollTitles }
                            items(scrollEntries) { (outfit, scroll) ->
                                val unlocked = StarWishRules.scrollUnlockedForOutfit(studyState, outfit)
                                val launches = state.imageLaunches.count { it.outfit == scroll.title || it.outfit == outfit }
                                StarWishListRow(
                                    title = scroll.title,
                                    subtitle = if (unlocked) "已点亮 · 已生成 $launches 次" else "未解锁 · 去考研 App 收集完整套装",
                                    unlocked = unlocked,
                                    progress = outfitProgress(outfit, studyState.inventory.normalFragments),
                                    icon = HugeIcons.AiImage,
                                    onClick = { if (unlocked) selectedScroll = outfit to scroll },
                                    onDelete = { vm.deleteScroll(scroll.title, outfit) },
                                )
                            }
                        }
                    }
                }
                StarWishSection.Theaters -> {
                    item {
                        Button(
                            onClick = {
                                plotVM.clear()
                                showPlotGenerator = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("剧情生成器 · 给我三套好看的故事")
                        }
                    }
                    val theaters = StarWishRules.allTheaters(state.customTheaters)
                        .filterNot { it.title in state.hiddenTheaterTitles }
                    if (theaters.isEmpty()) {
                        item {
                            StarWishEmptyCard(
                                title = "书架还是空的",
                                subtitle = "用上面的剧情生成器挑一套喜欢的故事。",
                                icon = HugeIcons.BookOpen02,
                                onClick = {
                                    plotVM.clear()
                                    showPlotGenerator = true
                                },
                            )
                        }
                    } else {
                        items(theaters) { theater ->
                            val chapters = state.theaterChapters[theater.title].orEmpty()
                                .filterNot { it.isPromptPlaceholder(theater) }
                                .size
                            val canCreate = studyState.inventory.theaterFragments >= StarWishRules.RARE_FRAGMENTS_PER_CHAPTER
                            StarWishListRow(
                                title = theater.title,
                                subtitle = when {
                                    chapters > 0 -> "$chapters 章 · 点击继续阅读"
                                    canCreate -> "尚未开篇 · 点击生成第一章"
                                    else -> "尚未开篇 · 剧场碎片不足"
                                },
                                unlocked = canCreate || chapters > 0,
                                progress = if (chapters > 0) 1f else 0f,
                                icon = HugeIcons.BookOpen02,
                                onClick = {
                                    if (canCreate || chapters > 0) {
                                        vm.rememberSection(StarWishSection.Theaters.name)
                                        navController.navigate(Screen.StarWishTheater(theater.title))
                                    }
                                },
                                onDelete = { vm.deleteTheater(theater.title) },
                            )
                        }
                    }
                }
                StarWishSection.Video -> {
                    val videos = StarWishRules.allVideos(state.customVideos)
                        .filterNot { it.id in state.hiddenVideoIds }
                    val unlockedCount = videos.count { it.id in state.unlockedVideoIds }
                    item {
                        VideoRewardCard(
                            epicFragments = studyState.inventory.videoFragments,
                            unlocked = unlockedCount,
                            total = videos.size,
                            onUnlock = vm::unlockNextVideoOrPlayRandom,
                            onUpload = { videoPickerLauncher.launch("video/*") },
                        )
                    }
                    if (videos.isEmpty()) {
                        item {
                            StarWishEmptyCard(
                                title = "还没有视频",
                                subtitle = "上传 AI 生成的视频后，可使用视频碎片解锁。",
                                icon = HugeIcons.Play,
                                onClick = { videoPickerLauncher.launch("video/*") },
                            )
                        }
                    } else {
                        items(videos) { video ->
                            StarWishVideoRow(
                                video = video,
                                unlocked = video.id in state.unlockedVideoIds,
                                onPlay = { vm.playVideo(video) },
                                onDelete = { vm.deleteVideo(video) },
                            )
                        }
                    }
                }
            }
        }
    }

    selectedScroll?.let { (outfit, scroll) ->
        val promptKey = scroll.title
        val saved = state.customOutfitPrompts[promptKey] ?: state.customOutfitPrompts[outfit]
        val prompts = saved ?: StarWishOutfitPrompts(
            solo = StarWishRules.imagePromptForCompanion(
                basePrompt = scroll.soloPrompt,
                assistant = companionAssistant,
                interaction = false,
            ),
            interaction = StarWishRules.imagePromptForCompanion(
                basePrompt = scroll.interactionPrompt,
                assistant = companionAssistant,
                interaction = true,
                userNickname = settings.displaySetting.userNickname,
                userProfile = settings.displaySetting.userProfile,
                userAppearancePrompt = settings.displaySetting.userAppearancePrompt,
            ),
        )
        ScrollDetailDialog(
            scroll = scroll,
            outfit = outfit,
            prompts = prompts,
            launches = state.imageLaunches.filter { it.outfit == promptKey || it.outfit == outfit },
            onDismiss = { selectedScroll = null },
            onSave = { vm.savePrompts(promptKey, it) },
            onCopy = {
                clipboard.setText(AnnotatedString(it))
                scope.launch { snackbarHostState.showSnackbar("提示词已复制") }
            },
            onGenerate = { prompt, isInteraction ->
                val finalPrompt = StarWishRules.imagePromptForCompanion(
                    basePrompt = prompt,
                    assistant = companionAssistant,
                    interaction = isInteraction,
                    userNickname = settings.displaySetting.userNickname,
                    userProfile = settings.displaySetting.userProfile,
                    userAppearancePrompt = settings.displaySetting.userAppearancePrompt,
                )
                vm.recordImageLaunch(promptKey, finalPrompt)
                selectedScroll = null
                navController.navigate(Screen.ImageGen(initialPrompt = finalPrompt, count = 1, autoGenerate = false))
            },
        )
    }

    selectedVideo?.let { video ->
        StarWishVideoPlayerDialog(video = video, onDismiss = { selectedVideo = null })
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
    val studyState by vm.studyState.collectAsStateWithLifecycle()
    val isGeneratingChapter by vm.isGeneratingChapter.collectAsStateWithLifecycle()
    val chapterError by vm.chapterError.collectAsStateWithLifecycle()
    val plotCandidates by plotVM.candidates.collectAsStateWithLifecycle()
    val isGeneratingPlot by plotVM.isGenerating.collectAsStateWithLifecycle()
    val plotError by plotVM.error.collectAsStateWithLifecycle()
    val theater = StarWishRules.allTheaters(state.customTheaters).firstOrNull { it.title == theaterTitle }
    var showMenu by remember(theaterTitle) { mutableStateOf(false) }
    var showPlotGenerator by remember(theaterTitle) { mutableStateOf(false) }
    var showChapterNavigation by remember(theaterTitle) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        theaterTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = { BackButton() },
                actions = {
                    if (theater != null) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(HugeIcons.MoreVertical, contentDescription = "更多")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("重新生成剧情规划") },
                                    onClick = {
                                        showMenu = false
                                        plotVM.clear()
                                        showPlotGenerator = true
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
                                    text = { Text("清空本书", color = MaterialTheme.colorScheme.error) },
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
        if (theater == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("这本小剧场暂时找不到了")
            }
        } else {
            StarWishImmersiveTheaterContent(
                theater = theater,
                chapters = state.theaterChapters[theater.title].orEmpty(),
                rareFragments = studyState.inventory.theaterFragments,
                isGenerating = isGeneratingChapter,
                error = chapterError,
                costPerChapter = StarWishRules.RARE_FRAGMENTS_PER_CHAPTER,
                showChapterNavigation = showChapterNavigation,
                onDismissChapterNavigation = { showChapterNavigation = false },
                onCreateChapter = { influence -> vm.createNextChapter(theater.title, influence) },
                onDeleteChapter = { chapterId -> vm.deleteChapter(theater.title, chapterId) },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (theater != null && showPlotGenerator) {
        StarWishPlotGeneratorDialog(
            existingTitle = theater.title,
            existingPremise = theater.prompt,
            candidates = plotCandidates,
            isGenerating = isGeneratingPlot,
            error = plotError,
            onGenerate = { direction -> plotVM.generate(theater.title, theater.prompt, direction) },
            onApply = { candidate ->
                plotVM.applyToExisting(theater.title, candidate)
                showPlotGenerator = false
            },
            onDismiss = { showPlotGenerator = false },
        )
    }
}
