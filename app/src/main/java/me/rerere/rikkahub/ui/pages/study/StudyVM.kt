package me.rerere.rikkahub.ui.pages.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.study.StudyDailyRecord
import me.rerere.rikkahub.data.study.StudyState
import me.rerere.rikkahub.data.study.StudyStore
import me.rerere.rikkahub.data.study.StudyTask
import java.time.LocalDate
import java.util.UUID

/** The learning app intentionally contains only user-created tasks and Pomodoro records. */
class StudyVM(
    private val store: StudyStore,
) : ViewModel() {
    val state: StateFlow<StudyState> = store.state

    fun selectCompanion(assistantId: String) = reduce {
        it.copy(selectedAssistantId = assistantId)
    }

    fun addTask(title: String) = reduce { current ->
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) current else current.copy(
            tasks = current.tasks + StudyTask(
                id = UUID.randomUUID().toString(),
                title = cleanTitle,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    fun deleteTask(id: String) = reduce { current ->
        current.copy(tasks = current.tasks.filterNot { it.id == id })
    }

    fun toggleTask(id: String, done: Boolean) = reduce { current ->
        current.copy(
            tasks = current.tasks.map { task ->
                if (task.id == id) {
                    task.copy(done = done, completedAt = if (done) System.currentTimeMillis() else null)
                } else {
                    task
                }
            },
        )
    }

    fun completePomodoro(minutes: Int) = reduce { current ->
        val studiedMinutes = minutes.coerceAtLeast(0)
        val date = LocalDate.now().toString()
        val record = current.dailyStudyRecords[date] ?: StudyDailyRecord()
        current.copy(
            lastStudyDate = date,
            stats = current.stats.copy(
                totalPomodoros = current.stats.totalPomodoros + 1,
                totalStudyMinutes = current.stats.totalStudyMinutes + studiedMinutes,
            ),
            dailyStudyRecords = current.dailyStudyRecords + (
                date to record.copy(
                    pomodoros = record.pomodoros + 1,
                    studyMinutes = record.studyMinutes + studiedMinutes,
                )
            ),
        )
    }

    private fun reduce(transform: (StudyState) -> StudyState) {
        viewModelScope.launch { store.update(transform) }
    }
}
