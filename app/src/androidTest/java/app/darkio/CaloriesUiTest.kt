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

        val title = context.getString(R.string.calories_title)
        val logFood = context.getString(R.string.calories_log_food)

        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithTag("caloriesCount").assertIsDisplayed()
        rule.onNodeWithText("1.840", substring = true).assertIsDisplayed()
        rule.onNodeWithText("kcal", substring = true).assertIsDisplayed()
        rule.onNodeWithText(logFood).assertIsDisplayed()
        rule.onNodeWithText("↗").assertIsDisplayed()
        rule.onNodeWithTag("caloriesLogFood").assertIsDisplayed()
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

        val title = context.getString(R.string.calories_title)
        val logFood = context.getString(R.string.calories_log_food)

        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithTag("caloriesCount").assertIsDisplayed()
        rule.onNodeWithText("1.840", substring = true).assertIsDisplayed()
        rule.onNodeWithText(logFood).assertDoesNotExist()
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

        val logFood = context.getString(R.string.calories_log_food)
        rule.onNodeWithText(logFood).performClick()
        rule.waitForIdle()
        assertTrue(openCount == 1)

        rule.onNodeWithTag("caloriesCardContent").performClick()
        rule.waitForIdle()
        assertTrue(openCount == 2)
    }

    @Test
    fun caloriesCardWithoutAccessDisplaysGrantAccessMessageAndButton() {
        rule.setContent {
            CaloriesCard(
                hasPermission = { false },
                loadCalories = { 0L },
            )
        }

        val title = context.getString(R.string.calories_grant_access_title)
        val explanation = context.getString(R.string.calories_grant_access_explanation)
        val grant = context.getString(R.string.card_grant_access)
        val logFood = context.getString(R.string.calories_log_food)

        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithText(explanation).assertIsDisplayed()
        rule.onNodeWithText(grant).assertIsDisplayed()
        rule.onNodeWithText(logFood).assertDoesNotExist()
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

        val grant = context.getString(R.string.card_grant_access)
        rule.onNodeWithText(grant).performClick()
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

        // Swipe til side 1 ("træning")
        rule.onNodeWithTag("homePager").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        rule.onNodeWithTag("page1").assertIsDisplayed()

        // Kaloriekortet vises på side 1 under skridtkortet
        val accessTitle = context.getString(R.string.calories_grant_access_title)
        rule.onNodeWithText(accessTitle).assertIsDisplayed()
    }

    @Test
    fun caloriesDataIsNotSavedLocally() {
        // N5: Sundhedsdata læses kun og gemmes ikke af DarkIO
        val db = AppDatabase.open(context)
        val tables = mutableListOf<String>()
        db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
        }
        // Verificér at der ikke findes tabeller eller kolonner til kaloriedata
        assertFalse(tables.any { it.contains("calorie", ignoreCase = true) })
        assertFalse(tables.any { it.contains("kalorie", ignoreCase = true) })
        assertFalse(tables.any { it.contains("forbraendt", ignoreCase = true) })
        assertFalse(tables.any { it.contains("food", ignoreCase = true) })
    }
}
