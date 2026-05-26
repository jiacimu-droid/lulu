package me.rerere.rikkahub.plugin.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.PlusSign
import me.rerere.hugeicons.stroke.Reload
import me.rerere.rikkahub.plugin.model.PluginInfo
import org.koin.androidx.compose.koinViewModel

/**
 * 插件管理页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManagePage(
    onNavigateToDetail: (String) -> Unit,
    viewModel: PluginViewModel = koinViewModel()
) {
    val plugins by viewModel.plugins.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val operationState by viewModel.operationState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.importPlugin(it)
        }
    }

    // 监听导入状态
    LaunchedEffect(importState) {
        when (importState) {
            is PluginViewModel.ImportState.Success -> {
                snackbarHostState.showSnackbar(message = "插件导入成功")
                viewModel.resetImportState()
            }
            is PluginViewModel.ImportState.Error -> {
                snackbarHostState.showSnackbar(
                    message = "导入失败: ${(importState as PluginViewModel.ImportState.Error).message}"
                )
                viewModel.resetImportState()
            }
            else -> {}
        }
    }

    // 监听操作状态
    LaunchedEffect(operationState) {
        when (operationState) {
            is PluginViewModel.OperationState.Success -> {
                snackbarHostState.showSnackbar(
                    message = (operationState as PluginViewModel.OperationState.Success).message
                )
                viewModel.resetOperationState()
            }
            is PluginViewModel.OperationState.Error -> {
                snackbarHostState.showSnackbar(
                    message = (operationState as PluginViewModel.OperationState.Error).message
                )
                viewModel.resetOperationState()
            }
            else -> {}
        }
    }

    // 首次加载
    LaunchedEffect(Unit) {
        viewModel.refreshPlugins()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("插件管理") },
                actions = {
                    IconButton(onClick = { viewModel.refreshPlugins() }) {
                        Icon(
                            imageVector = HugeIcons.Reload,
                            contentDescription = "刷新"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { filePickerLauncher.launch("application/zip") },
                icon = { Icon(HugeIcons.PlusSign, null) },
                text = { Text("导入插件") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && plugins.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (plugins.isEmpty()) {
                EmptyPluginState(
                    onImportClick = { filePickerLauncher.launch("application/zip") }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        PluginDirectoryInfo(
                            directory = viewModel.getPluginsDirectory().absolutePath
                        )
                    }
                    items(items = plugins, key = { it.manifest.id }) { plugin ->
                        PluginCard(
                            plugin = plugin,
                            onClick = { onNavigateToDetail(plugin.manifest.id) },
                            onToggle = { enabled ->
                                viewModel.togglePlugin(plugin.manifest.id, enabled)
                            },
                            onDelete = { viewModel.deletePlugin(plugin.manifest.id) }
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = importState is PluginViewModel.ImportState.Loading,
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun EmptyPluginState(onImportClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "暂无插件", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击右下角按钮导入插件，或手动将插件文件夹放入插件目录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onImportClick) { Text("导入插件") }
    }
}

@Composable
private fun PluginDirectoryInfo(directory: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = "插件目录", style = MaterialTheme.typography.titleSmall)
            Text(text = directory, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginCard(
    plugin: PluginInfo,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = plugin.manifest.icon,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.manifest.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "v${plugin.manifest.version} · ${plugin.manifest.author}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (plugin.loadError != null) {
                    Text(
                        text = "加载失败: ${plugin.loadError}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Switch(
                checked = plugin.isEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    imageVector = HugeIcons.Delete02,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除插件 \"${plugin.manifest.name}\" 吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}