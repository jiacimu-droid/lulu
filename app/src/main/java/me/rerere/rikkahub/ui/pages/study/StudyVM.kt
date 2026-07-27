package me.rerere.rikkahub.ui.pages.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.ApiUsageSource
import me.rerere.rikkahub.data.ai.ApiUsageStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.study.CurrentWeekStudyRecovery
import me.rerere.rikkahub.data.study.ExamStudyPlan
import me.rerere.rikkahub.data.study.StudyDrawResult
import me.rerere.rikkahub.data.study.StudyEntertainmentReward
import me.rerere.rikkahub.data.study.StudyGachaRewardPolicy
import me.rerere.rikkahub.data.study.StudyMysteryBoxReward
import me.rerere.rikkahub.data.study.StudyPlanTaskSync
import me.rerere.rikkahub.data.study.StudyRarity
import me.rerere.rikkahub.data.study.StudyRules
import me.rerere.rikkahub.data.study.StudyShopItem
import me.rerere.rikkahub.data.study.StudyState
import me.rerere.rikkahub.data.study.StudyStore
import me.rerere.rikkahub.data.study.StudyVocabularyPolicy
import me.rerere.rikkahub.data.study.SuperMomentChoice
import me.rerere.rikkahub.data.starwish.StarWishStore
import me.rerere.rikkahub.data.starwish.StarWishVideoItem
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

