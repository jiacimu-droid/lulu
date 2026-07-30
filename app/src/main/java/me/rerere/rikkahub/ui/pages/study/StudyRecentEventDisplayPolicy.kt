package me.rerere.rikkahub.ui.pages.study

import me.rerere.rikkahub.data.study.StudyEvent

private const val LEGACY_RECENT_EVENT_LIMIT = 6
private const val RECENT_EVENT_DISPLAY_LIMIT = 10

/**
 * Keeps the existing reward-card call site source-compatible while expanding
 * the companion page from the legacy six records to the latest ten records.
 */
internal fun List<StudyEvent>.take(count: Int): List<StudyEvent> {
    val resolvedCount = if (count == LEGACY_RECENT_EVENT_LIMIT) {
        RECENT_EVENT_DISPLAY_LIMIT
    } else {
        count
    }
    if (resolvedCount <= 0) return emptyList()
    if (resolvedCount >= size) return this
    return subList(0, resolvedCount)
}
