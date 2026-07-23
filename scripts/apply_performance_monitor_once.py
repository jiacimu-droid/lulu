from pathlib import Path
import re

root = Path('.')


def read(path: str) -> str:
    return (root / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    target = root / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding='utf-8')


# Desktop entry.
desktop_path = 'app/src/main/java/me/rerere/rikkahub/ui/pages/desktop/DesktopPage.kt'
desktop = read(desktop_path)
old_desktop = 'DesktopApp("缓存统计", HugeIcons.Chart, "stats") { navController.navigate(Screen.Stats) }'
new_desktop = 'DesktopApp("性能监测", HugeIcons.Chart, "performance") { navController.navigate(Screen.Stats) }'
if old_desktop not in desktop:
    raise SystemExit('Desktop cache entry not found')
write(desktop_path, desktop.replace(old_desktop, new_desktop, 1))

# Expose the existing token console.
debug_path = 'app/src/main/java/me/rerere/rikkahub/ui/pages/debug/DebugPage.kt'
debug = read(debug_path)
old_debug = 'private fun TokenLoggingPage(vm: DebugVM) {'
new_debug = 'fun TokenLoggingPage(vm: DebugVM = koinViewModel()) {'
if old_debug not in debug:
    raise SystemExit('TokenLoggingPage declaration not found')
write(debug_path, debug.replace(old_debug, new_debug, 1))

# Shared timing recorder.
monitor_path = 'app/src/main/java/me/rerere/rikkahub/data/ai/PerformanceMonitor.kt'
monitor = '''package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class PerformanceTiming(
    val id: String = UUID.randomUUID().toString(),
    val stage: String,
    val durationMillis: Long,
    val detail: String = "",
    val recordedAtMillis: Long = System.currentTimeMillis(),
)

object PerformanceMonitor {
    private const val MAX_RECORDS = 500

    private val _timings = MutableStateFlow<List<PerformanceTiming>>(emptyList())
    val timings: StateFlow<List<PerformanceTiming>> = _timings.asStateFlow()

    fun record(stage: String, durationMillis: Long, detail: String = "") {
        if (durationMillis < 0L) return
        val timing = PerformanceTiming(
            stage = stage,
            durationMillis = durationMillis,
            detail = detail,
        )
        _timings.update { current -> (listOf(timing) + current).take(MAX_RECORDS) }
    }

    fun recordNanos(stage: String, startedAtNanos: Long, detail: String = "") {
        record(stage, ((System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L), detail)
    }

    fun clear() {
        _timings.value = emptyList()
    }
}
'''
write(monitor_path, monitor)

# Performance monitor UI reuses cache cards and the existing token console.
stats_path = 'app/src/main/java/me/rerere/rikkahub/ui/pages/stats/StatsPage.kt'
stats = read(stats_path)
if 'import androidx.compose.foundation.pager.HorizontalPager' not in stats:
    stats = stats.replace(
        'import androidx.compose.foundation.rememberScrollState\n',
        'import androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.pager.HorizontalPager\nimport androidx.compose.foundation.pager.rememberPagerState\n',
        1,
    )
if 'import androidx.compose.material3.PrimaryTabRow' not in stats:
    stats = stats.replace(
        'import androidx.compose.material3.MaterialTheme\n',
        'import androidx.compose.material3.MaterialTheme\nimport androidx.compose.material3.PrimaryTabRow\nimport androidx.compose.material3.Tab\nimport androidx.compose.material3.TextButton\nimport androidx.compose.material3.TopAppBar\n',
        1,
    )
if 'import androidx.compose.runtime.rememberCoroutineScope' not in stats:
    stats = stats.replace(
        'import androidx.compose.runtime.remember\n',
        'import androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberCoroutineScope\n',
        1,
    )
if 'import me.rerere.rikkahub.data.ai.PerformanceMonitor' not in stats:
    stats = stats.replace(
        'import androidx.lifecycle.compose.collectAsStateWithLifecycle\n',
        'import androidx.lifecycle.compose.collectAsStateWithLifecycle\nimport kotlinx.coroutines.launch\nimport me.rerere.rikkahub.data.ai.PerformanceMonitor\nimport me.rerere.rikkahub.data.ai.PerformanceTiming\nimport me.rerere.rikkahub.ui.pages.debug.TokenLoggingPage\n',
        1,
    )

