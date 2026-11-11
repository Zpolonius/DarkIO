package app.darkio.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Brugerens valg for én app. Rækker slettes aldrig, så en geninstalleret app husker sine valg.
 * [customCategory] er kategoriens slug; [favoriteSlot] er pladsen (0-3) i docken, null hvis appen ikke er favorit.
 */
@Entity(tableName = "app_choice", primaryKeys = ["packageName", "activity", "userSerial"])
data class AppChoice(
    val packageName: String,
    val activity: String,
    val userSerial: Long,
    val hidden: Boolean = false,
    val customCategory: String? = null,
    val favoriteSlot: Int? = null,
)

/** Genvej på én hjemskærmsside; [slot] (0-3) er pladsen i rækken. Rækken slettes når genvejen fjernes. */
@Entity(tableName = "page_shortcut", primaryKeys = ["page", "packageName", "activity", "userSerial"])
data class PageShortcut(
    val page: Int,
    val packageName: String,
    val activity: String,
    val userSerial: Long,
    val slot: Int,
)

@Dao
abstract class AppChoiceDao {
    @Query("SELECT * FROM app_choice")
    abstract fun all(): Flow<List<AppChoice>>

    @Query("SELECT * FROM app_choice WHERE packageName = :packageName AND activity = :activity AND userSerial = :userSerial")
    protected abstract suspend fun find(packageName: String, activity: String, userSerial: Long): AppChoice?

    @Upsert
    protected abstract suspend fun upsert(choice: AppChoice)

    @Transaction
    open suspend fun setHidden(packageName: String, activity: String, userSerial: Long, hidden: Boolean) {
        val choice = find(packageName, activity, userSerial) ?: AppChoice(packageName, activity, userSerial)
        upsert(choice.copy(hidden = hidden))
    }

    /** Sætter [AppChoice.favoriteSlot] for hver ændring (null fjerner favoritten) i én transaktion; skjult og kategori bevares. */
    @Transaction
    open suspend fun setFavoriteSlots(changes: List<AppChoice>) {
        changes.forEach {
            val choice = find(it.packageName, it.activity, it.userSerial) ?: AppChoice(it.packageName, it.activity, it.userSerial)
            upsert(choice.copy(favoriteSlot = it.favoriteSlot))
        }
    }

    @Query("SELECT * FROM page_shortcut")
    abstract fun shortcuts(): Flow<List<PageShortcut>>

    @Upsert
    protected abstract suspend fun upsertShortcuts(shortcuts: List<PageShortcut>)

    @Query("DELETE FROM page_shortcut WHERE page = :page AND packageName = :packageName AND activity = :activity AND userSerial = :userSerial")
    protected abstract suspend fun deleteShortcut(page: Int, packageName: String, activity: String, userSerial: Long)

    /** Skriver pladser og fjerner genveje på én side i én transaktion; andre sider og app_choice røres ikke. */
    @Transaction
    open suspend fun setShortcuts(set: List<PageShortcut>, remove: List<PageShortcut>) {
        remove.forEach { deleteShortcut(it.page, it.packageName, it.activity, it.userSerial) }
        upsertShortcuts(set)
    }

    @Transaction
    open suspend fun setCategory(packageName: String, activity: String, userSerial: Long, category: String) {
        val choice = find(packageName, activity, userSerial) ?: AppChoice(packageName, activity, userSerial)
        upsert(choice.copy(customCategory = category))
    }
}

/** Version 2 tilføjer page_shortcut. Eksisterende rækker i app_choice (favoritter, skjulte, kategorier) røres ikke; ingen destruktiv migration. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `page_shortcut` (`page` INTEGER NOT NULL, `packageName` TEXT NOT NULL, `activity` TEXT NOT NULL, `userSerial` INTEGER NOT NULL, `slot` INTEGER NOT NULL, PRIMARY KEY(`page`, `packageName`, `activity`, `userSerial`))")
    }
}

@Database(entities = [AppChoice::class, PageShortcut::class, WorkoutDay::class, WorkoutRecord::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun choices(): AppChoiceDao
    abstract fun workouts(): WorkoutDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** Én database pr. proces, så rotation ikke åbner en ny hver gang. */
        fun open(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "darkio.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        db.execSQL("INSERT OR REPLACE INTO `workout_day` (`dayOfWeek`, `title`, `exercises`, `completedDate`) VALUES (2, 'tirsdag: spinning, romaskine, styrke og RPE', 'spinning, romaskine, styrke, RPE', NULL)")
                        db.execSQL("INSERT OR REPLACE INTO `workout_day` (`dayOfWeek`, `title`, `exercises`, `completedDate`) VALUES (4, 'torsdag: spinning, romaskine, styrke og RPE', 'spinning, romaskine, styrke, RPE', NULL)")
                        db.execSQL("INSERT OR REPLACE INTO `workout_day` (`dayOfWeek`, `title`, `exercises`, `completedDate`) VALUES (7, 'søndag: spinning, romaskine, styrke og RPE', 'spinning, romaskine, styrke, RPE', NULL)")
                    }
                })
                .build().also { instance = it }
        }
    }
}
