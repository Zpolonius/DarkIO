package app.darkio

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.data.AppDatabase
import app.darkio.data.MIGRATION_1_2
import app.darkio.data.MIGRATION_2_3
import app.darkio.data.MIGRATION_3_4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** F6, F4: migrationstests for databasen. */
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2KeepsAllChoicesAndStartsWithNoShortcuts() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL("INSERT INTO app_choice VALUES ('fav','fav.Main',0,0,NULL,2)")
            execSQL("INSERT INTO app_choice VALUES ('hid','hid.Main',0,1,NULL,NULL)")
            execSQL("INSERT INTO app_choice VALUES ('cat','cat.Main',10,0,'arbejde',NULL)")
            execSQL("INSERT INTO app_choice VALUES ('all','all.Main',0,1,'spil',3)")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test", 2, true, MIGRATION_1_2)
        db.query("SELECT packageName, userSerial, hidden, customCategory, favoriteSlot FROM app_choice ORDER BY packageName").use {
            val rows = generateSequence { if (it.moveToNext()) listOf(it.getString(0), it.getLong(1), it.getInt(2), it.getString(3), if (it.isNull(4)) null else it.getInt(4)) else null }.toList()
            assertEquals(
                listOf(
                    listOf("all", 0L, 1, "spil", 3),
                    listOf("cat", 10L, 0, "arbejde", null),
                    listOf("fav", 0L, 0, null, 2),
                    listOf("hid", 0L, 1, null, null),
                ),
                rows,
            )
        }
        db.query("SELECT COUNT(*) FROM page_shortcut").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun migrate2To3AddsWorkoutDayAndSeedsProgramAndPreservesChoicesAndShortcuts() {
        helper.createDatabase("migration-test-2-3", 2).apply {
            execSQL("INSERT INTO app_choice VALUES ('fav','fav.Main',0,0,NULL,2)")
            execSQL("INSERT INTO page_shortcut VALUES (1,'shortcut','shortcut.Main',0,0)")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test-2-3", 3, true, MIGRATION_1_2, MIGRATION_2_3)
        db.query("SELECT dayOfWeek, title FROM workout_day ORDER BY dayOfWeek").use {
            val rows = generateSequence { if (it.moveToNext()) listOf(it.getInt(0), it.getString(1)) else null }.toList()
            assertEquals(
                listOf(
                    listOf(2, "tirsdag: spinning, romaskine, styrke og RPE"),
                    listOf(4, "torsdag: spinning, romaskine, styrke og RPE"),
                    listOf(7, "søndag: spinning, romaskine, styrke og RPE"),
                ),
                rows,
            )
        }
        db.query("SELECT packageName, favoriteSlot FROM app_choice WHERE packageName = 'fav'").use {
            it.moveToFirst()
            assertEquals("fav", it.getString(0))
            assertEquals(2, it.getInt(1))
        }
        db.query("SELECT packageName, slot FROM page_shortcut WHERE page = 1").use {
            it.moveToFirst()
            assertEquals("shortcut", it.getString(0))
            assertEquals(0, it.getInt(1))
        }
    }

    @Test
    fun migrate3To4AddsWorkoutRecordAndPreservesCompletedDatesAndExistingData() {
        helper.createDatabase("migration-test-3-4", 3).apply {
            execSQL("INSERT INTO app_choice VALUES ('fav','fav.Main',0,0,NULL,2)")
            execSQL("INSERT INTO page_shortcut VALUES (1,'shortcut','shortcut.Main',0,0)")
            execSQL("INSERT INTO workout_day VALUES (2, 'tirsdag: spinning, romaskine, styrke og RPE', 'spinning, romaskine, styrke, RPE', '2026-11-10')")
            execSQL("INSERT INTO workout_day VALUES (4, 'torsdag: spinning, romaskine, styrke og RPE', 'spinning, romaskine, styrke, RPE', NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test-3-4", 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        db.query("SELECT date FROM workout_record").use {
            val rows = generateSequence { if (it.moveToNext()) it.getString(0) else null }.toList()
            assertEquals(listOf("2026-11-10"), rows)
        }
        db.query("SELECT dayOfWeek, completedDate FROM workout_day WHERE dayOfWeek = 2").use {
            it.moveToFirst()
            assertEquals(2, it.getInt(0))
            assertEquals("2026-11-10", it.getString(1))
        }
        db.query("SELECT packageName FROM app_choice WHERE packageName = 'fav'").use {
            it.moveToFirst()
            assertEquals("fav", it.getString(0))
        }
        db.query("SELECT packageName, slot FROM page_shortcut WHERE page = 1").use {
            it.moveToFirst()
            assertEquals("shortcut", it.getString(0))
            assertEquals(0, it.getInt(1))
        }
    }
}
