package app.darkio.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.darkio.R
import app.darkio.data.AppDatabase
import app.darkio.data.WorkoutDao
import app.darkio.data.WorkoutDay
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Redigér træningsprogrammet (F4, F13): dag, øvelser og RPE pr. pas. Hver ændring gemmes med det samme, i rækkefølge på IO.
 * Titlen (vist på kortet) gemmes som "dagsnavn: øvelser"; RPE vises ved siden af.
 */
@Composable
fun WorkoutProgramScreen(
    modifier: Modifier = Modifier,
    dao: WorkoutDao = AppDatabase.open(LocalContext.current).workouts(),
    now: () -> LocalDate = LocalDate::now,
) {
    val dayNames = stringArrayResource(R.array.workout_day_names)
    val shortNames = stringArrayResource(R.array.workout_week_days)
    var program by remember { mutableStateOf<List<WorkoutDay>?>(null) }
    LaunchedEffect(Unit) { program = withContext(Dispatchers.IO) { dao.getAllWorkouts() } }
    val scope = rememberCoroutineScope()
    val io = remember { Dispatchers.IO.limitedParallelism(1) }
    val today = now()
    fun save(old: Int?, w: WorkoutDay) {
        val saved = w.copy(title = "${dayNames[w.dayOfWeek - 1]}: ${w.exercises}")
        program = (program.orEmpty().filter { it.dayOfWeek != old && it.dayOfWeek != saved.dayOfWeek } + saved).sortedBy { it.dayOfWeek }
        scope.launch(io) { dao.saveWorkout(old, saved, today.toString(), today.dayOfWeek.value) }
    }
    fun delete(day: Int) {
        program = program.orEmpty().filter { it.dayOfWeek != day }
        scope.launch(io) { dao.deleteWorkout(day, today.toString(), today.dayOfWeek.value) }
    }
    val list = program ?: return
    Column(
        modifier
            .fillMaxSize()
            .background(DarkIOColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(start = DarkIOSpace.S4, end = DarkIOSpace.S4, top = DarkIOSpace.TopInset, bottom = DarkIOSpace.S5)
            .testTag("workoutProgram"),
        verticalArrangement = Arrangement.spacedBy(DarkIOSpace.S4),
    ) {
        BasicText(stringResource(R.string.settings_workout_program), style = DarkIOType.Title.copy(color = DarkIOColors.Ink))
        list.forEach { w ->
            key(w.dayOfWeek) {
                val d = w.dayOfWeek
                var text by remember { mutableStateOf(w.exercises) }
                val rpe = w.rpe
                Column(
                    Modifier.fillMaxWidth().clip(DarkIOShapes.Lg).background(DarkIOColors.Surface).padding(DarkIOSpace.S3),
                    verticalArrangement = Arrangement.spacedBy(DarkIOSpace.S3),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        shortNames.forEachIndexed { i, name ->
                            val day = i + 1
                            val free = list.none { it.dayOfWeek == day }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = DarkIOSize.TouchTarget)
                                    .testTag("programDay_${d}_$day")
                                    .semantics { selected = day == d; if (!free && day != d) disabled() }
                                    .then(if (free) Modifier.clickable(role = Role.RadioButton) { save(d, w.copy(dayOfWeek = day)) } else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                BasicText(
                                    name,
                                    style = DarkIOType.Label.copy(color = if (day == d) DarkIOColors.Accent else if (free) DarkIOColors.Ink else DarkIOColors.LineStrong),
                                )
                            }
                        }
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it; save(d, w.copy(exercises = it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = DarkIOSize.FieldHeight)
                            .clip(DarkIOShapes.Md)
                            .background(DarkIOColors.Bg)
                            .padding(DarkIOSpace.S3)
                            .testTag("programExercises_$d"),
                        textStyle = DarkIOType.Input.copy(color = DarkIOColors.Ink),
                        cursorBrush = SolidColor(DarkIOColors.Accent),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(stringResource(R.string.workout_program_rpe), style = DarkIOType.Item.copy(color = DarkIOColors.Ink))
                        StepButton(R.string.settings_step_minus, R.string.workout_program_rpe_down, "programRpeDown_$d") {
                            if (rpe > 1) save(d, w.copy(rpe = rpe - 1))
                        }
                        BasicText(
                            rpe.toString(),
                            Modifier.padding(horizontal = DarkIOSpace.S3).testTag("programRpe_$d"),
                            style = DarkIOType.Data.copy(color = DarkIOColors.Ink),
                        )
                        StepButton(R.string.settings_step_plus, R.string.workout_program_rpe_up, "programRpeUp_$d") {
                            if (rpe < 10) save(d, w.copy(rpe = rpe + 1))
                        }
                        BasicText(
                            stringResource(R.string.workout_program_delete),
                            Modifier
                                .weight(1f)
                                .heightIn(min = DarkIOSize.TouchTarget)
                                .clickable(role = Role.Button) { delete(d) }
                                .padding(vertical = DarkIOSpace.S3)
                                .testTag("programDelete_$d"),
                            style = DarkIOType.Button.copy(color = DarkIOColors.Accent, textAlign = TextAlign.End),
                        )
                    }
                }
            }
        }
        val freeDay = (1..7).firstOrNull { d -> list.none { it.dayOfWeek == d } }
        if (freeDay != null) {
            BasicText(
                stringResource(R.string.workout_program_add),
                Modifier
                    .heightIn(min = DarkIOSize.TouchTarget)
                    .clickable(role = Role.Button) { save(null, WorkoutDay(freeDay, "", "")) }
                    .padding(vertical = DarkIOSpace.S3)
                    .testTag("programAdd"),
                style = DarkIOType.Button.copy(color = DarkIOColors.Accent),
            )
        }
    }
}
