package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.ai.core.InputSchema
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpTool
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.Switch
import me.rerere.rikkahub.ui.components.ui.SwitchSize
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.hooks.EditState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

@Composable
internal fun McpServerConfigModal(state: EditState<McpServerConfig>) {
    state.EditStateContent { config, updateValue ->
        val pagerState = rememberPagerState { 2 }
        val scope = rememberCoroutineScope()
        ModalBottomSheet(
            onDismissRequest = { state.dismiss() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text(stringResource(R.string.setting_mcp_page_basic_settings)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text(stringResource(R.string.setting_mcp_page_tools)) },
                    )
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { page ->
                    when (page) {
                        0 -> McpCommonOptionsConfigure(config = config, update = updateValue)
                        1 -> McpToolsConfigure(config = config, update = updateValue)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(
                        onClick = { if (config.commonOptions.name.isNotBlank()) state.confirm() },
                    ) {
                        Text(stringResource(R.string.setting_mcp_page_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun McpCommonOptionsConfigure(
    config: McpServerConfig,
    update: (McpServerConfig) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()).imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FormItem(
            label = { Text(stringResource(R.string.setting_mcp_page_enable)) },
            description = { Text(stringResource(R.string.setting_mcp_page_enable_desc)) },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.setting_mcp_page_enable))
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = config.commonOptions.enable,
                    onCheckedChange = { enabled ->
                        update(config.clone(commonOptions = config.commonOptions.copy(enable = enabled)))
                    },
                )
            }
        }
        HorizontalDivider()
        FormItem(
            label = { Text(stringResource(R.string.setting_mcp_page_name)) },
            description = { Text(stringResource(R.string.setting_mcp_page_name_desc)) },
        ) {
            OutlinedTextField(
                value = config.commonOptions.name,
                onValueChange = { name ->
                    update(config.clone(commonOptions = config.commonOptions.copy(name = name)))
                },
                label = { Text(stringResource(R.string.setting_mcp_page_name)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_mcp_page_name_placeholder)) },
            )
        }
        HorizontalDivider()
        FormItem(
            label = { Text(stringResource(R.string.setting_mcp_page_transport_type)) },
            description = { Text(stringResource(R.string.setting_mcp_page_transport_type_desc)) },
        ) {
            val transportTypes = listOf("Streamable HTTP", "SSE")
            val currentTypeIndex = when (config) {
                is McpServerConfig.StreamableHTTPServer -> 0
                is McpServerConfig.SseTransportServer -> 1
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                transportTypes.forEachIndexed { index, type ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index, transportTypes.size),
                        onClick = {
                            if (index != currentTypeIndex) {
                                update(
                                    when (index) {
                                        0 -> McpServerConfig.StreamableHTTPServer(
                                            id = config.id,
                                            commonOptions = config.commonOptions,
                                            url = config.urlValue(),
                                        )
                                        1 -> McpServerConfig.SseTransportServer(
                                            id = config.id,
                                            commonOptions = config.commonOptions,
                                            url = config.urlValue(),
                                        )
                                        else -> config
                                    },
                                )
                            }
                        },
                        selected = index == currentTypeIndex,
                    ) {
                        Text(type)
                    }
                }
            }
        }
        HorizontalDivider()
        FormItem(
            label = { Text(stringResource(R.string.setting_mcp_page_server_url)) },
            description = {
                Text(
                    when (config) {
                        is McpServerConfig.SseTransportServer -> stringResource(R.string.setting_mcp_page_sse_url_desc)
                        is McpServerConfig.StreamableHTTPServer -> stringResource(R.string.setting_mcp_page_streamable_http_url_desc)
                    },
                )
            },
        ) {
            OutlinedTextField(
                value = config.urlValue(),
                onValueChange = { url -> update(config.withUrl(url)) },
                label = { Text(stringResource(R.string.setting_mcp_page_url_label)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        when (config) {
                            is McpServerConfig.SseTransportServer -> stringResource(R.string.setting_mcp_page_sse_url_placeholder)
                            is McpServerConfig.StreamableHTTPServer -> stringResource(R.string.setting_mcp_page_streamable_http_url_placeholder)
                        },
                    )
                },
            )
        }
        HorizontalDivider()
        McpHeadersConfigure(config = config, update = update)
    }
}

