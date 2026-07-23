from __future__ import annotations

from pathlib import Path
import re
import textwrap

ROOT = Path.cwd()


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8-sig")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8", newline="\n")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if text.count(old) != 1:
        raise RuntimeError(f"Expected exactly one {label}, found {text.count(old)}")
    return text.replace(old, new, 1)


def extract_chat_session_registry() -> None:
    path = "app/src/main/java/me/rerere/rikkahub/service/ChatService.kt"
    text = read(path)

    text = replace_once(
        text,
        """    // 统一会话管理
    private val sessions = ConcurrentHashMap<Uuid, ConversationSession>()
    private val _sessionsVersion = MutableStateFlow(0L)
""",
        """    // 会话生命周期由独立注册表管理，ChatService 只保留兼容入口。
    private val sessionRegistry = ConversationSessionRegistry(
        appScope = appScope,
        settingsStore = settingsStore,
    )
""",
        "ChatService session fields",
    )

    text = replace_once(
        text,
        """        sessions.values.forEach { it.cleanup() }
        sessions.clear()
""",
        """        sessionRegistry.cleanup()
""",
        "ChatService cleanup",
    )

    start_marker = "    private fun getOrCreateSession(conversationId: Uuid): ConversationSession {"
    end_marker = "    // ---- 初始化对话 ----"
    start = text.find(start_marker)
    end = text.find(end_marker, start)
    if start < 0 or end < 0:
        raise RuntimeError("Could not locate ChatService session-management block")

    replacement = """    private fun getOrCreateSession(conversationId: Uuid): ConversationSession =
        sessionRegistry.getOrCreate(conversationId)

    // ---- 引用管理 ----

    fun addConversationReference(conversationId: Uuid) {
        sessionRegistry.acquire(conversationId)
    }

    fun removeConversationReference(conversationId: Uuid) {
        sessionRegistry.release(conversationId)
    }

    private fun launchWithConversationReference(
        conversationId: Uuid,
        block: suspend () -> Unit,
    ): Job = sessionRegistry.launchWithReference(conversationId, block)

    // ---- 对话状态访问 ----

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> =
        sessionRegistry.conversationFlow(conversationId)

    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> =
        sessionRegistry.generationJobFlow(conversationId)

    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> =
        sessionRegistry.processingStatusFlow(conversationId)

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> = sessionRegistry.activeJobs()

"""
    text = text[:start] + replacement + text[end:]

    if "sessions." in text or "sessions[" in text or "_sessionsVersion" in text:
        raise RuntimeError("ChatService still directly owns session collection state")

    write(path, text)

    registry = """package me.rerere.rikkahub.service

import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/** Owns conversation-session creation, reference counting, jobs and idle cleanup. */
internal class ConversationSessionRegistry(
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
) {
    private val sessions = ConcurrentHashMap<Uuid, ConversationSession>()
    private val version = MutableStateFlow(0L)

    fun getOrCreate(conversationId: Uuid): ConversationSession =
        sessions.computeIfAbsent(conversationId) { id ->
            val settings = settingsStore.settingsFlow.value
            ConversationSession(
                id = id,
                initial = Conversation.ofId(
                    id = id,
                    assistantId = settings.getCurrentAssistant().id,
                ),
                scope = appScope,
                onIdle = ::removeIfIdle,
            ).also {
                version.value++
                Log.i(TAG, "createSession: $id (total: ${sessions.size + 1})")
            }
        }

    fun acquire(conversationId: Uuid) {
        getOrCreate(conversationId).acquire()
    }

    fun release(conversationId: Uuid) {
        sessions[conversationId]?.release()
    }

    fun launchWithReference(
        conversationId: Uuid,
        block: suspend () -> Unit,
    ): Job = appScope.launch {
        acquire(conversationId)
        try {
            block()
        } finally {
            release(conversationId)
        }
    }

    fun conversationFlow(conversationId: Uuid): StateFlow<Conversation> =
        getOrCreate(conversationId).state

    fun generationJobFlow(conversationId: Uuid): Flow<Job?> =
        sessions[conversationId]?.generationJob ?: flowOf(null)

    fun processingStatusFlow(conversationId: Uuid): StateFlow<String?> =
        sessions[conversationId]?.processingStatus ?: MutableStateFlow(null)

    fun activeJobs(): Flow<Map<Uuid, Job?>> = version.flatMapLatest {
        val current = sessions.values.toList()
        if (current.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(current.map { session ->
                session.generationJob.map { job -> session.id to job }
            }) { pairs ->
                pairs.filter { it.second != null }.toMap()
            }
        }
    }

    fun cleanup() {
        sessions.values.forEach(ConversationSession::cleanup)
        sessions.clear()
        version.value++
    }

    private fun removeIfIdle(conversationId: Uuid) {
        val session = sessions[conversationId] ?: return
        if (session.isInUse) {
            Log.d(TAG, "removeSession: skipped $conversationId (still in use)")
            return
        }
        if (sessions.remove(conversationId, session)) {
            session.cleanup()
            version.value++
            Log.i(TAG, "removeSession: $conversationId (remaining: ${sessions.size})")
        }
    }

    private companion object {
        const val TAG = "ConversationSessionRegistry"
    }
}
"""
    write("app/src/main/java/me/rerere/rikkahub/service/ConversationSessionRegistry.kt", registry)


