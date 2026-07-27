package me.rerere.rikkahub.data.service

import me.rerere.rikkahub.data.companion.CompanionConcernStatus
import me.rerere.rikkahub.data.companion.CompanionOutboundStatus
import me.rerere.rikkahub.data.companion.CompanionSnapshot
import me.rerere.rikkahub.data.datastore.ProactiveMessageSetting
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.AssistantInitiativeLevel
import me.rerere.rikkahub.data.model.AssistantInteractionProfile
import me.rerere.rikkahub.data.model.initiativeLevel
import me.rerere.rikkahub.data.model.isBlank
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

data class CompanionAutonomousPulseInput(
    val setting: ProactiveMessageSetting,
    val snapshot: CompanionSnapshot,
    val minutesSinceLastChat: Long,
    val activeTargetedTriggerMillis: Long = 0L,
    val nowMillis: Long = System.currentTimeMillis(),
    val interactionProfile: AssistantInteractionProfile = AssistantInteractionProfile(),
)

data class CompanionAutonomousPulsePlan(
    val delayMinutes: Int,
    val reason: String,
)

object CompanionAutonomousPulsePlanner {
    fun planNext(input: CompanionAutonomousPulseInput): CompanionAutonomousPulsePlan {
        val minMinutes = input.setting.minIntervalMinutes.coerceAtLeast(1)
        val maxMinutes = input.setting.maxIntervalMinutes.coerceAtLeast(minMinutes)
        val targetedDelay = input.activeTargetedTriggerMillis
            .takeIf { it > input.nowMillis }
            ?.let { ((it - input.nowMillis) / 60_000L).toInt().coerceAtLeast(1) }
        if (targetedDelay != null) {
            return CompanionAutonomousPulsePlan(
                delayMinutes = targetedDelay + if (input.setting.naturalScheduling) 15 else minMinutes,
                reason = "targeted_active",
            )
        }

        val activeWorkCount = input.snapshot.activeWorkCount(input.nowMillis)
        val interactionProfile = input.interactionProfile
            .takeUnless { it.isBlank() }
            ?: resolveInteractionProfile(input.snapshot.assistantId)
        val initiativeLevel = interactionProfile.initiativeLevel()
        if (input.setting.naturalScheduling) {
            val latestFeedback = input.snapshot.interactionTimeline.outboundContacts
                .maxByOrNull { it.generatedAt }
                ?.status
            val naturalDelay = when {
                latestFeedback == CompanionOutboundStatus.DECLINED -> 360..720
                latestFeedback == CompanionOutboundStatus.USER_BUSY -> 180..360
                latestFeedback == CompanionOutboundStatus.UNANSWERED -> when (initiativeLevel) {
                    AssistantInitiativeLevel.HIGH -> 90..180
                    AssistantInitiativeLevel.NORMAL -> 120..240
                    AssistantInitiativeLevel.LOW,
                    AssistantInitiativeLevel.NEVER,
                    AssistantInitiativeLevel.UNSPECIFIED -> 180..360
                }
                activeWorkCount > 0 && input.minutesSinceLastChat >= 45 -> 8..18
                activeWorkCount > 0 -> 18..35
                initiativeLevel == AssistantInitiativeLevel.NEVER -> 360..720
                initiativeLevel == AssistantInitiativeLevel.LOW -> 180..360
                initiativeLevel == AssistantInitiativeLevel.HIGH && input.minutesSinceLastChat >= 45 -> 20..45
                initiativeLevel == AssistantInitiativeLevel.HIGH -> 35..70
                initiativeLevel == AssistantInitiativeLevel.NORMAL && input.minutesSinceLastChat >= 90 -> 45..90
                initiativeLevel == AssistantInitiativeLevel.NORMAL -> 75..150
                input.minutesSinceLastChat >= 120 -> 60..120
                else -> 100..180
            }.stableMinute(input)
            return CompanionAutonomousPulsePlan(
                delayMinutes = naturalDelay,
                reason = "natural;initiative=${initiativeLevel.name};feedback=${latestFeedback?.name ?: "none"};${buildReason(input, activeWorkCount)}",
            )
        }
        val desired = when {
            activeWorkCount > 0 -> when {
                input.minutesSinceLastChat >= 45 -> minMinutes - 12
                else -> minMinutes - 5
            }
            input.minutesSinceLastChat >= 120 -> minMinutes - 18
            else -> (minMinutes + maxMinutes) / 2
        }
        return CompanionAutonomousPulsePlan(
            delayMinutes = desired.coerceIn(minOf(8, maxMinutes), maxMinutes),
            reason = buildReason(input, activeWorkCount),
        )
    }

    fun triggerTimeMillis(
        input: CompanionAutonomousPulseInput,
        plan: CompanionAutonomousPulsePlan,
    ): Long = input.nowMillis + TimeUnit.MINUTES.toMillis(plan.delayMinutes.toLong())

    private fun buildReason(input: CompanionAutonomousPulseInput, activeWorkCount: Int): String = buildList {
        when {
            activeWorkCount > 0 -> add("active_work")
            input.minutesSinceLastChat >= 120 -> add("long_silence")
            else -> add("steady_background")
        }
        add("silence=${input.minutesSinceLastChat}")
        add("active=$activeWorkCount")
    }.joinToString(";")

    private fun CompanionSnapshot.activeWorkCount(nowMillis: Long): Int =
        concerns.count { concern ->
            concern.status == CompanionConcernStatus.ACTIVE &&
                concern.nextPerceptionAt?.let { it <= nowMillis } == true
        }

    private fun IntRange.stableMinute(input: CompanionAutonomousPulseInput): Int {
        if (first >= last) return first
        val seed = input.nowMillis / 60_000L +
            input.snapshot.updatedAt / 60_000L +
            input.minutesSinceLastChat.coerceAtMost(10_000L)
        return first + Math.floorMod(seed, (last - first + 1).toLong()).toInt()
    }

    private fun resolveInteractionProfile(assistantId: String): AssistantInteractionProfile = runCatching {
        GlobalContext.get()
            .get<SettingsStore>()
            .settingsFlow
            .value
            .assistants
            .firstOrNull { it.id.toString() == assistantId }
            ?.interactionProfile
    }.getOrNull() ?: AssistantInteractionProfile()
}
