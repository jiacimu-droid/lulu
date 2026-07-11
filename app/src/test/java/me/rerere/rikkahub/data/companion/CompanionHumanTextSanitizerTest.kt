package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CompanionHumanTextSanitizerTest {
    @Test
    fun `technical tool instructions are removed from human facing commitment text`() {
        val cleaned = """
            刚才聊到了睡觉或休息，我会到点重新确认。
            到点时优先结合这些感知项或行动能力：get_gadgetbridge_data、get_app_usage、get_battery_info。
            - set_alarm: suggested args={}
        """.trimIndent().cleanCompanionHumanText("备用文案")

        assertEquals("刚才聊到了睡觉或休息，我会到点重新确认。", cleaned)
        assertFalse(cleaned.contains("get_gadgetbridge_data"))
        assertFalse(cleaned.contains("set_alarm"))
    }

    @Test
    fun `fully technical text uses a human fallback`() {
        val cleaned = "get_app_usage suggested args={}".cleanCompanionHumanText("我会继续留意。")

        assertEquals("我会继续留意。", cleaned)
    }
}