def extract_proactive_scheduler() -> None:
    path = "app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt"
    text = read(path)
    request_marker = "        private fun requestCode(base: Int, identity: String): Int = base xor identity.hashCode()"
    build_marker = "    suspend fun buildProactiveContext("
    request_start = text.find(request_marker)
    build_start = text.find(build_marker, request_start)
    if request_start < 0 or build_start < 0:
        raise RuntimeError("Could not locate proactive scheduler region")
    companion_close = text.rfind("\n    }\n\n", request_start, build_start)
    if companion_close < 0:
        raise RuntimeError("Could not locate ProactiveMessageService companion close")

    extracted = text[request_start:companion_close]
    imports = "\n".join(line for line in text.splitlines() if line.startswith("import "))
    scheduler_body = textwrap.indent(textwrap.dedent(extracted), "    ")
    scheduler = f"""package me.rerere.rikkahub.data.service

{imports}

/** Android scheduling, queue recovery and trigger dispatch for proactive turns. */
internal object ProactiveMessageScheduler {{
    private const val TAG = "ProactiveMessageScheduler"
    const val ACTION_PROACTIVE_MESSAGE = "me.rerere.rikkahub.PROACTIVE_MESSAGE"
    private const val REQUEST_CODE = 10001
    private const val TARGETED_REQUEST_CODE = 10002
    private const val RESPONSIBILITY_REVIEW_REQUEST_CODE = 10003

    private const val PREFS_NAME = "proactive_message_prefs"
    private const val KEY_NEXT_TRIGGER_TIME = "next_trigger_time"
    private const val KEY_TARGETED_TRIGGER_TIME = "targeted_trigger_time"
    private const val KEY_TARGETED_REASON = "targeted_reason"
    private const val KEY_TARGETED_USER_TEXT = "targeted_user_text"
    private const val KEY_TARGETED_KIND = "targeted_kind"
    private const val KEY_TARGETED_COMMITMENT_ID = "targeted_commitment_id"
    private const val KEY_TARGETED_QUEUE = "targeted_queue"
    private const val EXTRA_TARGETED_REASON = "targeted_reason"
    private const val EXTRA_TARGETED_USER_TEXT = "targeted_user_text"
    private const val EXTRA_TARGETED_KIND = "targeted_kind"
    private const val EXTRA_COMMITMENT_ID = "commitment_id"
    private const val EXTRA_ASSISTANT_ID = "assistant_id"

{scheduler_body}
}}
"""
    write("app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageScheduler.kt", scheduler)

    wrappers = """        fun scheduleNext(context: Context, setting: ProactiveMessageSetting) =
            ProactiveMessageScheduler.scheduleNext(context, setting)

        fun scheduleNext(
            context: Context,
            settings: Settings,
            minutesSinceLastChat: Long? = null,
            assistantId: Uuid? = null,
        ) = ProactiveMessageScheduler.scheduleNext(context, settings, minutesSinceLastChat, assistantId)

        fun scheduleTargeted(
            context: Context,
            setting: ProactiveMessageSetting,
            triggerAtMillis: Long,
            reason: String,
            userText: String,
            kind: String,
            assistantId: String = setting.assistantId,
            commitmentId: String? = null,
        ) = ProactiveMessageScheduler.scheduleTargeted(
            context, setting, triggerAtMillis, reason, userText, kind, assistantId, commitmentId,
        )

        fun scheduleAlwaysOnAnchorReview(
            context: Context,
            settings: Settings,
            assistantId: Uuid,
            nowMillis: Long = System.currentTimeMillis(),
        ) = ProactiveMessageScheduler.scheduleAlwaysOnAnchorReview(context, settings, assistantId, nowMillis)

        fun scheduleCommitment(
            context: Context,
            setting: ProactiveMessageSetting,
            commitment: CompanionCommitment,
        ) = ProactiveMessageScheduler.scheduleCommitment(context, setting, commitment)

        suspend fun reconcileDurableCommitments(
            context: Context,
            settings: Settings,
            nowMillis: Long = System.currentTimeMillis(),
        ): Boolean = ProactiveMessageScheduler.reconcileDurableCommitments(context, settings, nowMillis)

        fun clearTargetedQueue(context: Context) = ProactiveMessageScheduler.clearTargetedQueue(context)

        fun resetAssistantProjection(context: Context, settings: Settings, assistantId: Uuid) =
            ProactiveMessageScheduler.resetAssistantProjection(context, settings, assistantId)

        internal fun popCurrentTargetedAndScheduleNext(
            context: Context,
            setting: ProactiveMessageSetting,
        ): Boolean = ProactiveMessageScheduler.popCurrentTargetedAndScheduleNext(context, setting)

        fun getNextTriggerTime(context: Context): Long? = ProactiveMessageScheduler.getNextTriggerTime(context)

        fun cancel(context: Context, assistantId: String? = null) =
            ProactiveMessageScheduler.cancel(context, assistantId)

        fun resetTimer(context: Context, setting: ProactiveMessageSetting) =
            ProactiveMessageScheduler.resetTimer(context, setting)

        fun triggerNow(context: Context, setting: ProactiveMessageSetting) =
            ProactiveMessageScheduler.triggerNow(context, setting)
"""
    text = text[:request_start] + wrappers + text[companion_close:]
    write(path, text)


