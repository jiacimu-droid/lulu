package me.rerere.rikkahub.data.ai.transformers

import java.time.LocalDate
import me.rerere.rikkahub.data.study.StudyScheduleBlock
import me.rerere.rikkahub.data.study.StudyState
import me.rerere.rikkahub.data.study.StudyTask
import me.rerere.rikkahub.data.study.StudyTaskSource
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyStateTransformerTest {
    @Test
    fun `study context includes completed tasks undone tasks and today schedule`() {
        val state = StudyState(
            today = "2026-07-03",
            tasks = listOf(
                StudyTask(
                    id = "done",
                    title = "学习｜法理学第 2 章：回看错题",
                    done = true,
                    source = StudyTaskSource.Plan,
                ),
                StudyTask(
                    id = "open",
                    title = "英语｜不背单词 120 个（6 组）",
                    done = false,
                    source = StudyTaskSource.Plan,
                ),
            ),
            generatedSchedules = mapOf(
                "2026-07-03" to listOf(
                    StudyScheduleBlock("14:20-15:00", "法理入口", "回看错题和目录框架。"),
                ),
            ),
        )

        val context = buildStudyCompanionContext(state, LocalDate.of(2026, 7, 3))

        assertTrue(context.contains("已完成/已划掉待办"))
        assertTrue(context.contains("学习｜法理学第 2 章：回看错题"))
        assertTrue(context.contains("未完成待办"))
        assertTrue(context.contains("英语｜不背单词 120 个（6 组）"))
        assertTrue(context.contains("今日时间表"))
        assertTrue(context.contains("14:20-15:00｜法理入口｜回看错题和目录框架。"))
        assertTrue(context.contains("不要再把已完成/已划掉的任务当作未完成任务提醒"))
    }
}
