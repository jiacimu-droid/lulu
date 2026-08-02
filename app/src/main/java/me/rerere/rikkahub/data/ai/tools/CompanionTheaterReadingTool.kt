package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.starwish.StarWishStore
import me.rerere.rikkahub.data.starwish.StarWishTheaterReadingNote
import me.rerere.rikkahub.data.starwish.companionTheaterProgressKey
import org.koin.core.context.GlobalContext

fun createCompanionTheaterReadingTool(
    assistantId: String,
    assistantName: String,
    clockMillis: () -> Long = System::currentTimeMillis,
): Tool = Tool(
    name = "read_starwish_theater",
    description = "Read exactly one real generated StarWish theater chapter as the configured character. The tool persists per-character reading progress and a grounded reading note. Use for private background reading or shared-reading preparation; never claim a chapter was read unless this tool succeeds.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("theater_title") {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Optional preferred theater title. Leave blank to continue the character's unread progress."))
                }
                putJsonObject("reading_mood") {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("A short persona-consistent reason for choosing to read now."))
                }
            },
        )
    },
    execute = { input ->
        val store = GlobalContext.get().get<StarWishStore>()
        val snapshot = store.state.value
        val requestedTitle = input.jsonObject["theater_title"]?.jsonPrimitive?.contentOrNull
            ?.trim().orEmpty()
        val mood = input.jsonObject["reading_mood"]?.jsonPrimitive?.contentOrNull
            ?.trim().orEmpty()
        val available = snapshot.theaterChapters
            .mapValues { (_, chapters) -> chapters.sortedBy { it.chapter } }
            .filterValues { it.isNotEmpty() }
        val selectedTitle = when {
            requestedTitle.isNotBlank() && requestedTitle in available -> requestedTitle
            else -> available.keys.firstOrNull { title ->
                val progress = snapshot.companionTheaterProgress[companionTheaterProgressKey(assistantId, title)] ?: 0
                progress < available.getValue(title).size
            } ?: available.keys.firstOrNull()
        }
        if (selectedTitle == null) {
            return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                put("success", false)
                put("error", "没有可阅读的小剧场章节")
            }.toString()))
        }
        val chapters = available.getValue(selectedTitle)
        val key = companionTheaterProgressKey(assistantId, selectedTitle)
        val oldProgress = snapshot.companionTheaterProgress[key] ?: 0
        val index = oldProgress.coerceIn(0, chapters.lastIndex)
        val chapter = chapters[index]
        val now = clockMillis()
        val ending = chapter.content.takeLast(420).trim()
        val reactionSeed = buildString {
            if (mood.isNotBlank()) append("因为$mood，我读了这一章。")
            append("我最在意的是结尾留下的情绪和没有说透的东西。")
            if (ending.isNotBlank()) append(" 章节末尾是：${ending.take(260)}")
        }
        val note = StarWishTheaterReadingNote(
            id = "reading-$now-${chapter.id.hashCode()}",
            assistantId = assistantId,
            assistantName = assistantName.ifBlank { "当前角色" },
            theaterTitle = selectedTitle,
            chapterId = chapter.id,
            chapterNumber = chapter.chapter,
            chapterTitle = chapter.title,
            reaction = reactionSeed,
            questionForUser = "你读到这里时，最在意的是哪一个细节？",
            createdAt = now,
        )
        store.update { current ->
            current.copy(
                companionTheaterProgress = current.companionTheaterProgress + (key to (index + 1)),
                companionTheaterNotes = (listOf(note) + current.companionTheaterNotes)
                    .distinctBy { it.id }
                    .take(200),
            )
        }
        listOf(UIMessagePart.Text(buildJsonObject {
            put("success", true)
            put("theater_title", selectedTitle)
            put("chapter_id", chapter.id)
            put("chapter_number", chapter.chapter)
            put("chapter_title", chapter.title)
            put("character_name", assistantName)
            put("reading_note_id", note.id)
            put("reaction_seed", reactionSeed)
            put("question_for_user", note.questionForUser)
            put("chapter_excerpt", chapter.content.take(1_500))
            put("next_unread_chapter", index + 2)
        }.toString()))
    },
)
