package app.darkio.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Træningspas for en ugedag (1=mandag ... 7=søndag). */
@Entity(tableName = "workout_day")
data class WorkoutDay(
    @PrimaryKey val dayOfWeek: Int, // 1..7
    val title: String,
    val exercises: String,
    val completedDate: String? = null
)

@Dao
abstract class WorkoutDao {
    @Query("SELECT * FROM workout_day WHERE dayOfWeek = :dayOfWeek")
    abstract suspend fun getWorkoutForDay(dayOfWeek: Int): WorkoutDay?

    @androidx.room.Upsert
    abstract suspend fun upsert(workout: WorkoutDay)

    @Transaction
    open suspend fun setCompleted(dayOfWeek: Int, completed: Boolean, dateStr: String) {
        val existing = getWorkoutForDay(dayOfWeek)
        if (existing != null) {
            upsert(existing.copy(completedDate = if (completed) dateStr else null))
        }
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `workout_day` (`dayOfWeek` INTEGER NOT NULL, `title` TEXT NOT NULL, `exercises` TEXT NOT NULL, `completedDate` TEXT, PRIMARY KEY(`dayOfWeek`))")
        db.execSQL("INSERT OR REPLACE INTO `workout_day` (`dayOfWeek`, `title`, `exercises`, `completedDate`) VALUES (2, 'tirsdag: spinning, romaskine, styrke og RPE', 'spinning, romaskine, styrke, RPE', NULL)")
        db.execSQL("INSERT OR REPLACE INTO `workout_day` (`dayOfWeek`, `title`, `exercises`, `completedDate`) VALUES (4, 'torsdag: spinning, romaskine, styrke og RPE', 'spinning, romaskine, styrke, RPE', NULL)")
        db.execSQL("INSERT OR REPLACE INTO `workout_day` (`dayOfWeek`, `title`, `exercises`, `completedDate`) VALUES (7, 'søndag: spinning, romaskine, styrke og RPE', 'spinning, romaskine, styrke, RPE', NULL)")
    }
}
