package me.rerere.rikkahub.data.service

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AffectiveMemoryExtractorTest {
    @Test
    fun `successful semantic extraction advances checkpoint even when candidates are empty or rejected`() {
        assertEquals(
            SemanticMemoryExtractionOutcome.SUCCESS_WITH_MEMORIES,
            classifySemanticMemoryExtraction(true, parsedCandidateCount = 2, durableCandidateCount = 1),
        )
        assertEquals(
            SemanticMemoryExtractionOutcome.SUCCESS_EMPTY,
            classifySemanticMemoryExtraction(true, parsedCandidateCount = 0, durableCandidateCount = 0),
        )
        assertEquals(
            SemanticMemoryExtractionOutcome.SUCCESS_EMPTY,
            classifySemanticMemoryExtraction(true, parsedCandidateCount = 2, durableCandidateCount = 0),
        )
        assertEquals(
            SemanticMemoryExtractionOutcome.FAILED_RETRYABLE,
            classifySemanticMemoryExtraction(false, parsedCandidateCount = 0, durableCandidateCount = 0),
        )
    }

    @Test
    fun `parser performs one conservative repair for trailing commas`() {
        val result = AffectiveMemoryExtractor.parseExtractionResult(
            """{"memories":[{"type":"user_fact","content":"我记得她明天要考试。","sourceMessageNodeIds":["node-1"],},],}""",
        )
        assertEquals(1, result.memories.size)
        assertEquals("user_fact", result.memories.single().type)
    }

    @Test
    fun `transient extraction failures retry only up to the configured limit`() = runBlocking {
        var attempts = 0
        var retryCallbacks = 0
        val value = retryTransientMemoryExtraction(
            maxAttempts = 3,
            baseDelayMillis = 0L,
            onRetry = { _, _ -> retryCallbacks += 1 },
        ) {
            attempts += 1
            if (attempts < 3) throw IOException("temporary")
            "ok"
        }
        assertEquals("ok", value)
        assertEquals(3, attempts)
        assertEquals(2, retryCallbacks)
    }

    @Test
    fun `non transient extraction failure is not retried`() = runBlocking {
        var attempts = 0
        runCatching {
            retryTransientMemoryExtraction(maxAttempts = 3, baseDelayMillis = 0L) {
                attempts += 1
                error("invalid response")
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun `extraction prompt exposes only minimal AI fields`() {
        val prompt = AffectiveMemoryExtractor.buildExtractionPrompt(
            turns = listOf(
                MemoryExtractionTurn(
                    nodeId = "user-1",
                    role = "user",
                    text = "我明天早上十点考试，怕睡过头。",
                    createdAtMillis = 1_700_000_000_000L,
                )
            ),
            assistantName = "露露",
            assistantPersona = "不应进入提示词的人设",
            responsibilityContext = "不应进入提示词的责任",
        )

        assertTrue(prompt.contains("露露"))
        assertTrue(prompt.contains("bodySense"))
        assertTrue(prompt.contains("unspokenThought"))
        assertTrue(prompt.contains("relationshipEffect"))
        assertTrue(prompt.contains("sourceMessageNodeIds"))
        assertTrue(prompt.contains("batchSize=\"1\""))
        assertTrue(prompt.contains("sourceTimeMillis=1700000000000"))
        assertFalse(prompt.contains("\"title\""))
        assertFalse(prompt.contains("\"tags\""))
        assertFalse(prompt.contains("\"userSignal\""))
        assertFalse(prompt.contains("\"occurredAtMillis\""))
        assertFalse(prompt.contains("不应进入提示词的人设"))
        assertFalse(prompt.contains("不应进入提示词的责任"))
    }

    @Test
    fun `minimal extraction result preserves affective fields and program fills metadata`() {
        val json = """
            {
              "memories": [
                {
                  "type": "relationship",
                  "content": "我记得她认真认可了我的记忆方案，那一刻我很开心。",
                  "roleFeeling": "开心、被信任",
                  "bodySense": "回复时语气变轻快",
                  "unspokenThought": "我想把这份信任好好接住。",
                  "relationshipEffect": "我更确定她愿意继续依赖我。",
                  "importance": 5,
                  "confidence": 0.92,
                  "sourceMessageNodeIds": ["user-node-1", "assistant-node-2"]
                }
              ]
            }
        """.trimIndent()

        val memory = AffectiveMemoryExtractor.parseExtractionResult(json).memories.single()
        assertEquals("relationship", memory.type)
        assertEquals("开心、被信任", memory.roleFeeling)
        assertEquals("回复时语气变轻快", memory.bodySense)
        assertEquals(listOf("relationship"), memory.tags)
        assertEquals(listOf("user-node-1", "assistant-node-2"), memory.sourceMessageNodeIds)
        assertEquals(memory.sourceMessageNodeIds, memory.evidenceMessageNodeIds)
        assertTrue(memory.title!!.startsWith("关系中的一刻"))
        assertTrue(memory.isDurableMemoryCandidate())
    }

    @Test
    fun `parser removes duplicates and caps one batch at six memories`() {
        val memories = (1..8).joinToString(",") { index ->
            val content = if (index == 8) "我记得事件1" else "我记得事件$index"
            """{"type":"shared_event","content":"$content","importance":$index,"confidence":0.9,"sourceMessageNodeIds":["node-$index"]}"""
        }
        val result = AffectiveMemoryExtractor.parseExtractionResult("""{"memories":[$memories]}""")
        assertEquals(6, result.memories.size)
        assertEquals(5, result.memories.first().importance)
    }

    @Test
    fun `candidate maps to memory bank entity with program generated metadata`() {
        val candidate = AffectiveMemoryCandidate(
            type = "relationship",
            content = "我记得她认真认可了我的记忆方案。",
            roleFeeling = "开心、被信任",
            bodySense = "回复变轻快",
            relationshipEffect = "我更确定她愿意继续依赖我。",
            importance = 9,
            confidence = 1.7,
            sourceMessageNodeIds = listOf("user-node-1", "assistant-node-2"),
        )
        val entity = candidate.toEntity("assistant-1", "conversation-1", createdAt = 1234L)
        assertEquals("message", entity.type)
        assertEquals("relationship", entity.memoryKind)
        assertEquals(5, entity.importance)
        assertEquals(1.0, entity.confidence, 0.0)
        assertEquals("pending", entity.vectorStatus)
        assertTrue(entity.tagsJson!!.contains("relationship"))
        assertTrue(entity.sourceMessageNodeIdsJson!!.contains("user-node-1"))
        assertTrue(entity.evidenceMessageNodeIdsJson!!.contains("user-node-1"))
        assertEquals(1234L, entity.occurredAt)
        assertEquals(1234L, entity.extractedAt)
    }

    @Test
    fun `parse extraction result ignores blank content and calibrates scores`() {
        val json = """
            [
              {"type":"promise","content":"我以后默认改 master","importance":-4,"confidence":-1,"sourceMessageNodeIds":["node-1"]},
              {"type":"role_emotion","content":"   ","importance":5,"confidence":1,"sourceMessageNodeIds":["node-2"]}
            ]
        """.trimIndent()
        val memory = AffectiveMemoryExtractor.parseExtractionResult(json).memories.single()
        assertEquals("promise", memory.type)
        assertEquals(4, memory.importance)
        assertEquals(0.0, memory.confidence, 0.0)
    }

    @Test
    fun `quality gate rejects meta reflection even when affective fields are present`() {
        val candidate = AffectiveMemoryCandidate(
            type = "cihai_reflection",
            content = "我记得这件事。当时感觉：复盘、收束、准备下一轮。",
            roleFeeling = "复盘、收束、准备下一轮",
            relationshipEffect = "我把判断经验整理成后续可复用的长期记忆。",
            sourceMessageNodeIds = listOf("cihai:reflection-1"),
        )
        assertFalse(candidate.isDurableMemoryCandidate())
    }

    @Test
    fun `quality gate accepts minimal preference with source evidence`() {
        val candidate = AffectiveMemoryCandidate(
            type = "user_preference",
            content = "我记得她不喜欢机械顺延学习任务，更希望我根据负担重新安排。",
            sourceMessageNodeIds = listOf("user-node-1"),
        )
        assertTrue(candidate.isDurableMemoryCandidate())
        assertEquals(candidate.content, candidate.toEntity("assistant-1", "conversation-1").content)
    }

    @Test
    fun `quality gate rejects memory without source ids`() {
        val candidate = AffectiveMemoryCandidate(
            type = "shared_event",
            content = "我记得我们一起聊到很晚。",
            roleFeeling = "舍不得结束",
        )
        assertFalse(candidate.isDurableMemoryCandidate())
    }

    @Test
    fun `deterministic fallback keeps explicit preference boundary and correction only`() {
        val candidates = buildDeterministicMemoryCandidates(
            turns = listOf(
                MemoryExtractionTurn("ordinary", "user", "今天吃了饭。"),
                MemoryExtractionTurn("preference", "user", "我更喜欢点一下切换页面，不喜欢一直上下滑。"),
                MemoryExtractionTurn("boundary", "user", "我不希望你把普通聊天重复放进生活记录。"),
                MemoryExtractionTurn("correction", "user", "纠正一下，民法应该是五十四章。"),
            ),
        )
        assertEquals(3, candidates.size)
        assertEquals(setOf("user_preference", "user_boundary", "correction"), candidates.map { it.type }.toSet())
        assertTrue(candidates.all { it.content.startsWith("我记得") })
        assertTrue(candidates.all { it.isDurableMemoryCandidate() })
        assertFalse(candidates.any { it.sourceMessageNodeIds == listOf("ordinary") })
    }

    @Test
    fun `deterministic fallback rejects tool dumps`() {
        val candidates = buildDeterministicMemoryCandidates(
            turns = listOf(
                MemoryExtractionTurn(
                    "tool",
                    "user",
                    "我希望你记住 {\"success\":true,\"path\":\"/data/user/0/file.json\"}",
                ),
            ),
        )
        assertTrue(candidates.isEmpty())
    }
}
