package me.rerere.rikkahub.data.study

import kotlin.random.Random

/**
 * Exact entertainment reward split requested for the regular gacha pool.
 *
 * Aggregate rates are applied by MoonlightGachaRandom in StudyVM:
 * blue 93.8%, purple 4.5%, gold 1.5%, rainbow 0.2%.
 * This policy converts the legacy purple/gold subtype results into the new exact
 * subtypes while correcting the already-persisted inventory from StudyRules.draw.
 */
object StudyGachaRewardPolicy {
    const val GAME_ROUNDS_PER_TICKET: Int = 4
    const val FOUR_HOUR_STUDY_MINUTES: Int = 240
    const val FOUR_HOUR_DOUYIN_TICKETS: Int = 2

    const val GAME_ROUND_RATE: Double = 0.02
    const val DOUYIN_RATE: Double = 0.02
    const val THEATER_RATE: Double = 0.005
    const val GAME_UNLIMITED_RATE: Double = 0.01
    const val ACCESSORY_RATE: Double = 0.005
    const val ANIME_RATE: Double = 0.002

    private const val GAME_ROUND_KEY = "reward:game-round-ticket"
    private const val ACCESSORY_KEY = "reward:accessory-unlock-card"
    private const val FOUR_HOUR_BONUS_PREFIX = "system:four-hour-douyin:"

    data class RebalancedDraw(
        val state: StudyState,
        val results: List<StudyDrawResult>,
    )

    data class StudyBonusResult(
        val state: StudyState,
        val granted: Boolean,
    )

    fun rebalance(
        stateAfterLegacyDraw: StudyState,
        rawResults: List<StudyDrawResult>,
        random: Random = Random.Default,
    ): RebalancedDraw {
        var inventory = stateAfterLegacyDraw.inventory
        val mapped = rawResults.map { raw ->
            if (raw.rarity != StudyRarity.Normal) {
                inventory = inventory.removeLegacySpecial(raw)
            }
            val result = when (raw.rarity) {
                StudyRarity.Normal -> raw
                StudyRarity.Rare -> mapPurple(raw, random)
                StudyRarity.Epic -> mapGold(raw, random)
                StudyRarity.Rainbow -> StudyDrawResult(
                    rarity = StudyRarity.Rainbow,
                    fragmentKey = "rainbow:anime",
                    title = "番剧兑换券 · 3小时",
                    fragmentType = StudyFragmentType.Anime,
                    alreadyFull = raw.alreadyFull,
                )
            }
            if (result.rarity != StudyRarity.Normal) {
                inventory = inventory.addRequestedSpecial(result)
            }
            result
        }
        return RebalancedDraw(
            state = stateAfterLegacyDraw.copy(inventory = inventory),
            results = mapped,
        )
    }

    /**
     * At a 2% pool rate, 50 pulls yield one 20-minute Douyin ticket on average.
     * The once-per-day four-hour study reward grants two more tickets (40 minutes),
     * so a four-hour day has a guaranteed 40 minutes and a long-run expectation of
     * about 60 minutes without making purple results common inside the pool.
     */
    fun grantFourHourDouyinBonus(
        before: StudyState,
        after: StudyState,
    ): StudyBonusResult {
        val date = after.today.ifBlank { return StudyBonusResult(after, false) }
        val marker = "$FOUR_HOUR_BONUS_PREFIX$date"
        val beforeMinutes = before.dailyStudyRecords[date]?.studyMinutes ?: 0
        val afterMinutes = after.dailyStudyRecords[date]?.studyMinutes ?: 0
        if (
            beforeMinutes >= FOUR_HOUR_STUDY_MINUTES ||
            afterMinutes < FOUR_HOUR_STUDY_MINUTES ||
            marker in after.inventory.rareFragments
        ) {
            return StudyBonusResult(after, false)
        }
        return StudyBonusResult(
            state = after.copy(
                inventory = after.inventory.copy(
                    douyinFragments = after.inventory.douyinFragments + FOUR_HOUR_DOUYIN_TICKETS,
                    rareFragments = after.inventory.rareFragments + (marker to 1),
                ),
            ),
            granted = true,
        )
    }

    fun gameRoundTicketCount(state: StudyState): Int =
        state.inventory.rareFragments[GAME_ROUND_KEY] ?: 0

    fun accessoryCardCount(state: StudyState): Int =
        state.inventory.rareFragments[ACCESSORY_KEY] ?: 0

