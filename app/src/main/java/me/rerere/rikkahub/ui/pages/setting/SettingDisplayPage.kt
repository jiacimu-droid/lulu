package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

/**
 * Stable display-settings route. Each visual group lives in a focused file while
 * this page keeps the original ordering and persistence behavior.
 */
@Composable
fun SettingDisplayPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var amoledDarkMode by rememberAmoledDarkMode()

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_display_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DisplayThemeSection(
                    dynamicColor = settings.dynamicColor,
                    themeId = settings.themeId,
                    amoledDarkMode = amoledDarkMode,
                    onDynamicColorChange = { vm.updateSettings(settings.copy(dynamicColor = it)) },
                    onThemeChange = { vm.updateSettings(settings.copy(themeId = it)) },
                    onAmoledDarkModeChange = { amoledDarkMode = it },
                )
            }
            item { DisplayGeneralSection(displaySetting, ::updateDisplaySetting) }
            item { DisplayProfileSection(displaySetting, ::updateDisplaySetting) }
            item { DisplayMessageSection(displaySetting, ::updateDisplaySetting) }
            item { DisplayColorSection(displaySetting, ::updateDisplaySetting) }
            item { DisplayMediaAssetsSection(displaySetting, ::updateDisplaySetting) }
            item { DisplayAvatarFramesSection(displaySetting, ::updateDisplaySetting) }
            item { DisplayCodeSection(displaySetting, ::updateDisplaySetting) }
            item { DisplayInteractionSection(displaySetting, ::updateDisplaySetting) }
            item { DisplayTtsSection(displaySetting, ::updateDisplaySetting) }
        }
    }
}