def neutralize_persisted_responsibility_text() -> None:
    path = "app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRuntime.kt"
    text = read(path)
    text = text.replace("humanFacingConcernText()", "neutralResponsibilityText()")

    marker = "    private fun neutralResponsibilityText(): Pair<String, String> = when (category.family()) {"
    start = text.find(marker)
    end = text.find("\n}\n\nfun reconcileCompanionFollowUpDrafts", start)
    if start < 0 or end < 0:
        raise RuntimeError("Could not locate responsibility text mapper")
    neutral_mapper = """    private fun neutralResponsibilityText(): Pair<String, String> = when (category.family()) {
        "wake" -> "起床监督" to "按约定时间执行叫醒并核验完成状态"
        "sleep" -> "睡眠监督" to "按约定条件执行休息提醒并记录结果"
        "study" -> "学习监督" to "按当前计划执行学习跟进并记录状态"
        "health" -> "健康跟进" to "按约定时间核验身体状态"
        "meal" -> "用餐跟进" to "按约定时间核验用餐状态"
        "time" -> "定时事项" to "在约定时间执行对应提醒或动作"
        else -> {
            val family = category.family().ifBlank { "follow_up" }
            "待跟进事项:$family" to "执行待跟进事项:$family"
        }
    }
"""
    text = text[:start] + neutral_mapper + text[end:]
    write(path, text)

    proactive_path = "app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt"
    proactive = read(proactive_path)
    state_start = proactive.find("internal fun buildAutonomousPlanPresenceState(")
    state_end = proactive.find("internal fun nextAlwaysOnAnchorReviewAt(", state_start)
    if state_start < 0 or state_end < 0:
        raise RuntimeError("Could not locate autonomous presence-state mapper")
    neutral_state = """internal fun buildAutonomousPlanPresenceState(
    previous: CompanionState,
    assistantName: String,
    plan: CompanionIntentDecision,
    nowMillis: Long = System.currentTimeMillis(),
): CompanionState {
    val phase = when (plan.intent) {
        CompanionIntent.WAIT -> "等待"
        CompanionIntent.STAY_AVAILABLE -> "待命"
        CompanionIntent.REACH_OUT -> "准备联系"
        CompanionIntent.OBSERVE -> "观察"
        CompanionIntent.FOLLOW_UP -> "待跟进"
        CompanionIntent.SELF_ACTIVITY -> "执行自主活动"
    }
    return previous.copy(
        statusText = phase,
        innerThought = "",
        mindState = "intent:${plan.intent.name.lowercase()}",
        activityMode = when (plan.intent) {
            CompanionIntent.OBSERVE -> "observing"
            CompanionIntent.SELF_ACTIVITY -> "playing"
            else -> "waiting"
        },
        updatedAt = nowMillis,
        sinceAt = nowMillis,
        selfScene = "",
    )
}

"""
    proactive = proactive[:state_start] + neutral_state + proactive[state_end:]
    write(proactive_path, proactive)


