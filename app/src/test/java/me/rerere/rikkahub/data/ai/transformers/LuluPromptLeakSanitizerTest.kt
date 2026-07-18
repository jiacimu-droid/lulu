package me.rerere.rikkahub.data.ai.transformers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LuluPromptLeakSanitizerTest {
    @Test
    fun `removes leaked expression pool and user profile instructions`() {
        val leaked = """
            和宠溺，简短一句就好。
            这只是后台表达方向，不要把它原样说给用户。

            本轮可用表达池：TEXT，KAOMOJI
            表达池只是表达层 affordance，不决定是否行动，也不要逐字复述这些标签。

            用户资料（只作为理解用户和保持互动一致性的稳定设定，不要逐字复述）：
            昵称：木佳辞
            我的外貌：可爱的女孩
            聊天、称呼、关系感、身体/性别/外貌描述，都要优先遵守这些资料。
        """.trimIndent()

        val visible = sanitizeLuluVisibleExpression(leaked)

        assertEquals("我在呀，刚刚脑袋卡了一下，没有好好接住你。再跟我说一句嘛。", visible)
        assertFalse("affordance" in visible)
        assertFalse("用户资料" in visible)
        assertFalse("KAOMOJI" in visible)
    }

    @Test
    fun `keeps a natural reply while removing appended internal guidance`() {
        val visible = sanitizeLuluVisibleExpression(
            """
                我在呢，宝贝。
                本轮可用表达池：TEXT，KAOMOJI
                这只是后台表达方向，不要把它原样说给用户。
            """.trimIndent(),
        )

        assertEquals("我在呢，宝贝。", visible)
    }
}
