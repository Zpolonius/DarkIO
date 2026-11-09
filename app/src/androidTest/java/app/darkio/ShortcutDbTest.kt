package app.darkio

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.data.AppChoice
import app.darkio.data.AppDatabase
import app.darkio.data.PageShortcut
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** F6: genveje gemmes pr. side i Room og rører ikke app_choice. */
class ShortcutDbTest {
    private val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
    private val dao = db.choices()

    @After
    fun close() = db.close()

    @Test
    fun shortcutsArePerPageAndCanBeMovedAndRemoved() = runBlocking {
        dao.setFavoriteSlots(listOf(AppChoice("p", "p.Main", 0, favoriteSlot = 1)))
        dao.setShortcuts(listOf(PageShortcut(1, "p", "p.Main", 0, 0), PageShortcut(2, "p", "p.Main", 0, 3)), emptyList())
        assertEquals(setOf(PageShortcut(1, "p", "p.Main", 0, 0), PageShortcut(2, "p", "p.Main", 0, 3)), dao.shortcuts().first().toSet())
        // Flyt på side 1 og fjern på side 2 i én transaktion; hver side rører kun sig selv.
        dao.setShortcuts(listOf(PageShortcut(1, "p", "p.Main", 0, 2)), listOf(PageShortcut(2, "p", "p.Main", 0, 0)))
        assertEquals(listOf(PageShortcut(1, "p", "p.Main", 0, 2)), dao.shortcuts().first())
        assertEquals(1, dao.all().first().single().favoriteSlot)
    }
}
