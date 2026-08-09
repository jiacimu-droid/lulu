package me.rerere.rikkahub.data.study

import kotlinx.serialization.Serializable

@Serializable
data class StudyState(
    val today: String = "",
    val tasks: List<StudyTask> = emptyList(),
    val stats: StudyStats = StudyStats(),
    val dailyStudyRecords: Map<String, StudyDailyRecord> = emptyMap(),
    val lastStudyDate: String? = null,
    val selectedAssistantId: String? = null,
)

@Serializable
data class StudyTask(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val createdAt: Long = 0L,
    val completedAt: Long? = null,
    val source: StudyTaskSource = StudyTaskSource.Manual,
)

@Serializable
enum class StudyTaskSource { Manual }

@Serializable
data class StudyStats(
    val totalPomodoros: Int = 0,
    val totalTasksCompleted: Int = 0,
    val totalStudyMinutes: Int = 0,
)

@Serializable
data class StudyDailyRecord(
    val pomodoros: Int = 0,
    val studyMinutes: Int = 0,
)
