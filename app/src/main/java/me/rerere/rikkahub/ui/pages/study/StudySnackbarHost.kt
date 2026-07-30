package me.rerere.rikkahub.ui.pages.study

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * The study page already exposes reward results in its draw dialogs and recent
 * reward history. Dismiss queued snackbars immediately so repeated draws or
 * ticket redemptions never leave the page waiting for a stack of black bars.
 */
@Composable
internal fun SnackbarHost(hostState: SnackbarHostState) {
    val current = hostState.currentSnackbarData
    LaunchedEffect(current) {
        current?.dismiss()
    }
}
