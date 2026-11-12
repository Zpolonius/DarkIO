package app.darkio.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.darkio.R
import app.darkio.data.AppDatabase
import app.darkio.data.WorkoutDao
import app.darkio.data.WorkoutDay
import app.darkio.data.calculateWorkoutStreak
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class WorkoutCardData(
    val todayWorkout: WorkoutDay?,
    val trainingDays: Set<Int>,
    val completedDates: Set<LocalDate>,
    val streak: Int,
)

/** Viser dagens pas fra træningsprogrammet eller "hviledag_", ugeoversigt og antal pas i træk (F4). */
@Composable
fun WorkoutCard(
    modifier: Modifier = Modifier,
    dao: WorkoutDao = AppDatabase.open(LocalContext.current).workouts(),
    now: () -> LocalDate = LocalDate::now,
) {
    var reloadTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val today = now()
    val dow = today.dayOfWeek.value
    val weekDays = stringArrayResource(R.array.workout_week_days)

    LoadedCard(
        load = {
            val allWorkouts = dao.getAllWorkouts()
            val todayWorkout = allWorkouts.firstOrNull { it.dayOfWeek == dow } ?: dao.getWorkoutForDay(dow)
            val trainingDays = allWorkouts.map { it.dayOfWeek }.toSet()
            val completedDates = dao.getCompletedDates().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
            val streak = calculateWorkoutStreak(trainingDays, completedDates, today)
            CardState.Content(WorkoutCardData(todayWorkout, trainingDays, completedDates, streak))
        },
        modifier = modifier,
        reloadTrigger = reloadTrigger,
    ) { data ->
        Column(Modifier.fillMaxWidth()) {
            if (data.todayWorkout != null) {
                val isCompleted = today in data.completedDates || data.todayWorkout.completedDate == today.toString()
                BasicText(
                    data.todayWorkout.title,
                    style = DarkIOType.Item.copy(color = DarkIOColors.Ink),
                    modifier = Modifier.padding(bottom = DarkIOSpace.S1),
                )
                Spacer(Modifier.height(DarkIOSpace.S2))
                BasicText(
                    stringResource(if (isCompleted) R.string.workout_completed else R.string.workout_mark_completed),
                    modifier = Modifier
                        .heightIn(min = DarkIOSize.TouchMin)
                        .clickable(role = Role.Button) {
                            scope.launch(Dispatchers.IO) {
                                dao.setCompleted(dow, !isCompleted, today.toString())
                                reloadTrigger++
                            }
                        }
                        .padding(vertical = DarkIOSpace.S2),
                    style = DarkIOType.Button.copy(color = DarkIOColors.Accent),
                )
            } else {
                BasicText(
                    buildAnnotatedString {
                        append(stringResource(R.string.workout_rest_day))
                        withStyle(SpanStyle(color = DarkIOColors.Accent)) { append("_") }
                    },
                    style = DarkIOType.Item.copy(color = DarkIOColors.InkSoft),
                )
            }
            Spacer(Modifier.height(DarkIOSpace.S3))
            BasicText(
                stringResource(R.string.workout_streak, data.streak),
                style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft),
                modifier = Modifier.testTag("workoutStreak"),
            )
            Spacer(Modifier.height(DarkIOSpace.S2))
            WorkoutWeekRow(
                trainingDays = data.trainingDays,
                completedDates = data.completedDates,
                today = today,
                weekDays = weekDays,
                modifier = Modifier.testTag("workoutWeek"),
            )
        }
    }
}

@Composable
fun WorkoutWeekRow(
    trainingDays: Set<Int>,
    completedDates: Set<LocalDate>,
    today: LocalDate,
    weekDays: Array<String>,
    modifier: Modifier = Modifier,
) {
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0..6) {
            val date = monday.plusDays(i.toLong())
            val dayDow = i + 1
            val isTrainingDay = dayDow in trainingDays
            val isCompleted = date in completedDates
            val isToday = date == today
            val dayName = weekDays.getOrElse(i) { "" }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .testTag("workoutDay_$dayDow")
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$dayName: ${if (isCompleted) "gennemført" else if (isTrainingDay) "træning" else "hvile"}"
                    },
            ) {
                BasicText(
                    text = dayName,
                    style = DarkIOType.Label.copy(
                        color = if (isToday) DarkIOColors.Accent else DarkIOColors.InkMuted,
                    ),
                )
                Spacer(Modifier.height(DarkIOSpace.S1))
                val indicator = if (isCompleted) "✓" else if (isTrainingDay) "○" else "·"
                val indicatorColor = if (isCompleted) {
                    DarkIOColors.Accent
                } else if (isTrainingDay) {
                    DarkIOColors.InkSoft
                } else {
                    DarkIOColors.LineStrong
                }
                BasicText(
                    text = indicator,
                    style = DarkIOType.Data.copy(color = indicatorColor),
                )
            }
        }
    }
}
