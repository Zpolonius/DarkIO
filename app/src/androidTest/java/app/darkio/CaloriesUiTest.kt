package app.darkio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.dashboard.CaloriesCard
import app.darkio.data.AppDatabase
import app.darkio.data.AppEntry
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CaloriesUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun caloriesCardWithAccessAndFoodvisorDisplaysCardAndLogFood() {
        rule.setContent {
            CaloriesCard(
                hasPermission = { true },
                loadCalories = { 1_840L },
                isFoodAdvisorInstalled = { true },
            )
        }
        rule.onNodeWithText(context.getString(R.string.calories_title), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("caloriesCount", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("1.840", substring = true, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("kcal", substring = true, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.calories_log_food), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("↗", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("caloriesLogFood", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun caloriesCardWithoutFoodAdvisorHidesLogFood() {
        rule.setContent {
            CaloriesCard(
                hasPermission = { true },
                loadCalories = { 1_840L },
                isFoodAdvisorInstalled = { false },
            )
        }
        rule.onNodeWithText(context.getString(R.string.calories_title)).assertIsDisplayed()
        rule.onNodeWithTag("caloriesCount").assertIsDisplayed()
        rule.onNodeWithText("1.840", substring = true).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.calories_log_food)).assertDoesNotExist()
        rule.onNodeWithText("↗").assertDoesNotExist()
        rule.onNodeWithTag("caloriesLogFood").assertDoesNotExist()
    }

    @Test
    fun caloriesCardClickingCardOrLogFoodTriggersOpenFoodAdvisor() {
        var openCount = 0
        rule.setContent {
            CaloriesCard(
                hasPermission = { true },
                loadCalories = { 1_840L },
                isFoodAdvisorInstalled = { true },
                onOpenFoodAdvisor = { openCount++ },
            )
        }
        rule.onNodeWithText(context.getString(R.string.calories_log_food), useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals(1, openCount)

        rule.onNodeWithTag("caloriesCardContent").performClick()
        rule.waitForIdle()
        assertEquals(2, openCount)
    }

    @Test
    fun caloriesCardWithoutAccessDisplaysGrantAccessMessageAndButton() {
        rule.setContent {
            CaloriesCard(
                hasPermission = { false },
                loadCalories = { 0L },
            )
        }
        rule.onNodeWithText(context.getString(R.string.calories_grant_access_title)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.calories_grant_access_explanation)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.card_grant_access)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.calories_log_food)).assertDoesNotExist()
        rule.onNodeWithText("kcal", substring = true).assertDoesNotExist()
    }

    @Test
    fun caloriesCardClickingGrantAccessTriggersFlow() {
        var grantTriggered = false
        rule.setContent {
            CaloriesCard(
                hasPermission = { false },
                loadCalories = { 0L },
                onGrantAccess = { grantTriggered = true },
            )
        }
        rule.onNodeWithText(context.getString(R.string.card_grant_access)).performClick()
        rule.waitForIdle()
        assertTrue(grantTriggered)
    }

    @Test
    fun caloriesCardIsPresentOnHomeScreenPageOne() {
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
        rule.onNodeWithTag("homePager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithTag("page1").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.calories_grant_access_title)).assertIsDisplayed()
    }

    @Test
    fun caloriesDataIsNotSavedLocally() {
        val db = AppDatabase.open(context)
        val tables = mutableListOf<String>()
        db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) tables.add(cursor.getString(0))
        }
        assertFalse(tables.any { it.contains("calorie", true) || it.contains("kalorie", true) || it.contains("forbraendt", true) })
    }
}
