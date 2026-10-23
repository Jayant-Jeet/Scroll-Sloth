package com.halfbloodcoder.scrollsloth.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.halfbloodcoder.scrollsloth.score.ScoreConfig
import java.time.DayOfWeek
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun onboardingCompletionDefaultsToFalseAndCanBeSaved() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { temporaryFolder.root.resolve("settings.preferences_pb") },
        )
        val settingsStore = SettingsStore(dataStore)

        settingsStore.onboardingComplete.test {
            assertEquals(false, awaitItem())
            settingsStore.setOnboardingComplete(true)
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rulesUrlFallsBackToTheBuildDefaultAndCanBeTurnedOff() = runTest {
        val settingsStore = SettingsStore(InMemoryPreferencesDataStore())
        val default = "https://example.com/default.json"

        assertEquals(default, settingsStore.rulesUrl(default).first())
        settingsStore.setRulesUrlOverride("  https://example.com/mine.json ")
        assertEquals("https://example.com/mine.json", settingsStore.rulesUrl(default).first())
        settingsStore.setRulesUrlOverride("")
        assertEquals("", settingsStore.rulesUrl(default).first())
        settingsStore.setRulesUrlOverride(null)
        assertEquals(default, settingsStore.rulesUrl(default).first())
    }

    @Test
    fun trackingPauseAndRulesCheckTimeAreStored() = runTest {
        val settingsStore = SettingsStore(InMemoryPreferencesDataStore())

        assertEquals(false, settingsStore.trackingPaused.first())
        assertEquals(null, settingsStore.rulesLastCheckedMs.first())

        settingsStore.setTrackingPaused(true)
        settingsStore.setRulesLastCheckedMs(1_234)

        assertEquals(true, settingsStore.trackingPaused.first())
        assertEquals(1_234L, settingsStore.rulesLastCheckedMs.first())
    }

    @Test
    fun rulesUpdateStatusSurvivesStoreRecreation() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val settings = SettingsStore(dataStore)
        assertEquals(null, settings.rulesUpdateStatus.first())
        settings.setRulesUpdateStatus("Signature check failed: invalid signature or public key.")
        assertEquals(
            "Signature check failed: invalid signature or public key.",
            SettingsStore(dataStore).rulesUpdateStatus.first(),
        )
    }

    @Test
    fun inspectorModeDefaultsToOffAndCanBeToggled() = runTest {
        // In-memory store: file-backed DataStore cannot overwrite its file on Windows hosts.
        val settingsStore = SettingsStore(InMemoryPreferencesDataStore())

        settingsStore.inspectorMode.test {
            assertEquals(false, awaitItem())
            settingsStore.setInspectorMode(true)
            assertEquals(true, awaitItem())
            settingsStore.setInspectorMode(false)
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun weekStartDefaultsToMondayAndCanBeChanged() = runTest {
        val settingsStore = SettingsStore(InMemoryPreferencesDataStore())

        settingsStore.weekStart.test {
            assertEquals(DayOfWeek.MONDAY, awaitItem())
            settingsStore.setWeekStart(DayOfWeek.SUNDAY)
            assertEquals(DayOfWeek.SUNDAY, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun scoreConfigDefaultsSavesAndResets() = runTest {
        val settingsStore = SettingsStore(InMemoryPreferencesDataStore())
        val custom = ScoreConfig(
            heavyDaily = 120,
            dailyMinutesBudget = 45,
            sessionHeavy = 60,
            lateShareHeavy = 0.3,
            bandLabels = listOf("Alert", "Drifting", "Slumped", "Comatose", "Mineral"),
        )

        settingsStore.scoreConfig.test {
            assertEquals(ScoreConfig(), awaitItem())
            settingsStore.setScoreConfig(custom)
            assertEquals(custom, awaitItem())
            settingsStore.resetScoreConfig()
            assertEquals(ScoreConfig(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun invalidStoredValuesFallBackToDefaultsOneByOne() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        dataStore.edit {
            it[intPreferencesKey("score_heavy_daily")] = 0
            it[intPreferencesKey("score_session_heavy")] = 80
            it[doublePreferencesKey("score_late_share_heavy")] = 4.0
            it[stringPreferencesKey("score_band_label_2")] = "  "
            it[stringPreferencesKey("score_band_label_4")] = "Rock"
            it[intPreferencesKey("week_start")] = 9
        }
        val settingsStore = SettingsStore(dataStore)

        val config = settingsStore.scoreConfig.first()
        assertEquals(ScoreConfig.DEFAULT_HEAVY_DAILY, config.heavyDaily)
        assertEquals(80, config.sessionHeavy)
        assertEquals(ScoreConfig.DEFAULT_LATE_SHARE_HEAVY, config.lateShareHeavy, 0.0)
        assertEquals(listOf("Fully awake", "Casual scroller", "Sofa sloth", "Reel hibernator", "Rock"), config.bandLabels)
        assertEquals(DayOfWeek.MONDAY, settingsStore.weekStart.first())
    }

    @Test
    fun themeModeDefaultsToSystemAndIsStored() = runTest {
        val settingsStore = SettingsStore(InMemoryPreferencesDataStore())

        settingsStore.themeMode.test {
            assertEquals(ThemeMode.SYSTEM, awaitItem())
            settingsStore.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, awaitItem())
            settingsStore.setThemeMode(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unknownStoredThemeModeFallsBackToSystem() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        dataStore.edit { it[stringPreferencesKey("theme_mode")] = "SEPIA" }

        assertEquals(ThemeMode.SYSTEM, SettingsStore(dataStore).themeMode.first())
    }

    @Test
    fun resolveDarkThemeFollowsTheSettingThenThePhone() {
        assertEquals(false, resolveDarkTheme(ThemeMode.LIGHT, systemDark = true))
        assertEquals(true, resolveDarkTheme(ThemeMode.DARK, systemDark = false))
        assertEquals(true, resolveDarkTheme(ThemeMode.SYSTEM, systemDark = true))
        assertEquals(false, resolveDarkTheme(ThemeMode.SYSTEM, systemDark = false))
    }
}

private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(state.value).also { state.value = it } }
}
