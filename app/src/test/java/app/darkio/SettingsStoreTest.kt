package app.darkio

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.darkio.dashboard.DEFAULT_STEP_GOAL
import app.darkio.data.AccentChoice
import app.darkio.data.HomeCard
import app.darkio.data.Settings
import app.darkio.data.updateSettings
import app.darkio.data.settings
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        assertEquals(8_000L, s.stepGoal)
    }

    @Test
    fun saveThenReadRoundTrips() = runBlocking {
        val store = store()
        val saved = Settings(AccentChoice.Coral, HomeCard.entries.toSet() - HomeCard.Focus - HomeCard.Steps, 12_500L)
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
}
