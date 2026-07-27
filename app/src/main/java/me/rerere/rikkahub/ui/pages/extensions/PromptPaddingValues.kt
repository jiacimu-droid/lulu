package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.LayoutDirection

internal operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    start = calculateStartPadding(LayoutDirection.Ltr) + other.calculateStartPadding(LayoutDirection.Ltr),
    top = calculateTopPadding() + other.calculateTopPadding(),
    end = calculateEndPadding(LayoutDirection.Ltr) + other.calculateEndPadding(LayoutDirection.Ltr),
    bottom = calculateBottomPadding() + other.calculateBottomPadding(),
)