    fun consumeGameRoundTicket(state: StudyState): StudyState? {
        val count = gameRoundTicketCount(state)
        if (count <= 0) return null
        return state.copy(
            inventory = state.inventory.copy(
                rareFragments = state.inventory.rareFragments.withCount(GAME_ROUND_KEY, count - 1),
            ),
        )
    }

    fun consumeAccessoryCard(state: StudyState): StudyState? {
        val count = accessoryCardCount(state)
        if (count <= 0) return null
        return state.copy(
            inventory = state.inventory.copy(
                rareFragments = state.inventory.rareFragments.withCount(ACCESSORY_KEY, count - 1),
            ),
        )
    }

    private fun mapPurple(raw: StudyDrawResult, random: Random): StudyDrawResult {
        val roll = random.nextDouble()
        return when {
            roll < 4.0 / 9.0 -> StudyDrawResult(
                rarity = StudyRarity.Rare,
                fragmentKey = GAME_ROUND_KEY,
                title = "游戏局数券 · ${GAME_ROUNDS_PER_TICKET}局",
                fragmentType = null,
                alreadyFull = raw.alreadyFull,
            )
            roll < 8.0 / 9.0 -> StudyDrawResult(
                rarity = StudyRarity.Rare,
                fragmentKey = "rare:douyin",
                title = "抖音时长券 · 20分钟",
                fragmentType = StudyFragmentType.Douyin,
                alreadyFull = raw.alreadyFull,
            )
            else -> StudyDrawResult(
                rarity = StudyRarity.Rare,
                fragmentKey = "rare:theater",
                title = "剧场碎片",
                fragmentType = StudyFragmentType.Theater,
                alreadyFull = raw.alreadyFull,
            )
        }
    }

    private fun mapGold(raw: StudyDrawResult, random: Random): StudyDrawResult =
        if (random.nextDouble() < 2.0 / 3.0) {
            StudyDrawResult(
                rarity = StudyRarity.Epic,
                fragmentKey = "epic:game",
                title = "游戏畅玩券 · 120分钟",
                fragmentType = StudyFragmentType.Game,
                alreadyFull = raw.alreadyFull,
            )
        } else {
            StudyDrawResult(
                rarity = StudyRarity.Epic,
                fragmentKey = ACCESSORY_KEY,
                title = "饰品解锁卡",
                fragmentType = null,
                alreadyFull = raw.alreadyFull,
            )
        }

    private fun StudyInventory.removeLegacySpecial(result: StudyDrawResult): StudyInventory = when (result.fragmentType) {
        StudyFragmentType.Douyin -> copy(douyinFragments = (douyinFragments - 1).coerceAtLeast(0))
        StudyFragmentType.Theater -> copy(theaterFragments = (theaterFragments - 1).coerceAtLeast(0))
        StudyFragmentType.Game -> copy(gameFragments = (gameFragments - 1).coerceAtLeast(0))
        StudyFragmentType.Video -> copy(videoFragments = (videoFragments - 1).coerceAtLeast(0))
        StudyFragmentType.Anime -> copy(animeFragments = (animeFragments - 1).coerceAtLeast(0))
        null -> this
    }

    private fun StudyInventory.addRequestedSpecial(result: StudyDrawResult): StudyInventory = when {
        result.fragmentKey == GAME_ROUND_KEY -> copy(
            rareFragments = rareFragments.withCount(GAME_ROUND_KEY, (rareFragments[GAME_ROUND_KEY] ?: 0) + 1),
        )
        result.fragmentKey == ACCESSORY_KEY -> copy(
            rareFragments = rareFragments.withCount(ACCESSORY_KEY, (rareFragments[ACCESSORY_KEY] ?: 0) + 1),
        )
        result.fragmentType == StudyFragmentType.Douyin -> copy(douyinFragments = douyinFragments + 1)
        result.fragmentType == StudyFragmentType.Theater -> copy(theaterFragments = theaterFragments + 1)
        result.fragmentType == StudyFragmentType.Game -> copy(gameFragments = gameFragments + 1)
        result.fragmentType == StudyFragmentType.Video -> copy(videoFragments = videoFragments + 1)
        result.fragmentType == StudyFragmentType.Anime -> copy(animeFragments = animeFragments + 1)
        else -> this
    }

    private fun Map<String, Int>.withCount(key: String, count: Int): Map<String, Int> =
        if (count <= 0) this - key else this + (key to count)
}
