package app.darkio

import app.darkio.data.AppEntry
import app.darkio.data.splitApps
import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenAppsTest {
    private val a = AppEntry("a/A#0", "A")
    private val b = AppEntry("b/B#0", "B")
    private val c = AppEntry("c/C#0", "C")

    @Test
    fun hiddenAppIsOnlyInHiddenList() {
        val lists = splitApps(listOf(a, b, c), setOf(b.id))
        assertEquals(listOf(a, c), lists.visible)
        assertEquals(listOf(b), lists.hidden)
    }

    @Test
    fun emptyChoiceTableShowsAllApps() {
        val lists = splitApps(listOf(a, b), emptySet())
        assertEquals(listOf(a, b), lists.visible)
        assertEquals(emptyList<AppEntry>(), lists.hidden)
    }

    @Test
    fun uninstalledAppDisappearsAndReinstallIsStillHidden() {
        val hiddenIds = setOf(b.id)
        val uninstalled = splitApps(listOf(a, c), hiddenIds)
        assertEquals(listOf(a, c), uninstalled.visible)
        assertEquals(emptyList<AppEntry>(), uninstalled.hidden)

        val reinstalled = splitApps(listOf(a, b, c), hiddenIds)
        assertEquals(listOf(a, c), reinstalled.visible)
        assertEquals(listOf(b), reinstalled.hidden)
    }
}
