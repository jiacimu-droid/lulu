package me.rerere.rikkahub.data.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AffectiveMemoryExtractionSupportTest {
    @Test
    fun `extracts json after reasoning tags`() {
        val raw = """
            <think>先分析哪些内容值得记忆。</think>
            {"memories":[]}
        """.trimIndent()

        assertEquals("{\"memories\":[]}", raw.extractJsonPayload())
    }

    @Test
    fun `extracts fenced json with trailing explanation`() {
        val raw = """
            结果如下：
            ```json
            {"memories":[{"content":"花括号 { 也可能出现在字符串里"}]}
            ```
            已完成。
        """.trimIndent()

        assertEquals(
            "{\"memories\":[{\"content\":\"花括号 { 也可能出现在字符串里\"}]}",
            raw.extractJsonPayload(),
        )
    }

    @Test
    fun `stops at first balanced json value`() {
        val raw = "模型回复：{\"memories\":[]} 后面这句话不属于 JSON"

        assertEquals("{\"memories\":[]}", raw.extractJsonPayload())
    }
}
