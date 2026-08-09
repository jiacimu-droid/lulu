package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.study.StudyState
import me.rerere.rikkahub.data.study.StudyStore
import org.koin.core.context.GlobalContext
import java.time.LocalDate

fun createTodayStudyPlanTool(
    @Suppress("UNUSED_PARAMETER") assistantId: String? = null,
    @Suppress("UNUSED_PARAMETER") assistantName: String = "",
): Tool = Tool(
    name = "today_study_plan",
    description = "Read today's user-created learning tasks and Pomodoro statistics, or mark existing tasks complete/unfinished. This tool never creates plans or grants rewards.",
    parameters = {
        InputSchema.Obj(properties = buildJsonObject {
            putJsonObject("action") {
                put("type", JsonPrimitive("string"))
                put("enum", buildJsonArray {
                    add(JsonPrimitive("read"))
                    add(JsonPrimitive("set_completion"))
                })
            }
            putJsonObject("complete_task_ids") {
                put("type", JsonPrimitive("array"))
                putJsonObject("items") { put("type", JsonPrimitive("string")) }
            }
            putJsonObject("unfinished_task_ids") {
                put("type", JsonPrimitive("array"))
                putJsonObject("items") { put("type", JsonPrimitive("string")) }
            }
        })
    },
    execute = { args ->
        val store = GlobalContext.get().get<StudyStore>()
        val params = args.jsonObject
        if (params["action"]?.jsonPrimitive?.contentOrNull == "set_completion") {
            val completedIds = params.idSet("complete_task_ids")
            val unfinishedIds = params.idSet("unfinished_task_ids")
            store.update { current ->
                current.copy(tasks = current.tasks.map { task ->
                    when (task.id) {
                        in completedIds -> task.copy(done = true, completedAt = System.currentTimeMillis())
                        in unfinishedIds -> task.copy(done = false, completedAt = null)
                        else -> task
                    }
                })
            }
        }
        listOf(UIMessagePart.Text(buildTodayStudyPlanPayload(store.state.first()).toString()))
    },
)

fun buildTodayStudyPlanPayload(
    state: StudyState,
    today: LocalDate = state.today.takeIf(String::isNotBlank)?.let(LocalDate::parse) ?: LocalDate.now(),
) = buildJsonObject {
    val date = state.today.ifBlank { today.toString() }
    val record = state.dailyStudyRecords[date]
    put("success", true)
    put("source", "learning_app_local_store")
    put("date", date)
    put("pomodoros_today", record?.pomodoros ?: 0)
    put("study_minutes_today", record?.studyMinutes ?: 0)
    put("pomodoros_total", state.stats.totalPomodoros)
    put("study_minutes_total", state.stats.totalStudyMinutes)
    put("tasks", buildJsonArray {
        state.tasks.forEach { task ->
            add(buildJsonObject {
                put("id", task.id)
                put("title", task.title)
                put("done", task.done)
            })
        }
    })
}

private fun kotlinx.serialization.json.JsonObject.idSet(key: String): Set<String> = this[key]
    ?.jsonArray
    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
    ?.toSet()
    .orEmpty()
