package me.rerere.rikkahub.data.study

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `gold result can become an accessory unlock card`() {
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

        assertEquals("饰品解锁卡", result.results.single().title)
        assertEquals(1, StudyGachaRewardPolicy.accessoryCardCount(result.state))
        assertEquals(0, result.state.inventory.videoFragments)
    }

    @Test
    fun `four hour study bonus grants two douyin tickets only once`() {
        val date = "2026-07-27"
        val before = StudyState(
            today = date,
            dailyStudyRecords = mapOf(date to StudyDailyRecord(studyMinutes = 220)),
        )
        val after = before.copy(
            dailyStudyRecords = mapOf(date to StudyDailyRecord(studyMinutes = 240)),
        )

        val first = StudyGachaRewardPolicy.grantFourHourDouyinBonus(before, after)
        val second = StudyGachaRewardPolicy.grantFourHourDouyinBonus(
            before = first.state,
            after = first.state.copy(
                dailyStudyRecords = mapOf(date to StudyDailyRecord(studyMinutes = 300)),
            ),
        )

        assertTrue(first.granted)
        assertEquals(2, first.state.inventory.douyinFragments)
        assertFalse(second.granted)
        assertEquals(2, second.state.inventory.douyinFragments)
    }

    @Test
    fun `requested regular pool rates sum to six point two percent special`() {
        val specialRate = StudyGachaRewardPolicy.GAME_ROUND_RATE +
            StudyGachaRewardPolicy.DOUYIN_RATE +
            StudyGachaRewardPolicy.THEATER_RATE +
            StudyGachaRewardPolicy.GAME_UNLIMITED_RATE +
            StudyGachaRewardPolicy.ACCESSORY_RATE +
            StudyGachaRewardPolicy.ANIME_RATE

        assertEquals(0.062, specialRate, 0.0000001)
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
