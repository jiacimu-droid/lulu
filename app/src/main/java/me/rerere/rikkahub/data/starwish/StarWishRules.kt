package me.rerere.rikkahub.data.starwish

object StarWishRules {
    fun defaultTheaterGuide(seed: StarWishTheaterSeed): StarWishTheaterGuide = StarWishTheaterGuide(
        overview = seed.prompt,
        chapters = List(6) { "" },
        wordCount = "1200-2200",
    )

    fun theaterChapterPrompt(
        seed: StarWishTheaterSeed,
        previousChapters: List<StarWishTheaterChapter>,
        chapter: Int,
        influence: String = "",
        guide: StarWishTheaterGuide = defaultTheaterGuide(seed),
    ): String {
        val normalized = guide.normalized()
        return buildString {
            appendLine("请续写《${seed.title}》第 $chapter 章，只输出正文。")
            appendLine("故事设定：${normalized.overview.ifBlank { seed.prompt }}")
            normalized.chapters.getOrNull(chapter - 1)?.takeIf(String::isNotBlank)?.let {
                appendLine("本章方向：$it")
            }
            appendLine("篇幅：${normalized.wordCount} 字。")
            appendLine("保持人物设定、事件因果和关系连续；推进情节，不重复已完成的场景。")
            previousChapters.lastOrNull()?.let { previous ->
                appendLine("上一章末尾：")
                appendLine(previous.content.takeLast(1800))
            }
            if (influence.isNotBlank()) appendLine("用户希望本章发生：$influence")
        }.trim()
    }
}