class StudyVM(
    private val store: StudyStore,
    private val starWishStore: StarWishStore,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val apiUsageStore: ApiUsageStore,
) : ViewModel() {
    val state: StateFlow<StudyState> = store.state

    private val _effects = MutableSharedFlow<StudyEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<StudyEffect> = _effects
    private val _isGeneratingSchedule = MutableStateFlow(false)
    val isGeneratingSchedule = _isGeneratingSchedule.asStateFlow()

    fun syncToday() = reduce { current ->
        StudyPlanTaskSync.sync(current, LocalDate.now())
    }

    fun selectCompanion(assistantId: String) = reduce { StudyRules.selectCompanion(it, assistantId) }

    fun signIn() = reduce {
        val result = StudyRules.signIn(it, LocalDate.now())
        emitReward(result.reward.title)
        result.state
    }

    fun addTask(title: String) = reduce {
        if (title.isBlank()) return@reduce it
        StudyRules.clearGeneratedSchedule(StudyRules.addTask(it, title), LocalDate.now())
    }

    fun deleteTask(id: String) = reduce {
        StudyRules.clearGeneratedSchedule(StudyRules.deleteTask(it, id), LocalDate.now())
    }

    fun generateTodaySchedule() {
        viewModelScope.launch {
            if (_isGeneratingSchedule.value) return@launch
            _isGeneratingSchedule.value = true
            try {
                val date = LocalDate.now()
                val currentTime = LocalTime.now()
                val currentState = StudyPlanTaskSync.sync(state.value, date)
                val settings = settingsStore.settingsFlow.first()
                val assistant = settings.getCurrentAssistant()
                val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)
                    ?.takeIf { it.type == ModelType.CHAT }
                    ?: error("请先设置主聊天模型，再生成今日计划。")
                val providerSetting = model.findProvider(settings.providers)
                    ?: error("当前主聊天模型没有找到对应提供商。")
                val provider = providerManager.getProviderByType(providerSetting)
                val presetPlan = StudyPlanTaskSync.visiblePlan(currentState, date)
                val prompt = StudyVocabularyPolicy.normalizeText(
                    ExamStudyPlan.dynamicSchedulePrompt(
                        date = date,
                        presetPlan = presetPlan,
                        defaultSchedule = emptyList(),
                        tasks = currentState.tasks,
                        currentTime = currentTime,
                    ),
                )
                val scheduleSystemPrompt = buildString {
                    appendLine(ExamStudyPlan.dynamicScheduleSystemPrompt)
                    appendLine()
                    appendLine(CurrentWeekStudyRecovery.executionOrderReference)
                    appendLine()
                    appendLine("新课与三轮硬节点：10月7日前全部常规新课结束；10月14日前第一轮全科结束；10月31日前第二轮全科结束；11月30日前第三轮全科结束。")
                    appendLine("每日单词固定为${StudyVocabularyPolicy.dailyTarget}个（${StudyVocabularyPolicy.dailyGroups}组）；旧提示里的120词全部作废。")
                    appendLine("每周星期日完整休息；被用户删除的系统待办不得重新排回今日计划。")
                    appendLine("日计划可给建议章节，但不是强制日终点；周计划和月计划的章节终点必须硬验收。")
                }
                val chunk = provider.generateText(
                    providerSetting = providerSetting,
                    messages = listOf(
                        UIMessage.system(scheduleSystemPrompt),
                        UIMessage.user(prompt),
                    ),
                    params = TextGenerationParams(
                        model = model,
                        temperature = 0.35f,
                        topP = 0.9f,
                        maxTokens = 1800,
                        reasoningLevel = ReasoningLevel.OFF,
                    ),
                )
                chunk.usage?.let { usage ->
                    apiUsageStore.record(
                        source = ApiUsageSource.OTHER,
                        title = "学习：今日计划",
                        model = model.displayName.ifBlank { model.modelId },
                        provider = providerSetting.name.ifBlank { providerSetting.id.toString() },
                        usage = usage,
                    )
                }
                val text = StudyVocabularyPolicy.normalizeText(
                    chunk.choices.firstOrNull()?.message?.toText().orEmpty(),
                )
                val schedule = ExamStudyPlan.scheduleBlocksFromTime(
                    blocks = ExamStudyPlan.parseScheduleBlocks(text),
                    currentTime = currentTime,
                )
                if (schedule.isEmpty()) {
                    error("主 API 没有返回可读取的时间表，请再点一次生成。")
                }
                store.update { current ->
                    StudyRules.saveGeneratedSchedule(current, date, schedule)
                }
                _effects.tryEmit(StudyEffect.Message("今日计划表已生成"))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _effects.tryEmit(StudyEffect.Message(e.message ?: "今日计划表生成失败"))
            } finally {
                _isGeneratingSchedule.value = false
            }
        }
    }

    fun toggleTask(id: String, done: Boolean) = reduce {
        val result = StudyRules.toggleTask(it, id, done)
        emitReward(result.reward.title)
        if (result.state.superMomentAvailable && !it.superMomentAvailable) {
            _effects.tryEmit(StudyEffect.SuperMomentReady)
        }
        result.state
    }

    fun completePomodoro(minutes: Int) = reduce { current ->
        val result = StudyRules.completePomodoro(current, minutes, Random.Default)
        emitReward(result.reward.title)
        val bonus = StudyGachaRewardPolicy.grantFourHourDouyinBonus(
            before = current,
            after = result.state,
        )
        if (bonus.granted) {
            emitReward(
                "今日累计学习满4小时：抖音时长券 · 20分钟 x${StudyGachaRewardPolicy.FOUR_HOUR_DOUYIN_TICKETS}",
            )
        }
        bonus.state
    }

    fun openMysteryBox(index: Int = 0) = reduce {
        val result = StudyRules.openMysteryBox(it, index)
        if (result.reward.title.isNotBlank()) {
            _effects.tryEmit(
                StudyEffect.MysteryBox(
                    StudyMysteryBoxReward(
                        kudos = result.reward.kudos,
                        universalNormalFragments = result.reward.universalNormalFragments,
                    ),
                ),
            )
        }
        result.state
    }

    fun draw(count: Int) {
        viewModelScope.launch {
            var revealItems: List<StudyDrawReveal> = emptyList()
            var message: String? = null
            store.update { current ->
                // The new pool uses literal per-pull rates. Reset the legacy streak
                // before each 1/10-pull request so the retired 30-pull forced-purple
                // path cannot inflate the requested 2%/2%/0.5% purple split.
                val random = MoonlightGachaRandom(
                    delegate = Random.Default,
                    initialDrawsSinceNonNormal = 0,
                )
                val legacyResult = StudyRules.draw(
                    state = current.copy(drawsSinceNonNormal = 0),
                    drawCount = count,
                    random = random,
                )
                if (legacyResult.results.isEmpty()) {
                    message = "夸夸值或抽卡券不够"
                    return@update current
                }
                val legacyStateWithoutSafety = legacyResult.state
                    .suppressNewLegacyPurpleSafety(previousState = current)
                val balanced = StudyGachaRewardPolicy.rebalance(
                    stateAfterLegacyDraw = legacyStateWithoutSafety,
                    rawResults = legacyResult.results,
                    random = Random.Default,
                )
                val updatedStudy = balanced.state.correctMoonlightSpecialTracking(
                    previousState = current,
                    results = balanced.results,
                ).copy(drawsSinceNonNormal = 0)
                revealItems = balanced.results.map(::StudyDrawReveal)
                updatedStudy
            }
            message?.let {
                _effects.tryEmit(StudyEffect.Message(it))
                return@launch
            }
            _effects.tryEmit(StudyEffect.DrawResults(revealItems))
        }
    }

    fun drawPurpleTicket() {
        viewModelScope.launch {
            var revealItems: List<StudyDrawReveal> = emptyList()
            store.update { current ->
                val legacyResult = StudyRules.drawPurpleTicket(current, Random.Default)
                val balanced = StudyGachaRewardPolicy.rebalance(
                    stateAfterLegacyDraw = legacyResult.state,
                    rawResults = legacyResult.results,
                    random = Random.Default,
                )
                revealItems = balanced.results.map(::StudyDrawReveal)
                balanced.state
            }
            if (revealItems.isEmpty()) {
                _effects.tryEmit(StudyEffect.Message("没有可用的今日安全抽"))
            } else {
                _effects.tryEmit(StudyEffect.DrawResults(revealItems))
            }
        }
    }

    fun claimSuperMoment(choice: SuperMomentChoice) = reduce {
        val result = StudyRules.claimSuperMoment(it, choice)
        emitReward(result.reward.title)
        result.state
    }

    fun claimAchievement(id: String) = reduce {
        val result = StudyRules.claimAchievement(it, id)
        emitReward(result.reward.title)
        result.state
    }

    fun claimLevel(level: Int) = reduce {
        val result = StudyRules.claimLevelReward(it, level)
        emitReward(result.reward.title)
        result.state
    }

    fun refreshShop() = reduce { StudyRules.manualRefreshShop(it, LocalDate.now(), Random.Default) }

    fun buyShopItem(item: StudyShopItem) = reduce {
        val result = StudyRules.buyShopItem(it, item.id)
        emitReward(result.reward.title.ifBlank { "购买失败，夸夸值不够或商品已售罄" })
        result.state
    }

    fun redeemEntertainment(rewardType: StudyEntertainmentReward) = reduce {
        val result = StudyRules.redeemEntertainment(it, rewardType)
        emitReward(result.reward.title.ifBlank { "还需要 1 个对应碎片" })
        result.state
    }

    fun redeemGameRoundTicket() = reduce {
        val next = StudyGachaRewardPolicy.consumeGameRoundTicket(it)
        if (next == null) {
            emitReward("还没有游戏局数券")
            it
        } else {
            emitReward("游戏局数券已使用 · 可玩${StudyGachaRewardPolicy.GAME_ROUNDS_PER_TICKET}局")
            next
        }
    }

    fun redeemAccessoryCard() = reduce {
        val next = StudyGachaRewardPolicy.consumeAccessoryCard(it)
        if (next == null) {
            emitReward("还没有饰品解锁卡")
            it
        } else {
            emitReward("饰品解锁卡已使用")
            next
        }
    }

    fun applyUniversalNormal(key: String) = reduce {
        val result = StudyRules.useUniversalNormalFragment(it, key)
        emitReward(result.reward.title)
        result.state
    }

    fun applyBestUniversalNormal() = reduce {
        val key = StudyRules.bestNormalFragmentTarget(it)
        val result = key?.let { target -> StudyRules.useUniversalNormalFragment(it, target) }
        if (result == null) {
            _effects.tryEmit(StudyEffect.Message("普通套装已经全部补满"))
            it
        } else {
            emitReward(result.reward.title)
            result.state
        }
    }

    fun applyPenalty() = reduce {
        val result = StudyRules.applyInactivityPenalty(it)
        emitReward(result.reward.title)
        result.state
    }

    private fun reduce(transform: (StudyState) -> StudyState) {
        viewModelScope.launch {
            store.update(transform)
        }
    }

    private fun emitReward(title: String) {
        if (title.isNotBlank()) {
            _effects.tryEmit(StudyEffect.Message(title))
        }
    }
}

