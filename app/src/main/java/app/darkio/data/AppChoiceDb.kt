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
import kotlinx.coroutines.flow.Flow

/**
 * Brugerens valg for én app. Rækker slettes aldrig, så en geninstalleret app husker sine valg.
 * [customCategory] og [favoriteSlot] er kun lagret her til senere funktioner.
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

@Dao
abstract class AppChoiceDao {
    @Query("SELECT * FROM app_choice WHERE hidden = 1")
    abstract fun hidden(): Flow<List<AppChoice>>

    @Query("SELECT * FROM app_choice WHERE packageName = :packageName AND activity = :activity AND userSerial = :userSerial")
    protected abstract suspend fun find(packageName: String, activity: String, userSerial: Long): AppChoice?

    @Upsert
    protected abstract suspend fun upsert(choice: AppChoice)

    @Transaction
    open suspend fun setHidden(packageName: String, activity: String, userSerial: Long, hidden: Boolean) {
        val choice = find(packageName, activity, userSerial) ?: AppChoice(packageName, activity, userSerial)
        upsert(choice.copy(hidden = hidden))
    }
}

/** Version 1. Ingen destruktiv migration: fremtidige skemaændringer skal have rigtige migrationer. */
@Database(entities = [AppChoice::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun choices(): AppChoiceDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** Én database pr. proces, så rotation ikke åbner en ny hver gang. */
        fun open(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "darkio.db").build().also { instance = it }
        }
    }
}
