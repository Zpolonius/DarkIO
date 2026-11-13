package app.darkio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.darkio.settings.WorkoutProgramScreen
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import app.darkio.dashboard.WorkoutCard
import app.darkio.data.AppEntry
import app.darkio.data.WorkoutDao
import app.darkio.data.WorkoutDay
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals

import app.darkio.data.WorkoutRecord

private class FakeWorkoutDao(
    val data: MutableMap<Int, WorkoutDay> = mutableMapOf(),
    private val completed: MutableSet<String> = mutableSetOf(),
) : WorkoutDao() {
    init {
        data.values.mapNotNull { it.completedDate }.forEach { completed.add(it) }
    }
    fun getCompletedDatesBlocking() = runBlocking { getCompletedDates() }
    override suspend fun getWorkoutForDay(dayOfWeek: Int): WorkoutDay? = data[dayOfWeek]
    override suspend fun getAllWorkouts(): List<WorkoutDay> = data.values.sortedBy { it.dayOfWeek }
    override suspend fun getCompletedDates(): List<String> = completed.sorted()
    override suspend fun upsert(workout: WorkoutDay) { data[workout.dayOfWeek] = workout }
    override suspend fun insertCompleted(record: WorkoutRecord) { completed.add(record.date) }
    override suspend fun deleteCompleted(date: String) { completed.remove(date) }
    override suspend fun deleteDay(dayOfWeek: Int) { data.remove(dayOfWeek) }
    override suspend fun setCompleted(dayOfWeek: Int, completed: Boolean, dateStr: String) {
        val existing = data[dayOfWeek]
        if (existing != null) {
            data[dayOfWeek] = existing.copy(completedDate = if (completed) dateStr else null)
        }
        if (completed) this.completed.add(dateStr) else this.completed.remove(dateStr)
    }
}

class WorkoutUiTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun workoutCardSwitchesWhenMarkedCompleted() {
        val tuesday = LocalDate.of(2026, 11, 17) // Tuesday = day 2
        val fakeDao = FakeWorkoutDao(
            mutableMapOf(
                2 to WorkoutDay(2, "tirsdag: spinning, romaskine, styrke og RPE", "spinning, romaskine, styrke, RPE"),
            ),
        )

        rule.setContent {
            WorkoutCard(dao = fakeDao, now = { tuesday })
        }

        rule.onNodeWithText("tirsdag: spinning, romaskine, styrke og RPE · RPE 7").assertIsDisplayed()
        rule.onNodeWithText("markér som gennemført").assertIsDisplayed()

        // Klik på "markér som gennemført"
        rule.onNodeWithText("markér som gennemført").performClick()
        rule.waitForIdle()

