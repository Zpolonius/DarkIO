package app.darkio

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import app.darkio.data.AppEntry
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** F11, N11: notifikationer ved swipe ned, og intet trykbart i systemGestures-zonen. */
class GestureUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val alfa = AppEntry("alfa/A#0", "Alfa")
    private var notifications = 0
    private var gestureBottomPx = 0

    private fun show() {
        rule.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        rule.setContent {
            gestureBottomPx = WindowInsets.systemGestures.getBottom(LocalDensity.current)
            HomeScreen(
                apps = splitApps(listOf(alfa), emptySet()).copy(favoriteSlots = mapOf(alfa.id to 0)),
                hintSeen = true,
                onHintSeen = {}, onLaunch = {}, onSetHidden = { _, _ -> }, onSetFavorites = {},
                onSetCategory = { _, _ -> }, onAppInfo = {}, onUninstall = {},
                onOpenNotifications = { notifications++ },
            )
        }
    }

    @Test
    fun swipeDownOnHomeOpensNotifications() {
        show()
        rule.onNodeWithTag("homePager").performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals(1, notifications)
    }

    @Test
    fun swipeDownWithDrawerOpenOnlyClosesDrawer() {
        show()
        rule.onNodeWithTag("homePager").performTouchInput { swipeUp() }
        rule.onNodeWithTag("homePager").performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals(0, notifications)
    }

    @Test
    fun horizontalSwipeDoesNotOpenNotifications() {
        show()
        rule.onNodeWithTag("homePager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithTag("page1").assertExists()
        assertEquals(0, notifications)
    }

    @Test
    fun swipeUpOpensDrawerNotNotifications() {
        show()
        rule.onNodeWithTag("homePager").performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.onNodeWithTag("drawerGrid").assertExists()
        assertEquals(0, notifications)
    }

    @Test
    fun shortSwipeDoesNothing() {
        show()
        rule.onNodeWithTag("homePager").performTouchInput { swipeDown(startY = centerY, endY = centerY + 20f) }
        rule.waitForIdle()
        assertEquals(0, notifications)
    }

    @Test
    fun swipeDownWithActiveSearchDoesNotOpenNotifications() {
        show()
        rule.onNodeWithTag("searchField").performTextInput("a")
        rule.onNodeWithTag("homePager").performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals(0, notifications)
    }

    @Test
    fun nothingInSystemGesturesZone() {
        show()
        rule.waitForIdle()
        val rootBottom = rule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val limit = rootBottom - gestureBottomPx
        assertTrue("kræver gesture-navigation", gestureBottomPx > 0)
        assertTrue("ingen trykbare noder fundet", rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().isNotEmpty())
        // Swipe-området (pageren fylder det) slutter over zonen, og det samme gør alt i det.
        assertTrue(rule.onNodeWithTag("homePager").fetchSemanticsNode().boundsInRoot.bottom <= limit)
        rule.onAllNodes(!isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.boundsInRoot.bottom < rootBottom || it.boundsInRoot.top > 0f }
            .forEach { assertTrue("${it.boundsInRoot} under $limit (inset $gestureBottomPx)", it.boundsInRoot.bottom <= limit) }
    }
}
