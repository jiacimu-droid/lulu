package me.rerere.rikkahub.ui.pages.log

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.JsonTree
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.utils.JsonInstantPretty
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogPage() {
    var logs by remember { mutableStateOf(Logging.getRecentLogs()) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("报错日志") },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(
                        onClick = {
                            Logging.clear()
                            logs = Logging.getRecentLogs()
                        }
                    ) {
                        Icon(HugeIcons.Delete01, contentDescription = "清空日志")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        UnifiedLogList(
            logs = logs,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        )
    }
}

@Composable
private fun UnifiedLogList(logs: List<LogEntry>, modifier: Modifier = Modifier) {
    var selectedLog by remember { mutableStateOf<LogEntry.RequestLog?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val sortedLogs = remember(logs) { logs.sortedByDescending { it.timestamp } }

    if (sortedLogs.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "暂时没有日志",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(sortedLogs, key = { it.id }, contentType = { it.javaClass.simpleName }) { log ->
                when (log) {
                    is LogEntry.RequestLog -> RequestLogCard(
                        log = log,
                        onClick = {
                            selectedLog = log
                            scope.launch { sheetState.show() }
                        }
                    )

                    is LogEntry.TextLog -> TextLogCard(log = log)
                }
            }
        }
    }

    selectedLog?.let { log ->
        ModalBottomSheet(
            onDismissRequest = { selectedLog = null },
            sheetState = sheetState
        ) {
            RequestLogDetail(log)
        }
    }
}

@Composable
private fun RequestLogCard(log: LogEntry.RequestLog, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val isError = log.error != null || (log.responseCode != null && log.responseCode !in 200..299)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isError) "请求异常" else "请求记录",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = dateFormat.format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${log.method} ${log.url}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = JetbrainsMono,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                log.responseCode?.let { code ->
                    Text(
                        text = "状态码: $code",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (code in 200..299) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
                log.durationMs?.let { duration ->
                    Text(
                        text = "${duration}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            log.error?.let { error ->
                Text(
                    text = "错误: $error",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RequestLogDetail(log: LogEntry.RequestLog) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    SelectionContainer {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "请求详情",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("log", log.toCopyText(dateFormat)))
                                )
                            }
                        }
                    ) {
                        Icon(HugeIcons.Copy01, contentDescription = "复制日志")
                    }
                }
            }

            item { DetailSection("时间", dateFormat.format(Date(log.timestamp))) }
            item { DetailSection("地址", log.url) }
            item { DetailSection("方法", log.method) }

            log.responseCode?.let { code ->
                item { DetailSection("状态码", code.toString()) }
            }

            log.durationMs?.let { duration ->
                item { DetailSection("耗时", "${duration}ms") }
            }

            log.error?.let { error ->
                item { DetailSection("错误", error) }
            }

            if (log.requestHeaders.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Text(
                        text = "请求 Headers",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                log.requestHeaders.forEach { (key, value) ->
                    item { HeaderItem(key, value) }
                }
            }

            log.requestBody?.let { body ->
                item {
                    HorizontalDivider()
                    Text(
                        text = "请求 Body",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    val jsonElement = remember(body) {
                        runCatching { JsonInstantPretty.parseToJsonElement(body) }.getOrNull()
                    }
                    if (jsonElement != null) {
                        JsonTree(
                            json = jsonElement,
                            modifier = Modifier.padding(top = 4.dp),
                            initialExpandLevel = 2
                        )
                    } else {
                        Text(
                            text = body,
                            fontFamily = JetbrainsMono,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            if (log.responseHeaders.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Text(
                        text = "响应 Headers",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                log.responseHeaders.forEach { (key, value) ->
                    item { HeaderItem(key, value) }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = JetbrainsMono
        )
    }
}

@Composable
private fun HeaderItem(key: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetbrainsMono
        )
    }
}

@Composable
private fun TextLogCard(log: LogEntry.TextLog) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = log.tag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = dateFormat.format(Date(log.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(
                                            ClipData.newPlainText(
                                                "log",
                                                "[${dateFormat.format(Date(log.timestamp))}] ${log.tag}\n${log.message}"
                                            )
                                        )
                                    )
                                }
                            }
                        ) {
                            Icon(HugeIcons.Copy01, contentDescription = "复制日志")
                        }
                    }
                }
                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetbrainsMono
                )
            }
        }
    }
}

private fun LogEntry.RequestLog.toCopyText(dateFormat: SimpleDateFormat): String = buildString {
    appendLine("[${dateFormat.format(Date(timestamp))}] $method $url")
    responseCode?.let { appendLine("Status: $it") }
    durationMs?.let { appendLine("Duration: ${it}ms") }
    error?.let { appendLine("Error: $it") }
    if (requestHeaders.isNotEmpty()) {
        appendLine()
        appendLine("Request Headers:")
        requestHeaders.forEach { (key, value) -> appendLine("$key: $value") }
    }
    requestBody?.let {
        appendLine()
        appendLine("Request Body:")
        appendLine(it)
    }
    if (responseHeaders.isNotEmpty()) {
        appendLine()
        appendLine("Response Headers:")
        responseHeaders.forEach { (key, value) -> appendLine("$key: $value") }
    }
}
