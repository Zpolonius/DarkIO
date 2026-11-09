package app.darkio

import android.view.KeyEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.data.AppCategory
import app.darkio.data.AppEntry
import app.darkio.data.splitApps
import app.darkio.data.withCustomCategories
import app.darkio.home.HomeScreen
import app.darkio.ui.theme.DarkIOSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppMenuUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val allApps = context.getString(R.string.home_all_apps)
    private val installed = listOf(AppEntry("a/A#0", "Alfa"), AppEntry("b/B#0", "Bravo"))
    private val hide = context.getString(R.string.menu_hide)
    private val move = context.getString(R.string.menu_move_category)
    private val info = context.getString(R.string.menu_app_info)
    private val uninstall = context.getString(R.string.menu_uninstall)
    private val work = context.getString(AppCategory.WORK.labelRes)
    private val events = mutableListOf<String>()
    private var keyboard: SoftwareKeyboardController? = null
    private var view: View? = null

    private fun imeShown() = rule.runOnUiThread { ViewCompat.getRootWindowInsets(view!!)?.isVisible(WindowInsetsCompat.Type.ime()) == true }

    private fun waitForIme(shown: Boolean) = (1..50).any { Thread.sleep(100); imeShown() == shown }

    /** Samme kombination af valg som AppRepository bruger; [customAtStart] svarer til customCategory i Room. */
    private fun show(customAtStart: Map<String, String> = emptyMap()) = rule.setContent {
        var hidden by rememberSaveable { mutableStateOf(emptySet<String>()) }
        var custom by rememberSaveable { mutableStateOf(customAtStart) }
        keyboard = LocalSoftwareKeyboardController.current
        view = LocalView.current
        HomeScreen(
            apps = splitApps(withCustomCategories(installed, custom), hidden),
            hintSeen = true,
            onHintSeen = {},
            onLaunch = { events += "launch ${it.id}" },
            onSetHidden = { app, h -> hidden = if (h) hidden + app.id else hidden - app.id },
            onSetCategory = { app, c -> events += "category ${app.id} ${c.slug}"; custom = custom + (app.id to c.slug) },
            onAppInfo = { events += "info ${it.id}" },
            onUninstall = { events += "uninstall ${it.id}" },
        )
    }

    private fun openMenuFromDrawer() {
        rule.onNodeWithText(allApps).performClick()
        rule.onNodeWithText("Bravo").performTouchInput { longClick() }
    }

    private fun back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    @Test
    fun longPressInDrawerOpensSheetWithFourActionsAndDoesNotHide() {
        show()
        openMenuFromDrawer()
        listOf(hide, move, info, uninstall).forEach { rule.onNode(hasText(it) and hasAnyAncestor(hasTestTag("appMenu"))).assertIsDisplayed() }
        // Bravo findes stadig i skuffen bag arket (gitter + arkets titel).
        assertEquals(2, rule.onAllNodes(hasText("Bravo")).fetchSemanticsNodes().size)
        assertTrue(events.isEmpty())
    }

    @Test
    fun longPressOnSearchResultOpensSheet() {
        show()
        rule.onNodeWithTag("searchField").performTextInput("brav")
        rule.onNodeWithText("Bravo").performTouchInput { longClick() }
        rule.onNodeWithTag("appMenu").assertIsDisplayed()
        rule.onNode(hasText(uninstall)).assertIsDisplayed()
    }

    @Test
    fun keyboardIsHiddenWhenSheetOpensFromSearch() {
        show()
        rule.onNodeWithTag("searchField").performClick()
        rule.runOnUiThread { keyboard?.show() }
        assertTrue("forudsætning: tastaturet vises", waitForIme(true))
        rule.onNodeWithTag("searchField").performTextInput("brav")
        rule.onNodeWithText("Bravo").performTouchInput { longClick() }
        rule.onNodeWithTag("appMenu").assertIsDisplayed()
        // Tastaturet må ikke dække arket: ingen input-metode-vindue, når arket er åbent.
        assertTrue("tastaturet er stadig synligt", waitForIme(false))
    }

    @Test
    fun sheetIsInBottomHalfWithTouchTargets() {
        show()
        openMenuFromDrawer()
        val middle = rule.onRoot().fetchSemanticsNode().boundsInRoot.center.y
        val sheet = rule.onNodeWithTag("appMenu").fetchSemanticsNode().boundsInRoot
        assertTrue("arket starter over midten: $sheet", sheet.top >= middle)
        val minPx = with(Density(context)) { DarkIOSize.TouchMin.toPx() }
        listOf("menuHide", "menuMoveCategory", "menuAppInfo", "menuUninstall").forEach {
            val b = rule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot
            assertTrue("$it for lav: $b", b.height >= minPx - 1f && b.top >= middle)
        }
    }

    @Test
    fun backClosesSheetAndKeepsDrawer() {
        show()
        openMenuFromDrawer()
        back()
        rule.onNodeWithTag("appMenu").assertDoesNotExist()
        rule.onNodeWithText("Bravo").assertIsDisplayed()
    }

    @Test
    fun hideInSheetHidesAndClosesSheet() {
        show()
        openMenuFromDrawer()
        rule.onNodeWithTag("menuHide").performClick()
        rule.onNodeWithTag("appMenu").assertDoesNotExist()
        rule.onNodeWithText("Bravo").assertDoesNotExist()
        rule.onNodeWithText("Alfa").assertIsDisplayed()
    }

    @Test
    fun appInfoAndUninstallReportExactApp() {
        show()
        openMenuFromDrawer()
        rule.onNodeWithTag("menuAppInfo").performClick()
        rule.onNodeWithTag("appMenu").assertDoesNotExist()
        rule.onNodeWithText("Bravo").performTouchInput { longClick() }
        rule.onNodeWithTag("menuUninstall").performClick()
        rule.onNodeWithTag("appMenu").assertDoesNotExist()
        assertEquals(listOf("info b/B#0", "uninstall b/B#0"), events)
    }

    @Test
    fun moveCategoryShowsSixCategoriesAndFiltersByEffectiveCategory() {
        show()
        openMenuFromDrawer()
        rule.onNodeWithTag("menuMoveCategory").performClick()
        AppCategory.entries.filter { it != AppCategory.ALL }.forEach { rule.onNodeWithTag("menuCategory_${it.slug}").assertIsDisplayed() }
        rule.onNodeWithTag("menuCategory_alle").assertDoesNotExist()
        rule.onNodeWithTag("menuCategory_arbejde").performClick()
        rule.onNodeWithTag("appMenu").assertDoesNotExist()
        assertEquals(listOf("category b/B#0 arbejde"), events)
        // Kategori-baren bruger den effektive kategori: Bravo ligger nu kun under Arbejde.
        rule.onNodeWithContentDescription(work).performClick()
        rule.onNodeWithText("Bravo").assertIsDisplayed()
        rule.onNodeWithText("Alfa").assertDoesNotExist()
    }

    @Test
    fun savedCustomCategoryIsUsedFromStartAndInvalidOneFallsBack() {
        show(customAtStart = mapOf("a/A#0" to "arbejde", "b/B#0" to "ugyldig"))
        rule.onNodeWithText(allApps).performClick()
        rule.onNodeWithContentDescription(work).performClick()
        rule.onNodeWithText("Alfa").assertIsDisplayed()
        rule.onNodeWithText("Bravo").assertDoesNotExist()
    }
}
