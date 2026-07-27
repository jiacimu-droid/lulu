package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Stable MCP settings route; cards, editing and import now live in focused files. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingMcpPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val editState = useEditState<McpServerConfig> { config ->
        val exists = settings.mcpServers.any { it.id == config.id }
        vm.updateSettings(
            settings.copy(
                mcpServers = if (exists) {
                    settings.mcpServers.map { if (it.id == config.id) config else it }
                } else {
                    settings.mcpServers + config
                },
            ),
        )
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var expanded by rememberSaveable { mutableStateOf(true) }
    var showImportModal by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val list = settings.mcpServers.toMutableList()
        val item = list.removeAt(from.index - 1)
        list.add(to.index - 1, item)
        vm.updateSettings(settings.copy(mcpServers = list))
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_mcp_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .floatingToolbarVerticalNestedScroll(
                        expanded = expanded,
                        onExpand = { expanded = true },
                        onCollapse = { expanded = false },
                    ),
                contentPadding = PaddingValues(16.dp) + PaddingValues(bottom = 128.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = lazyListState,
            ) {
                item(key = "mcdonalds-shortcut") {
                    McDonaldsMcpShortcutCard(
                        configs = settings.mcpServers,
                        onSave = { token ->
                            vm.updateSettings(
                                settings.copy(
                                    mcpServers = upsertMcdonaldsMcpServer(settings.mcpServers, token),
                                ),
                            )
                        },
                        onDelete = { config ->
                            vm.updateSettings(settings.copy(mcpServers = settings.mcpServers - config))
                        },
                    )
                }
                if (settings.mcpServers.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillParentMaxHeight(0.75f).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.setting_mcp_page_no_servers),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(settings.mcpServers, key = { it.id }) { item ->
                        ReorderableItem(state = reorderableState, key = item.id) { isDragging ->
                            McpServerItem(
                                item = item,
                                modifier = Modifier
                                    .longPressDraggableHandle()
                                    .graphicsLayer {
                                        if (isDragging) {
                                            scaleX = 1.05f
                                            scaleY = 1.05f
                                        }
                                    },
                                onDelete = {
                                    vm.updateSettings(settings.copy(mcpServers = settings.mcpServers - item))
                                },
                                onEdit = editState::open,
                            )
                        }
                    }
                }
            }
            HorizontalFloatingToolbar(
                expanded = expanded,
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = -ScreenOffset),
                leadingContent = {
                    IconButton(onClick = { showImportModal = true }) {
                        Icon(HugeIcons.FileImport, contentDescription = stringResource(R.string.setting_mcp_page_import))
                    }
                },
            ) {
                Button(onClick = { editState.open(McpServerConfig.StreamableHTTPServer()) }) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(HugeIcons.Add01, contentDescription = null)
                        AnimatedVisibility(expanded) {
                            Row {
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.setting_mcp_page_add_server))
                            }
                        }
                    }
                }
            }
        }
    }

    McpServerConfigModal(editState)
    if (showImportModal) {
        McpImportModal(
            onDismiss = { showImportModal = false },
            onImport = { imported ->
                vm.updateSettings(settings.copy(mcpServers = settings.mcpServers + imported))
                showImportModal = false
            },
        )
    }
}
