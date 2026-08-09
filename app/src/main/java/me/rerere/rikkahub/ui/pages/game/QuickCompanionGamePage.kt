package me.rerere.rikkahub.ui.pages.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.companion.CompanionLifeEvent
import me.rerere.rikkahub.data.companion.CompanionLifeEventSource
import me.rerere.rikkahub.data.companion.CompanionLifeEventStatus
import me.rerere.rikkahub.data.companion.CompanionLifeEventType
import me.rerere.rikkahub.data.companion.CompanionRuntime
import me.rerere.rikkahub.data.companion.CompanionTurnMutation
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCompanionGamePage(gameId: String) {
    val game = remember(gameId) { QuickCompanionGame.fromWireName(gameId) }
    val settings = LocalSettings.current
    val companionRuntime = koinInject<CompanionRuntime>()
    val settingsStore = koinInject<SettingsStore>()
    val scope = rememberCoroutineScope()
    var selectedAssistantId by remember(settings.assistantId, settings.assistants) {
        mutableStateOf(settings.assistantId)
    }
    val selectedAssistant = settings.assistants.firstOrNull { it.id == selectedAssistantId }
        ?: settings.assistants.firstOrNull()
        ?: Assistant()

    fun recordGame(title: String, summary: String, detailsJson: String) {
        if (selectedAssistant.name.isBlank()) return
        val now = System.currentTimeMillis()
        scope.launch {
            companionRuntime.applyTurn(
                CompanionTurnMutation(
                    assistantId = selectedAssistant.id.toString(),
                    lifeEvents = listOf(
                        CompanionLifeEvent(
                            id = "shared-game:${game.wireName}:${selectedAssistant.id}:$now",
                            assistantId = selectedAssistant.id.toString(),
                            type = CompanionLifeEventType.GAME,
                            status = CompanionLifeEventStatus.COMPLETED,
                            title = title,
                            summary = summary,
                            source = CompanionLifeEventSource.CHAT,
                            evidenceReference = "shared-game:${game.wireName}:$now",
                            detailsJson = detailsJson,
                            importance = 3,
                            startedAt = now,
                            endedAt = now,
                            createdAt = now,
                        ),
                    ),
                    nowMillis = now,
                ),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton() },
                title = { Text(game.title) },
                colors = CustomColors.topBarColors,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (settings.assistants.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(settings.assistants, key = { it.id }) { assistant ->
                        FilterChip(
                            selected = assistant.id == selectedAssistant.id,
                            onClick = {
                                selectedAssistantId = assistant.id
                                scope.launch { settingsStore.updateAssistant(assistant.id) }
                            },
                            label = { Text(assistant.name.ifBlank { "未命名角色" }) },
                        )
                    }
                }
            }
            when (game) {
                QuickCompanionGame.YACHT_DICE -> YachtDiceGame(
                    assistantName = selectedAssistant.name.ifBlank { "角色" },
                    onRoundCompleted = { round, userCategory, roleCategory, userScore, roleScore, outcome, userTotal, roleTotal ->
                        val summary = "快艇骰子第 $round 轮：用户 $userScore 分，角色 $roleScore 分；$outcome。"
                        recordGame(
                            title = "一起完成一轮快艇骰子",
                            summary = summary,
                            detailsJson = buildJsonObject {
                                put("game", "yacht_dice")
                                put("round", round)
                                put("user_category", userCategory)
                                put("role_category", roleCategory)
                                put("user_score", userScore)
                                put("role_score", roleScore)
                                put("user_total", userTotal)
                                put("role_total", roleTotal)
                                put("outcome", outcome)
                            }.toString(),
                        )
                    },
                )
                QuickCompanionGame.GOMOKU -> GomokuGame(
                    assistantName = selectedAssistant.name.ifBlank { "角色" },
                    onCompleted = { outcome, moves, board ->
                        recordGame(
                            title = "一起玩完一局五子棋",
                            summary = "用户执黑、角色执白完成一局五子棋，共走 $moves 手，结果：$outcome。",
                            detailsJson = buildJsonObject {
                                put("game", "gomoku")
                                put("outcome", outcome)
                                put("moves", moves)
                                put("board", board.joinToString(","))
                            }.toString(),
                        )
                    },
                )
            }
        }
    }
}

internal enum class QuickCompanionGame(val wireName: String, val title: String) {
    YACHT_DICE("yacht_dice", "快艇骰子"),
    GOMOKU("gomoku", "五子棋");

    companion object {
        fun fromWireName(value: String): QuickCompanionGame = when (value) {
            "yacht_dice" -> YACHT_DICE
            else -> GOMOKU
        }
    }
}
