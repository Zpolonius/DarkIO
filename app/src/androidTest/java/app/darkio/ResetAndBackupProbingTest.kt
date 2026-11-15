package app.darkio

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.data.AccentChoice
import app.darkio.data.AppChoice
import app.darkio.data.AppDatabase
import app.darkio.data.HomeCard
import app.darkio.data.PageShortcut
import app.darkio.data.WorkoutDay
import app.darkio.data.WorkoutRecord
import app.darkio.data.darkIoPrefs
import app.darkio.data.hintSeen
import app.darkio.data.markHintSeen
import app.darkio.data.resetPreferences
import app.darkio.data.settings
import app.darkio.data.updateSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

class ResetAndBackupProbingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun resetAllClearsRoomAndDataStoreIncludingUninstalledAndReseedsWorkouts() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.choices()
            val wDao = db.workouts()

            // Setup custom room state (both installed and uninstalled app choices)
            dao.setHidden("app.uninstalled", "Main", 0, true)
            dao.setFavoriteSlots(listOf(AppChoice("app.uninstalled", "Main", 0, favoriteSlot = 1)))
            dao.setCategory("app.uninstalled", "Main", 0, "spil")
            dao.setFavoriteSlots(listOf(AppChoice("app.installed", "Main", 0, favoriteSlot = 0)))
            dao.setShortcuts(listOf(PageShortcut(1, "app.installed", "Main", 0, 0)), emptyList())

            wDao.upsert(WorkoutDay(1, "mandag", "løb"))
            wDao.insertCompleted(WorkoutRecord("2026-11-13"))

            // Setup custom DataStore state
            context.darkIoPrefs.updateSettings {
                it.copy(
                    accent = AccentChoice.Coral,
                    cards = setOf(HomeCard.Calendar),
                    stepGoal = 15000L,
                    scrimTop = 65,
                )
            }
            context.darkIoPrefs.markHintSeen()

            // Verify preconditions
            assertEquals(2, dao.all().first().size)
            assertEquals(1, dao.shortcuts().first().size)
            assertEquals(listOf("2026-11-13"), wDao.getCompletedDates())
            assertTrue(context.darkIoPrefs.hintSeen().first())
            val sBefore = context.darkIoPrefs.settings().first()
            assertEquals(AccentChoice.Coral, sBefore.accent)
            assertEquals(setOf(HomeCard.Calendar), sBefore.cards)
            assertEquals(15000L, sBefore.stepGoal)
            assertEquals(65, sBefore.scrimTop)

            // Execute resetAll matching MainActivity
            db.resetAll()
            context.darkIoPrefs.resetPreferences()

            // Assert Room: choices including uninstalled apps are completely wiped
            assertEquals(emptyList<AppChoice>(), dao.all().first())
            assertEquals(emptyList<PageShortcut>(), dao.shortcuts().first())
            assertEquals(emptyList<String>(), wDao.getCompletedDates())

            // Assert Room: workout days are reseeded to default (Tue, Thu, Sun)
            val days = wDao.getAllWorkouts()
            assertEquals(listOf(2, 4, 7), days.map { it.dayOfWeek })
            assertEquals(listOf(null, null, null), days.map { it.completedDate })

            // Assert DataStore: all settings reset to defaults
            val sAfter = context.darkIoPrefs.settings().first()
            assertEquals(AccentChoice.Lime, sAfter.accent)
            assertEquals(HomeCard.entries.toSet(), sAfter.cards)
            assertEquals(8000L, sAfter.stepGoal)
            assertEquals(80, sAfter.scrimTop)

            // Assert DataStore: hint seen is reset to false
            assertFalse(context.darkIoPrefs.hintSeen().first())
        } finally {
            db.close()
            context.darkIoPrefs.resetPreferences()
        }
    }

    @Test
    fun dataExtractionRulesIncludesRoomWalAndDataStore() {
        val parser = context.resources.getXml(R.xml.data_extraction_rules)
        val databaseIncludes = mutableListOf<String>()
        val fileIncludes = mutableListOf<String>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "include") {
                var domain: String? = null
                var path: String? = null
                for (i in 0 until parser.attributeCount) {
                    when (parser.getAttributeName(i)) {
                        "domain" -> domain = parser.getAttributeValue(i)
                        "path" -> path = parser.getAttributeValue(i)
                    }
                }
                if (domain == "database" && path != null) databaseIncludes.add(path)
                if (domain == "file" && path != null) fileIncludes.add(path)
            }
            eventType = parser.next()
        }
        assertTrue("database includes contain '.' to cover WAL sidecars", databaseIncludes.contains("."))
        assertTrue("file includes contain DataStore preferences", fileIncludes.contains("datastore/darkio.preferences_pb"))
    }
}