replacement = '''@Composable
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                listOf("缓存", "控制台", "时长监测").forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                when (page) {
                    0 -> CacheMonitorContent(stats)
                    1 -> TokenLoggingPage()
                    else -> DurationMonitorContent()
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
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { CacheRecordsCard(records = stats.cacheRecords) }
            item { CacheStatsCard(stats = stats) }
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
                Column {
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
        }
        items(summaries, key = { it.stage }) { summary ->
            Card(colors = CustomColors.cardColorsOnSurfaceContainer) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(summary.stage, style = MaterialTheme.typography.titleMedium)
                        Text("${summary.latestMillis} ms", color = MaterialTheme.colorScheme.primary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("平均 ${summary.averageMillis} ms", style = MaterialTheme.typography.bodySmall)
                        Text("最大 ${summary.maxMillis} ms", style = MaterialTheme.typography.bodySmall)
                        Text("${summary.count} 次", style = MaterialTheme.typography.bodySmall)
                    }
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
        .groupBy { it.stage }
        .map { (stage, records) ->
            TimingSummary(
                stage = stage,
                latestMillis = records.first().durationMillis,
                averageMillis = records.map { it.durationMillis }.average().toLong(),
                maxMillis = records.maxOf { it.durationMillis },
                count = records.size,
                latestDetail = records.first().detail,
            )
        }
        .sortedBy { summary ->
            listOf("总耗时", "Prompt 构建", "首 Token", "模型请求", "工具执行", "Planner", "Memory Extraction")
                .indexOf(summary.stage)
                .let { if (it < 0) Int.MAX_VALUE else it }
        }

@Composable
private fun CacheStatsCard'''
pattern = re.compile(
    r'@Composable\nfun StatsPage\(vm: StatsVM = koinViewModel\(\)\) \{.*?\n\}\n\n@Composable\nprivate fun CacheStatsCard',
    re.S,
)
stats, count = pattern.subn(replacement, stats, count=1)
if count != 1:
    raise SystemExit(f'Failed to replace StatsPage block: {count}')
write(stats_path, stats)

# Generation timings.
generation_path = 'app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt'
generation = read(generation_path)
generation = generation.replace(
    '    ): Flow<GenerationChunk> = flow {\n        val provider = model.findProvider(settings.providers) ?: error("Provider not found")',
    '    ): Flow<GenerationChunk> = flow {\n        val pipelineStartedAt = System.nanoTime()\n        val provider = model.findProvider(settings.providers) ?: error("Provider not found")',
    1,
)
generation = generation.replace(
    '    ) {\n        val effectiveSystemPrompt =',
    '    ) {\n        val promptBuildStartedAt = System.nanoTime()\n        val effectiveSystemPrompt =',
    1,
)
prompt_anchor = '        val breakdown = buildGenerationTokenBreakdown(\n'
if prompt_anchor not in generation:
    raise SystemExit('Generation prompt anchor not found')
generation = generation.replace(
    prompt_anchor,
    '        PerformanceMonitor.recordNanos("Prompt 构建", promptBuildStartedAt, apiUsageTitle.ifBlank { assistant.name })\n' + prompt_anchor,
    1,
)
stream_anchor = '''                providerImpl.streamText(
                    providerSetting = provider,
                    messages = internalMessages,
                    params = params
                ).collect {
                    messages = messages.handleMessageChunk(chunk = it, model = model)
'''
stream_replacement = '''                val requestStartedAt = System.nanoTime()
                var firstChunkPending = true
                providerImpl.streamText(
                    providerSetting = provider,
                    messages = internalMessages,
                    params = params
                ).collect {
                    if (firstChunkPending) {
                        PerformanceMonitor.recordNanos("首 Token", requestStartedAt, apiUsageTitle.ifBlank { assistant.name })
                        firstChunkPending = false
                    }
                    messages = messages.handleMessageChunk(chunk = it, model = model)
'''
if stream_anchor not in generation:
    raise SystemExit('Streaming request anchor not found')
