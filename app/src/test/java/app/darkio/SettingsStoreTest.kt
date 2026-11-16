package app.darkio

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.darkio.dashboard.DEFAULT_STEP_GOAL
import app.darkio.data.AccentChoice
import app.darkio.data.DEFAULT_WORK_DAYS
import app.darkio.data.DEFAULT_WORK_END_MINUTES
import app.darkio.data.DEFAULT_WORK_START_MINUTES
import app.darkio.data.HomeCard
import app.darkio.data.Settings
import app.darkio.data.markWorkRowSeeded
import app.darkio.data.settings
import app.darkio.data.updateSettings
import app.darkio.data.workRowSeeded
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** F13: indstillinger i DataStore. Manglende nøgler giver defaults, så eksisterende installationer er uændrede. */
class SettingsStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(name: String = "s") = PreferenceDataStoreFactory.create { File(tmp.root, "$name.preferences_pb") }

    @Test
    fun missingKeysGiveDefaults() = runBlocking {
        val s = store().settings().first()
        assertEquals(AccentChoice.Lime, s.accent)
        assertEquals(HomeCard.entries.toSet(), s.cards)
        assertEquals(DEFAULT_STEP_GOAL, s.stepGoal)
        assertEquals(10_000L, s.stepGoal)
        assertEquals(80, s.scrimTop)
        assertEquals(DEFAULT_WORK_DAYS, s.workDays)
        assertEquals(DEFAULT_WORK_START_MINUTES, s.workStartMinutes)
        assertEquals(DEFAULT_WORK_END_MINUTES, s.workEndMinutes)
        org.junit.Assert.assertNull(s.workEndOverrideDate)
        assertEquals(emptySet<Long>(), s.workCalendarIds)
        org.junit.Assert.assertNull(s.workZenRuleId)
    }

    @Test
    fun saveThenReadRoundTrips() = runBlocking {
        val store = store()
        val saved = Settings(AccentChoice.Coral, HomeCard.entries.toSet() - HomeCard.Focus - HomeCard.Steps, 12_500L, 65)
        store.updateSettings { saved }
        assertEquals(saved, store.settings().first())
    }

    @Test
    fun allCardsOffRoundTrips() = runBlocking {
        val store = store()
        store.updateSettings { it.copy(cards = emptySet()) }
        assertEquals(emptySet<HomeCard>(), store.settings().first().cards)
    }

    @Test
    fun unknownAccentIdFallsBackToLime() = runBlocking {
        val store = store()
        store.edit { it[stringPreferencesKey("accent")] = "pink" }
        assertEquals(AccentChoice.Lime, store.settings().first().accent)
    }

    @Test
    fun onlyChangedKeysAreWritten() = runBlocking {
        val store = store()
        store.updateSettings { it.copy(accent = AccentChoice.Sky) }
        assertEquals(setOf("accent"), store.data.first().asMap().keys.map { it.name }.toSet())
    }

    @Test
    fun stepGoalIsKeptInRange() {
        assertEquals(1_000L, Settings().withStepGoal(0L).stepGoal)
        assertEquals(1_000L, Settings().withStepGoal(-500L).stepGoal)
        assertEquals(8_000L, Settings().withStepGoal(8_000L).stepGoal)
        assertEquals(50_000L, Settings().withStepGoal(50_500L).stepGoal)
    }

    @Test
    fun scrimTopIsKeptBetween60And90() {
        assertEquals(60, Settings().withScrimTop(55).scrimTop)
        assertEquals(60, Settings().withScrimTop(0).scrimTop)
        assertEquals(75, Settings().withScrimTop(75).scrimTop)
        assertEquals(90, Settings().withScrimTop(95).scrimTop)
    }

    @Test
    fun scrimTopIsWrittenOnlyWhenChanged() = runBlocking {
        val store = store()
        store.updateSettings { it.withScrimTop(60).copy(accent = AccentChoice.Lime) }
        assertEquals(setOf("scrim_top"), store.data.first().asMap().keys.map { it.name }.toSet())
        assertEquals(60, store.settings().first().scrimTop)
    }

    /** Eksisterende installation: gemt før F21 fandtes, dvs. uden scrim_top. Alt andet overlever, toningen er 80 %. */
    @Test
    fun oldSavedSettingsSurviveAndScrimTopDefaultsTo80() = runBlocking {
        val store = store()
        store.edit {
            it[stringPreferencesKey("accent")] = "Coral"
            it[booleanPreferencesKey("card_Focus")] = false
            it[longPreferencesKey("step_goal")] = 12_500L
        }
        val s = store.settings().first()
        assertEquals(AccentChoice.Coral, s.accent)
        assertEquals(HomeCard.entries.toSet() - HomeCard.Focus, s.cards)
        assertEquals(12_500L, s.stepGoal)
        assertEquals(80, s.scrimTop)
        assertEquals(DEFAULT_WORK_DAYS, s.workDays)
        assertEquals(emptySet<Long>(), s.workCalendarIds)
    }

    @Test
    fun workSettingsSaveAndReadRoundTrips() = runBlocking {
        val store = store()
        val customDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val overrideDate = LocalDate.of(2026, 11, 16)
        val custom = Settings(
            workDays = customDays,
            workStartMinutes = 540, // 09:00
            workEndMinutes = 1020, // 17:00
            workEndOverrideDate = overrideDate,
            workCalendarIds = setOf(42L, 99L),
            workZenRuleId = "rule-123",
        )
        store.updateSettings { custom }
        val read = store.settings().first()
        assertEquals(customDays, read.workDays)
        assertEquals(540, read.workStartMinutes)
        assertEquals(1020, read.workEndMinutes)
        assertEquals(overrideDate, read.workEndOverrideDate)
        assertEquals(setOf(42L, 99L), read.workCalendarIds)
        assertEquals("rule-123", read.workZenRuleId)
    }

    @Test
    fun workRowSeededFlagPersistence() = runBlocking<Unit> {
        val store = store()
        assertFalse("Som standard er workRowSeeded false", store.workRowSeeded().first())
        store.markWorkRowSeeded()
        assertTrue("Efter markWorkRowSeeded er workRowSeeded true", store.workRowSeeded().first())
    }
}
