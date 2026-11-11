package app.darkio.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.darkio.R
import app.darkio.data.AppDatabase
import app.darkio.data.WorkoutDao
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Viser dagens pas fra træningsprogrammet eller "hviledag_". Kan markeres som gennemført (F4). */
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

    LoadedCard(
        load = {
            val workout = dao.getWorkoutForDay(dow)
            CardState.Content(workout)
        },
        modifier = modifier,
        reloadTrigger = reloadTrigger,
    ) { workout ->
        Column(Modifier.fillMaxWidth()) {
            if (workout != null) {
                val isCompleted = workout.completedDate == today.toString()
                BasicText(
                    workout.title,
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
        }
    }
}
