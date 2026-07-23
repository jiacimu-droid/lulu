package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.voicecall.ProactiveCallManager
import org.koin.core.context.GlobalContext
import kotlin.uuid.Uuid

/**
 * A real character action for explicit requests such as “给我打电话” or “现在打过来”.
 *
 * This is deliberately separate from random proactive-call scheduling: an explicit user
 * invitation may bypass probability, quiet-hour and cooldown gates, while Android's actual
 * notification/full-screen permissions still remain authoritative.
 */
fun createRequestedVoiceCallTool(
    context: Context,
    assistantId: String,
): Tool = Tool(
    name = "place_voice_call",
    description = """
        Place a real incoming voice call from the current character to the user.
        Use only when the user explicitly asks this character to call, phone, ring, or start a voice call now.
        After a successful call, do not merely promise that you will call later and do not claim failure.
        Do not use this for figurative phrases or when the user is only discussing the call feature.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("reason", buildJsonObject {
                    put("type", "string")
                    put("description", "A short in-character reason for this requested call")
                })
            }
        )
    },
    execute = { input ->
        val koin = GlobalContext.get()
        val settings = koin.get<SettingsStore>().settingsFlow.first()
        val parsedAssistantId = runCatching { Uuid.parse(assistantId) }.getOrNull()
        val assistant = parsedAssistantId
            ?.let(settings::getAssistantById)
            ?: settings.getCurrentAssistant()
        val conversationId = koin.get<ConversationRepository>()
            .getConversationsOfAssistant(assistant.id)
            .first()
            .maxByOrNull { it.createAt }
            ?.id
            ?.toString()
            .orEmpty()
        val reason = input.jsonObject["reason"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: "用户明确邀请当前角色现在发起语音通话"
        val offered = ProactiveCallManager.offerIncomingCall(
            context = context,
            assistantId = assistant.id.toString(),
            assistantName = assistant.name.ifBlank { "当前角色" },
            conversationId = conversationId,
            reason = reason,
            setting = assistant.proactiveCallSetting,
            force = true,
        )
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("success", offered)
                    put("status", if (offered) "incoming_call_started" else "incoming_call_blocked")
                    put(
                        "message",
                        if (offered) {
                            "Incoming call started. Continue naturally without promising another call."
                        } else {
                            "Android blocked the incoming-call surface. Check notification, full-screen notification, and background pop-up permissions."
                        },
                    )
                }.toString()
            )
        )
    },
)
