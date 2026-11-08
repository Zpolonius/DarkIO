package app.darkio

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.data.AppEntry
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import org.junit.Rule
import org.junit.Test

class HiddenAppsUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val allApps = context.getString(R.string.home_all_apps)
    private val hiddenTitle = context.getString(R.string.hidden_apps_title)
    private val note = context.getString(R.string.hidden_apps_note)
    private val showAgain = context.getString(R.string.hidden_apps_show_again)
    private val installed = listOf(AppEntry("a/A#0", "Alfa"), AppEntry("b/B#0", "Bravo"))

    /** Samme splitApps som AppRepository bruger; [hiddenAtStart] står for rækkerne i Room. */
    private fun show(hiddenAtStart: Set<String> = emptySet()) = rule.setContent {
        var hiddenIds by rememberSaveable { mutableStateOf(hiddenAtStart) }
        HomeScreen(
            apps = splitApps(installed, hiddenIds),
            hintSeen = true,
            onHintSeen = {},
            onLaunch = {},
            onSetHidden = { app, hidden -> hiddenIds = if (hidden) hiddenIds + app.id else hiddenIds - app.id },
        )
    }

    private fun openHiddenScreen() {
        rule.onNodeWithText(allApps).performClick()
        rule.onNodeWithText(hiddenTitle).performClick()
    }

    @Test
    fun hiddenAppIsNotInDrawer() {
        show(hiddenAtStart = setOf("b/B#0"))
        rule.onNodeWithText(allApps).performClick()
        rule.onNodeWithText("Alfa").assertIsDisplayed()
        rule.onNodeWithText("Bravo").assertDoesNotExist()
    }

    @Test
    fun longPressHidesApp() {
        show()
        rule.onNodeWithText(allApps).performClick()
        rule.onNodeWithText("Bravo").performTouchInput { longClick() }
        rule.onNodeWithText("Bravo").assertDoesNotExist()
        rule.onNodeWithText("Alfa").assertIsDisplayed()
    }

    @Test
    fun hiddenScreenShowsAppShowAgainAndHonestNote() {
        show(hiddenAtStart = setOf("b/B#0"))
        openHiddenScreen()
        rule.onNodeWithText("Bravo").assertIsDisplayed()
        rule.onNodeWithText(showAgain).assertIsDisplayed()
        rule.onNodeWithText(note).assertIsDisplayed()
    }

    @Test
    fun hiddenScreenDoesNotComposeDrawerBehindIt() {
        show()
        openHiddenScreen()
        rule.onNodeWithText("Alfa").assertDoesNotExist()
        rule.onNodeWithText(allApps).assertDoesNotExist()
    }

    @Test
    fun showAgainBringsAppBackToDrawer() {
        show(hiddenAtStart = setOf("b/B#0"))
        openHiddenScreen()
        rule.onNodeWithText(showAgain).performClick()
        rule.onNodeWithText(context.getString(R.string.hidden_apps_empty)).assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
        rule.onNodeWithText("Bravo").assertIsDisplayed()
    }
}