/**
 * Rebalances only the regular-pool rarity roll while leaving StudyRules' payment,
 * persistence and reveal flow untouched. Its old cross-request pity is disabled by
 * always starting this adapter and StudyRules' legacy streak at zero.
 */
private class MoonlightGachaRandom(
    private val delegate: Random,
    initialDrawsSinceNonNormal: Int,
) : Random() {
    private var drawsSinceNonNormal = initialDrawsSinceNonNormal
        .coerceIn(0, StudyRules.NON_NORMAL_PITY_DRAW_COUNT - 1)
    private var waitingForSubtypeRoll = false

    override fun nextBits(bitCount: Int): Int {
        if (bitCount == 0) return 0
        return delegate.nextInt().ushr(Int.SIZE_BITS - bitCount)
    }

    override fun nextDouble(): Double {
        if (waitingForSubtypeRoll) {
            waitingForSubtypeRoll = false
            return delegate.nextDouble()
        }

        if (drawsSinceNonNormal >= StudyRules.NON_NORMAL_PITY_DRAW_COUNT - 1) {
            drawsSinceNonNormal = 0
            return delegate.nextDouble()
        }

        val roll = delegate.nextDouble()
        return when {
            roll < MOONLIGHT_NORMAL_END -> {
                drawsSinceNonNormal += 1
                mapRoll(roll, 0.0, MOONLIGHT_NORMAL_END, 0.0, LEGACY_NORMAL_END)
            }
            roll < MOONLIGHT_RARE_END -> {
                drawsSinceNonNormal = 0
                waitingForSubtypeRoll = true
                mapRoll(roll, MOONLIGHT_NORMAL_END, MOONLIGHT_RARE_END, LEGACY_NORMAL_END, LEGACY_RARE_END)
            }
            roll < MOONLIGHT_EPIC_END -> {
                drawsSinceNonNormal = 0
                waitingForSubtypeRoll = true
                mapRoll(roll, MOONLIGHT_RARE_END, MOONLIGHT_EPIC_END, LEGACY_RARE_END, LEGACY_EPIC_END)
            }
            else -> {
                drawsSinceNonNormal = 0
                mapRoll(roll, MOONLIGHT_EPIC_END, 1.0, LEGACY_EPIC_END, 1.0)
            }
        }
    }

    private fun mapRoll(
        value: Double,
        sourceStart: Double,
        sourceEnd: Double,
        targetStart: Double,
        targetEnd: Double,
    ): Double {
        val fraction = (value - sourceStart) / (sourceEnd - sourceStart)
        return targetStart + fraction * (targetEnd - targetStart)
    }

    private companion object {
        // Requested regular-pool rates:
        // blue 93.8%, purple 4.5%, gold 1.5%, rainbow 0.2%.
        const val MOONLIGHT_NORMAL_END = 0.938
        const val MOONLIGHT_RARE_END = 0.983
        const val MOONLIGHT_EPIC_END = 0.998

        // Legacy StudyRules thresholds. Mapping into these intervals keeps payment,
        // inventory persistence and the reveal animation in one existing path.
        const val LEGACY_NORMAL_END = 0.9215
        const val LEGACY_RARE_END = 0.9815
        const val LEGACY_EPIC_END = 0.9965
    }
}

