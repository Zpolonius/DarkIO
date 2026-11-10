package app.darkio

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.data.AppDatabase
import app.darkio.data.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** F6: v1 til v2 mister hverken favoritter, skjulte apps eller kategorivalg; alle sider starter uden genveje. */
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
}