def add_neutral_persistence_test() -> None:
    test = """package me.rerere.rikkahub.data.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CompanionNeutralPersistenceTest {
    @Test
    fun durableResponsibilitiesStoreNeutralFactsInsteadOfDefaultPersonaSentences() {
        val draft = CompanionFollowUpDraft(
            assistantId = "role-1",
            category = "wake",
            reason = "明天叫醒",
            sourceText = "以后每天监督我起床",
            dueAt = 1234L,
        )

        val concern = draft.toConcern(nowMillis = 100L)
        val commitment = draft.toCommitment(nowMillis = 100L)
        val stored = listOf(
            concern.event,
            concern.goal,
            commitment.promise,
            commitment.responsibility,
            commitment.actionPlan.userFacingSummary,
        ).joinToString("\n")

        assertEquals("起床监督", concern.event)
        assertEquals("按约定时间执行叫醒并核验完成状态", commitment.responsibility)
        assertFalse(stored.contains("记着"))
        assertFalse(stored.contains("放在心上"))
        assertFalse(stored.contains("你已经醒来"))
    }
}
"""
    write(
        "app/src/test/java/me/rerere/rikkahub/data/companion/CompanionNeutralPersistenceTest.kt",
        test,
    )


def validate_result() -> None:
    chat = read("app/src/main/java/me/rerere/rikkahub/service/ChatService.kt")
    proactive = read("app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt")
    runtime = read("app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRuntime.kt")

    required = {
        "ConversationSessionRegistry": "sessionRegistry" in chat,
        "ProactiveMessageScheduler": "ProactiveMessageScheduler.scheduleNext" in proactive,
        "neutral responsibility mapper": "neutralResponsibilityText" in runtime,
    }
    missing = [name for name, present in required.items() if not present]
    if missing:
        raise RuntimeError("Refactor validation failed: " + ", ".join(missing))
    if "private val sessions = ConcurrentHashMap" in chat:
        raise RuntimeError("ChatService still owns its session map")
    if "private fun requestCode(base: Int" in proactive:
        raise RuntimeError("ProactiveMessageService still owns scheduler implementation")


def main() -> None:
    extract_chat_session_registry()
    extract_proactive_scheduler()
    neutralize_persisted_responsibility_text()
    add_neutral_persistence_test()
    validate_result()


if __name__ == "__main__":
    main()
