package app.darkio.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import app.darkio.R
import app.darkio.data.AppDatabase
import app.darkio.data.WorkoutDao
import app.darkio.data.WorkoutDay
import app.darkio.data.calculateWorkoutStreak
import app.darkio.data.isWorkoutCompletedOn
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WorkoutCardData(
    val todayWorkout: WorkoutDay?,
    val isCompleted: Boolean,
    val streak: Int,
    val today: LocalDate,
)

/** Viser dagens pas fra træningsprogrammet eller "hviledag_" og antal pas i træk (F4). */
@Composable
fun WorkoutCard(
    modifier: Modifier = Modifier,
    dao: WorkoutDao = AppDatabase.open(LocalContext.current).workouts(),
    onCompletedChanged: () -> Unit = {},
    verticalPadding: Dp = DarkIOSpace.S4,
    now: () -> LocalDate = LocalDate::now,
) {
    var reloadTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LoadedCard(
        load = {
            val today = now()
            val dow = today.dayOfWeek.value
            val allWorkouts = dao.getAllWorkouts()
            val todayWorkout = allWorkouts.firstOrNull { it.dayOfWeek == dow } ?: dao.getWorkoutForDay(dow)
            val trainingDays = allWorkouts.map { it.dayOfWeek }.toSet()
            val completedDates = dao.getCompletedDates().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
            val isCompleted = isWorkoutCompletedOn(dao, today)
            val streak = calculateWorkoutStreak(trainingDays, completedDates, today)
            CardState.Content(WorkoutCardData(todayWorkout, isCompleted, streak, today))
        },
        modifier = modifier,
        verticalPadding = verticalPadding,
        reloadTrigger = reloadTrigger,
    ) { data ->
        Column(Modifier.fillMaxWidth()) {
            if (data.todayWorkout != null) {
                BasicText(
                    stringResource(R.string.workout_title_rpe, data.todayWorkout.title, data.todayWorkout.rpe),
                    style = DarkIOType.Item.copy(color = DarkIOColors.Ink),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        stringResource(if (data.isCompleted) R.string.workout_completed else R.string.workout_mark_completed),
                        modifier = Modifier
                            .heightIn(min = DarkIOSize.TouchMin)
                            .clickable(role = Role.Button) {
                                scope.launch(Dispatchers.IO) {
                                    dao.setCompleted(data.today.dayOfWeek.value, !data.isCompleted, data.today.toString())
                                    reloadTrigger++
                                    withContext(Dispatchers.Main) {
                                        onCompletedChanged()
                                    }
                                }
                            }
                            .padding(vertical = DarkIOSpace.S2),
                        style = DarkIOType.Button.copy(color = DarkIOColors.Accent),
                    )
                    BasicText(
                        stringResource(R.string.workout_streak, data.streak),
                        style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft),
                        modifier = Modifier.testTag("workoutStreak"),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        buildAnnotatedString {
                            append(stringResource(R.string.workout_rest_day))
                            withStyle(SpanStyle(color = DarkIOColors.Accent)) { append("_") }
                        },
                        style = DarkIOType.Item.copy(color = DarkIOColors.InkSoft),
                    )
                    BasicText(
                        stringResource(R.string.workout_streak, data.streak),
                        style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft),
                        modifier = Modifier.testTag("workoutStreak"),
                    )
                }
            }
        }
    }
}
