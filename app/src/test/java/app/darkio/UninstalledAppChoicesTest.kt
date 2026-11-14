package app.darkio

import app.darkio.data.AppCategory
import app.darkio.data.AppChoice
import app.darkio.data.AppEntry
import app.darkio.data.PageShortcut
import app.darkio.data.appId
import app.darkio.data.buildAppLists
import app.darkio.data.firstFreeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UninstalledAppChoicesTest {
    private val installedApp = AppEntry(appId("app.installed", "Main", 0), "Installed", category = AppCategory.WORK)

    @Test
    fun choicesForUninstalledAppsAreIgnoredInListsAndSlots() {
        val choices = listOf(
            AppChoice("app.uninstalled", "Main", 0, hidden = true, customCategory = "spil", favoriteSlot = 0),
        )
        val shortcuts = listOf(
            PageShortcut(0, "app.uninstalled", "Main", 0, slot = 0),
        )

        val lists = buildAppLists(listOf(installedApp), choices, shortcuts)

        assertEquals(listOf(installedApp), lists.visible)
        assertTrue(lists.hidden.isEmpty())
        assertTrue("Favorite slots should not contain uninstalled apps", lists.favoriteSlots.isEmpty())
        assertTrue("Shortcut slots should not contain uninstalled apps", lists.shortcutSlots[0].orEmpty().isEmpty())
        assertEquals(0, firstFreeSlot(lists.favoriteSlots.values))
        assertEquals(0, firstFreeSlot(lists.shortcutSlots[0].orEmpty().values))
    }
}
