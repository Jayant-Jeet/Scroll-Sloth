package com.reelsloth.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.reelsloth.ai.Tone
import com.reelsloth.score.ScoreBands
import com.reelsloth.score.ScoreConfig
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SettingsStore(private val dataStore: DataStore<Preferences>) {
    val onboardingComplete: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[ONBOARDING_COMPLETE] ?: false }

    val inspectorMode: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[INSPECTOR_MODE] ?: false }

    /** First day of the week for weekly stats. Monday unless changed. */
    val weekStart: Flow<DayOfWeek> = dataStore.data
        .map { preferences -> preferences[WEEK_START]?.takeIf { it in 1..7 }?.let(DayOfWeek::of) ?: DayOfWeek.MONDAY }
        .distinctUntilChanged()

    /** Scoring thresholds and band labels. Any stored value that is missing or invalid falls back to its default. */
    val scoreConfig: Flow<ScoreConfig> = dataStore.data
        .map { preferences -> preferences.toScoreConfig() }
        .distinctUntilChanged()

    /** Commentary tone. Gentle unless changed. */
    val commentaryTone: Flow<Tone> = dataStore.data
        .map { preferences -> Tone.entries.firstOrNull { it.name == preferences[COMMENTARY_TONE] } ?: Tone.GENTLE }
        .distinctUntilChanged()

    /** True while the user has paused counting. */
    val trackingPaused: Flow<Boolean> = dataStore.data.map { it[TRACKING_PAUSED] ?: false }.distinctUntilChanged()

    /** When the app last asked the rules server for an update (epoch ms), whatever the answer. */
    val rulesLastCheckedMs: Flow<Long?> = dataStore.data.map { it[RULES_LAST_CHECKED] }.distinctUntilChanged()

    suspend fun setTrackingPaused(paused: Boolean) {
        dataStore.edit { preferences -> preferences[TRACKING_PAUSED] = paused }
    }

    suspend fun setRulesLastCheckedMs(timeMs: Long) {
        dataStore.edit { preferences -> preferences[RULES_LAST_CHECKED] = timeMs }
    }

    /** Rules server address the user set, or null for the build default. An empty string turns updates off. */
    val rulesUrlOverride: Flow<String?> = dataStore.data.map { it[RULES_URL] }.distinctUntilChanged()

    /** The address to fetch rules from: the user's, or [default]. */
    fun rulesUrl(default: String): Flow<String> = rulesUrlOverride.map { it ?: default }.distinctUntilChanged()

    /** Pass null to go back to the build default. */
    suspend fun setRulesUrlOverride(url: String?) {
        dataStore.edit { preferences ->
            if (url == null) preferences.remove(RULES_URL) else preferences[RULES_URL] = url.trim()
        }
    }

    /** True once the user asked for the language model, until they delete it. */
    val modelWanted: Flow<Boolean> = dataStore.data.map { it[MODEL_WANTED] ?: false }.distinctUntilChanged()

    /** The DownloadManager id of the model download in progress, if any. */
    val modelDownloadId: Flow<Long?> = dataStore.data.map { it[MODEL_DOWNLOAD_ID] }.distinctUntilChanged()

    /** Whether the model may download over mobile data. Wi-Fi only unless changed. */
    val modelAllowMetered: Flow<Boolean> = dataStore.data.map { it[MODEL_ALLOW_METERED] ?: false }.distinctUntilChanged()

    suspend fun setCommentaryTone(tone: Tone) {
        dataStore.edit { preferences -> preferences[COMMENTARY_TONE] = tone.name }
    }

    suspend fun setModelWanted(wanted: Boolean) {
        dataStore.edit { preferences -> preferences[MODEL_WANTED] = wanted }
    }

    suspend fun setModelDownloadId(id: Long?) {
        dataStore.edit { preferences -> if (id == null) preferences.remove(MODEL_DOWNLOAD_ID) else preferences[MODEL_DOWNLOAD_ID] = id }
    }

    suspend fun setModelAllowMetered(allow: Boolean) {
        dataStore.edit { preferences -> preferences[MODEL_ALLOW_METERED] = allow }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETE] = complete
        }
    }

    suspend fun setInspectorMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[INSPECTOR_MODE] = enabled
        }
    }

    suspend fun setWeekStart(day: DayOfWeek) {
        dataStore.edit { preferences -> preferences[WEEK_START] = day.value }
    }

    suspend fun setScoreConfig(config: ScoreConfig) {
        dataStore.edit { preferences ->
            preferences[HEAVY_DAILY] = config.heavyDaily
            preferences[DAILY_MINUTES_BUDGET] = config.dailyMinutesBudget
            preferences[SESSION_HEAVY] = config.sessionHeavy
            preferences[LATE_SHARE_HEAVY] = config.lateShareHeavy
            BAND_LABELS.forEachIndexed { index, key -> preferences[key] = config.bandLabels[index] }
        }
    }

    suspend fun resetScoreConfig() {
        dataStore.edit { preferences ->
            (listOf(HEAVY_DAILY, DAILY_MINUTES_BUDGET, SESSION_HEAVY, LATE_SHARE_HEAVY) + BAND_LABELS)
                .forEach { preferences.remove(it) }
        }
    }

    private fun Preferences.toScoreConfig(): ScoreConfig {
        val defaults = ScoreConfig()
        fun <T> valid(value: T?, default: T, check: (T) -> Boolean): T = value?.takeIf(check) ?: default
        return ScoreConfig(
            heavyDaily = valid(this[HEAVY_DAILY], defaults.heavyDaily) { it in 1..ScoreConfig.MAX_COUNT },
            dailyMinutesBudget = valid(this[DAILY_MINUTES_BUDGET], defaults.dailyMinutesBudget) {
                it in 1..ScoreConfig.MAX_MINUTES
            },
            sessionHeavy = valid(this[SESSION_HEAVY], defaults.sessionHeavy) { it in 1..ScoreConfig.MAX_COUNT },
            lateShareHeavy = valid(this[LATE_SHARE_HEAVY], defaults.lateShareHeavy) { it > 0.0 && it <= 1.0 },
            bandLabels = BAND_LABELS.mapIndexed { index, key ->
                valid(this[key], ScoreBands.DEFAULT_LABELS[index]) { it.isNotBlank() }
            },
        )
    }

    private companion object {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val INSPECTOR_MODE = booleanPreferencesKey("inspector_mode")
        val WEEK_START = intPreferencesKey("week_start")
        val HEAVY_DAILY = intPreferencesKey("score_heavy_daily")
        val DAILY_MINUTES_BUDGET = intPreferencesKey("score_daily_minutes_budget")
        val SESSION_HEAVY = intPreferencesKey("score_session_heavy")
        val LATE_SHARE_HEAVY = doublePreferencesKey("score_late_share_heavy")
        val COMMENTARY_TONE = stringPreferencesKey("commentary_tone")
        val MODEL_WANTED = booleanPreferencesKey("model_wanted")
        val MODEL_DOWNLOAD_ID = longPreferencesKey("model_download_id")
        val MODEL_ALLOW_METERED = booleanPreferencesKey("model_allow_metered")
        val TRACKING_PAUSED = booleanPreferencesKey("tracking_paused")
        val RULES_LAST_CHECKED = longPreferencesKey("rules_last_checked_ms")
        val RULES_URL = stringPreferencesKey("rules_url")
        val BAND_LABELS = (0 until ScoreBands.COUNT).map { stringPreferencesKey("score_band_label_$it") }
    }
}
