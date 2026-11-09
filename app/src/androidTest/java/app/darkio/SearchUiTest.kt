package app.darkio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.data.AppEntry
import app.darkio.data.AppLists
import app.darkio.home.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchUiTest {
    @get:Rule val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val placeholder = context.getString(R.string.search_placeholder)
    private val noResults = context.getString(R.string.search_no_results)
    private val allApps = context.getString(R.string.home_all_apps)
    private val apps = (1..20).map { AppEntry("id$it", "App %03d".format(it)) }
    private var launched: AppEntry? = null

    private fun show() = rule.setContent {
        HomeScreen(apps = AppLists(apps, emptyList()), hintSeen = true, onHintSeen = {}, onLaunch = { launched = it }, onSetHidden = { _, _ -> }, onSetFavorites = {}, onSetCategory = { _, _ -> }, onAppInfo = {}, onUninstall = {})
    }

    private fun assertClickablesBelowMiddle() {
        val middle = rule.onRoot().fetchSemanticsNode().boundsInRoot.center.y
        val clickables = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
        assertTrue(clickables.isNotEmpty())
        clickables.forEach { assertTrue("trykbar over midten: ${it.boundsInRoot}", it.boundsInRoot.top >= middle) }
    }

    @Test
    fun searchFieldIsDisplayedWithPromptAndPlaceholder() {
        show()
        rule.onNodeWithText(">").assertIsDisplayed()
        rule.onNodeWithText(placeholder).assertIsDisplayed()
        assertClickablesBelowMiddle()
    }

    @Test
    fun searchResultsGrowUpwardsAndClickablesBelowMiddle() {
        show()
        rule.onNodeWithTag("searchField").performTextInput("App 00")
        rule.onNodeWithText("App 001").assertIsDisplayed()
        rule.onNodeWithText("App 002").assertIsDisplayed()
        val fieldTop = rule.onNodeWithTag("searchField").fetchSemanticsNode().boundsInRoot.top
        val top1 = rule.onNodeWithText("App 001").fetchSemanticsNode().boundsInRoot.top
        val top2 = rule.onNodeWithText("App 002").fetchSemanticsNode().boundsInRoot.top
        assertTrue("bedste hit tættest på feltet", top1 > top2 && top1 < fieldTop)
        assertClickablesBelowMiddle()
    }

    @Test
    fun enterActionOpensFirstHit() {
        show()
        rule.onNodeWithTag("searchField").performTextInput("App 00")
        rule.onNodeWithTag("searchField").performImeAction()
        assertEquals("App 001", launched?.label)
    }

    @Test
    fun noHitsShowsEmptyState() {
        show()
        rule.onNodeWithTag("searchField").performTextInput("xyznonexistent")
        rule.onNodeWithText(noResults).assertIsDisplayed()
        assertClickablesBelowMiddle()
    }

    @Test
    fun searchWorksInDrawer() {
        show()
        rule.onNodeWithText(allApps).performClick()
        rule.onNodeWithTag("drawerGrid").assertIsDisplayed()
        val drawerSearch = rule.onAllNodesWithTag("searchField")
        drawerSearch[drawerSearch.fetchSemanticsNodes().size - 1].performTextInput("015")
        rule.onNode(hasClickAction() and hasText("App 015")).assertIsDisplayed().performClick()
        assertEquals("App 015", launched?.label)
    }
}
