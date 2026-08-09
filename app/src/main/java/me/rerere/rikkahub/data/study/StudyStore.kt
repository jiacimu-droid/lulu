package me.rerere.rikkahub.data.study

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.utils.JsonInstant
import java.time.LocalDate

private val Context.studyDataStore: DataStore<Preferences> by preferencesDataStore(name = "study_reward")

class StudyStore(
    private val context: Context,
    scope: AppScope,
    private val json: Json = JsonInstant,
) {
    private val stateKey = stringPreferencesKey("state")
    private val backupStateKey = stringPreferencesKey("state_backup")

    init {
        scope.launch {
            context.studyDataStore.edit { prefs ->
                val current = readState(prefs) ?: return@edit
                val migrated = current.prepareForCurrentStudySystem()
                if (migrated != current) {
                    prefs.writeState(migrated)
                }
            }
        }
    }

    val state: StateFlow<StudyState> = context.studyDataStore.data
        .map { prefs ->
            readState(prefs) ?: StudyState(today = LocalDate.now().toString())
        }
        .catch { emit(StudyState(today = LocalDate.now().toString())) }
        .map(StudyState::prepareForCurrentStudySystem)
        .stateIn(scope, SharingStarted.Eagerly, StudyState(today = LocalDate.now().toString()))

    suspend fun update(transform: (StudyState) -> StudyState) {
        context.studyDataStore.edit { prefs ->
            val current = readState(prefs) ?: StudyState(today = LocalDate.now().toString())
            val migrated = current.prepareForCurrentStudySystem()
            prefs.writeState(transform(migrated).prepareForCurrentStudySystem())
        }
    }

    suspend fun set(state: StudyState) {
        context.studyDataStore.edit { prefs ->
            prefs.writeState(state.prepareForCurrentStudySystem())
        }
    }

    private fun readState(prefs: Preferences): StudyState? =
        prefs[stateKey]?.let(::decodeStudyStateOrNull)
            ?: prefs[backupStateKey]?.let(::decodeStudyStateOrNull)

    private fun MutablePreferences.writeState(state: StudyState) {
        val encoded = json.encodeToString(state)
        this[stateKey] = encoded
        this[backupStateKey] = encoded
    }
}

internal fun decodeStudyStateOrNull(raw: String): StudyState? {
    val migratedRaw = raw
        .replace("\"UniversalRareFragment\"", "\"TheaterFragment\"")
        .replace("\"UniversalEpicFragment\"", "\"VideoFragment\"")
    return runCatching { studyJson.decodeFromString<StudyState>(migratedRaw) }
        .recoverCatching { error ->
            if (error !is SerializationException && error !is IllegalArgumentException) throw error
            JsonInstant.decodeFromString(migratedRaw)
        }
        .getOrNull()
}

private val studyJson = Json(JsonInstant) {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

private fun StudyState.prepareForCurrentStudySystem(): StudyState = ensureToday()

private fun StudyState.ensureToday(date: LocalDate = LocalDate.now()): StudyState {
    val dateText = date.toString()
    return copy(
        today = dateText,
        tasks = if (today == dateText) tasks else emptyList(),
    )
}
