package app.darkio

import app.darkio.data.AppChoice
import app.darkio.data.AppEntry
import app.darkio.data.MediaApp
import app.darkio.data.PAGE_COUNT
import app.darkio.data.PageShortcut
import app.darkio.data.WORK_SHORTCUTS_PAGE
import app.darkio.data.appId
import app.darkio.data.buildAppLists
import app.darkio.data.mediaAppList
import app.darkio.data.splitApps
import org.junit.Assert.assertEquals
import org.junit.Test

/** F25: medie-appsene vælges af brugeren, gemmes i rækkefølge og følger skjul/afinstallér som genveje. */
class MediaAppsTest {
    private val a = AppEntry(appId("a", "A", 0), "Alfa")
    private val b = AppEntry(appId("b", "B", 0), "Bravo")
    private val c = AppEntry(appId("c", "C", 0), "Charlie")
    private val rows = listOf(MediaApp("c", "C", 0, 0), MediaApp("a", "A", 0, 2), MediaApp("b", "B", 0, 1))

    @Test
    fun listFollowsStoredPosition() {
        assertEquals(listOf(c, b, a), mediaAppList(listOf(a, b, c), rows))
    }

    @Test
    fun hiddenAndUninstalledAppsAreLeftOut() {
        assertEquals(listOf(c, a), mediaAppList(splitApps(listOf(a, b, c), setOf(b.id)).visible, rows))
        assertEquals(listOf(b), mediaAppList(listOf(b), rows))
    }

    @Test
    fun sameAppForAnotherProfileIsADifferentApp() {
        assertEquals(emptyList<AppEntry>(), mediaAppList(listOf(a), listOf(MediaApp("a", "A", 10, 0))))
    }

    @Test
    fun noRowsMeansEmptyList() {
        assertEquals(emptyList<AppEntry>(), mediaAppList(listOf(a, b), emptyList()))
    }

    @Test
    fun buildAppListsExposesMediaAppsAndLeavesShortcutPagesAlone() {
        val shortcuts = listOf(PageShortcut(0, "a", "A", 0, 1), PageShortcut(WORK_SHORTCUTS_PAGE, "b", "B", 0, 0))
        val lists = buildAppLists(listOf(a, b, c), listOf(AppChoice("b", "B", 0, hidden = true)), shortcuts, rows)
        assertEquals(listOf(c, a), lists.mediaApps)
        assertEquals(4, PAGE_COUNT)
        assertEquals(mapOf(a.id to 1), lists.shortcutSlots[0])
        assertEquals(mapOf(b.id to 0), lists.shortcutSlots[WORK_SHORTCUTS_PAGE])
        assertEquals(emptyMap<String, Int>(), lists.shortcutSlots[3])
    }
}
