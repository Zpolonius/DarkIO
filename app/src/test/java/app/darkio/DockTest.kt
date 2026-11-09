package app.darkio

import app.darkio.data.AppEntry
import app.darkio.data.dockSlots
import app.darkio.data.firstFreeSlot
import app.darkio.data.reorderedSlots
import org.junit.Assert.assertEquals
import org.junit.Test

class DockTest {
    private val a = AppEntry("a/A#0", "Alfa")
    private val b = AppEntry("b/B#0", "Bravo")
    private val c = AppEntry("c/C#0", "Charlie")

    @Test
    fun emptyDockHasFourEmptySlots() {
        assertEquals(listOf(null, null, null, null), dockSlots(listOf(a, b), emptyMap()))
        assertEquals(0, firstFreeSlot(emptyList()))
    }

    @Test
    fun favouritesSitInTheirOwnSlots() {
        assertEquals(listOf(null, b, null, a), dockSlots(listOf(a, b, c), mapOf(a.id to 3, b.id to 1)))
    }

    @Test
    fun hiddenOrUninstalledFavouriteIsNotShownAndReturnsToItsSlot() {
        val slots = mapOf(a.id to 0, b.id to 2)
        assertEquals(listOf(a, null, null, null), dockSlots(listOf(a, c), slots))
        assertEquals(listOf(a, null, b, null), dockSlots(listOf(a, b, c), slots))
    }

    @Test
    fun invalidSlotsAndDuplicatesAreIgnored() {
        assertEquals(listOf(a, null, null, null), dockSlots(listOf(a, b, c), mapOf(a.id to 0, b.id to 0, c.id to 7)))
    }

    @Test
    fun fullDockHasNoFreeSlot() {
        assertEquals(null, firstFreeSlot(listOf(0, 1, 2, 3)))
        assertEquals(2, firstFreeSlot(listOf(3, 0, 1)))
    }

    @Test
    fun movingSwapsWithOccupantOrTakesEmptySlot() {
        val shown = listOf(a, b, null, null)
        assertEquals(mapOf(a.id to 1, b.id to 0), reorderedSlots(shown, 0, 1, emptySet()))
        assertEquals(mapOf(a.id to 3), reorderedSlots(shown, 0, 3, emptySet()))
        assertEquals(emptyMap<String, Int>(), reorderedSlots(shown, 0, 0, emptySet()))
        assertEquals(emptyMap<String, Int>(), reorderedSlots(shown, 2, 1, emptySet()))
    }

    @Test
    fun movingOntoSlotReservedByHiddenFavouriteIsRefused() {
        assertEquals(emptyMap<String, Int>(), reorderedSlots(listOf(a, null, null, null), 0, 2, setOf(2)))
        assertEquals(mapOf(a.id to 1), reorderedSlots(listOf(a, null, null, null), 0, 1, setOf(2)))
    }
}
