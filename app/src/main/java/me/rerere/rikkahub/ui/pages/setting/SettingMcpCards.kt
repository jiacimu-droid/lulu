package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AlertCircle
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.hugeicons.stroke.MessageBlocked
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.extendColors
import org.koin.compose.koinInject

@Composable
internal fun McDonaldsMcpShortcutCard(
    configs: List<McpServerConfig>,
    onSave: (String) -> Unit,
    onDelete: (McpServerConfig) -> Unit,
) {
    val config = configs.firstOrNull { it.isMcdonaldsMcp() }
    val savedToken = config?.commonOptions?.headers
        ?.firstOrNull { it.first.equals("Authorization", ignoreCase = true) }
        ?.second
        ?.removePrefix("Bearer")
        ?.trim()
        .orEmpty()
    var token by remember(savedToken) { mutableStateOf(savedToken) }
    val tokenReady = token.isNotBlank()
    val enabledTools = config?.commonOptions?.tools?.count { it.enable } ?: 0
    val totalTools = config?.commonOptions?.tools?.size ?: 0
    Card(colors = CardDefaults.cardColors(containerColor = CustomColors.listItemColors.containerColor)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(HugeIcons.McpServer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("麦当劳 MCP 快捷接入", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "普通 MCP 工具。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (config != null) {
                    IconButton(onClick = { onDelete(config) }) {
                        Icon(HugeIcons.Delete01, contentDescription = "删除麦当劳 MCP")
                    }
                }
            }
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("MCP Token") },
                placeholder = { Text("留空给你自己填；保存后自动写入 Authorization") },
                supportingText = {
                    Text("服务地址固定为 $MCDONALDS_MCP_URL，请求头为 Authorization: Bearer <token>。")
                },
                minLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        config == null -> "未安装"
                        !tokenReady -> "已安装，还差 token"
                        totalTools == 0 -> "已保存，等待同步工具"
                        else -> "已同步 $totalTools 个工具，启用 $enabledTools 个"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { onSave(token) }, enabled = token.isNotBlank()) {
                    Text("保存并接通")
                }
            }
        }
    }
}

@Composable
internal fun McpServerItem(
    item: McpServerConfig,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onEdit: (McpServerConfig) -> Unit,
) {
    val mcpManager = koinInject<McpManager>()
    val status by mcpManager.getStatus(item).collectAsStateWithLifecycle(McpStatus.Idle)
    val dismissBoxState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = dismissBoxState,
        backgroundContent = {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                FilledTonalIconButton(onClick = { scope.launch { dismissBoxState.reset() } }) {
                    Icon(HugeIcons.Cancel01, contentDescription = null)
                }
                FilledTonalIconButton(onClick = onDelete) {
                    Icon(HugeIcons.Delete01, contentDescription = null)
                }
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        modifier = modifier,
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = CustomColors.listItemColors.containerColor)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (status) {
                    McpStatus.Idle -> Icon(HugeIcons.MessageBlocked, contentDescription = null)
                    McpStatus.Connecting -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    McpStatus.Connected -> Icon(HugeIcons.McpServer, contentDescription = null)
                    is McpStatus.Reconnecting -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    is McpStatus.Error -> Icon(HugeIcons.AlertCircle, contentDescription = null)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(item.commonOptions.name, style = MaterialTheme.typography.titleLarge)
                        val dotColor = if (item.commonOptions.enable) {
                            MaterialTheme.extendColors.green6
                        } else {
                            MaterialTheme.extendColors.red6
                        }
                        Box(
                            modifier = Modifier.size(8.dp).drawWithContent { drawCircle(color = dotColor) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tag(type = TagType.SUCCESS) {
                            Text(
                                when (item) {
                                    is McpServerConfig.SseTransportServer -> "SSE"
                                    is McpServerConfig.StreamableHTTPServer -> "Streamable HTTP"
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(HugeIcons.Delete01, contentDescription = "删除 MCP")
                }
                IconButton(onClick = { onEdit(item) }) {
                    Icon(HugeIcons.Settings03, contentDescription = null)
                }
            }
        }
    }
}
