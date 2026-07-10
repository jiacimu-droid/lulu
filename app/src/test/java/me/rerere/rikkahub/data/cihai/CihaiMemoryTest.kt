package me.rerere.rikkahub.data.cihai

import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.service.AffectiveMemoryCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CihaiMemoryTest {
    @Test
    fun `adding a new entry makes it visible and queues it atomically`() {
        val entry = memoryEntry(id = "new-entry", createdAt = 1_000)

        val updated = CihaiState().addEntryToMemoryQueue(entry)

        assertEquals(listOf("new-entry"), updated.entries.map { it.id })
        assertEquals(CihaiMemoryDisposition.PENDING, updated.entries.single().resolvedMemoryDisposition)
        assertEquals(listOf("new-entry"), updated.memoryQueue.map { it.entryId })
        assertEquals(1_000L, updated.memoryQueue.single().enqueuedAt)
    }

    @Test
    fun `non diary entries are rejected before entering memory queue`() {
        val reflection = memoryEntry(id = "reflection", kind = CihaiEntryKind.REFLECTION)
        val innerJournal = memoryEntry(id = "inner", kind = CihaiEntryKind.INNER_JOURNAL)

        val updated = CihaiState()
            .addEntryToMemoryQueue(reflection)
            .addEntryToMemoryQueue(innerJournal)

        assertTrue(updated.entries.isEmpty())
        assertTrue(updated.memoryQueue.isEmpty())
    }

    @Test
    fun `completing a settlement marks evidence saved and other reviewed entries cihai only`() {
        val initial = CihaiState()
            .addEntryToMemoryQueue(memoryEntry(id = "saved", createdAt = 100))
            .addEntryToMemoryQueue(memoryEntry(id = "reviewed", createdAt = 200))

        val updated = initial.completeMemorySettlement(
            reviewedEntryIds = setOf("saved", "reviewed"),
            savedEvidenceEntryIds = setOf("saved"),
        )

        assertEquals(CihaiMemoryDisposition.SAVED, updated.entries.first { it.id == "saved" }.resolvedMemoryDisposition)
        assertEquals(
            CihaiMemoryDisposition.CIHAI_ONLY,
            updated.entries.first { it.id == "reviewed" }.resolvedMemoryDisposition,
        )
        assertTrue(updated.memoryQueue.isEmpty())
    }

    @Test
    fun `failed settlement keeps entries pending and applies capped retry backoff`() {
        val initial = CihaiState()
            .addEntryToMemoryQueue(memoryEntry(id = "retry", createdAt = 100))

        val first = initial.retryMemorySettlement(
            entryIds = setOf("retry"),
            failedAt = 1_000,
            error = "network unavailable",
        )
        val heavilyRetried = first.copy(
            memoryQueue = first.memoryQueue.map { it.copy(attemptCount = 20) },
        ).retryMemorySettlement(
            entryIds = setOf("retry"),
            failedAt = 2_000,
            error = "x".repeat(500),
        )

        assertEquals(1, first.memoryQueue.single().attemptCount)
        assertTrue(first.memoryQueue.single().nextAttemptAt > 1_000)
        assertEquals(CihaiMemoryDisposition.PENDING, first.entries.single().resolvedMemoryDisposition)
        assertTrue(heavilyRetried.memoryQueue.single().nextAttemptAt <= 2_000 + 6 * 60 * 60 * 1_000L)
        assertTrue(heavilyRetried.memoryQueue.single().lastError!!.length <= 300)
    }

    @Test
    fun `deleting an entry also removes its pending queue item`() {
        val initial = CihaiState()
            .addEntryToMemoryQueue(memoryEntry(id = "delete-me"))
            .addEntryToMemoryQueue(memoryEntry(id = "keep-me"))

        val updated = initial.removeCihaiEntry("delete-me")

        assertEquals(listOf("keep-me"), updated.entries.map { it.id })
        assertEquals(listOf("keep-me"), updated.memoryQueue.map { it.entryId })
    }

    @Test
    fun `clearing assistant records keeps other assistants`() {
        val targetEntry = memoryEntry(id = "target", assistantId = "assistant-a")
        val otherEntry = memoryEntry(id = "other", assistantId = "assistant-b")
        val state = CihaiState(
            selectedAssistantId = "assistant-a",
            entries = listOf(targetEntry, otherEntry),
            memoryQueue = listOf(
                queueItem(entryId = "target", assistantId = "assistant-a", enqueuedAt = 100),
                queueItem(entryId = "other", assistantId = "assistant-b", enqueuedAt = 100),
            ),
        )

        val cleared = state.withoutAssistantRecords("assistant-a")

        assertEquals(listOf("other"), cleared.entries.map { it.id })
        assertEquals(listOf("other"), cleared.memoryQueue.map { it.entryId })
        assertEquals("assistant-a", cleared.selectedAssistantId)
    }

    @Test
    fun `normalization removes queue items whose entries are invalid or no longer pending`() {
        val invalidEntry = memoryEntry(id = "invalid").copy(content = "")
        val savedEntry = memoryEntry(id = "saved").copy(
            kind = CihaiEntryKind.DIARY,
            memoryDisposition = CihaiMemoryDisposition.SAVED,
            memorySaved = true,
        )
        val state = CihaiState(
            entries = listOf(invalidEntry, savedEntry),
            memoryQueue = listOf(
                queueItem(entryId = "invalid"),
                queueItem(entryId = "saved"),
                queueItem(entryId = "missing"),
            ),
        )

        val normalized = state.normalizedCihaiState()

        assertEquals(listOf("saved"), normalized.entries.map { it.id })
        assertTrue(normalized.memoryQueue.isEmpty())
    }

    @Test
    fun `normalization removes legacy reflection and background journal entries`() {
        val reflection = memoryEntry(id = "legacy-reflection").copy(
            kind = CihaiEntryKind.REFLECTION,
            title = "露露的下一轮判断沉淀",
            content = "本轮感知世界包、意义评估、动态判断和状态生成的结果。",
        )
        val innerJournal = memoryEntry(
            id = "legacy-inner-journal",
            kind = CihaiEntryKind.INNER_JOURNAL,
        )
        val diary = memoryEntry(id = "diary").copy(kind = CihaiEntryKind.DIARY)
        val state = CihaiState(
            entries = listOf(reflection, innerJournal, diary),
            memoryQueue = listOf(
                queueItem(entryId = reflection.id),
                queueItem(entryId = innerJournal.id),
                queueItem(entryId = diary.id),
            ),
        )

        val normalized = state.normalizedCihaiState()

        assertEquals(listOf("diary"), normalized.entries.map { it.id })
        assertEquals(listOf("diary"), normalized.memoryQueue.map { it.entryId })
    }

    @Test
    fun `legacy memory flags resolve without enqueueing old entries`() {
        val state = Json.decodeFromString(
            CihaiState.serializer(),
            """
                {
                  "entries": [
                    {
                      "id": "saved-entry",
                      "assistantId": "lulu",
                      "kind": "inner_journal",
                      "title": "saved-entry",
                      "content": "saved",
                      "createdAt": 100,
                      "memorySaved": true
                    },
                    {
                      "id": "unsaved-entry",
                      "assistantId": "lulu",
                      "kind": "inner_journal",
                      "title": "unsaved-entry",
                      "content": "unsaved",
                      "createdAt": 100
                    }
                  ]
                }
            """.trimIndent(),
        )
        val oldSavedEntry = state.entries.first { it.id == "saved-entry" }
        val oldUnsavedEntry = state.entries.first { it.id == "unsaved-entry" }

        assertEquals(CihaiMemoryDisposition.SAVED, oldSavedEntry.resolvedMemoryDisposition)
        assertEquals(CihaiMemoryDisposition.CIHAI_ONLY, oldUnsavedEntry.resolvedMemoryDisposition)
        assertTrue(state.memoryQueue.isEmpty())
    }

    @Test
    fun `due batch is isolated by assistant threshold and batch limit`() {
        val queue = listOf(
            queueItem(entryId = "lulu-3", assistantId = "lulu", enqueuedAt = 30),
            queueItem(entryId = "nana-1", assistantId = "nana", enqueuedAt = 5),
            queueItem(entryId = "lulu-1", assistantId = "lulu", enqueuedAt = 10),
            queueItem(entryId = "lulu-2", assistantId = "lulu", enqueuedAt = 20),
        )

        val batch = selectDueCihaiMemoryBatch(
            queue = queue,
            assistantId = "lulu",
            nowMillis = 100,
            policy = CihaiMemorySettlementPolicy(
                minimumBatchSize = 3,
                maximumWaitMillis = 1_000,
                maximumBatchSize = 2,
            ),
        )

        assertEquals(listOf("lulu-1", "lulu-2"), batch.map { it.entryId })
        assertTrue(batch.all { it.assistantId == "lulu" })
    }

    @Test
    fun `oldest due item triggers a batch after maximum wait`() {
        val batch = selectDueCihaiMemoryBatch(
            queue = listOf(queueItem(entryId = "old", enqueuedAt = 100)),
            assistantId = "lulu",
            nowMillis = 1_100,
            policy = CihaiMemorySettlementPolicy(
                minimumBatchSize = 4,
                maximumWaitMillis = 1_000,
                maximumBatchSize = 4,
            ),
        )

        assertEquals(listOf("old"), batch.map { it.entryId })
    }

    @Test
    fun `batch waits while count and maximum wait are both below threshold`() {
        val batch = selectDueCihaiMemoryBatch(
            queue = listOf(
                queueItem(entryId = "first", enqueuedAt = 100),
                queueItem(entryId = "second", enqueuedAt = 200),
            ),
            assistantId = "lulu",
            nowMillis = 999,
            policy = CihaiMemorySettlementPolicy(
                minimumBatchSize = 3,
                maximumWaitMillis = 1_000,
                maximumBatchSize = 3,
            ),
        )

        assertTrue(batch.isEmpty())
    }

    @Test
    fun `retry item is ineligible until next attempt time`() {
        val retry = queueItem(
            entryId = "retry",
            enqueuedAt = 100,
            attemptCount = 2,
            nextAttemptAt = 2_000,
            lastError = "temporary failure",
        )
        val policy = CihaiMemorySettlementPolicy(
            minimumBatchSize = 1,
            maximumWaitMillis = 0,
            maximumBatchSize = 4,
        )

        assertTrue(
            selectDueCihaiMemoryBatch(
                queue = listOf(retry),
                assistantId = "lulu",
                nowMillis = 1_999,
                policy = policy,
            ).isEmpty()
        )
        assertEquals(
            listOf("retry"),
            selectDueCihaiMemoryBatch(
                queue = listOf(retry),
                assistantId = "lulu",
                nowMillis = 2_000,
                policy = policy,
            ).map { it.entryId },
        )
    }

    @Test
    fun `settlement prompt limits durable value and labels current batch evidence`() {
        val prompt = CihaiMemorySettlement.buildPrompt(
            entries = listOf(
                memoryEntry(
                    id = "preference",
                    content = "她明确说晚上十点后不要打电话。",
                )
            ),
            assistantName = "露露",
        )

        assertTrue(prompt.contains("cihai:preference"))
        assertTrue(prompt.contains("稳定用户事实"))
        assertTrue(prompt.contains("用户纠正"))
        assertTrue(prompt.contains("工具 JSON"))
        assertTrue(prompt.contains("无用户证据的关系升级"))
    }

    @Test
    fun `settlement parser accepts fenced evidence backed candidate`() {
        val entries = listOf(memoryEntry(id = "preference"))
        val raw = """
            ```json
            {
              "memories": [
                {
                  "type": "user_preference",
                  "content": "我记得她明确说过晚上十点后不要打电话。",
                  "userSignal": "她明确说晚上十点后不要打电话。",
                  "sourceMessageNodeIds": ["cihai:preference"],
                  "evidenceMessageNodeIds": ["cihai:preference"]
                }
              ]
            }
            ```
        """.trimIndent()

        val candidates = CihaiMemorySettlement.parseAndValidateCandidates(
            rawText = raw,
            assistantId = "lulu",
            entries = entries,
        )

        assertEquals(1, candidates.size)
        assertEquals("cihai:preference", candidates.single().sourceMessageNodeIds.single())
    }

    @Test
    fun `quality gate requires all source and evidence ids to close over the current assistant batch`() {
        val entries = listOf(
            memoryEntry(id = "lulu-entry", assistantId = "lulu"),
            memoryEntry(id = "nana-entry", assistantId = "nana"),
        )
        val candidates = listOf(
            durableCandidate(sourceId = "cihai:missing"),
            durableCandidate(sourceId = "cihai:nana-entry"),
            durableCandidate(
                sourceId = "cihai:lulu-entry",
                evidenceIds = listOf("cihai:lulu-entry", "cihai:missing"),
            ),
        )

        val accepted = CihaiMemorySettlement.validateCandidates(
            assistantId = "lulu",
            entries = entries,
            candidates = candidates,
        )

        assertTrue(accepted.isEmpty())
    }

    @Test
    fun `quality gate rejects unsupported type and durable claims without user evidence`() {
        val entries = listOf(memoryEntry(id = "evidence"))
        val candidates = listOf(
            durableCandidate(type = "event"),
            durableCandidate(
                type = "user_fact",
                content = "我记得她每天早上六点起床。",
                userSignal = null,
            ),
            durableCandidate(
                type = "promise",
                content = "我答应她晚上十点后不再打电话。",
                userSignal = null,
            ),
            durableCandidate(
                type = "shared_event",
                content = "我记得我们一起聊过一次考试。",
                userSignal = "她提到过考试。",
            ),
        )

        val accepted = CihaiMemorySettlement.validateCandidates(
            assistantId = "lulu",
            entries = entries,
            candidates = candidates,
        )

        assertTrue(accepted.isEmpty())
    }

    @Test
    fun `quality gate rejects trace fallback and unsupported relationship upgrade`() {
        val entries = listOf(memoryEntry(id = "evidence"))
        val candidates = listOf(
            durableCandidate(
                content = "我记录了工具 JSON：{\"success\":true,\"tool_result\":{\"status\":\"ok\"}}",
            ),
            durableCandidate(
                content = "我记得感知层和动态判断层完成了本轮状态生成。",
            ),
            durableCandidate(
                content = "我整理了记忆，之后可以参考并继续观察。",
            ),
            durableCandidate(
                type = "relationship",
                content = "我感觉我们的关系更亲密、更信任了。",
                userSignal = null,
            ),
        )

        val accepted = CihaiMemorySettlement.validateCandidates(
            assistantId = "lulu",
            entries = entries,
            candidates = candidates,
        )

        assertTrue(accepted.isEmpty())
    }

    @Test
    fun `quality gate rejects generic reading insight without user evidence`() {
        val readingEntry = memoryEntry(
            id = "reading",
            kind = CihaiEntryKind.READING_NOTE,
            content = "书里说沟通时应当保持耐心。",
        )
        val candidate = durableCandidate(
            type = "shared_event",
            content = "我从阅读中明白沟通时应该保持耐心。",
            sourceId = "cihai:reading",
            userSignal = null,
        )

        val accepted = CihaiMemorySettlement.validateCandidates(
            assistantId = "lulu",
            entries = listOf(readingEntry),
            candidates = listOf(candidate),
        )

        assertTrue(accepted.isEmpty())
    }

    @Test
    fun `quality gate deduplicates normalized text and merges batch evidence`() {
        val entries = listOf(
            memoryEntry(id = "first"),
            memoryEntry(id = "second"),
        )
        val candidates = listOf(
            durableCandidate(
                content = "我记得她明确说过，晚上十点后不要打电话。",
                sourceId = "cihai:first",
            ),
            durableCandidate(
                content = "  我记得她明确说过 晚上十点后不要打电话  ",
                sourceId = "cihai:second",
            ),
        )

        val accepted = CihaiMemorySettlement.validateCandidates(
            assistantId = "lulu",
            entries = entries,
            candidates = candidates,
        )

        assertEquals(1, accepted.size)
        assertEquals(
            listOf("cihai:first", "cihai:second"),
            accepted.single().sourceMessageNodeIds,
        )
    }

    @Test
    fun `cihai memory context keeps recent and unsummarized entries with sixty entry threshold`() {
        val entries = (1..75).map { index ->
            CihaiEntry(
                assistantId = "lulu",
                kind = CihaiEntryKind.INNER_JOURNAL,
                title = "entry-$index",
                content = "content-$index",
                createdAt = index.toLong(),
                memoryDisposition = if (index <= 10) {
                    CihaiMemoryDisposition.SAVED
                } else {
                    CihaiMemoryDisposition.PENDING
                },
                memorySaved = index <= 10,
            )
        }

        val context = buildCihaiMemoryContext(entries)

        assertEquals(60, context.recentEntries.size)
        assertEquals("entry-16", context.recentEntries.first().title)
        assertEquals(60, context.unsummarizedEntries.size)
        assertEquals("entry-11", context.unsummarizedEntries.first().title)
        assertTrue(context.shouldSummarize)
    }

    private fun memoryEntry(
        id: String,
        assistantId: String = "lulu",
        kind: CihaiEntryKind = CihaiEntryKind.DIARY,
        content: String = "她明确说晚上十点后不要打电话。",
        memorySaved: Boolean = false,
    ): CihaiEntry = CihaiEntry(
        id = id,
        assistantId = assistantId,
        kind = kind,
        title = id,
        content = content,
        createdAt = 100,
        memorySaved = memorySaved,
    )

    private fun queueItem(
        entryId: String,
        assistantId: String = "lulu",
        enqueuedAt: Long,
        attemptCount: Int = 0,
        nextAttemptAt: Long = enqueuedAt,
        lastError: String? = null,
    ): CihaiMemoryQueueItem = CihaiMemoryQueueItem(
        entryId = entryId,
        assistantId = assistantId,
        enqueuedAt = enqueuedAt,
        attemptCount = attemptCount,
        nextAttemptAt = nextAttemptAt,
        lastError = lastError,
    )

    private fun durableCandidate(
        type: String = "user_preference",
        content: String = "我记得她明确说过晚上十点后不要打电话。",
        sourceId: String = "cihai:evidence",
        userSignal: String? = "她明确说晚上十点后不要打电话。",
        evidenceIds: List<String> = listOf(sourceId),
    ): AffectiveMemoryCandidate = AffectiveMemoryCandidate(
        type = type,
        content = content,
        userSignal = userSignal,
        sourceMessageNodeIds = listOf(sourceId),
        evidenceMessageNodeIds = evidenceIds,
    )
}
