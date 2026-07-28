package me.rerere.rikkahub.ui.pages.game

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Shared palette preserved from the original single-file game implementation. */
internal object GameColors {
    val background = Color(0xFFF8F4F0)
    val accent = Color(0xFF8B3D5E)
    val success = Color(0xFF2E8B68)
    val soft = Color(0xFF6F6A87)
    val heroBrush = Brush.linearGradient(
        listOf(
            Color(0xFF8B3D5E),
            Color(0xFFBD7E64),
            Color(0xFF4D314E),
        ),
    )
}