private fun StudyState.suppressNewLegacyPurpleSafety(previousState: StudyState): StudyState {
    val drawDate = today.ifBlank { LocalDate.now().toString() }
    val newlyGranted =
        previousState.purpleSafetyGrantedDate != drawDate &&
            purpleSafetyGrantedDate == drawDate &&
            wallet.purpleDrawTickets > previousState.wallet.purpleDrawTickets
    if (!newlyGranted) return this
    return copy(
        wallet = wallet.copy(
            purpleDrawTickets = (wallet.purpleDrawTickets - 1).coerceAtLeast(0),
        ),
        purpleSafetyGrantedDate = previousState.purpleSafetyGrantedDate,
    )
}

private fun StudyState.correctMoonlightSpecialTracking(
    previousState: StudyState,
    results: List<StudyDrawResult>,
): StudyState {
    val specialCount = results.count { it.rarity != StudyRarity.Normal }
    val purpleCount = results.count { it.rarity == StudyRarity.Rare }
    val goldOrRainbowCount = specialCount - purpleCount
    if (goldOrRainbowCount <= 0) return this

    val drawDate = today.ifBlank { LocalDate.now().toString() }
    val safetyWasIncorrectlyGranted =
        purpleCount == 0 &&
            previousState.purpleSafetyGrantedDate != drawDate &&
            purpleSafetyGrantedDate == drawDate &&
            wallet.purpleDrawTickets > previousState.wallet.purpleDrawTickets

    return copy(
        wallet = if (safetyWasIncorrectlyGranted) {
            wallet.copy(purpleDrawTickets = (wallet.purpleDrawTickets - 1).coerceAtLeast(0))
        } else {
            wallet
        },
        // Keep the old serialized field name for compatibility, but from this point
        // it records all purple/gold/rainbow results, not purple alone.
        dailyPurpleDrawCount = dailyPurpleDrawCount + goldOrRainbowCount,
        purpleSafetyGrantedDate = if (safetyWasIncorrectlyGranted) {
            previousState.purpleSafetyGrantedDate
        } else {
            purpleSafetyGrantedDate
        },
    )
}

sealed interface StudyEffect {
    data class Message(val text: String) : StudyEffect
    data object MysteryBoxReady : StudyEffect
    data class MysteryBox(val reward: StudyMysteryBoxReward) : StudyEffect
    data class DrawResults(val results: List<StudyDrawReveal>) : StudyEffect
    data object SuperMomentReady : StudyEffect
}

data class StudyDrawReveal(
    val result: StudyDrawResult,
    val video: StarWishVideoItem? = null,
)
