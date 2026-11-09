package app.darkio

import app.darkio.data.AppEntry
import app.darkio.data.PageShortcut
import app.darkio.data.appId
import app.darkio.data.dockSlots
import app.darkio.data.shortcutSlotsOnPage
import org.junit.Assert.assertEquals
import org.junit.Test

class PageShortcutsTest {
    private val a = AppEntry(appId("a", "A", 0), "Alfa")
    private val b = AppEntry(appId("b", "B", 0), "Bravo")
    private val rows = listOf(
        PageShortcut(1, "a", "A", 0, 2),
        PageShortcut(1, "b", "B", 0, 0),
        PageShortcut(0, "a", "A", 0, 3),
    )

    @Test
    fun eachPageOnlySeesItsOwnShortcuts() {
        assertEquals(mapOf(a.id to 3), shortcutSlotsOnPage(rows, 0))
        assertEquals(mapOf(a.id to 2, b.id to 0), shortcutSlotsOnPage(rows, 1))
        assertEquals(emptyMap<String, Int>(), shortcutSlotsOnPage(rows, 2))
    }

    @Test
    fun hiddenOrUninstalledShortcutIsNotShown() {
        val slots = shortcutSlotsOnPage(rows, 1)
        assertEquals(listOf(b, null, a, null), dockSlots(listOf(a, b), slots))
        assertEquals(listOf(b, null, null, null), dockSlots(listOf(b), slots))
        assertEquals(listOf(null, null, a, null), dockSlots(listOf(a), slots))
    }
}
