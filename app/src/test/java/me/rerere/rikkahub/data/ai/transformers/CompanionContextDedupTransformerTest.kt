package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionContextDedupTransformerTest {
    @Test
    fun `latest runtime snapshot replaces all older copies`() {
        val messages = listOf(
            UIMessage.system("<companion_runtime>old one</companion_runtime>"),
            UIMessage.user("第一句"),
            UIMessage.system("<companion_runtime>old two</companion_runtime>"),
            UIMessage.assistant("回应"),
            UIMessage.system("<companion_runtime>newest</companion_runtime>"),
            UIMessage.user("最新一句"),
        )

        val result = deduplicateTransientCompanionContext(messages)
        val runtimeBlocks = result.filter { "<companion_runtime" in it.toText() }

        assertEquals(1, runtimeBlocks.size)
        assertTrue(runtimeBlocks.single().toText().contains("newest"))
        assertEquals(listOf("第一句", "回应", "最新一句"), result.filterNot {
            "<companion_runtime" in it.toText()
        }.map(UIMessage::toText))
    }

    @Test
    fun `each transient kind keeps its newest snapshot independently`() {
        val result = deduplicateTransientCompanionContext(
            listOf(
                UIMessage.system("<companion_private_context>old</companion_private_context>"),
                UIMessage.system("<companion_presence_contract>old</companion_presence_contract>"),
                UIMessage.system("<companion_private_context>new</companion_private_context>"),
                UIMessage.system("<companion_presence_contract>new</companion_presence_contract>"),
            ),
        )

        assertEquals(2, result.size)
        assertTrue(result.all { it.toText().contains("new") })
    }

    @Test
    fun `identical static system blocks are sent only once but distinct ones remain`() {
        val result = deduplicateTransientCompanionContext(
            listOf(
                UIMessage.system("stable persona prompt"),
                UIMessage.system("world book A"),
                UIMessage.system(" stable persona prompt \n"),
                UIMessage.system("world book B"),
            ),
        )

        assertEquals(
            listOf("world book A", " stable persona prompt \n", "world book B"),
            result.map(UIMessage::toText),
        )
    }
}
