package me.rerere.rikkahub.ui.pages.starwish

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import me.rerere.rikkahub.data.study.StudyRules

internal fun outfitProgress(outfit: String, fragments: Map<String, Int>): Float {
    val prefix = "normal:$outfit:"
    val count = fragments.entries.sumOf { (key, value) ->
        if (key.startsWith(prefix)) value else 0
    }.coerceAtMost(StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT)
    return count / StudyRules.NORMAL_FRAGMENTS_PER_OUTFIT.toFloat()
}

internal object StarWishColors {
    val paper = Color(0xFFF4F7F8)
    val mistBlue = Color(0xFFDDECF2)
    val inkBlue = Color(0xFF2E596B)
    val locked = Color(0xFFE7EAEC)
}

internal fun StarWishHeroBrush(): Brush = Brush.linearGradient(
    listOf(Color(0xFFEAF3F6), Color(0xFFF8FAF7), Color(0xFFE9E5F1))
)
