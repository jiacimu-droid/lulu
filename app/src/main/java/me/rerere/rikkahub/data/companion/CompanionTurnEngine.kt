package me.rerere.rikkahub.data.companion

import me.rerere.rikkahub.data.ai.PerformanceMonitor
import java.util.UUID

/**
 * Entry point shared by chat, voice calls, proactive messages and companion games.
 *
 * The engine owns the lifecycle order, while each surface supplies its concrete
 * preparation, decision, generation, tool, persistence and maintenance work.
 * Persistence always finishes before a result is returned. Heavy maintenance is
 * represented as a deferred task so callers can display the reply first and then
 * launch memory extraction, planning cleanup or indexing in an application scope.
 */
class CompanionTurnEngine {
    suspend fun <Context, Decision, Generated, Tools, Persisted> execute(
        request: CompanionTurnRequest,
        prepareContext: suspend (CompanionTurnRequest) -> Context,
        decide: suspend (Context) -> Decision,
        generate: suspend (Context, Decision) -> Generated,
        executeTools: suspend (Context, Decision, Generated) -> Tools,
        persist: suspend (CompanionTurnPersistenceInput<Context, Decision, Generated, Tools>) -> Persisted,
        maintain: suspend (CompanionTurnMaintenanceInput<Context, Decision, Generated, Tools, Persisted>) -> Unit = {},
    ): CompanionTurnResult<Context, Decision, Generated, Tools, Persisted> {
        val traceId = request.traceId.ifBlank { UUID.randomUUID().toString() }
        val trace = mutableListOf<CompanionTurnStageRecord>()

        suspend fun <T> stage(stage: CompanionTurnStage, block: suspend () -> T): T {
            val startedAtNanos = System.nanoTime()
            val startedAtMillis = System.currentTimeMillis()
            return try {
                block().also {
                    val duration = elapsedMillis(startedAtNanos)
                    trace += CompanionTurnStageRecord(stage, startedAtMillis, duration, CompanionTurnStageStatus.COMPLETED)
                    PerformanceMonitor.record(
                        stage = "陪伴回合/${stage.label}",
                        durationMillis = duration,
                        detail = "${request.entryPoint.name.lowercase()} trace=$traceId",
                    )
                }
            } catch (error: Throwable) {
                val duration = elapsedMillis(startedAtNanos)
                trace += CompanionTurnStageRecord(
                    stage = stage,
                    startedAtMillis = startedAtMillis,
                    durationMillis = duration,
                    status = CompanionTurnStageStatus.FAILED,
                    errorType = error::class.java.simpleName,
                )
                PerformanceMonitor.record(
                    stage = "陪伴回合/${stage.label}/失败",
                    durationMillis = duration,
                    detail = "${request.entryPoint.name.lowercase()} trace=$traceId ${error::class.java.simpleName}",
                )
                throw error
            }
        }

        val context = stage(CompanionTurnStage.PREPARE_CONTEXT) { prepareContext(request) }
        val decision = stage(CompanionTurnStage.DECIDE) { decide(context) }
        val generated = stage(CompanionTurnStage.GENERATE) { generate(context, decision) }
        val tools = stage(CompanionTurnStage.EXECUTE_TOOLS) { executeTools(context, decision, generated) }
        val persisted = stage(CompanionTurnStage.PERSIST) {
            persist(
                CompanionTurnPersistenceInput(
                    request = request.copy(traceId = traceId),
                    context = context,
                    decision = decision,
                    generated = generated,
                    tools = tools,
                ),
            )
        }

        val maintenanceInput = CompanionTurnMaintenanceInput(
            request = request.copy(traceId = traceId),
            context = context,
            decision = decision,
            generated = generated,
            tools = tools,
            persisted = persisted,
        )
        return CompanionTurnResult(
            traceId = traceId,
            request = request.copy(traceId = traceId),
            context = context,
            decision = decision,
            generated = generated,
            tools = tools,
            persisted = persisted,
            foregroundTrace = trace.toList(),
            runMaintenance = {
                stage(CompanionTurnStage.MAINTAIN) { maintain(maintenanceInput) }
            },
        )
    }

    private fun elapsedMillis(startedAtNanos: Long): Long =
        ((System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L)
}

enum class CompanionTurnEntryPoint {
    CHAT,
    VOICE_CALL,
    PROACTIVE,
    GAME,
}

enum class CompanionTurnStage(val label: String) {
    PREPARE_CONTEXT("准备上下文"),
    DECIDE("决策"),
    GENERATE("生成"),
    EXECUTE_TOOLS("工具执行"),
    PERSIST("状态落库"),
    MAINTAIN("后台维护"),
}

enum class CompanionTurnStageStatus {
    COMPLETED,
    FAILED,
}

data class CompanionTurnRequest(
    val assistantId: String,
    val conversationId: String? = null,
    val entryPoint: CompanionTurnEntryPoint,
    val userText: String? = null,
    val sourceMessageId: String? = null,
    val traceId: String = UUID.randomUUID().toString(),
    val requestedAtMillis: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
)

data class CompanionTurnStageRecord(
    val stage: CompanionTurnStage,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val status: CompanionTurnStageStatus,
    val errorType: String? = null,
)

data class CompanionTurnPersistenceInput<Context, Decision, Generated, Tools>(
    val request: CompanionTurnRequest,
    val context: Context,
    val decision: Decision,
    val generated: Generated,
    val tools: Tools,
)

data class CompanionTurnMaintenanceInput<Context, Decision, Generated, Tools, Persisted>(
    val request: CompanionTurnRequest,
    val context: Context,
    val decision: Decision,
    val generated: Generated,
    val tools: Tools,
    val persisted: Persisted,
)

data class CompanionTurnResult<Context, Decision, Generated, Tools, Persisted>(
    val traceId: String,
    val request: CompanionTurnRequest,
    val context: Context,
    val decision: Decision,
    val generated: Generated,
    val tools: Tools,
    val persisted: Persisted,
    val foregroundTrace: List<CompanionTurnStageRecord>,
    val runMaintenance: suspend () -> Unit,
)

/**
 * Neutral facts stored by the core. Role-flavoured wording belongs to the final
 * response layer and must not be persisted here.
 */
data class CompanionNeutralEvent(
    val type: String,
    val assistantId: String,
    val occurredAtMillis: Long,
    val status: CompanionNeutralEventStatus,
    val actor: CompanionEventActor,
    val subjectKey: String? = null,
    val evidenceReferences: List<String> = emptyList(),
    val responsibility: CompanionResponsibility? = null,
    val attributes: Map<String, String> = emptyMap(),
)

enum class CompanionNeutralEventStatus {
    PLANNED,
    ACTIVE,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class CompanionEventActor {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL,
}

data class CompanionResponsibility(
    val owner: CompanionEventActor,
    val beneficiary: CompanionEventActor? = null,
    val actionType: String,
    val dueAtMillis: Long? = null,
    val recurrence: String? = null,
)
