package app.darkio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

private class FakeWorkoutDao(
    private val data: MutableMap<Int, WorkoutDay> = mutableMapOf(),
) : WorkoutDao() {
    override suspend fun getWorkoutForDay(dayOfWeek: Int): WorkoutDay? = data[dayOfWeek]
    override suspend fun upsert(workout: WorkoutDay) { data[workout.dayOfWeek] = workout }
    override suspend fun setCompleted(dayOfWeek: Int, completed: Boolean, dateStr: String) {
        val existing = data[dayOfWeek]
        if (existing != null) {
            data[dayOfWeek] = existing.copy(completedDate = if (completed) dateStr else null)
        }
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

        rule.onNodeWithText("tirsdag: spinning, romaskine, styrke og RPE").assertIsDisplayed()
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
}
