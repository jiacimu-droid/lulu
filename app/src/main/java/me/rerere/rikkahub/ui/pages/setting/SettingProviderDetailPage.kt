package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.Share01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.ShareSheet
import me.rerere.rikkahub.ui.components.ui.rememberShareSheetState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

/** Stable route shell for provider configuration and model management. */
@Composable
fun SettingProviderDetailPage(id: Uuid, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val provider = settings.providers.find { it.id == id } ?: return
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val context = LocalContext.current

    val onEdit = { newProvider: ProviderSetting ->
        vm.updateSettings(
            settings.copy(
                providers = settings.providers.map {
                    if (newProvider.id == it.id) newProvider else it
                },
            ),
        )
    }
    val onDelete = {
        vm.updateSettings(settings.copy(providers = settings.providers - provider))
        navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton() },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AutoAIIcon(provider.name, modifier = Modifier.size(22.dp))
                        Text(provider.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    val shareSheetState = rememberShareSheetState()
                    ShareSheet(shareSheetState)
                    IconButton(onClick = { shareSheetState.show(provider) }) {
                        Icon(HugeIcons.Share01, null)
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = pager.currentPage == 0,
                    label = { Text(stringResource(R.string.setting_provider_page_configuration)) },
                    icon = { Icon(HugeIcons.Tools, null) },
                    onClick = { scope.launch { pager.animateScrollToPage(0) } },
                )
                NavigationBarItem(
                    selected = pager.currentPage == 1,
                    label = { Text(stringResource(R.string.setting_provider_page_models)) },
                    icon = { Icon(HugeIcons.Package01, null) },
                    onClick = { scope.launch { pager.animateScrollToPage(1) } },
                )
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) { page ->
            when (page) {
                0 -> SettingProviderConfigContent(
                    provider = provider,
                    onEdit = {
                        onEdit(it)
                        toaster.show(
                            context.getString(R.string.setting_provider_page_save_success),
                            type = ToastType.Success,
                        )
                    },
                    onDelete = onDelete,
                )
                1 -> SettingProviderModelList(providerSetting = provider, onUpdateProvider = onEdit)
            }
        }
    }
}
