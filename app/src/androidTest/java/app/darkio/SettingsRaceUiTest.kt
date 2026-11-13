package app.darkio

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.data.AccentChoice
import app.darkio.data.HomeCard
import app.darkio.data.Settings
import app.darkio.data.updateSettings
import app.darkio.data.settings
import app.darkio.settings.SettingsScreen
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** QA: to hurtige tryk, før DataStore har sendt den nye værdi tilbage, må ikke tabe den første ændring. */
class SettingsRaceUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val store = PreferenceDataStoreFactory.create {
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "race_${System.nanoTime()}.preferences_pb")
    }

    private fun show() = rule.setContent {
        val s by store.settings().collectAsState(Settings())
        val scope = rememberCoroutineScope()
        SettingsScreen(s, { change -> scope.launch(Dispatchers.IO) { store.updateSettings(change) } }, {}, requestAccess = {})
    }

    @Test
    fun twoFastPlusTapsRaiseGoalTwice() {
        show()
        rule.onNodeWithTag("stepGoalUp").performTouchInput { click(); advanceEventTime(150); click() }
        rule.waitUntil(2_000) { runBlocking { store.settings().first().stepGoal } != 8_000L }
        Thread.sleep(300)
        rule.onNodeWithTag("settingsStepGoal").assertTextContains("9.000", substring = true)
    }

    @Test
    fun fastToggleThenAccentKeepsBoth() {
        show()
        rule.onNodeWithTag("toggle_Clock").performTouchInput { click() }
        rule.onNodeWithTag("accent_Coral").performTouchInput { click() }
        Thread.sleep(500)
        val saved = runBlocking { store.settings().first() }
        assertEquals(AccentChoice.Coral, saved.accent)
        assertEquals(false, HomeCard.Clock in saved.cards)
    }
}
