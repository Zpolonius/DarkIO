package app.darkio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.dashboard.FocusCard
import app.darkio.dashboard.FocusData
import app.darkio.data.AppDatabase
import app.darkio.data.AppEntry
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FocusUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun focusCardWithAccessDisplaysCardScreenTimeAndUnlocks() {
        rule.setContent {
            FocusCard(
                hasPermission = { true },
                loadFocus = { FocusData(screenTimeMillis = 84 * 60 * 1000L, unlockCount = 12) },
            )
        }
        rule.onNodeWithText(context.getString(R.string.focus_title), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("focusScreenTime", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("1 t 24 m", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("focusUnlockCount", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("12 oplåsninger", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun focusCardWithoutAccessDisplaysGrantAccessMessageAndButton() {
        rule.setContent {
            FocusCard(
                hasPermission = { false },
                loadFocus = { FocusData(0L, 0) },
            )
        }
        rule.onNodeWithText(context.getString(R.string.focus_grant_access_title)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.focus_grant_access_explanation)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.card_grant_access)).assertIsDisplayed()
        rule.onNodeWithTag("focusScreenTime").assertDoesNotExist()
        rule.onNodeWithTag("focusUnlockCount").assertDoesNotExist()
    }

    @Test
    fun focusCardClickingGrantAccessTriggersFlow() {
        var grantTriggered = false
        rule.setContent {
            FocusCard(
                hasPermission = { false },
                loadFocus = { FocusData(0L, 0) },
                onGrantAccess = { grantTriggered = true },
            )
        }
        rule.onNodeWithText(context.getString(R.string.card_grant_access)).performClick()
        rule.waitForIdle()
        assertTrue(grantTriggered)
    }

    @Test
    fun focusCardPermissionRevokedFallsBackToNeedsAccessWithoutCrash() {
        var permissionGranted by mutableStateOf(true)
        rule.setContent {
            FocusCard(
                hasPermission = { permissionGranted },
                loadFocus = {
                    if (permissionGranted) {
                        FocusData(screenTimeMillis = 3600_000L, unlockCount = 5)
                    } else {
                        throw SecurityException("Adgang tilbagekaldt")
                    }
                },
                reloadTrigger = permissionGranted,
            )
        }
        rule.onNodeWithText("1 t", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("5 oplåsninger", useUnmergedTree = true).assertIsDisplayed()

        // Adgang fjernes
        permissionGranted = false
        rule.waitForIdle()

        rule.onNodeWithText(context.getString(R.string.focus_grant_access_title)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.card_grant_access)).assertIsDisplayed()
    }

    @Test
    fun focusCardIsPresentOnHomeScreenPageZero() {
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
        rule.onNodeWithTag("page0").assertIsDisplayed()
        // På side 0 ("i dag") uden adgang vises adgangskortet
        rule.onNodeWithText(context.getString(R.string.focus_grant_access_title)).assertIsDisplayed()
    }

    @Test
    fun focusDataIsNotSavedLocally() {
        val db = AppDatabase.open(context)
        val tables = mutableListOf<String>()
        db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) tables.add(cursor.getString(0))
        }
        assertFalse(tables.any { it.contains("focus", true) || it.contains("fokus", true) || it.contains("usage", true) })
    }
}
