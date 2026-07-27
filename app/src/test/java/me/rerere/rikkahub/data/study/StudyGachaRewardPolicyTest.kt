package me.rerere.rikkahub.data.study

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyGachaRewardPolicyTest {
    @Test
    fun `purple result can become a four-round game ticket`() {
        val afterLegacy = StudyState(
            inventory = StudyInventory(douyinFragments = 1),
        )
        val raw = StudyDrawResult(
            rarity = StudyRarity.Rare,
            fragmentKey = "rare:douyin",
            title = "抖音时长券 · 20分钟",
            fragmentType = StudyFragmentType.Douyin,
        )

        val result = StudyGachaRewardPolicy.rebalance(
            stateAfterLegacyDraw = afterLegacy,
            rawResults = listOf(raw),
            random = FixedDoubleRandom(0.0),
        )

        assertEquals("游戏局数券 · 4局", result.results.single().title)
        assertEquals(1, StudyGachaRewardPolicy.gameRoundTicketCount(result.state))
        assertEquals(0, result.state.inventory.douyinFragments)
    }

    @Test
    fun `gold result can remain a video unlock card`() {
        val afterLegacy = StudyState(
            inventory = StudyInventory(videoFragments = 1),
        )
        val raw = StudyDrawResult(
            rarity = StudyRarity.Epic,
            fragmentKey = "epic:video",
            title = "视频解锁卡",
            fragmentType = StudyFragmentType.Video,
        )

        val result = StudyGachaRewardPolicy.rebalance(
            stateAfterLegacyDraw = afterLegacy,
            rawResults = listOf(raw),
            random = FixedDoubleRandom(0.99),
        )

        assertEquals("视频解锁卡", result.results.single().title)
        assertEquals(1, result.state.inventory.videoFragments)
        assertEquals(0, result.state.inventory.accessoryUnlockCards)
    }

    @Test
    fun `thirtieth consecutive normal pull is forced purple`() {
        val result = StudyRules.draw(
            state = StudyState(
                wallet = StudyWallet(kudos = StudyRules.SINGLE_DRAW_COST),
                drawsSinceNonNormal = StudyRules.NON_NORMAL_PITY_DRAW_COUNT - 1,
            ),
            count = 1,
            random = FixedDoubleRandom(0.0),
        )

        assertEquals(StudyRarity.Rare, result.results.single().rarity)
        assertEquals(0, result.state.drawsSinceNonNormal)
    }

    @Test
    fun `requested regular pool rates sum to six point two percent special`() {
        val specialRate = StudyGachaRewardPolicy.GAME_ROUND_RATE +
            StudyGachaRewardPolicy.DOUYIN_RATE +
            StudyGachaRewardPolicy.THEATER_RATE +
            StudyGachaRewardPolicy.GAME_UNLIMITED_RATE +
            StudyGachaRewardPolicy.VIDEO_RATE +
            StudyGachaRewardPolicy.ANIME_RATE

        assertEquals(0.062, specialRate, 0.0000001)
        assertTrue(StudyRules.NON_NORMAL_PITY_DRAW_COUNT == 30)
    }

    private class FixedDoubleRandom(vararg values: Double) : Random() {
        private val values = values.copyOf()
        private var index = 0

        override fun nextBits(bitCount: Int): Int = 0

        override fun nextDouble(): Double = values[index.coerceAtMost(values.lastIndex)].also {
            index += 1
        }
    }
}