generation = generation.replace(stream_anchor, stream_replacement, 1)
generation = generation.replace(
    '                aiLoggingManager.finishGeneration(apiLog.id)\n                recordedUsage?.let { usage ->',
    '                PerformanceMonitor.recordNanos("模型请求", requestStartedAt, apiUsageTitle.ifBlank { assistant.name })\n                aiLoggingManager.finishGeneration(apiLog.id)\n                recordedUsage?.let { usage ->',
    1,
)
nonstream_anchor = '''                val chunk = providerImpl.generateText(
                    providerSetting = provider,
                    messages = internalMessages,
                    params = params,
                )
                messages = messages.handleMessageChunk(chunk = chunk, model = model)
'''
nonstream_replacement = '''                val requestStartedAt = System.nanoTime()
                val chunk = providerImpl.generateText(
                    providerSetting = provider,
                    messages = internalMessages,
                    params = params,
                )
                PerformanceMonitor.recordNanos("首 Token", requestStartedAt, apiUsageTitle.ifBlank { assistant.name })
                PerformanceMonitor.recordNanos("模型请求", requestStartedAt, apiUsageTitle.ifBlank { assistant.name })
                messages = messages.handleMessageChunk(chunk = chunk, model = model)
'''
if nonstream_anchor not in generation:
    raise SystemExit('Non-stream request anchor not found')
generation = generation.replace(nonstream_anchor, nonstream_replacement, 1)
tool_anchor = '                            val result = toolDef.execute(args)\n                            executedTools += tool.copy(output = result)'
tool_replacement = '''                            val toolStartedAt = System.nanoTime()
                            val result = try {
                                toolDef.execute(args)
                            } finally {
                                PerformanceMonitor.recordNanos("工具执行", toolStartedAt, toolDef.name)
                            }
                            executedTools += tool.copy(output = result)'''
if tool_anchor not in generation:
    raise SystemExit('Tool timing anchor not found')
generation = generation.replace(tool_anchor, tool_replacement, 1)
end_anchor = '        }\n\n    }.flowOn(Dispatchers.IO)'
if end_anchor not in generation:
    raise SystemExit('Generation total timing anchor not found')
generation = generation.replace(
    end_anchor,
    '        }\n        PerformanceMonitor.recordNanos("总耗时", pipelineStartedAt, apiUsageTitle.ifBlank { assistant.name })\n\n    }.flowOn(Dispatchers.IO)',
    1,
)
write(generation_path, generation)

# Planner and background memory extraction timings.
chat_path = 'app/src/main/java/me/rerere/rikkahub/service/ChatService.kt'
chat = read(chat_path)
if 'import me.rerere.rikkahub.data.ai.PerformanceMonitor' not in chat:
    chat = chat.replace(
        'import me.rerere.rikkahub.data.ai.GenerationHandler\n',
        'import me.rerere.rikkahub.data.ai.GenerationHandler\nimport me.rerere.rikkahub.data.ai.PerformanceMonitor\n',
        1,
    )
planner_start = '''    ): ChatTurnPlanResult {
        val input = CompanionChatTurnPlanInput(perception = perception)
'''
if planner_start not in chat:
    raise SystemExit('Planner start anchor not found')
chat = chat.replace(
    planner_start,
    '''    ): ChatTurnPlanResult {
        val plannerStartedAt = System.nanoTime()
        val input = CompanionChatTurnPlanInput(perception = perception)
''',
    1,
)
planner_return = '''        return ChatTurnPlanResult(
            plan = basePlan.copy(
'''
if planner_return not in chat:
    raise SystemExit('Planner return anchor not found')
chat = chat.replace(
    planner_return,
    '        PerformanceMonitor.recordNanos("Planner", plannerStartedAt, assistant.name)\n' + planner_return,
    1,
)
memory_start = '''): Job = appScope.launch {
            var activeExtractionBatchId: String? = null
'''
if memory_start not in chat:
    raise SystemExit('Memory start anchor not found')
chat = chat.replace(
    memory_start,
    '''): Job = appScope.launch {
            val extractionStartedAt = System.nanoTime()
            var activeExtractionBatchId: String? = null
''',
    1,
)
memory_end = '''                Log.w(TAG, "Affective memory extraction failed for conversation=$conversationId", error)
            }
        }


    // ---- 检查无效消息 ----
'''
if memory_end not in chat:
    raise SystemExit('Memory end anchor not found')
chat = chat.replace(
    memory_end,
    '''                Log.w(TAG, "Affective memory extraction failed for conversation=$conversationId", error)
            }
            PerformanceMonitor.recordNanos("Memory Extraction", extractionStartedAt, assistant.name)
        }


    // ---- 检查无效消息 ----
''',
    1,
)
write(chat_path, chat)

# Clean up one-time automation files in the final commit.
Path('.github/workflows/add-performance-monitor-once.yml').unlink(missing_ok=True)
Path('scripts/apply_performance_monitor_once.py').unlink(missing_ok=True)
