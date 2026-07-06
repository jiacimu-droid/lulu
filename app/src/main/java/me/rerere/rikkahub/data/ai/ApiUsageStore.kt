package me.rerere.rikkahub.data.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.ai.core.TokenUsage
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

private val Context.apiUsageDataStore: DataStore<Preferences> by preferencesDataStore(name = "api_usage")

@Serializable
data class ApiUsageState(
    val records: List<ApiUsageRecord> = emptyList(),
)

@Serializable
data class ApiUsageRecord(
    val id: String = Uuid.random().toString(),
    val source: ApiUsageSource,
    val title: String = "",
    val model: String = "",
    val provider: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val cachedTokens: Long = 0,
)

@Serializable
enum class ApiUsageSource(val label: String) {
    @SerialName("chat")
    CHAT("聊天"),

    @SerialName("phone")
    PHONE("电话"),

    @SerialName("game")
    GAME("游戏"),

    @SerialName("other")
    OTHER("其他"),
}

data class ApiUsageSummary(
    val source: ApiUsageSource,
    val promptTokens: Long,
    val completionTokens: Long,
    val cachedTokens: Long,
    val callCount: Int,
) {
    val cacheRate: Float
        get() = if (promptTokens > 0) cachedTokens.toFloat() / promptTokens.toFloat() else 0f
}

fun List<ApiUsageRecord>.summarizeApiUsage(): List<ApiUsageSummary> =
    groupBy { it.source }
        .map { (source, records) ->
            ApiUsageSummary(
                source = source,
                promptTokens = records.sumOf { it.promptTokens },
                completionTokens = records.sumOf { it.completionTokens },
                cachedTokens = records.sumOf { it.cachedTokens },
                callCount = records.size,
            )
        }
        .sortedBy { it.source.ordinal }

class ApiUsageStore(
    private val context: Context,
    scope: AppScope,
    private val json: Json = JsonInstant,
) {
    private val stateKey = stringPreferencesKey("state")

    val state: StateFlow<ApiUsageState> = context.apiUsageDataStore.data
        .map { prefs ->
            prefs[stateKey]?.let { raw ->
                runCatching { json.decodeFromString<ApiUsageState>(raw) }.getOrDefault(ApiUsageState())
            } ?: ApiUsageState()
        }
        .catch { emit(ApiUsageState()) }
        .stateIn(scope, SharingStarted.Eagerly, ApiUsageState())

    suspend fun record(
        source: ApiUsageSource,
        title: String,
        model: String,
        provider: String,
        usage: TokenUsage,
    ) {
        if (usage.promptTokens <= 0 && usage.completionTokens <= 0 && usage.cachedTokens <= 0) return
        update { state ->
            state.copy(
                records = (
                    listOf(
                        ApiUsageRecord(
                            source = source,
                            title = title,
                            model = model,
                            provider = provider,
                            promptTokens = usage.promptTokens.toLong(),
                            completionTokens = usage.completionTokens.toLong(),
                            cachedTokens = usage.cachedTokens.toLong(),
                        )
                    ) + state.records
                ).take(API_USAGE_RECORD_LIMIT)
            )
        }
    }

    private suspend fun update(transform: (ApiUsageState) -> ApiUsageState) {
        context.apiUsageDataStore.edit { prefs ->
            val current = prefs[stateKey]?.let { raw ->
                runCatching { json.decodeFromString<ApiUsageState>(raw) }.getOrDefault(ApiUsageState())
            } ?: ApiUsageState()
            prefs[stateKey] = json.encodeToString(transform(current))
        }
    }

    private companion object {
        const val API_USAGE_RECORD_LIMIT = 500
    }
}
