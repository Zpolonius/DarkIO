package app.darkio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.dashboard.StepsCard
import app.darkio.data.AppDatabase
import app.darkio.data.AppEntry
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class StepsUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun stepsCardWithAccessDisplaysStepsGoalAndProgressBar() {
        rule.setContent {
            StepsCard(
                hasPermission = { true },
                loadSteps = { 6_420L },
                goal = 8_000L,
            )
        }

        val title = context.getString(R.string.steps_title)
        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithTag("stepsCount").assertIsDisplayed()
        rule.onNodeWithText("6.420", substring = true).assertIsDisplayed()
        rule.onNodeWithText("8.000", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("stepsProgressBar").assertIsDisplayed()
    }

    @Test
    fun stepsCardWithoutAccessDisplaysGrantAccessMessageAndButton() {
        rule.setContent {
            StepsCard(
                hasPermission = { false },
                loadSteps = { 0L },
            )
        }

        val title = context.getString(R.string.steps_grant_access_title)
        val explanation = context.getString(R.string.steps_grant_access_explanation)
        val grant = context.getString(R.string.card_grant_access)

        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithText(explanation).assertIsDisplayed()
        rule.onNodeWithText(grant).assertIsDisplayed()
    }

    @Test
    fun stepsCardClickingGrantAccessTriggersFlowAndKeepsState() {
        var grantTriggered = false
        rule.setContent {
            StepsCard(
                hasPermission = { false },
                loadSteps = { 0L },
                onGrantAccess = { grantTriggered = true },
            )
        }

        val grant = context.getString(R.string.card_grant_access)
        val title = context.getString(R.string.steps_grant_access_title)

        rule.onNodeWithText(grant).performClick()
        rule.waitForIdle()

        org.junit.Assert.assertTrue(grantTriggered)
        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithText(grant).assertIsDisplayed()
    }

    @Test
    fun stepsCardIsPresentOnHomeScreenPageOne() {
        val entry = AppEntry("app.darkio/app.darkio.MainActivity#0", "DarkIO")
        rule.setContent {
            HomeScreen(
                apps = splitApps(listOf(entry), emptySet()),
                hintSeen = true,
                onHintSeen = {},
                onLaunch = {},
                onSetHidden = { _, _ -> },
                onSetFavorites = {},
                onSetCategory = { _, _ -> },
                onAppInfo = {},
                onUninstall = {},
            )
        }

        // Swipe til side 1 ("træning")
        rule.onNodeWithTag("homePager").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        rule.onNodeWithTag("page1").assertIsDisplayed()

        // Skridtkortet vises på side 1 under træningskortet (enten "Giv adgang til skridt" eller "skridt")
        val accessTitle = context.getString(R.string.steps_grant_access_title)
        rule.onNodeWithText(accessTitle).assertIsDisplayed()
    }

    @Test
    fun stepsDataIsNotSavedLocally() {
        // N5: Sundhedsdata læses kun og gemmes ikke af DarkIO
        val db = AppDatabase.open(context)
        val tables = mutableListOf<String>()
        db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
        }
        // Verificér at der ikke findes tabeller eller kolonner til skridtdata
        assertFalse(tables.any { it.contains("step", ignoreCase = true) })
        assertFalse(tables.any { it.contains("skridt", ignoreCase = true) })
    }
}
