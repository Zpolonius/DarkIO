package app.darkio.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

/** Træningspas for en ugedag (1=mandag ... 7=søndag). */
@Entity(tableName = "workout_day")
data class WorkoutDay(
    @PrimaryKey val dayOfWeek: Int, // 1..7
    val title: String,
    val exercises: String,
    val completedDate: String? = null,
    @ColumnInfo(defaultValue = "7") val rpe: Int = 7,
)

/** Historik over gennemførte pas. */
@Entity(tableName = "workout_record")
data class WorkoutRecord(
    @PrimaryKey val date: String, // "YYYY-MM-DD"
)

@Dao
abstract class WorkoutDao {
    @Query("SELECT * FROM workout_day WHERE dayOfWeek = :dayOfWeek")
    abstract suspend fun getWorkoutForDay(dayOfWeek: Int): WorkoutDay?

    @Query("SELECT * FROM workout_day ORDER BY dayOfWeek")
    abstract suspend fun getAllWorkouts(): List<WorkoutDay>

    @Query("SELECT date FROM workout_record ORDER BY date")
    abstract suspend fun getCompletedDates(): List<String>

    @androidx.room.Upsert
    abstract suspend fun upsert(workout: WorkoutDay)

    @androidx.room.Upsert
    abstract suspend fun insertCompleted(record: WorkoutRecord)

    @Query("DELETE FROM workout_record WHERE date = :date")
    abstract suspend fun deleteCompleted(date: String)

    @Query("DELETE FROM workout_day WHERE dayOfWeek = :dayOfWeek")
    abstract suspend fun deleteDay(dayOfWeek: Int)

    /**
     * Gemmer [workout]; [oldDay] er dagen passet havde før (null for et nyt pas). Flyttes passet væk fra i dag,
     *. Et pas flyttet til i dag starter uden "gennemført" (i dag var fri, så der er ingen post at slette). Historik for tidligere datoer røres ikke.
     */
    @Transaction
    open suspend fun saveWorkout(oldDay: Int?, workout: WorkoutDay, today: String, todayDow: Int) {
        val moved = oldDay != workout.dayOfWeek
        val kept = if (moved) null else getWorkoutForDay(workout.dayOfWeek)?.completedDate
        if (oldDay != null && moved) deleteWorkout(oldDay, today, todayDow)
        upsert(workout.copy(completedDate = kept))
    }

    @Transaction
    open suspend fun deleteWorkout(dayOfWeek: Int, today: String, todayDow: Int) {
        deleteDay(dayOfWeek)
        if (dayOfWeek == todayDow) deleteCompleted(today)
    }

    @Transaction
    open suspend fun setCompleted(dayOfWeek: Int, completed: Boolean, dateStr: String) {
        val existing = getWorkoutForDay(dayOfWeek)
        if (existing != null) {
            upsert(existing.copy(completedDate = if (completed) dateStr else null))
        }
        if (completed) {
            insertCompleted(WorkoutRecord(dateStr))
        } else {
            deleteCompleted(dateStr)
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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `workout_record` (`date` TEXT NOT NULL, PRIMARY KEY(`date`))")
        db.execSQL("INSERT OR IGNORE INTO `workout_record` (`date`) SELECT `completedDate` FROM `workout_day` WHERE `completedDate` IS NOT NULL")
    }
}

/**
 * Version 5 tilføjer rpe (standard 7). Alle rækker og al historik bevares. Uændrede seed-pas mister kun det overflødige ", RPE"
 * i teksten (RPE vises nu som tal); andre øvelsestekster røres ikke.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `workout_day` ADD COLUMN `rpe` INTEGER NOT NULL DEFAULT 7")
        db.execSQL("UPDATE `workout_day` SET `title` = substr(`title`, 1, instr(`title`, ':')) || ' spinning, romaskine, styrke', `exercises` = 'spinning, romaskine, styrke' WHERE `exercises` = 'spinning, romaskine, styrke, RPE'")
    }
}

/**
 * Beregner antal pas i træk.
 * Hviledage (dage uden for [trainingDays]) bryder ikke stimen, et misset pas gør.
 */
fun calculateWorkoutStreak(
    trainingDays: Set<Int>,
    completedDates: Set<LocalDate>,
    today: LocalDate,
): Int {
    if (trainingDays.isEmpty() || completedDates.isEmpty()) return 0
    val validCompleted = completedDates.filter { !it.isAfter(today) }.toSet()
    if (validCompleted.isEmpty()) return 0

    var streak = 0
    if (today in validCompleted) {
        streak++
    }

    var checkDate = today.minusDays(1)
    val earliest = validCompleted.minOrNull() ?: return streak
    while (!checkDate.isBefore(earliest)) {
        if (checkDate in validCompleted) {
            streak++
        } else if (checkDate.dayOfWeek.value in trainingDays) {
            break
        }
        checkDate = checkDate.minusDays(1)
    }
    return streak
}
