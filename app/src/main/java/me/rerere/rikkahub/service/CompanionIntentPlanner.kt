package me.rerere.rikkahub.service

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.companion.CompanionPerceptionPacket
import me.rerere.rikkahub.data.companion.toPromptContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.AssistantInteractionProfile
import me.rerere.rikkahub.data.model.toPromptContext
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.core.context.GlobalContext

data class CompanionIntentInput(
    val perception: CompanionPerceptionPacket,
    val mode: CompanionDecisionMode,
    val minutesSinceLastChat: Long,
)

data class CompanionIntentDecision(
    val intent: CompanionIntent,
    val shouldMessageNow: Boolean,
    val delayMinutes: Int?,
    val toolNames: List<String>,
    val reason: String,
    val tone: String,
    val innerThought: String = "",
    val followUps: List<CompanionFollowUpPlan> = emptyList(),
    val category: String? = null,
    val actionToolName: String? = null,
    val actionArgumentsJson: String = "{}",
    val fromModel: Boolean = false,
)

data class CompanionFollowUpPlan(
    val delayMinutes: Int,
    val reason: String,
    val category: String? = null,
)

enum class CompanionIntent {
    FOLLOW_UP,
    STAY_AVAILABLE,
    REACH_OUT,
    OBSERVE,
    SELF_ACTIVITY,
    WAIT,
}

enum class CompanionDecisionMode {
    FOREGROUND,
    BACKGROUND,
}

object CompanionIntentFallbackPlanner {
    fun plan(
        input: CompanionIntentInput,
        interactionProfile: AssistantInteractionProfile = resolveInteractionProfile(input),
    ): CompanionIntentDecision {
        if (input.mode == CompanionDecisionMode.FOREGROUND) {
            return waitDecision("The current turn has no validated follow-up decision; keep existing commitments unchanged.")
        }

        val dueConcern = input.perception.activeConcerns.firstOrNull { concern ->
            concern.nextPerceptionAt != null && concern.nextPerceptionAt <= input.perception.nowMillis
        }
        if (dueConcern != null) {
            return CompanionIntentDecision(
                intent = CompanionIntent.OBSERVE,
                shouldMessageNow = false,
                delayMinutes = 1,
                toolNames = chooseObservationTools(input.perception.availableToolNames),
                reason = "An active concern is due for fresh perception before any expression.",
                tone = interactionProfile.toCompactBehaviorText()
                    .ifBlank { "Follow the persona and current user boundaries." },
                innerThought = "我先重新看清现在的情况，再决定要不要开口。",
                category = "concern",
            )
        }

        val initiativeBand = interactionProfile.initiativeBand()
        if (initiativeBand == InteractionInitiativeBand.NEVER) {
            return waitDecision(
                "The editable interaction profile says this role does not initiate ordinary contact.",
                interactionProfile,
            )
        }
        if (input.hasExplicitDoNotDisturbSignal()) {
            return waitDecision(
                "The user's latest explicit boundary asks for silence or space, which overrides initiative.",
                interactionProfile,
            )
        }
        val reachOutAfterMinutes = when (initiativeBand) {
            InteractionInitiativeBand.HIGH -> 45L
            InteractionInitiativeBand.NORMAL -> 90L
            InteractionInitiativeBand.LOW -> 180L
            InteractionInitiativeBand.NEVER, null -> null
        }
        if (
            reachOutAfterMinutes != null &&
            input.minutesSinceLastChat >= reachOutAfterMinutes
        ) {
            return CompanionIntentDecision(
                intent = CompanionIntent.REACH_OUT,
                shouldMessageNow = true,
                delayMinutes = null,
                toolNames = emptyList(),
                reason = buildString {
                    append("The role's editable interaction profile authorizes a natural reach-out after ")
                    append(input.minutesSinceLastChat)
                    append(" quiet minutes. ")
                    append(interactionProfile.toCompactBehaviorText())
                }.take(720),
                tone = interactionProfile.toCompactBehaviorText().take(500),
                innerThought = "按我被设定的相处方式，这时候主动开口是自然的，不需要假装发生了别的事件。",
                category = "interaction_profile",
            )
        }

        val recentlyPlayed = input.perception.recentLifeEvents.any { event ->
            event.type == me.rerere.rikkahub.data.companion.CompanionLifeEventType.GAME &&
                input.perception.nowMillis - (event.endedAt ?: event.startedAt) < SELF_ACTIVITY_COOLDOWN_MILLIS
        }
        if (
            "play_companion_game" in input.perception.availableToolNames &&
            input.minutesSinceLastChat in SELF_ACTIVITY_MIN_IDLE_MINUTES until DEFAULT_SELF_ACTIVITY_END_MINUTES &&
            !recentlyPlayed
        ) {
            return CompanionIntentDecision(
                intent = CompanionIntent.SELF_ACTIVITY,
                shouldMessageNow = false,
                delayMinutes = null,
                toolNames = emptyList(),
                reason = "The character has quiet time and chose one real app-local activity without interrupting the user.",
                tone = "No user-facing message is needed for this private activity.",
                innerThought = "现在不用打扰你，我想自己去玩一小局，再把真实结果留下来。",
                category = "digital_life",
                actionToolName = "play_companion_game",
                actionArgumentsJson = """{"strategy":"curious"}""",
            )
        }

        return waitDecision(
            if (interactionProfile.initiativeBand() == null) {
                "No editable interaction profile is available, so the conservative fallback does not invent initiative."
            } else {
                "The role's interaction profile does not yet justify contact at this point in its own rhythm."
            },
            interactionProfile,
        )
    }

