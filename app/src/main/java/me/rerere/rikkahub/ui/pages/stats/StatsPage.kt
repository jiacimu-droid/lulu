package me.rerere.rikkahub.ui.pages.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.ApiUsageRecord
import me.rerere.rikkahub.data.ai.ApiUsageSummary
import me.rerere.rikkahub.data.ai.PerformanceMonitor
import me.rerere.rikkahub.data.ai.PerformanceTiming
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.pages.debug.TokenLoggingPage
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

private val MonitorTabHeight = 48.dp

@Composable
fun StatsPage(vm: StatsVM = koinViewModel()) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("性能监测") },
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = MonitorTabHeight),
            ) { page ->
                when (page) {
                    0 -> CacheMonitorContent(stats)
                    1 -> TokenLoggingPage()
                    else -> DurationMonitorContent()
                }
            }

            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MonitorTabHeight)
                    .align(Alignment.TopCenter),
            ) {
                listOf("缓存", "控制台", "时长监测").forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CacheMonitorContent(stats: AppStats) {
    if (stats.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val records = remember(stats.cacheRecords) {
        stats.cacheRecords.visibleCacheRecords()
    }
    val promptTokens = stats.cacheRecords
        .sumOf(ApiUsageRecord::promptTokens)
        .takeIf { stats.cacheRecords.isNotEmpty() }
        ?: stats.totalPromptTokens
    val cachedTokens = stats.cacheRecords
        .sumOf(ApiUsageRecord::cachedTokens)
        .takeIf { stats.cacheRecords.isNotEmpty() }
        ?: stats.totalCachedTokens
    val cacheRate = if (promptTokens > 0L) {
        cachedTokens.toFloat() / promptTokens.toFloat()
    } else {
        0f
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("缓存统计", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${(cacheRate * 100).formatPercent()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { cacheRate.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MetricLine("输入 Token", formatTokens(promptTokens))
                    MetricLine("缓存读取", formatTokens(cachedTokens))
                    MetricLine("缓存记录", stats.cacheRecords.size.toString())
                    if (stats.voiceCallStats.sessionCount > 0) {
                        MetricLine("电话会话", stats.voiceCallStats.sessionCount.toString())
                        MetricLine("电话记录", stats.voiceCallStats.visibleLineCount.toString())
                    }
                }
            }
        }

        if (stats.cacheSummaries.isNotEmpty()) {
            item {
                Text("调用来源", style = MaterialTheme.typography.titleMedium)
            }
            items(
                items = stats.cacheSummaries,
                key = { it.source.name },
            ) { summary ->
                CacheSourceSummaryCard(summary)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("缓存明细", style = MaterialTheme.typography.titleMedium)
                Text(
                    "最新 ${records.size} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (records.isEmpty()) {
            item {
                Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
                    Text(
                        "还没有带 Token 用量的记录。聊天或电话回复完成后会自动出现在这里。",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(
                items = records,
                key = { it.stableCacheRecordKey() },
            ) { record ->
                CacheRecordCard(record)
            }
        }
    }
}

@Composable
private fun CacheSourceSummaryCard(summary: ApiUsageSummary) {
    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(summary.source.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${(summary.cacheRate * 100).formatPercent()}%",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            MetricLine("调用次数", summary.callCount.toString())
            MetricLine("缓存 Token", formatTokens(summary.cachedTokens))
        }
    }
}

@Composable
private fun CacheRecordCard(record: ApiUsageRecord) {
    val cacheRate = if (record.promptTokens > 0L) {
        record.cachedTokens.toFloat() / record.promptTokens.toFloat() * 100
    } else {
        0f
    }

    Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.fillMaxWidth(0.72f)) {
                    Text(
                        record.title.ifBlank { record.source.label },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (record.model.isNotBlank()) {
                        Text(
                            record.model,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    record.createdAtMillis.asShortTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            MetricLine("输入", formatTokens(record.promptTokens))
            MetricLine("输出", formatTokens(record.completionTokens))
            MetricLine("缓存", formatTokens(record.cachedTokens))
            MetricLine("缓存率", "${cacheRate.formatPercent()}%")
        }
    }
}

@Composable
private fun DurationMonitorContent() {
    val timings by PerformanceMonitor.timings.collectAsStateWithLifecycle()
    val summaries = remember(timings) { buildTimingSummaries(timings) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.fillMaxWidth(0.76f)) {
                    Text("聊天全链路耗时", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "记录 Prompt、首 Token、模型、工具、Planner、Memory 与总耗时。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = PerformanceMonitor::clear) { Text("清空") }
            }
        }

        if (summaries.isEmpty()) {
            item {
                Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
                    Text(
                        "还没有耗时记录。发送一条消息后，这里会自动出现各阶段数据。",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(summaries, key = TimingSummary::stage) { summary ->
                Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(summary.stage, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${summary.latestMillis} ms",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        MetricLine("平均", "${summary.averageMillis} ms")
                        MetricLine("最大", "${summary.maxMillis} ms")
                        MetricLine("记录", "${summary.count} 次")
                        summary.latestDetail.takeIf(String::isNotBlank)?.let { detail ->
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

private data class TimingSummary(
    val stage: String,
    val latestMillis: Long,
    val averageMillis: Long,
    val maxMillis: Long,
    val count: Int,
    val latestDetail: String,
)

private fun buildTimingSummaries(timings: List<PerformanceTiming>): List<TimingSummary> =
    timings
        .groupBy(PerformanceTiming::stage)
        .map { (stage, records) ->
            TimingSummary(
                stage = stage,
                latestMillis = records.first().durationMillis,
                averageMillis = records.map(PerformanceTiming::durationMillis).average().toLong(),
                maxMillis = records.maxOf(PerformanceTiming::durationMillis),
                count = records.size,
                latestDetail = records.first().detail,
            )
        }
        .sortedBy { summary ->
            listOf(
                "总耗时",
                "Prompt 构建",
                "首 Token",
                "模型请求",
                "工具执行",
                "Planner",
                "Memory Extraction",
            ).indexOf(summary.stage).let { if (it < 0) Int.MAX_VALUE else it }
        }

private fun formatTokens(count: Long): String = when {
    count >= 1_000_000_000 -> "%.2fB".format(count / 1_000_000_000.0)
    count >= 1_000_000 -> "%.2fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

private fun Float.formatPercent(): String = "%.1f".format(this)

private fun Long.asShortTime(): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(this))

internal fun List<ApiUsageRecord>.visibleCacheRecords(): List<ApiUsageRecord> =
    take(MaxVisibleCacheRecords)

internal fun ApiUsageRecord.stableCacheRecordKey(): String = id

private const val MaxVisibleCacheRecords = 15
