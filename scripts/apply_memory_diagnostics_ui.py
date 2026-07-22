#!/usr/bin/env python3
from pathlib import Path

VM = Path("app/src/main/java/me/rerere/rikkahub/ui/pages/memory/MemoryBankVM.kt")
PAGE = Path("app/src/main/java/me/rerere/rikkahub/ui/pages/memory/MemoryBankPage.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count == 0:
        if new in text:
            return text
        raise SystemExit(f"missing patch anchor: {label}")
    if count != 1:
        raise SystemExit(f"ambiguous patch anchor ({count}): {label}")
    return text.replace(old, new, 1)


vm = VM.read_text(encoding="utf-8")
vm = replace_once(
    vm,
    """    val remainingMessageCount: Int,\n    val batches: List<MemoryExtractionBatchEntity>,\n)""",
    """    val remainingMessageCount: Int,\n    val diagnostic: MemoryTriggerDiagnostic,\n    val batches: List<MemoryExtractionBatchEntity>,\n)""",
    "MemoryBatchOverview diagnostic field",
)
vm = replace_once(
    vm,
    """                    MemoryBatchOverview(\n                        conversationId = conversationId,\n                        successfulThrough = successfulThrough,\n                        nextBatchStart = successfulThrough + 1,\n                        stableRegionEnd = stableRegionEnd,\n                        remainingMessageCount = (stableRegionEnd - successfulThrough).coerceAtLeast(0),\n                        batches = conversationBatches.sortedBy { it.batchStartSequence },\n                    )""",
    """                    MemoryBatchOverview(\n                        conversationId = conversationId,\n                        successfulThrough = successfulThrough,\n                        nextBatchStart = successfulThrough + 1,\n                        stableRegionEnd = stableRegionEnd,\n                        remainingMessageCount = (stableRegionEnd - successfulThrough).coerceAtLeast(0),\n                        diagnostic = buildMemoryTriggerDiagnostic(\n                            totalMessageCount = conversation.messageNodes.size,\n                            protectedRecentCount = protectedRecent,\n                            batchSize = batchSize,\n                            successfulThrough = successfulThrough,\n                            batches = conversationBatches,\n                        ),\n                        batches = conversationBatches.sortedBy { it.batchStartSequence },\n                    )""",
    "build MemoryBatchOverview diagnostic",
)
VM.write_text(vm, encoding="utf-8")

page = PAGE.read_text(encoding="utf-8")
page = replace_once(
    page,
    """                            Text(\n                                \"成功点 ${overview.successfulThrough} · 下一批从 ${overview.nextBatchStart} 开始 · 稳定区到 ${overview.stableRegionEnd} · 剩余 ${overview.remainingMessageCount} 条\",\n                                style = MaterialTheme.typography.bodySmall,\n                                color = MaterialTheme.colorScheme.onSurfaceVariant,\n                            )""",
    """                            Text(\n                                overview.diagnostic.explanation,\n                                style = MaterialTheme.typography.bodySmall,\n                                color = when (overview.diagnostic.state) {\n                                    MemoryTriggerState.PROCESSING_OR_RETRYING -> MaterialTheme.colorScheme.error\n                                    MemoryTriggerState.READY -> MaterialTheme.colorScheme.primary\n                                    else -> MaterialTheme.colorScheme.onSurfaceVariant\n                                },\n                            )\n                            Text(\n                                \"消息 ${overview.diagnostic.totalMessageCount} · 保护 ${overview.diagnostic.protectedRecentCount} · 可整理 ${overview.diagnostic.eligibleMessageCount} · 每批 ${overview.diagnostic.batchSize}\",\n                                style = MaterialTheme.typography.labelSmall,\n                                color = MaterialTheme.colorScheme.onSurfaceVariant,\n                            )\n                            Text(\n                                \"成功 ${overview.diagnostic.successfulBatchCount} 批 · 空结果 ${overview.diagnostic.emptyBatchCount} 批 · 可重试 ${overview.diagnostic.retryableFailureCount} 批 · 人工复核 ${overview.diagnostic.manualReviewCount} 批\",\n                                style = MaterialTheme.typography.labelSmall,\n                                color = MaterialTheme.colorScheme.onSurfaceVariant,\n                            )\n                            Text(\n                                \"成功点 ${overview.successfulThrough} · 下一批从 ${overview.nextBatchStart} 开始 · 稳定区到 ${overview.stableRegionEnd} · 剩余 ${overview.remainingMessageCount} 条\",\n                                style = MaterialTheme.typography.labelSmall,\n                                color = MaterialTheme.colorScheme.onSurfaceVariant,\n                            )""",
    "render memory diagnostic card",
)
PAGE.write_text(page, encoding="utf-8")
print("memory diagnostics UI patch applied")
