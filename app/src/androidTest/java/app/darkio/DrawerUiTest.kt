package app.darkio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.data.AppEntry
import app.darkio.home.HomeScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DrawerUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val allApps = context.getString(R.string.home_all_apps)
    private val hint = context.getString(R.string.hint_swipe_up)
    private val apps = (1..200).map { AppEntry("id$it", "App %03d".format(it)) }

    private fun show(hintSeen: Boolean = true) = rule.setContent {
        HomeScreen(apps = apps, hintSeen = hintSeen, onHintSeen = {}, onLaunch = {})
    }

    /** Alle trykbare elementer skal ligge under skærmens midte. */
    private fun assertClickablesBelowMiddle() {
        val middle = rule.onRoot().fetchSemanticsNode().boundsInRoot.center.y
        // Prefetchede gitter-celler er komponeret men ikke placeret (størrelse 0); de er ikke synlige.
        val clickables = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
        assertTrue("ingen trykbare elementer fundet", clickables.isNotEmpty())
        clickables.forEach { assertTrue("trykbar ligger over midten: ${it.boundsInRoot}", it.boundsInRoot.top >= middle) }
    }

    @Test
    fun drawerOpensFromButtonScrollsAndKeepsClickablesBelowMiddle() {
        show()
        rule.onNodeWithText(allApps).performClick()
        rule.onNodeWithTag("drawerGrid").assertIsDisplayed()
        rule.onNodeWithText("App 001").assertIsDisplayed()
        assertClickablesBelowMiddle()

        rule.onNodeWithTag("drawerGrid").performScrollToIndex(199)
        rule.onNodeWithText("App 200").assertIsDisplayed()
        assertClickablesBelowMiddle()
    }

    @Test
    fun swipeUpOpensAndSwipeDownCloses() {
        show()
        rule.onRoot().performTouchInput { swipeUp() }
        rule.onNodeWithTag("drawerGrid").assertIsDisplayed()
        rule.onNodeWithTag("drawerGrip").performTouchInput {
            val x = width / 2f
            swipe(start = Offset(x, height / 2f), end = Offset(x, height / 2f + 200f))
        }
        rule.onNodeWithTag("drawerGrid").assertDoesNotExist()
    }

    @Test
    fun hintShownWhenNotSeen() {
        show(hintSeen = false)
        rule.onNodeWithText(hint).assertIsDisplayed()
    }

    @Test
    fun hintHiddenWhenSeen() {
        show(hintSeen = true)
        rule.onNodeWithText(hint).assertDoesNotExist()
    }
}
