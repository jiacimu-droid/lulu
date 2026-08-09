package me.rerere.rikkahub.data.ai.transformers

import kotlinx.coroutines.flow.first
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.study.StudyState
import me.rerere.rikkahub.data.study.StudyStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate

object StudyStateTransformer : InputMessageTransformer, KoinComponent {
    private val studyStore: StudyStore by inject()

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val state = studyStore.state.first()
        if (state.selectedAssistantId != ctx.assistant.id.toString()) return messages
        if (state.tasks.isEmpty() && state.stats.totalStudyMinutes <= 0) return messages
        return messages + UIMessage.system(buildStudyCompanionContext(state))
    }
}

internal fun buildStudyCompanionContext(state: StudyState): String {
    val date = state.today.ifBlank { LocalDate.now().toString() }
    val record = state.dailyStudyRecords[date]
    return buildString {
        appendLine("<learning_state>")
        appendLine("日期：$date")
        appendLine("今日番茄钟：${record?.pomodoros ?: 0} 个，${record?.studyMinutes ?: 0} 分钟")
        appendLine("累计学习：${state.stats.totalPomodoros} 个番茄钟，${state.stats.totalStudyMinutes} 分钟")
        val completed = state.tasks.filter { it.done }
        val pending = state.tasks.filterNot { it.done }
        if (completed.isNotEmpty()) {
            appendLine("已完成待办：")
            completed.take(12).forEach { appendLine("- ${it.title}") }
        }
        if (pending.isNotEmpty()) {
            appendLine("未完成待办：")
            pending.take(12).forEach { appendLine("- ${it.title}") }
        }
        append("</learning_state>")
    }
}
