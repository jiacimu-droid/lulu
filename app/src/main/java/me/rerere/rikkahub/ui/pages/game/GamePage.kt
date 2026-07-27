package me.rerere.rikkahub.ui.pages.game

import androidx.compose.runtime.Composable

/**
 * Stable route entry points for the game module.
 *
 * The implementations live in focused feature files so route names and external
 * callers remain unchanged while each game is easier to inspect and modify.
 */
@Composable
fun GameHubPage() {
    GameHubFeaturePage()
}

@Composable
fun SignalHuntGamePage(recordId: String? = null) {
    SignalHuntFeaturePage(recordId)
}

@Composable
fun PerfectManGamePage() {
    PerfectManGameFeaturePage()
}