    private fun waitDecision(
        reason: String,
        interactionProfile: AssistantInteractionProfile = AssistantInteractionProfile(),
    ): CompanionIntentDecision = CompanionIntentDecision(
        intent = CompanionIntent.WAIT,
        shouldMessageNow = false,
        delayMinutes = null,
        toolNames = emptyList(),
        reason = reason,
        tone = interactionProfile.toCompactBehaviorText()
            .ifBlank { "Follow the configured persona without inventing intimacy." },
        innerThought = "现在更符合我的相处方式的是保持安静，等真正适合的时候再开口。",
    )

    private fun chooseObservationTools(available: Set<String>): List<String> = listOf(
        "get_battery_info",
        "get_app_usage",
        "get_gadgetbridge_data",
    ).filter { it in available }

    private const val SELF_ACTIVITY_MIN_IDLE_MINUTES = 45L
    private const val DEFAULT_SELF_ACTIVITY_END_MINUTES = 180L
    private const val SELF_ACTIVITY_COOLDOWN_MILLIS = 6L * 60L * 60L * 1_000L
}

object CompanionIntentModelPlanner {
    suspend fun planOrNull(
        input: CompanionIntentInput,
        settings: Settings,
        model: Model,
        providerManager: ProviderManager,
    ): CompanionIntentDecision? {
        val provider = model.findProvider(settings.providers) ?: return null
        val providerImpl = providerManager.getProviderByType(provider)
        val interactionProfile = settings.assistants
            .firstOrNull { it.id.toString() == input.perception.snapshot.assistantId }
            ?.interactionProfile
            ?: AssistantInteractionProfile()
        val chunk = providerImpl.generateText(
            providerSetting = provider,
            messages = listOf(
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text(buildPrompt(input, interactionProfile))),
                ),
            ),
            params = TextGenerationParams(
                model = model,
                temperature = 0.2f,
                topP = 0.8f,
                maxTokens = 600,
            ),
        )
        val raw = chunk.choices.firstOrNull()?.message?.toText().orEmpty()
        return parsePlan(raw, input.perception.availableToolNames)
            ?.enforceRelationshipPolicy(input.perception.snapshot.relationship)
    }

    fun buildPrompt(
        input: CompanionIntentInput,
        interactionProfile: AssistantInteractionProfile = AssistantInteractionProfile(),
    ): String = buildString {
        appendLine("You are the background decision layer for ${input.perception.assistantName.ifBlank { "the current role" }}.")
        appendLine("Decide only the next intention and perception point. Do not generate user-facing message text.")
        appendLine("The role persona and the user-editable interaction_profile are authoritative. Never impose one default amount of warmth, initiative, attachment, supervision, or silence on every role.")
        appendLine("When interaction_profile is present, use it as the explicit behavioral contract for initiative, sharing, responsibility, follow-up, and passivity. Do not override it merely because the role is labelled lover, housekeeper, friend, cold, or caring.")
        appendLine("A role whose interaction_profile says it never initiates or normally waits for the user should usually WAIT. A role whose profile says it actively checks in, shares spontaneously, follows responsibilities, or often initiates may REACH_OUT because a persona-consistent quiet interval has passed; it does not need a fabricated external event.")
        appendLine("Use evidence from the unified runtime snapshot. Existing commitments are durable and ordinary chat never cancels them.")
        appendLine("responsibility_anchors are duties the character must actively remember, separate from private_impression and optional recall. Check their triggers on every decision and either execute a real available action, schedule the next evidence check, or consciously wait; never merely repeat the anchor text.")
        appendLine("A model may propose changes, but deterministic reducers validate identity, time, transitions, and relationship deltas.")
        appendLine("Return JSON only with: intent, shouldMessageNow, delayMinutes, toolNames, reason, tone, innerThought, category, followUps, actionToolName, actionArguments.")
        appendLine("intent must be one of FOLLOW_UP, STAY_AVAILABLE, REACH_OUT, OBSERVE, SELF_ACTIVITY, WAIT.")
        appendLine("SELF_ACTIVITY means silently doing one real App-local action. It requires an available actionToolName and JSON object actionArguments; never invent completion and do not send a user-facing message merely to narrate it.")
        appendLine("Do not repeat the same SELF_ACTIVITY when recent_digital_life already contains it within roughly six hours.")
        appendLine("followUps contains objects with delayMinutes, reason, and optional category. Schedule only useful next perception points, and follow the profile's follow-up limits when the user has not answered.")
        appendLine("Elapsed silence is context. It may be a valid reason to REACH_OUT only when the editable interaction profile makes such initiative natural; otherwise it is not enough by itself.")
        appendLine("The user decides immediate boundaries through what they say. If recent conversation says they are busy, unavailable, need space, cannot chat for a while, resting, or asleep, that newer explicit preference overrides initiative. Prefer WAIT, STAY_AVAILABLE, a later evidence check, or silent SELF_ACTIVITY; do not make a guilt-inducing check-in.")
        appendLine("When the user is temporarily busy or unavailable, prefer STAY_AVAILABLE with shouldMessageNow=false and a thoughtful delayMinutes for the next perception rather than a near-term generic pulse. Let the duration follow what the user said and the interaction profile; do not fabricate an exact schedule.")
        appendLine("Use local time, recent conversation, sleep/health facts, routine, commitments, and interaction_profile as context. Do not impose a universal night curfew or daily message quota. At night, weigh the profile and real responsibilities carefully, while respecting explicit sleep or silence requests.")
        appendLine("The role owns its rhythm. Your job is to make a reasoned decision from its configured interaction behavior and current evidence, not to obey one mechanical frequency rule.")
        appendLine("Never prewrite a future message. Never increase closeness merely because time passed.")
        appendLine("Formal diary writing requires the explicit write_lulu_journal tool and new concrete content.")
        appendLine("<decision_mode>${input.mode.name}</decision_mode>")
        appendLine("<minutes_since_last_chat>${input.minutesSinceLastChat}</minutes_since_last_chat>")
        interactionProfile.toPromptContext().takeIf(String::isNotBlank)?.let {
            appendLine(it)
        }
        appendLine("<persona>")
        appendLine(input.perception.persona)
        appendLine("</persona>")
        appendLine(input.perception.toPromptContext())
        if (input.perception.memoryContext.isNotBlank()) {
            appendLine("<memory_context>")
            appendLine(input.perception.memoryContext)
            appendLine("</memory_context>")
        }
        appendLine("<recent_conversation>")
        input.perception.recentTurns.takeLast(8).forEach { turn ->
            appendLine("${turn.role.name}: ${turn.content.take(500)}")
        }
        appendLine("</recent_conversation>")
        appendLine("<available_tools>${input.perception.availableToolNames.joinToString(", ")}</available_tools>")
    }

    fun parsePlan(
        rawText: String,
        availableToolNames: Set<String>,
    ): CompanionIntentDecision? {
        val root = runCatching {
            JsonInstant.parseToJsonElement(rawText.extractCompanionJsonPayload())
        }.getOrNull() as? JsonObject ?: return null
        val intent = root.string("intent")?.toCompanionIntent() ?: return null
        val actionToolName = root.string("actionToolName")
            ?.trim()
            ?.takeIf { it in availableToolNames }
        if (intent == CompanionIntent.SELF_ACTIVITY && actionToolName == null) return null
        val shouldMessageNow = root["shouldMessageNow"]?.jsonPrimitive?.booleanOrNull
            ?: (intent == CompanionIntent.REACH_OUT)
        val delayMinutes = root["delayMinutes"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 24 * 60)
        val toolNames = (root["toolNames"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
            ?.filter { it in availableToolNames }
            ?.distinct()
            ?.take(5)
            .orEmpty()
        return CompanionIntentDecision(
            intent = intent,
            shouldMessageNow = shouldMessageNow,
            delayMinutes = delayMinutes,
            toolNames = toolNames,
            reason = root.string("reason")
                ?.sanitizeCompanionPlanText(240)
                ?: "The model selected the next companion action from current evidence.",
            tone = root.string("tone")
                ?.sanitizeCompanionPlanText(100)
                ?: "Follow the configured persona and interaction profile.",
            innerThought = root.string("innerThought")
                ?.cleanCompanionInnerThought()
                ?: root.string("inner_thought")?.cleanCompanionInnerThought()
                ?: fallbackCompanionInnerThought(intent),
            followUps = parseFollowUps(root),
            category = root.string("category")?.trim()?.take(60)?.takeIf(String::isNotBlank),
            actionToolName = actionToolName,
            actionArgumentsJson = (root["actionArguments"] as? JsonObject)?.toString()
                ?: root.string("actionArgumentsJson")?.take(1_200)
                ?: "{}",
            fromModel = true,
        )
    }

    private fun parseFollowUps(root: JsonObject): List<CompanionFollowUpPlan> =
        (root["followUps"] as? JsonArray)
            ?.mapNotNull { item ->
                val plan = item as? JsonObject ?: return@mapNotNull null
                val delay = plan["delayMinutes"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 24 * 60)
                    ?: return@mapNotNull null
                val reason = plan.string("reason")
                    ?.sanitizeCompanionPlanText(180)
                    ?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                CompanionFollowUpPlan(
                    delayMinutes = delay,
                    reason = reason,
                    category = plan.string("category")
                        ?: plan.string("kind"),
                )
            }
            ?.distinctBy { it.delayMinutes to it.reason }
            ?.take(5)
            .orEmpty()

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull
}

private enum class InteractionInitiativeBand {
    NEVER,
    LOW,
    NORMAL,
    HIGH,
}

private fun resolveInteractionProfile(input: CompanionIntentInput): AssistantInteractionProfile = runCatching {
    GlobalContext.get()
        .get<SettingsStore>()
        .settingsFlow
        .value
        .assistants
        .firstOrNull { it.id.toString() == input.perception.snapshot.assistantId }
        ?.interactionProfile
}.getOrNull() ?: AssistantInteractionProfile()

private fun AssistantInteractionProfile.initiativeBand(): InteractionInitiativeBand? {
    val text = listOf(initiative, sharingDesire, responsibility, followUpStyle, passivity)
        .joinToString("\n")
        .lowercase()
    if (text.isBlank()) return null
    if (text.containsAny(
            "绝不主动", "从不主动", "不会主动联系", "不主动找", "只等用户", "等待用户先开口",
            "用户先开口才", "never initiate", "never reach out", "will not initiate",
        )
    ) return InteractionInitiativeBand.NEVER
    if (text.containsAny(
            "很少主动", "极少主动", "偶尔才主动", "不轻易主动", "通常不主动", "克制地主动",
            "rarely initiates", "seldom initiates", "low initiative",
        )
    ) return InteractionInitiativeBand.LOW
    if (text.containsAny(
            "经常主动", "会主动联系", "主动关心", "主动询问", "主动确认", "频繁联系", "分享欲强",
            "主动分享", "及时跟进", "会继续追问", "积极照看", "主动监督", "high initiative",
            "often initiates", "frequently reaches out",
        )
    ) return InteractionInitiativeBand.HIGH
    return InteractionInitiativeBand.NORMAL
}

private fun AssistantInteractionProfile.toCompactBehaviorText(): String = listOfNotNull(
    initiative.trim().takeIf(String::isNotBlank)?.let { "主动意愿：$it" },
    sharingDesire.trim().takeIf(String::isNotBlank)?.let { "分享欲：$it" },
    responsibility.trim().takeIf(String::isNotBlank)?.let { "责任感：$it" },
    followUpStyle.trim().takeIf(String::isNotBlank)?.let { "追问：$it" },
    passivity.trim().takeIf(String::isNotBlank)?.let { "被动：$it" },
).joinToString("；")

private fun CompanionIntentInput.hasExplicitDoNotDisturbSignal(): Boolean {
    val latestUserText = perception.recentTurns
        .asReversed()
        .firstOrNull { it.role.name == "USER" }
        ?.content
        ?.lowercase()
        .orEmpty()
    return latestUserText.containsAny(
        "别打扰", "不要打扰", "先别联系", "不要联系", "等我找你", "需要空间", "让我一个人",
        "稍后再聊", "不能聊", "我睡了", "睡觉了", "休息了", "do not disturb", "don't contact",
        "need space", "talk later",
    )
}

private fun String.containsAny(vararg markers: String): Boolean = markers.any { it.lowercase() in this }

private fun String.toCompanionIntent(): CompanionIntent? = when (trim().uppercase()) {
    "FOLLOW_UP", "CARE_REMINDER" -> CompanionIntent.FOLLOW_UP
    "STAY_AVAILABLE", "STAY_NEAR" -> CompanionIntent.STAY_AVAILABLE
    "REACH_OUT" -> CompanionIntent.REACH_OUT
    "OBSERVE", "CHECK_CONTEXT" -> CompanionIntent.OBSERVE
    "SELF_ACTIVITY", "PRIVATE_ACTIVITY", "PLAY" -> CompanionIntent.SELF_ACTIVITY
    "WAIT", "DO_NOT_DISTURB" -> CompanionIntent.WAIT
    else -> null
}

private fun fallbackCompanionInnerThought(intent: CompanionIntent): String = when (intent) {
    CompanionIntent.FOLLOW_UP -> "我把这件明确的后续记住，到点会重新看当时的真实情况。"
    CompanionIntent.STAY_AVAILABLE -> "我先不打断，把注意留在这里，等下一次有意义的变化。"
    CompanionIntent.REACH_OUT -> "安静了一阵，我想按自己的互动设定自然开口。"
    CompanionIntent.OBSERVE -> "我先重新看清上下文，再决定行动和表达。"
    CompanionIntent.SELF_ACTIVITY -> "现在不用打扰你，我想自己做一件真实的小事。"
    CompanionIntent.WAIT -> "现在更符合我的相处方式的是保持安静。"
}

private fun String.cleanCompanionInnerThought(): String? {
    val compact = trim()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .take(180)
    if (compact.isBlank()) return null
    val forbidden = listOf("Seven-layer trace", "tool_result", "requested_tools=", "{", "}")
    return compact.takeUnless { text -> forbidden.any { text.contains(it, ignoreCase = true) } }
}

private fun String.sanitizeCompanionPlanText(maxLength: Int): String = lineSequence()
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .joinToString(" ")
    .take(maxLength)

private fun String.extractCompanionJsonPayload(): String {
    val trimmed = trim()
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed
    val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
        .find(trimmed)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    if (!fenced.isNullOrBlank()) return fenced
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    return if (start >= 0 && end >= start) trimmed.substring(start, end + 1) else trimmed
}
