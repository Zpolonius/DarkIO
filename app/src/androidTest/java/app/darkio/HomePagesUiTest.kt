package app.darkio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.data.AppEntry
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** F20, F11, F15: tre sider med sideprikker over en fast dock. */
class HomePagesUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alfa = AppEntry("alfa/A#0", "Alfa")
    private var homeSignal by mutableIntStateOf(0)

    private fun show() {
        rule.setContent {
            HomeScreen(
                apps = splitApps(listOf(alfa), emptySet()).copy(favoriteSlots = mapOf(alfa.id to 0)),
                hintSeen = true,
                onHintSeen = {}, onLaunch = {}, onSetHidden = { _, _ -> }, onSetFavorites = {},
                onSetCategory = { _, _ -> }, onAppInfo = {}, onUninstall = {},
                homeSignal = homeSignal,
            )
        }
    }

    private fun assertOnlySelected(page: Int) = (0..2).forEach {
        if (it == page) rule.onNodeWithTag("pageDot$it").assertIsSelected() else rule.onNodeWithTag("pageDot$it").assertIsNotSelected()
    }

    @Test
    fun startsOnTodayWithThreeDots() {
        show()
        rule.onNodeWithTag("page0").assertIsDisplayed()
        assertOnlySelected(0)
    }

    @Test
    fun swipeChangesPageAndDotsFollow() {
        show()
        rule.onNodeWithTag("homePager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithTag("page1").assertIsDisplayed()
        assertOnlySelected(1)
        rule.onNodeWithTag("homePager").performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertOnlySelected(0)
    }

    @Test
    fun tappingDotSwitchesPage() {
        show()
        rule.onNodeWithTag("pageDot2").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("page2").assertIsDisplayed()
        assertOnlySelected(2)
    }

    @Test
    fun dotSizesMatchSpec() {
        show()
        val active = rule.onNodeWithTag("pageDot0").getUnclippedBoundsInRoot()
        assertEquals(44f, active.width.value, 0.5f)
        assertEquals(24f, active.height.value, 0.5f)
    }

    @Test
    fun homeResetsToFirstPage() {
        show()
        rule.onNodeWithTag("pageDot2").performClick()
        rule.waitForIdle()
        assertOnlySelected(2)
        homeSignal++
        rule.waitForIdle()
        rule.onNodeWithTag("page0").assertIsDisplayed()
        assertOnlySelected(0)
    }

    @Test
    fun dockStaysPutWhenPageChanges() {
        show()
        val before = rule.onNodeWithTag("dock").getUnclippedBoundsInRoot()
        rule.onNodeWithTag("pageDot1").performClick()
        rule.waitForIdle()
        val after = rule.onNodeWithTag("dock").getUnclippedBoundsInRoot()
        assertEquals(before, after)
    }
}
