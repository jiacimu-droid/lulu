package me.rerere.rikkahub.data.study

import kotlin.random.Random

/**
 * Exact entertainment reward split for the regular gacha pool.
 *
 * Aggregate rates are applied by MoonlightGachaRandom in StudyVM:
 * blue 93.8%, purple 4.5%, gold 1.5%, rainbow 0.2%.
 * The only extra protection is the legacy consecutive 30-pull purple pity.
 */
object StudyGachaRewardPolicy {
    const val GAME_ROUNDS_PER_TICKET: Int = 4

    const val GAME_ROUND_RATE: Double = 0.02
    const val DOUYIN_RATE: Double = 0.02
    const val THEATER_RATE: Double = 0.005
    const val GAME_UNLIMITED_RATE: Double = 0.01
    const val VIDEO_RATE: Double = 0.005
    const val ANIME_RATE: Double = 0.002

    private const val GAME_ROUND_KEY = "reward:game-round-ticket"

    data class RebalancedDraw(
        val state: StudyState,
        val results: List<StudyDrawResult>,
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

    fun gameRoundTicketCount(state: StudyState): Int = state.inventory.gameRoundTickets

    fun consumeGameRoundTicket(state: StudyState): StudyState? {
        val count = gameRoundTicketCount(state)
        if (count <= 0) return null
        val title = "游戏局数券已使用 · 可玩${GAME_ROUNDS_PER_TICKET}局"
        return state.copy(
            inventory = state.inventory.copy(gameRoundTickets = count - 1),
            recentEvents = state.recentEvents.addGameRoundTicketUseEvent(title),
        )
    }

    /** Kept only until the obsolete VM entry point is removed; accessories are never awarded. */
    @Deprecated("Accessory cards are no longer part of the gacha pool")
    fun consumeAccessoryCard(@Suppress("UNUSED_PARAMETER") state: StudyState): StudyState? = null

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
                fragmentKey = "epic:video",
                title = "视频解锁卡",
                fragmentType = StudyFragmentType.Video,
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
        result.fragmentKey == GAME_ROUND_KEY -> copy(gameRoundTickets = gameRoundTickets + 1)
        result.fragmentType == StudyFragmentType.Douyin -> copy(douyinFragments = douyinFragments + 1)
        result.fragmentType == StudyFragmentType.Theater -> copy(theaterFragments = theaterFragments + 1)
        result.fragmentType == StudyFragmentType.Game -> copy(gameFragments = gameFragments + 1)
        result.fragmentType == StudyFragmentType.Video -> copy(videoFragments = videoFragments + 1)
        result.fragmentType == StudyFragmentType.Anime -> copy(animeFragments = animeFragments + 1)
        else -> this
    }

    private fun List<StudyEvent>.addGameRoundTicketUseEvent(title: String): List<StudyEvent> {
        val event = StudyEvent(
            id = "event-${System.currentTimeMillis()}-${size}",
            type = StudyEventType.Entertainment,
            title = title,
            detail = "使用 1 张游戏局数券",
            createdAt = System.currentTimeMillis(),
        )
        return (listOf(event) + this).take(40)
    }
}
