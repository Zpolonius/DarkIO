package app.darkio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import app.darkio.ui.theme.DarkIOSize
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.data.AppEntry
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** F6, F20: hver side har sin egen genvejsrække. */
class PageShortcutsUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val installed = listOf("Alfa", "Bravo", "Charlie", "Delta", "Echo").map { AppEntry("${it.lowercase()}/A#0", it) }
    private var shortcuts by mutableStateOf(emptyMap<Int, Map<String, Int>>())
    private var hidden by mutableStateOf(emptySet<String>())
    private var apps by mutableStateOf(installed)
    private var launched = 0
    private var favs by mutableStateOf(emptyMap<String, Int>())

    private fun id(name: String) = installed.first { it.label == name }.id

    private fun show(start: Map<Int, Map<String, Int>> = emptyMap(), hintSeen: Boolean = true) {
        shortcuts = start
        rule.setContent {
            HomeScreen(
                apps = splitApps(apps, hidden).copy(shortcutSlots = shortcuts, favoriteSlots = favs),
                hintSeen = hintSeen, onHintSeen = {}, onLaunch = { launched++ }, onSetHidden = { _, _ -> },
                onSetFavorites = { ch -> favs = favs + ch.mapNotNull { (a, s) -> s?.let { a.id to it } } },
                onSetCategory = { _, _ -> }, onAppInfo = {}, onUninstall = {},
                onSetShortcuts = { page, changes ->
                    val now = shortcuts[page].orEmpty() + changes.mapNotNull { (a, s) -> s?.let { a.id to it } } - changes.filter { it.second == null }.map { it.first.id }.toSet()
                    shortcuts = shortcuts + (page to now)
                },
            )
        }
    }

    private fun goTo(page: Int) {
        rule.onNodeWithTag("pageDot$page").performClick()
        rule.waitForIdle()
    }

    private fun addFromDrawer(name: String) {
        rule.onNodeWithTag("homePager").performTouchInput { swipeUp() }
        rule.onNodeWithText(name).performTouchInput { longClick() }
        rule.onNodeWithText(context.getString(R.string.menu_shortcut)).performClick()
        rule.onNodeWithTag("appMenu").assertDoesNotExist()
        // Luk skuffen med et swipe ned i grebet, så den ikke ligger over rækken.
        rule.onNodeWithTag("drawerGrip").performTouchInput { swipe(Offset(width / 2f, height / 2f), Offset(width / 2f, height / 2f + 200f)) }
        rule.waitForIdle()
        rule.onNodeWithTag("drawerGrid").assertDoesNotExist()
    }

    @Test
    fun shortcutsOnPage2AreNotShownOnPages1And3() {
        show(mapOf(1 to mapOf(id("Alfa") to 0, id("Bravo") to 1)))
        rule.onNodeWithTag("shortcuts_${id("Alfa")}").assertDoesNotExist()
        goTo(1)
        rule.onNodeWithTag("shortcuts_${id("Alfa")}").assertIsDisplayed()
        rule.onNodeWithTag("shortcuts_${id("Bravo")}").assertIsDisplayed()
        goTo(2)
        rule.onNodeWithTag("shortcuts_${id("Alfa")}").assertDoesNotExist()
        rule.onNodeWithTag("shortcutsEmpty_0").assertIsDisplayed()
        goTo(0)
        rule.onNodeWithTag("shortcuts_${id("Bravo")}").assertDoesNotExist()
    }

    @Test
    fun addAndRemoveShortcutThroughMenuOnlyAffectsCurrentPage() {
        show()
        goTo(1)
        addFromDrawer("Charlie")
        rule.onNodeWithTag("shortcuts_${id("Charlie")}").assertIsDisplayed()
        assertEquals(mapOf(1 to mapOf(id("Charlie") to 0)), shortcuts)
        rule.onNodeWithTag("shortcuts_${id("Charlie")}").performTouchInput { longClick() }
        rule.onNodeWithText(context.getString(R.string.menu_unshortcut)).performClick()
        rule.onNodeWithTag("shortcuts_${id("Charlie")}").assertDoesNotExist()
        assertEquals(emptyMap<String, Int>(), shortcuts[1])
    }

    @Test
    fun fifthShortcutIsRefusedAndHiddenAppIsNotShown() {
        show(mapOf(0 to mapOf(id("Alfa") to 0, id("Bravo") to 1, id("Charlie") to 2, id("Delta") to 3)))
        addFromDrawer("Echo")
        rule.onNodeWithTag("shortcutsFull").assertIsDisplayed()
        rule.onNodeWithTag("shortcuts_${id("Echo")}").assertDoesNotExist()
        hidden = setOf(id("Bravo"))
        rule.waitForIdle()
        rule.onNodeWithTag("shortcuts_${id("Bravo")}").assertDoesNotExist()
        rule.onNodeWithTag("shortcutsEmpty_1").assertIsDisplayed()
    }

    @Test
    fun uninstalledAppIsNotShown() {
        show(mapOf(0 to mapOf(id("Alfa") to 0, id("Bravo") to 1)))
        rule.onNodeWithTag("shortcuts_${id("Bravo")}").assertIsDisplayed()
        apps = installed.filter { it.label != "Bravo" }
        rule.waitForIdle()
        rule.onNodeWithTag("shortcuts_${id("Bravo")}").assertDoesNotExist()
        rule.onNodeWithTag("shortcuts_${id("Alfa")}").assertIsDisplayed()
    }

    @Test
    fun reorderOnPage2OnlyWritesPage2() {
        show(mapOf(1 to mapOf(id("Alfa") to 0, id("Bravo") to 1), 0 to mapOf(id("Alfa") to 3)))
        goTo(1)
        val right = context.getString(R.string.dock_move_right)
        val actions = rule.onNodeWithTag("shortcuts_${id("Alfa")}").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnIdle { actions.first { it.label == right }.action() }
        rule.waitForIdle()
        assertEquals(mapOf(1 to mapOf(id("Alfa") to 1, id("Bravo") to 0), 0 to mapOf(id("Alfa") to 3)), shortcuts)
    }

    @Test
    fun searchFieldKeepsFullHeightWithHintAndBothFullMessages() {
        val four = listOf("Alfa", "Bravo", "Charlie", "Delta")
        favs = four.withIndex().associate { id(it.value) to it.index }
        show(four.withIndex().associate { id(it.value) to it.index }.let { mapOf(0 to it) }, hintSeen = false)
        rule.onNodeWithTag("homePager").performTouchInput { swipeUp() }
        rule.onNodeWithText("Echo").performTouchInput { longClick() }
        rule.onNodeWithText(context.getString(R.string.menu_favorite)).performClick()
        rule.onNodeWithText("Echo").performTouchInput { longClick() }
        rule.onNodeWithText(context.getString(R.string.menu_shortcut)).performClick()
        rule.onNodeWithTag("drawerGrip").performTouchInput { swipe(Offset(width / 2f, height / 2f), Offset(width / 2f, height / 2f + 200f)) }
        rule.waitForIdle()
        rule.onNodeWithTag("dockFull").assertIsDisplayed()
        rule.onNodeWithTag("shortcutsFull").assertIsDisplayed()
        // Prikkerne, rækken, docken og søgefeltets boks (ikke kun tekstfeltet) ligger over hinanden uden overlap og klipning.
        val dots = rule.onNodeWithTag("pageDot0").getUnclippedBoundsInRoot()
        val row = rule.onNodeWithTag("shortcuts").getUnclippedBoundsInRoot()
        val dock = rule.onNodeWithTag("dock").getUnclippedBoundsInRoot()
        val search = rule.onNodeWithTag("searchField").getUnclippedBoundsInRoot()
        assertTrue("prikker $dots over række $row", dots.bottom <= row.top)
        assertTrue("række $row over dock $dock", row.bottom <= dock.top)
        assertTrue("dock $dock over søgefelt $search", dock.bottom <= search.top)
        listOf("shortcuts", "dock", "searchField").forEach {
            assertEquals("$it klippes", rule.onNodeWithTag(it).getUnclippedBoundsInRoot(), rule.onNodeWithTag(it).getBoundsInRoot())
        }
        // Boksen er første forfader til tekstfeltet med dockens bredde.
        val dockWidth = rule.onNodeWithTag("dock").fetchSemanticsNode().size.width
        var box: LayoutInfo? = rule.onNodeWithTag("searchField").fetchSemanticsNode().layoutInfo
        while (box != null && box.width < dockWidth) box = box.parentInfo
        assertEquals(DarkIOSize.FieldHeight.value, box!!.height / box.density.density, 0.5f)
    }

    @Test
    fun swipeStartingOnShortcutChangesPage() {
        show(mapOf(0 to mapOf(id("Alfa") to 0, id("Bravo") to 1)))
        rule.onNodeWithTag("shortcuts_${id("Bravo")}").performTouchInput { swipeLeft(startX = centerX, endX = centerX - 600f) }
        rule.waitForIdle()
        rule.onNodeWithTag("page1").assertIsDisplayed()
        assertEquals(mapOf(0 to mapOf(id("Alfa") to 0, id("Bravo") to 1)), shortcuts)
    }

    @Test
    fun longPressOnShortcutOpensMenuWithoutLaunching() {
        show(mapOf(0 to mapOf(id("Alfa") to 0, id("Bravo") to 3)))
        rule.onNodeWithTag("shortcuts_${id("Bravo")}").performTouchInput { longClick() }
        rule.onNodeWithTag("appMenu").assertIsDisplayed()
        assertEquals(0, launched)
    }

    // Langt tryk bruger testuret: advanceTimeBy mellem separate performTouchInput-kald.
    @Test
    fun longPressThenDragMovesShortcutWithoutLaunching() {
        show(mapOf(0 to mapOf(id("Alfa") to 0, id("Bravo") to 1)))
        val node = rule.onNodeWithTag("shortcuts_${id("Alfa")}")
        val w = node.fetchSemanticsNode().size.width.toFloat()
        node.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1000)
        repeat(10) { node.performTouchInput { moveBy(Offset(w / 10f, 0f)) }; rule.mainClock.advanceTimeBy(16) }
        node.performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(mapOf(0 to mapOf(id("Alfa") to 1, id("Bravo") to 0)), shortcuts)
        assertEquals(0, launched)
    }

    @Test
    fun longPressWithTinyJitterStillOpensMenu() {
        show(mapOf(0 to mapOf(id("Alfa") to 0, id("Bravo") to 1)))
        val node = rule.onNodeWithTag("shortcuts_${id("Alfa")}")
        node.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1000)
        node.performTouchInput { moveBy(Offset(2f, 0f)) }
        node.performTouchInput { up() }
        rule.waitForIdle()
        rule.onNodeWithTag("appMenu").assertIsDisplayed()
        assertEquals(0, launched)
    }
}
