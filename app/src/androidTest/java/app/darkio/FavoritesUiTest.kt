package app.darkio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.data.AppEntry
import app.darkio.data.AppLists
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FavoritesUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val installed = listOf("Alfa", "Bravo", "Charlie", "Delta", "Echo").map { AppEntry("${it.lowercase()}/A#0", it) }
    private val favorite = context.getString(R.string.menu_favorite)
    private val unfavorite = context.getString(R.string.menu_unfavorite)
    private var slots by mutableStateOf(emptyMap<String, Int>())
    private var hidden by mutableStateOf(emptySet<String>())

    /** Samme splitApps som AppRepository bruger; [slots] og [hidden] står for rækkerne i Room (pladsen bevares, når en app skjules). */
    private fun show(slotsAtStart: Map<String, Int> = emptyMap()) {
        slots = slotsAtStart
        rule.setContent {
            val lists: AppLists = splitApps(installed, hidden).copy(favoriteSlots = slots)
            HomeScreen(
                apps = lists,
                hintSeen = true,
                onHintSeen = {},
                onLaunch = {},
                onSetHidden = { app, h -> hidden = if (h) hidden + app.id else hidden - app.id },
                onSetFavorites = { changes -> slots = slots + changes.mapNotNull { (app, slot) -> slot?.let { app.id to it } } - changes.filter { it.second == null }.map { it.first.id }.toSet() },
                onSetCategory = { _, _ -> },
                onAppInfo = {},
                onUninstall = {},
            )
        }
    }

    private fun id(name: String) = installed.first { it.label == name }.id

    private fun toggleFromDrawer(name: String, label: String) {
        rule.onNodeWithTag("homePager").performTouchInput { swipeUp() }
        rule.onNodeWithText(name).performTouchInput { longClick() }
        rule.onNodeWithText(label).performClick()
        rule.onNodeWithTag("appMenu").assertDoesNotExist()
        // Luk skuffen med et swipe ned i grebet, så den ikke ligger over docken (virker flere gange i én test).
        rule.onNodeWithTag("drawerGrip").performTouchInput { swipe(Offset(width / 2f, height / 2f), Offset(width / 2f, height / 2f + 200f)) }
        rule.waitForIdle()
        rule.onNodeWithTag("drawerGrid").assertDoesNotExist()
    }


    private fun dockLeft(name: String) = rule.onNodeWithTag("dock_${id(name)}").getUnclippedBoundsInRoot().left

    @Test
    fun firstStartDockIsEmptyWithPlaceholders() {
        show()
        (0..3).forEach { rule.onNodeWithTag("dockEmpty_$it").assertIsDisplayed() }
    }

    @Test
    fun addAndRemoveFavouriteThroughMenu() {
        show()
        toggleFromDrawer("Alfa", favorite)
        rule.onNodeWithTag("dock_${id("Alfa")}").assertIsDisplayed()
        rule.onNodeWithTag("dockEmpty_0").assertDoesNotExist()
        rule.onNodeWithTag("dockEmpty_1").assertIsDisplayed()
        // Langt tryk på dock-appen: menuen tilbyder nu at fjerne den.
        rule.onNodeWithTag("dock_${id("Alfa")}").performTouchInput { longClick() }
        rule.onNodeWithText(unfavorite).performClick()
        rule.onNodeWithTag("dock_${id("Alfa")}").assertDoesNotExist()
        rule.onNodeWithTag("dockEmpty_0").assertIsDisplayed()
    }

    @Test
    fun fullDockSaysSoAndDoesNotOverwrite() {
        show(mapOf(id("Alfa") to 0, id("Bravo") to 1, id("Charlie") to 2, id("Delta") to 3))
        toggleFromDrawer("Echo", favorite)
        rule.onNodeWithTag("dockFull").assertIsDisplayed()
        rule.onNodeWithTag("dock_${id("Echo")}").assertDoesNotExist()
        listOf("Alfa", "Bravo", "Charlie", "Delta").forEach { rule.onNodeWithTag("dock_${id(it)}").assertIsDisplayed() }
    }

    private fun dragRightOneSlot(name: String) {
        val slotPx = rule.onNodeWithTag("dock").fetchSemanticsNode().size.width / 4f
        rule.onNodeWithTag("dock_${id(name)}").performTouchInput {
            down(center)
            moveBy(Offset(slotPx * 0.6f, 0f))
            moveBy(Offset(slotPx * 0.6f, 0f))
            up()
        }
        rule.waitForIdle()
    }

    @Test
    fun secondDragUsesCurrentApps() {
        show(mapOf(id("Alfa") to 0, id("Bravo") to 1))
        dragRightOneSlot("Alfa")
        dragRightOneSlot("Bravo")
        assertEquals(mapOf(id("Alfa") to 0, id("Bravo") to 1), slots)
    }

    @Test
    fun swipeUpStartingOnDockOpensDrawer() {
        show(mapOf(id("Alfa") to 0))
        rule.onNodeWithTag("dock_${id("Alfa")}").performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.onNodeWithTag("drawerGrid").assertIsDisplayed()
    }

    @Test
    fun slotOfHiddenFavouriteIsNotGivenToAnotherApp() {
        show(mapOf(id("Alfa") to 0, id("Bravo") to 1))
        hidden = setOf(id("Bravo"))
        rule.waitForIdle()
        toggleFromDrawer("Charlie", favorite)
        assertEquals(2, slots[id("Charlie")])
        hidden = emptySet()
        rule.waitForIdle()
        listOf("Alfa", "Bravo", "Charlie").forEach { rule.onNodeWithTag("dock_${id(it)}").assertIsDisplayed() }
        rule.onNodeWithTag("dockEmpty_3").assertIsDisplayed()
    }

    private fun action(name: String, label: String) {
        val actions = rule.onNodeWithTag("dock_${id(name)}").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnIdle { actions.first { it.label == label }.action() }
        rule.waitForIdle()
    }

    @Test
    fun customActionsMoveAndEdgesOfferNoPointlessAction() {
        show(mapOf(id("Alfa") to 0, id("Bravo") to 1, id("Delta") to 3))
        val left = context.getString(R.string.dock_move_left)
        val right = context.getString(R.string.dock_move_right)
        fun labels(name: String) = rule.onNodeWithTag("dock_${id(name)}").fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf(right), labels("Alfa"))
        assertEquals(listOf(left), labels("Delta"))
        action("Alfa", right)
        assertEquals(mapOf(id("Alfa") to 1, id("Bravo") to 0, id("Delta") to 3), slots)
        action("Alfa", right)
        assertEquals(2, slots[id("Alfa")])
    }

    @Test
    fun dragOntoHiddenFavouritesSlotDoesNotShareIt() {
        show(mapOf(id("Alfa") to 0, id("Bravo") to 2))
        hidden = setOf(id("Bravo"))
        rule.waitForIdle()
        val slotPx = rule.onNodeWithTag("dock").fetchSemanticsNode().size.width / 4f
        rule.onNodeWithTag("dock_${id("Alfa")}").performTouchInput {
            down(center); moveBy(Offset(slotPx, 0f)); moveBy(Offset(slotPx, 0f)); up()
        }
        rule.waitForIdle()
        assertEquals(mapOf(id("Alfa") to 0, id("Bravo") to 2), slots)
    }

    @Test
    fun dragReordersFavourites() {
        show(mapOf(id("Alfa") to 0, id("Bravo") to 1))
        dragRightOneSlot("Alfa")
        assertEquals(mapOf(id("Alfa") to 1, id("Bravo") to 0), slots)
        assertTrue(dockLeft("Bravo") < dockLeft("Alfa"))
    }

    @Test
    fun hiddenFavouriteIsNotInDockAndReturnsToItsSlot() {
        show(mapOf(id("Alfa") to 0, id("Bravo") to 2))
        hidden = setOf(id("Bravo"))
        rule.waitForIdle()
        rule.onNodeWithTag("dock_${id("Bravo")}").assertDoesNotExist()
        rule.onNodeWithTag("dockEmpty_2").assertIsDisplayed()
        rule.onNodeWithTag("dockEmpty_1").assertIsDisplayed()
        val before = dockLeft("Alfa")
        hidden = emptySet()
        rule.waitForIdle()
        rule.onNodeWithTag("dock_${id("Bravo")}").assertIsDisplayed()
        rule.onNodeWithTag("dockEmpty_1").assertIsDisplayed()
        rule.onNodeWithTag("dockEmpty_2").assertDoesNotExist()
        assertTrue(dockLeft("Bravo") > before)
    }
}
