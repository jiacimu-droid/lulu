package me.rerere.rikkahub.ui.pages.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.study.StudyEntertainmentReward
import me.rerere.rikkahub.data.study.SuperMomentChoice
import me.rerere.rikkahub.data.starwish.StarWishRules
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate

private enum class StudySection(val label: String) {
    Companion("陪伴"),
    Today("今日"),
    Plan("计划"),
    Gacha("抽卡"),
    Collection("收藏"),
    Achievements("成就"),
    Shop("商店"),
    Guide("说明"),
}

@Composable
fun StudyPage(vm: StudyVM = koinViewModel()) {
    val navController = LocalNavController.current
    val settings = LocalSettings.current
    val state by vm.state.collectAsStateWithLifecycle()
    val isGeneratingSchedule by vm.isGeneratingSchedule.collectAsStateWithLifecycle()
    val companionAssistant = remember(settings.assistants, settings.assistantId, state.selectedAssistantId) {
        val selected = state.selectedAssistantId
        settings.assistants.firstOrNull { it.id.toString() == selected }
            ?: settings.getCurrentAssistant()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var section by remember { mutableStateOf(StudySection.Today) }
    var newTask by remember { mutableStateOf("") }
    var drawDialog by remember { mutableStateOf<List<StudyDrawReveal>?>(null) }
    var pendingBoxDialog by remember { mutableStateOf(false) }
    var boxDialog by remember { mutableStateOf<me.rerere.rikkahub.data.study.StudyMysteryBoxReward?>(null) }
    var showSuperDialog by remember { mutableStateOf(false) }
    var showLevelDialog by remember { mutableStateOf(false) }
    val isGachaSection = section == StudySection.Gacha
    val pageColor = if (isGachaSection) StudyGachaPageColor else StudyDefaultPageColor

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is StudyEffect.Message -> snackbarHostState.showSnackbar(effect.text)
                StudyEffect.MysteryBoxReady -> pendingBoxDialog = true
                is StudyEffect.MysteryBox -> boxDialog = effect.reward
                is StudyEffect.DrawResults -> drawDialog = effect.results
                StudyEffect.SuperMomentReady -> showSuperDialog = true
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = pageColor,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageColor),
        ) {
            if (isGachaSection) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(studyGachaPageBrush()),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = padding + PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = !isGachaSection,
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp).clickable { navController.popBackStack() },
                            shape = CircleShape,
                            color = if (isGachaSection) {
                                Color.White.copy(alpha = 0.90f)
                            } else {
                                Color.White.copy(alpha = 0.72f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isGachaSection) Color(0xFFFFC9A6) else StudyPageBlue.copy(alpha = 0.12f),
                            ),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    HugeIcons.ArrowLeft01,
                                    contentDescription = "返回",
                                    tint = if (isGachaSection) Color.White else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        StudySectionChips(
                            labels = StudySection.entries.map { it.label },
                            selectedLabel = section.label,
                            onSelected = { selectedLabel ->
                                StudySection.entries
                                    .firstOrNull { it.label == selectedLabel }
                                    ?.let { section = it }
                            },
                            gacha = isGachaSection,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                when (section) {
                    StudySection.Companion -> {
                        item {
                            StudyHeroPanel(
                                state = state,
                                assistant = companionAssistant,
                                assistants = settings.assistants,
                                onSignIn = vm::signIn,
                                onOpenLevel = { showLevelDialog = true },
                                onSelectCompanion = { vm.selectCompanion(it.id.toString()) },
                            )
                        }
                        item {
                            StudySleepHabitRewardCard(
                                state = state,
                                assistantName = companionAssistant.name.ifBlank { "当前角色" },
                            )
                        }
                        item { StudyRecentEventsCard(events = state.recentEvents) }
                    }

                    StudySection.Today -> {
                        item {
                            StudyTodayPomodoroLaunchCard(
                                onClick = { navController.navigate(Screen.StudyPomodoro) },
                            )
                        }
                        item {
                            StudyDailyDashboard(
                                tasks = state.tasks,
                                assistantName = companionAssistant.name.ifBlank { "当前角色" },
                                generatedSchedule = state.generatedSchedules[LocalDate.now().toString()],
                                isGeneratingSchedule = isGeneratingSchedule,
                                newTask = newTask,
                                onNewTask = { newTask = it },
                                onAdd = {
                                    vm.addTask(newTask)
                                    newTask = ""
                                },
                                onGenerateSchedule = vm::generateTodaySchedule,
                                onToggle = vm::toggleTask,
                                onDelete = vm::deleteTask,
                            )
                        }
                        item {
                            StudyTodayProgressCard(
                                state = state,
                                onClaimNormal = { vm.claimSuperMoment(SuperMomentChoice.NormalFragments) },
                            )
                        }
                    }

                    StudySection.Plan -> {
                        item { StudyPlanOverviewPanel() }
                    }

                    StudySection.Gacha -> {
                        item {
                            val standardGachaState = state.copy(
                                wallet = state.wallet.copy(purpleDrawTickets = 0),
                            )
                            GachaCard(
                                state = standardGachaState,
                                onSingle = { vm.draw(1) },
                                onTen = { vm.draw(10) },
                                onPurple = vm::drawPurpleTicket,
                            )
                        }
                    }

                    StudySection.Collection -> {
                        item {
                            StudyCollectionPanel(
                                inventory = state.inventory,
                                onUseUniversalNormalTarget = vm::applyUniversalNormal,
                                onOpenMysteryBox = { vm.openMysteryBox(it) },
                                onRedeemDouyin = { vm.redeemEntertainment(StudyEntertainmentReward.Douyin) },
                                onRedeemGameRoundTicket = vm::redeemGameRoundTicket,
                                onRedeemGame = { vm.redeemEntertainment(StudyEntertainmentReward.Game) },
                                onRedeemAnime = { vm.redeemEntertainment(StudyEntertainmentReward.Anime) },
                                onOpenStarWish = { navController.navigate(Screen.StarWish) },
                                onOpenImageGen = { outfit ->
                                    val scroll = StarWishRules.scrollForOutfit(outfit)
                                    val prompt = StarWishRules.imagePromptForCompanion(
                                        basePrompt = scroll.soloPrompt,
                                        assistant = companionAssistant,
                                        interaction = false,
                                        userNickname = settings.displaySetting.userNickname,
                                        userProfile = settings.displaySetting.userProfile,
                                        userAppearancePrompt = settings.displaySetting.userAppearancePrompt,
                                    )
                                    navController.navigate(
                                        Screen.ImageGen(
                                            initialPrompt = prompt,
                                            count = 1,
                                            autoGenerate = false,
                                        ),
                                    )
                                },
                            )
                        }
                    }

                    StudySection.Achievements -> {
                        item {
                            StudyAchievementPanel(
                                state = state,
                                onClaim = vm::claimAchievement,
                            )
                        }
                    }

                    StudySection.Shop -> {
                        item {
                            StudyShopPanel(
                                state = state,
                                onRefresh = vm::refreshShop,
                                onBuy = vm::buyShopItem,
                            )
                        }
                    }

                    StudySection.Guide -> {
                        item { StudyRewardGuidePanel() }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            vm.syncToday()
            delay(60_000)
        }
    }

    boxDialog?.let { reward ->
        StudyMysteryBoxCelebration(
            reward = reward,
            onDismissRequest = { boxDialog = null },
        )
    }

    if (pendingBoxDialog) {
        StudyMysteryBoxPendingDialog(
            onOpen = {
                pendingBoxDialog = false
                vm.openMysteryBox()
            },
            onKeep = { pendingBoxDialog = false },
        )
    }

    drawDialog?.let { results ->
        StudyDrawResultCelebration(
            results = results,
            onDismissRequest = { drawDialog = null },
        )
    }

    if (showSuperDialog) {
        StudySuperMomentCelebration(
            assistant = companionAssistant,
            onDismissRequest = { showSuperDialog = false },
            onClaimNormal = {
                showSuperDialog = false
                vm.claimSuperMoment(SuperMomentChoice.NormalFragments)
            },
        )
    }

    if (showLevelDialog) {
        StudyLevelDialog(
            state = state,
            onClaimLevel = vm::claimLevel,
            onDismissRequest = { showLevelDialog = false },
        )
    }
}

@Composable
fun StudyPomodoroPage() {
    StudyPomodoroPageContent()
}

@Composable
fun StudyPomodoroFocusPage(
    minutes: Int,
    task: String,
    imageEnabled: Boolean,
    voiceEnabled: Boolean,
    vm: StudyVM = koinViewModel(),
) {
    StudyPomodoroFocusPageContent(
        minutes = minutes,
        task = task,
        imageEnabled = imageEnabled,
        voiceEnabled = voiceEnabled,
        vm = vm,
    )
}

private fun studyGachaPageBrush(): Brush = Brush.linearGradient(
    listOf(Color(0xFFFFF4BD), Color(0xFFFFD8C7), Color(0xFFD7ECFF)),
)

private val StudyDefaultPageColor = Color(0xFFF7F3EA)
private val StudyGachaPageColor = Color(0xFFFFE4C8)
private val StudyPageBlue = Color(0xFF3D7EA6)
