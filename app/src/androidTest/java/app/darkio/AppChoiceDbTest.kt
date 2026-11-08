package app.darkio

import android.os.Process
import android.os.UserManager
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.data.AppDatabase
import app.darkio.data.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AppChoiceDbTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val dao = db.choices()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val self = "app.darkio/app.darkio.MainActivity#"

    @After
    fun close() {
        // Repositoryets flow skal være stoppet, før databasen lukkes.
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        db.close()
    }

    @Test
    fun emptyTableHasNoHiddenApps() = runBlocking {
        assertEquals(emptyList<Any>(), dao.hidden().first())
    }

    @Test
    fun hiddenChoiceIsKeptAndCanBeUndone() = runBlocking {
        dao.setHidden("p", "p.Main", 0, true)
        assertEquals(listOf("p"), dao.hidden().first().map { it.packageName })
        // Afinstallation rører aldrig databasen: rækken er der stadig, når appen kommer igen.
        assertEquals("p.Main", dao.hidden().first().single().activity)
        dao.setHidden("p", "p.Main", 0, false)
        assertEquals(emptyList<Any>(), dao.hidden().first())
    }

    @Test
    fun otherProfileOrActivityIsNotHidden() = runBlocking {
        dao.setHidden("p", "p.Main", 0, true)
        dao.setHidden("p", "p.Other", 10, false)
        assertEquals(1, dao.hidden().first().size)
    }

    @Test
    fun setHiddenKeepsCategoryAndFavorite() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO app_choice (packageName, activity, userSerial, hidden, customCategory, favoriteSlot) VALUES ('p','p.Main',0,0,'arbejde',2)",
        )
        dao.setHidden("p", "p.Main", 0, true)
        dao.setHidden("p", "p.Main", 0, false)
        db.openHelper.readableDatabase.query("SELECT hidden, customCategory, favoriteSlot FROM app_choice").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
            assertEquals("arbejde", it.getString(1))
            assertEquals(2, it.getInt(2))
        }
    }

    @Test
    fun repositoryHidesAndShowsAgainThroughRoom() = runBlocking {
        val repo = AppRepository(context, scope, dao)
        inst.runOnMainSync { repo.start() }
        val app = withTimeout(10_000) { repo.lists.first { l -> l.visible.any { it.id.startsWith(self) } } }
            .visible.first { it.id.startsWith(self) }
        inst.runOnMainSync { repo.setHidden(app, true) }
        val hidden = withTimeout(10_000) { repo.lists.first { l -> l.hidden.any { it.id == app.id } } }
        assertEquals(false, hidden.visible.any { it.id == app.id })
        inst.runOnMainSync { repo.setHidden(app, false) }
        withTimeout(10_000) { repo.lists.first { l -> l.visible.any { it.id == app.id } } }
        inst.runOnMainSync { repo.stop() }
    }

    @Test
    fun appHiddenInDbIsNeverVisibleAfterColdStart() = runBlocking {
        val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
        dao.setHidden("app.darkio", "app.darkio.MainActivity", serial, true)
        val repo = AppRepository(context, scope, dao)
        inst.runOnMainSync { repo.start() }
        // Den første liste med apps må allerede have appen som skjult (ingen blink af skjulte apps).
        val first = withTimeout(10_000) { repo.lists.first { it.visible.isNotEmpty() || it.hidden.isNotEmpty() } }
        inst.runOnMainSync { repo.stop() }
        assertEquals(false, first.visible.any { it.id.startsWith(self) })
        assertEquals(true, first.hidden.any { it.id.startsWith(self) })
    }
}
