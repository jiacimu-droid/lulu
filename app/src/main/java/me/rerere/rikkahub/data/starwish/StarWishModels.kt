package me.rerere.rikkahub.data.starwish

import kotlinx.serialization.Serializable

@Serializable
data class StarWishState(
    val theaterChapters: Map<String, List<StarWishTheaterChapter>> = emptyMap(),
    val theaterGuides: Map<String, StarWishTheaterGuide> = emptyMap(),
    val customTheaters: List<StarWishTheaterSeed> = emptyList(),
    val companionTheaterProgress: Map<String, Int> = emptyMap(),
    val companionTheaterNotes: List<StarWishTheaterReadingNote> = emptyList(),
)

@Serializable
data class StarWishTheaterChapter(
    val id: String,
    val theater: String,
    val chapter: Int,
    val title: String,
    val content: String,
    val userInfluence: String = "",
    val createdAt: Long,
)

@Serializable
data class StarWishTheaterSeed(
    val id: String,
    val title: String,
    val prompt: String,
    val createdAt: Long = 0L,
)

@Serializable
data class StarWishTheaterGuide(
    val worldview: String = "",
    val overview: String = "",
    val chapters: List<String> = List(6) { "" },
    val wordCount: String = "1200-2200",
) {
    fun normalized(): StarWishTheaterGuide = copy(
        worldview = worldview.trim(),
        overview = overview.trim(),
        chapters = chapters.ifEmpty { List(6) { "" } }.map(String::trim),
        wordCount = wordCount.trim().ifBlank { "1200-2200" },
    )
}

@Serializable
data class StarWishTheaterReadingNote(
    val id: String,
    val assistantId: String,
    val assistantName: String,
    val theaterTitle: String,
    val chapterId: String,
    val chapterNumber: Int,
    val chapterTitle: String,
    val reaction: String,
    val questionForUser: String = "",
    val createdAt: Long,
    val sharedWithUser: Boolean = false,
)

fun companionTheaterProgressKey(assistantId: String, theaterTitle: String): String =
    "$assistantId::$theaterTitle"