@Composable
private fun McpHeadersConfigure(
    config: McpServerConfig,
    update: (McpServerConfig) -> Unit,
) {
    FormItem(
        label = { Text(stringResource(R.string.setting_mcp_page_custom_headers)) },
        description = { Text(stringResource(R.string.setting_mcp_page_custom_headers_desc)) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            config.commonOptions.headers.forEachIndexed { index, header ->
                var headerName by remember(header.first) { mutableStateOf(header.first) }
                var headerValue by remember(header.second) { mutableStateOf(header.second) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = headerName,
                            onValueChange = { value ->
                                headerName = value
                                val headers = config.commonOptions.headers.toMutableList().apply {
                                    this[index] = value.trim() to this[index].second
                                }
                                update(config.clone(commonOptions = config.commonOptions.copy(headers = headers)))
                            },
                            label = { Text(stringResource(R.string.setting_mcp_page_header_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.setting_mcp_page_header_name_placeholder)) },
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = headerValue,
                            onValueChange = { value ->
                                headerValue = value
                                val headers = config.commonOptions.headers.toMutableList().apply {
                                    this[index] = this[index].first to value.trim()
                                }
                                update(config.clone(commonOptions = config.commonOptions.copy(headers = headers)))
                            },
                            label = { Text(stringResource(R.string.setting_mcp_page_header_value)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.setting_mcp_page_header_value_placeholder)) },
                        )
                    }
                    IconButton(
                        onClick = {
                            val headers = config.commonOptions.headers.toMutableList().apply { removeAt(index) }
                            update(config.clone(commonOptions = config.commonOptions.copy(headers = headers)))
                        },
                    ) {
                        Icon(HugeIcons.Delete01, contentDescription = stringResource(R.string.setting_mcp_page_delete_header))
                    }
                }
            }
            androidx.compose.material3.Button(
                onClick = {
                    val headers = config.commonOptions.headers + ("" to "")
                    update(config.clone(commonOptions = config.commonOptions.copy(headers = headers)))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(HugeIcons.Add01, contentDescription = stringResource(R.string.setting_mcp_page_add_header))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.setting_mcp_page_add_header))
            }
        }
    }
}

@Composable
private fun McpToolsConfigure(
    config: McpServerConfig,
    update: (McpServerConfig) -> Unit,
) {
    val mcpManager = koinInject<McpManager>()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (mcpManager.getClient(config) == null) {
            item { Text(stringResource(R.string.setting_mcp_page_tools_unavailable_message)) }
        }
        items(config.commonOptions.tools) { tool ->
            McpToolCard(
                tool = tool,
                onEnableChange = { enabled ->
                    update(config.updateTool(tool.name) { it.copy(enable = enabled) })
                },
                onNeedsApprovalChange = { needsApproval ->
                    update(config.updateTool(tool.name) { it.copy(needsApproval = needsApproval) })
                },
            )
        }
    }
}

@Composable
private fun McpToolCard(
    tool: McpTool,
    onEnableChange: (Boolean) -> Unit,
    onNeedsApprovalChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = CustomColors.listItemColors.containerColor)) {
        Column(
            modifier = Modifier.animateContentSize().fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.setting_mcp_page_needs_approval), style = MaterialTheme.typography.labelSmall)
                    Switch(
                        checked = tool.needsApproval,
                        onCheckedChange = onNeedsApprovalChange,
                        size = SwitchSize.Small,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("启用", style = MaterialTheme.typography.labelSmall)
                    Switch(checked = tool.enable, onCheckedChange = onEnableChange, size = SwitchSize.Small)
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (expanded) {
                if (!tool.description.isNullOrBlank()) {
                    Text(
                        text = tool.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
                (tool.inputSchema as? InputSchema.Obj)?.let { schema ->
                    if (schema.properties.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            schema.properties.forEach { (key, _) ->
                                Tag(
                                    type = if (schema.required?.contains(key) == true) TagType.INFO else TagType.DEFAULT,
                                ) {
                                    Text(key, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun McpServerConfig.urlValue(): String = when (this) {
    is McpServerConfig.SseTransportServer -> url
    is McpServerConfig.StreamableHTTPServer -> url
}

private fun McpServerConfig.withUrl(url: String): McpServerConfig = when (this) {
    is McpServerConfig.SseTransportServer -> copy(url = url)
    is McpServerConfig.StreamableHTTPServer -> copy(url = url)
}

private fun McpServerConfig.updateTool(
    toolName: String,
    transform: (McpTool) -> McpTool,
): McpServerConfig = clone(
    commonOptions = commonOptions.copy(
        tools = commonOptions.tools.map { if (it.name == toolName) transform(it) else it },
    ),
)
