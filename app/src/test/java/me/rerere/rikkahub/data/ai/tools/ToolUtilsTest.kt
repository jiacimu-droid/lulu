package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolUtilsTest {
    @Test
    fun `generation tool selection keeps planner requests and trims unrelated schemas`() {
        val tools = (1..20).map { index -> tool("unrelated_$index") } + listOf(
            tool("set_alarm"),
            tool("favorite_user_message"),
            tool("write_lulu_journal"),
            tool("today_study_plan"),
        )

        val selected = tools.selectCompanionToolsForGeneration(
            messages = listOf(UIMessage.user("明天七点叫我起床")),
            preferredToolNames = listOf("set_alarm"),
        )

        assertTrue(selected.any { it.name == "set_alarm" })
        assertTrue(selected.any { it.name == "favorite_user_message" })
        assertTrue(selected.any { it.name == "today_study_plan" })
        assertTrue(selected.size <= 12)
    }

    @Test
    fun `study tool remains available even when current message uses no exact keyword`() {
        val tools = (1..20).map { index -> tool("unrelated_$index") } +
            listOf(tool("today_study_plan"), tool("favorite_user_message"))

        val selected = tools.selectCompanionToolsForGeneration(
            messages = listOf(UIMessage.user("你还记得我今天要做什么吗")),
        )

        assertTrue(selected.any { it.name == "today_study_plan" })
    }

    @Test
    fun `speech recognition aliases route to study tool and exclude calendar`() {
        val tools = listOf(tool("today_study_plan"), tool("calendar_tool"), tool("set_alarm"))

        listOf("我现在有多少框框值", "给我发画纸", "昨天早睡了").forEach { message ->
            val selected = tools.selectRelevantToolsForPrompt(listOf(UIMessage.user(message)))
            assertTrue(selected.any { it.name == "today_study_plan" })
            assertFalse(selected.any { it.name == "calendar_tool" })
        }
    }

    @Test
    fun `sleep reward guidance requires a real settlement instead of verbal promise`() {
        val guidance = humanLikeToolGuidance("today_study_plan")

        assertTrue(guidance.contains("必须在同一轮调用 action=claim_sleep_reward"))
        assertTrue(guidance.contains("reported_hour/minute 不知道时可以省略"))
        assertTrue(guidance.contains("不能凭记忆猜") || guidance.contains("绝不能凭记忆猜"))
    }

    @Test
    fun `generation tool selection keeps all tools when the set is already small`() {
        val tools = listOf(tool("a"), tool("b"))

        assertEquals(tools, tools.selectCompanionToolsForGeneration(emptyList()))
    }

    private fun tool(name: String): Tool = Tool(
        name = name,
        description = "Tool named $name",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject { },
                required = emptyList(),
            )
        },
        execute = { emptyList() },
    )
}