        // Kortet skifter til "gennemført"
        rule.onNodeWithText("gennemført").assertIsDisplayed()
    }

    @Test
    fun workoutCardShowsHviledagOnRestDay() {
        val wednesday = LocalDate.of(2026, 11, 18) // Wednesday = day 3 (rest day)
        val fakeDao = FakeWorkoutDao()

        rule.setContent {
            WorkoutCard(dao = fakeDao, now = { wednesday })
        }

        rule.onNodeWithText("hviledag_").assertIsDisplayed()
    }

    @Test
    fun workoutCardIsPresentOnHomeScreenPageOne() {
        val alfa = AppEntry("alfa/A#0", "Alfa")
        rule.setContent {
            HomeScreen(
                apps = splitApps(listOf(alfa), emptySet()),
                hintSeen = true,
                onHintSeen = {},
                onLaunch = {},
                onSetHidden = { _, _ -> },
                onSetFavorites = {},
                onSetCategory = { _, _ -> },
                onAppInfo = {},
                onUninstall = {},
            )
        }

        // Swipe til side 2 ("træning", index 1)
        rule.onNodeWithTag("homePager").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        rule.onNodeWithTag("page1").assertIsDisplayed()
    }

    @Test
    fun workoutCardShowsWeekOverviewAndStreak() {
        val tuesday = LocalDate.of(2026, 11, 17) // Tuesday
        val fakeDao = FakeWorkoutDao(
            data = mutableMapOf(
                2 to WorkoutDay(2, "tirsdag: spinning, romaskine, styrke og RPE", "spinning, romaskine, styrke, RPE"),
                4 to WorkoutDay(4, "torsdag: spinning, romaskine, styrke og RPE", "spinning, romaskine, styrke, RPE"),
                7 to WorkoutDay(7, "søndag: spinning, romaskine, styrke og RPE", "spinning, romaskine, styrke, RPE"),
            ),
            completed = mutableSetOf(
                "2026-11-12", // torsdag
                "2026-11-15", // søndag
            ),
        )

        rule.setContent {
            WorkoutCard(dao = fakeDao, now = { tuesday })
        }

        // Ugeoversigten vises med de 7 dage
        rule.onNodeWithTag("workoutWeek").assertIsDisplayed()
        listOf("man", "tir", "ons", "tor", "fre", "lør", "søn").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }

        // 2 pas i træk før tirsdagens pas er gennemført
        rule.onNodeWithText("2 pas i træk").assertIsDisplayed()

        // Gennemfør tirsdagens pas
        rule.onNodeWithText("markér som gennemført").performClick()
        rule.waitForIdle()

        // Nu er stimen 3 pas i træk
        rule.onNodeWithText("3 pas i træk").assertIsDisplayed()
        rule.onNodeWithText("gennemført").assertIsDisplayed()
    }

    private val tuesday = LocalDate.of(2026, 11, 17)

    /** Programredigering og kortet i samme komposition: skift til kortet ligesom at gå tilbage fra indstillinger. */
    private fun showEditor(fakeDao: FakeWorkoutDao): () -> Unit {
        var editing by mutableStateOf(true)
        rule.setContent {
            if (editing) WorkoutProgramScreen(dao = fakeDao, now = { tuesday }) else WorkoutCard(dao = fakeDao, now = { tuesday })
        }
        return { editing = false }
    }

    @Test
    fun addingAWorkoutShowsItOnTheCard() {
        val fakeDao = FakeWorkoutDao(mutableMapOf(2 to WorkoutDay(2, "tirsdag: spinning", "spinning")))
        val close = showEditor(fakeDao)
        rule.onNodeWithTag("programAdd").performClick()
        rule.onNodeWithTag("programExercises_1").performTextInput("løb")
        rule.onNodeWithTag("programRpeUp_1").performClick()
        rule.runOnIdle(close)
        rule.waitForIdle()
        assertEquals(listOf(1, 2), fakeDao.data.keys.sorted())
        assertEquals("mandag: løb", fakeDao.data[1]?.title)
        assertEquals(8, fakeDao.data[1]?.rpe)
        rule.onNodeWithTag("workoutDay_1").assertIsDisplayed()
    }

    @Test
    fun changingDayExercisesAndRpeShowsOnTheCardAndKeepsOtherDaysFree() {
        val fakeDao = FakeWorkoutDao(mutableMapOf(2 to WorkoutDay(2, "tirsdag: spinning", "spinning", rpe = 7)))
        val close = showEditor(fakeDao)
        rule.onNodeWithTag("programExercises_2").performTextClearance()
        rule.onNodeWithTag("programExercises_2").performTextInput("roning")
        rule.onNodeWithTag("programRpeDown_2").performClick()
        rule.onNodeWithTag("programRpe_2").assertTextEquals("6")
        rule.onNodeWithTag("programDay_2_4").performClick() // flyt til torsdag
        rule.onNodeWithTag("programRpe_4").assertTextEquals("6")
        rule.runOnIdle(close)
        rule.waitForIdle()
        assertEquals(listOf(4), fakeDao.data.keys.toList())
        assertEquals("torsdag: roning", fakeDao.data[4]?.title)
        rule.onNodeWithText("hviledag_").assertIsDisplayed() // tirsdag er ikke længere et pas
    }

    @Test
    fun deletingTodaysWorkoutRemovesItAndItsCompletionFromTheCard() {
        val fakeDao = FakeWorkoutDao(
            mutableMapOf(2 to WorkoutDay(2, "tirsdag: spinning", "spinning", completedDate = tuesday.toString(), rpe = 8)),
        )
        val close = showEditor(fakeDao)
        rule.onNodeWithTag("programDelete_2").performClick()
        rule.onNodeWithTag("programExercises_2").assertDoesNotExist()
        rule.runOnIdle(close)
        rule.waitForIdle()
        assertEquals(emptyList<String>(), fakeDao.getCompletedDatesBlocking())
        rule.onNodeWithText("hviledag_").assertIsDisplayed()
    }

    @Test
    fun workoutCardShowsEditedExercisesWithRpe() {
        val fakeDao = FakeWorkoutDao(mutableMapOf(2 to WorkoutDay(2, "tirsdag: roning", "roning", rpe = 9)))
        rule.setContent { WorkoutCard(dao = fakeDao, now = { tuesday }) }
        rule.onNodeWithText("tirsdag: roning · RPE 9").assertIsDisplayed()
    }
}
