package app.darkio

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.data.AppCategory
import app.darkio.data.AppEntry
import app.darkio.data.AppLists
import app.darkio.home.HomeScreen
import app.darkio.ui.theme.DarkIOSize
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DrawerUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val hint = context.getString(R.string.hint_swipe_up)
    private val apps = (1..200).map { AppEntry("id$it", "App %03d".format(it)) }

    private fun show(hintSeen: Boolean = true, apps: List<AppEntry> = this.apps) = rule.setContent {
        HomeScreen(apps = AppLists(apps, emptyList()), hintSeen = hintSeen, onHintSeen = {}, onLaunch = {}, onSetHidden = { _, _ -> }, onSetFavorites = {}, onSetCategory = { _, _ -> }, onAppInfo = {}, onUninstall = {})
    }

    /** Kategori-baren ligger under skærmens midte (F15), og alt trykbart holder sig inden for skuffens andel af skærmen (F7). */
    private fun assertClickablesBelowMiddle() {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val categoryBar = rule.onNodeWithTag("categoryBar").fetchSemanticsNode().boundsInRoot
        assertTrue("kategori-baren ligger over midten: $categoryBar", categoryBar.top >= root.center.y)
        val middle = root.height * (1f - DarkIOSize.DrawerMaxFraction) - 1f
        // Prefetchede gitter-celler er komponeret men ikke placeret (størrelse 0); de er ikke synlige.
        val clickables = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
        assertTrue("ingen trykbare elementer fundet", clickables.isNotEmpty())
        clickables.forEach { assertTrue("trykbar ligger over skuffens top: ${it.boundsInRoot}", it.boundsInRoot.top >= middle) }
    }

    @Test
    fun drawerOpensFromSwipeUpScrollsAndKeepsClickablesBelowMiddle() {
        show()
        rule.onNodeWithTag("homePager").performTouchInput { swipeUp() }
        rule.onNodeWithTag("drawerGrid").assertIsDisplayed()
        rule.onNodeWithText("App 001").assertIsDisplayed()
        assertClickablesBelowMiddle()

        rule.onNodeWithTag("drawerGrid").performScrollToIndex(199)
        rule.onNodeWithText("App 200").assertIsDisplayed()
        // Scroll i gitteret lukker ikke skuffen; kun grebet gør.
        rule.onNodeWithTag("drawerGrip").assertIsDisplayed()
        assertClickablesBelowMiddle()
    }

    @Test
    fun drawerShowsAtLeastFourRowsAndNoHiddenAppsLink() {
        show()
        rule.onNodeWithTag("homePager").performTouchInput { swipeUp() }
        val rows = rule.onAllNodes(hasText("App ", substring = true)).fetchSemanticsNodes()
            .filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
            .map { it.boundsInRoot.top.toInt() }.distinct()
        assertTrue("kun ${rows.size} rækker synlige", rows.size >= 4)
        rule.onNodeWithText(context.getString(R.string.hidden_apps_title)).assertDoesNotExist()
    }

    @Test
    fun drawerShrinksToContentWithFewApps() {
        show(apps = apps.take(3))
        rule.onNodeWithTag("homePager").performTouchInput { swipeUp() }
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val grip = rule.onNodeWithTag("drawerGrip").fetchSemanticsNode().boundsInRoot
        val categoryBar = rule.onNodeWithTag("categoryBar").fetchSemanticsNode().boundsInRoot
        // Én række apps: skuffen fylder mindre end halvdelen og står stadig i bunden.
        assertTrue("skuffen krymper ikke: $grip", grip.top > root.center.y)
        assertTrue("skuffen står ikke i bunden: $categoryBar", categoryBar.bottom > root.height * 0.9f)
        rule.onNodeWithText("App 003").assertIsDisplayed()
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

    @Test
    fun categoryButtonsAreAtLeast48DpAndHaveTalkBackLabels() {
        show()
        rule.onNodeWithTag("homePager").performTouchInput { swipeUp() }
        val minPx = with(rule.density) { 48.dp.toPx() } - 0.5f
        listOf(R.string.category_all, R.string.category_social, R.string.category_game, R.string.category_photo, R.string.category_work, R.string.category_tool, R.string.category_other)
            .map { context.getString(it) }.forEach { label ->
                val bounds = rule.onNodeWithContentDescription(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue("Bredde < 48dp", bounds.width >= minPx)
                assertTrue("Højde < 48dp", bounds.height >= minPx)
            }
    }

    @Test
    fun switchingCategoryFiltersGridAndShowsHeader() {
        val testApps = listOf(
            AppEntry("s1", "ChatApp", category = AppCategory.SOCIAL),
            AppEntry("g1", "PixelGame", category = AppCategory.GAME),
            AppEntry("p1", "CameraPro", category = AppCategory.PHOTO),
            AppEntry("w1", "OfficeSuite", category = AppCategory.WORK),
            AppEntry("t1", "MapNav", category = AppCategory.TOOL),
            AppEntry("o1", "MiscTool", category = AppCategory.OTHER),
        )
        rule.setContent { HomeScreen(apps = AppLists(testApps, emptyList()), hintSeen = true, onHintSeen = {}, onLaunch = {}, onSetHidden = { _, _ -> }, onSetFavorites = {}, onSetCategory = { _, _ -> }, onAppInfo = {}, onUninstall = {}) }
        rule.onNodeWithTag("homePager").performTouchInput { swipeUp() }

        val steps = listOf(
            Triple(R.string.category_all, "~/apps/alle · 6 stk", testApps.map { it.label }),
            Triple(R.string.category_social, "~/apps/social · 1 stk", listOf("ChatApp")),
            Triple(R.string.category_game, "~/apps/spil · 1 stk", listOf("PixelGame")),
            Triple(R.string.category_photo, "~/apps/foto · 1 stk", listOf("CameraPro")),
            Triple(R.string.category_work, "~/apps/arbejde · 1 stk", listOf("OfficeSuite")),
            Triple(R.string.category_tool, "~/apps/værktøj · 1 stk", listOf("MapNav")),
            Triple(R.string.category_other, "~/apps/andet · 1 stk", listOf("MiscTool")),
        )
        steps.forEach { (res, header, visibleLabels) ->
            rule.onNodeWithContentDescription(context.getString(res)).performClick().assertIsSelected()
            rule.onNodeWithText(header).assertIsDisplayed()
            visibleLabels.forEach { rule.onNodeWithText(it).assertIsDisplayed() }
            (testApps.map { it.label } - visibleLabels.toSet()).forEach { rule.onNodeWithText(it).assertDoesNotExist() }
            assertClickablesBelowMiddle()
        }
    }
}
