package app.darkio.dashboard

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.darkio.R
import app.darkio.data.AppDatabase
import app.darkio.data.WorkoutDao
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ActivityGridCell(
    val date: LocalDate,
    val weekIndex: Int, // 0..19 (0 = 19 uger siden, 19 = denne uge)
    val dayOfWeek: Int, // 1..7 (1 = mandag ... 7 = søndag)
    val steps: Long,
    val isWorkoutCompleted: Boolean,
    val isGoalReached: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
    val lacksHistory: Boolean,
)

data class ActivityGridData(
    val cells: List<ActivityGridCell>,
    val goalDaysCount: Int,
    val workoutsCount: Int,
    val hasHistoryAccess: Boolean,
    val hasStepsAccess: Boolean = true,
)

fun calculateGridMonday(today: LocalDate): LocalDate =
    today.minusDays((today.dayOfWeek.value - 1).toLong())

/**
 * Bygger en ren datamodel for aktivitetsgitteret over 20 uger (man–søn).
 * Nyeste uge til højre, fremtidige dage efter [today] udelades/er tomme.
 */
fun buildActivityGrid(
    today: LocalDate,
    stepGoal: Long,
    stepsByDate: Map<LocalDate, Long>,
    completedWorkoutDates: Set<LocalDate>,
    hasHistoryAccess: Boolean,
    hasStepsAccess: Boolean = true,
): ActivityGridData {
    val currentMonday = calculateGridMonday(today)
    val startMonday = currentMonday.minusWeeks(19)
    val historyCutoff = today.minusDays(30)

    val cells = ArrayList<ActivityGridCell>(20 * 7)
    var goalDaysCount = 0
    var workoutsCount = 0

    for (week in 0 until 20) {
        val weekMonday = startMonday.plusWeeks(week.toLong())
        for (dayIndex in 0 until 7) {
            val date = weekMonday.plusDays(dayIndex.toLong())
            val isFuture = date.isAfter(today)
            val isToday = date == today
            val lacksHistory = !hasHistoryAccess && date.isBefore(historyCutoff)
            val steps = if (lacksHistory || isFuture) 0L else (stepsByDate[date] ?: 0L)
            val isWorkoutCompleted = !isFuture && date in completedWorkoutDates
            val isGoalReached = !isFuture && !lacksHistory && steps >= stepGoal

            if (!isFuture) {
                if (isGoalReached) goalDaysCount++
                if (isWorkoutCompleted) workoutsCount++
            }

            cells.add(
                ActivityGridCell(
                    date = date,
                    weekIndex = week,
                    dayOfWeek = dayIndex + 1,
                    steps = steps,
                    isWorkoutCompleted = isWorkoutCompleted,
                    isGoalReached = isGoalReached,
                    isToday = isToday,
                    isFuture = isFuture,
                    lacksHistory = lacksHistory,
                ),
            )
        }
    }

    return ActivityGridData(
        cells = cells,
        goalDaysCount = goalDaysCount,
        workoutsCount = workoutsCount,
        hasHistoryAccess = hasHistoryAccess,
        hasStepsAccess = hasStepsAccess,
    )
}

/** Henter alle gennemførte træningsdatoer fra Room på Dispatchers.IO. */
suspend fun queryCompletedWorkoutDates(dao: WorkoutDao): Set<LocalDate> = withContext(Dispatchers.IO) {
    val recordDates = dao.getCompletedDates().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
    val dayDates = dao.getAllWorkouts().mapNotNull { it.completedDate?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() } }
    (recordDates + dayDates).toSet()
}

/** Tjekker om Health Connect har givet adgang til henholdsvis READ_STEPS og READ_HEALTH_DATA_HISTORY. */
suspend fun checkHealthHistoryPermissions(context: Context): Pair<Boolean, Boolean> =
    try {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            false to false
        } else {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            val hasSteps = granted.containsAll(STEPS_PERMISSIONS)
            val hasHistory = granted.contains(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
            hasSteps to hasHistory
        }
    } catch (_: Throwable) {
        false to false
    }

/** Mapper aggregerede perioder fra Health Connect til daglige skridt pr. lokal kalenderdato. */
fun parseDailyStepsFromGroups(
    groups: List<Pair<LocalDateTime, Long>>,
): Map<LocalDate, Long> = groups.associate { (dt, steps) -> dt.toLocalDate() to steps }

/** Henter daglige skridt fra Health Connect med aggregateGroupByPeriod på Dispatchers.IO (N5/N10: gemmes aldrig lokalt). */
suspend fun queryHealthConnectStepsByPeriod(
    context: Context,
    startDate: LocalDate,
    endDate: LocalDate,
): Map<LocalDate, Long> = withContext(Dispatchers.IO) {
    if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
        return@withContext emptyMap()
    }
    try {
        val client = HealthConnectClient.getOrCreate(context)
        val startDateTime = startDate.atStartOfDay()
        val endDateTime = endDate.plusDays(1).atStartOfDay()
        val response = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startDateTime, endDateTime),
                timeRangeSlicer = Period.ofDays(1),
            ),
        )
        val items = response.map { group ->
            group.startTime to (group.result[StepsRecord.COUNT_TOTAL] ?: 0L)
        }
        parseDailyStepsFromGroups(items)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        emptyMap()
    }
}

/** Indlæser aktivitetsgitterets tilstand. Returnerer Content eller Error ved uventet fejl. */
suspend fun loadActivityGridCardState(
    dao: WorkoutDao,
    hasPermissions: suspend () -> Pair<Boolean, Boolean>,
    queryStepsByDate: suspend (startDate: LocalDate, endDate: LocalDate) -> Map<LocalDate, Long>,
    stepGoal: Long = DEFAULT_STEP_GOAL,
    today: LocalDate = LocalDate.now(),
): CardState<ActivityGridData> =
    try {
        val (hasSteps, hasHistory) = hasPermissions()
        val currentMonday = calculateGridMonday(today)
        val startMonday = currentMonday.minusWeeks(19)
        val historyCutoff = today.minusDays(30)
        val effectiveStart = if (hasHistory) startMonday else maxOf(startMonday, historyCutoff)

        val stepsByDate = if (hasSteps) {
            queryStepsByDate(effectiveStart, today)
        } else {
            emptyMap()
        }
        val completedDates = queryCompletedWorkoutDates(dao)

        val data = buildActivityGrid(
            today = today,
            stepGoal = stepGoal,
            stepsByDate = stepsByDate,
            completedWorkoutDates = completedDates,
            hasHistoryAccess = hasHistory,
            hasStepsAccess = hasSteps,
        )

        CardState.Content(data)
    } catch (e: CancellationException) {
        throw e
    } catch (_: SecurityException) {
        CardState.Error
    } catch (_: Throwable) {
        CardState.Error
    }

/**
 * Aktivitetsgitterkort til træningssiden (side 1) jf. F22.
 * 20 uger × 7 dage (man–søn).
 * Over gitteret vises antal dage med nået skridtmål og antal gennemførte træninger.
 */
@Composable
fun ActivityGridCard(
    modifier: Modifier = Modifier,
    dao: WorkoutDao = AppDatabase.open(LocalContext.current).workouts(),
    queryStepsByDate: (suspend (startDate: LocalDate, endDate: LocalDate) -> Map<LocalDate, Long>)? = null,
    hasPermissions: (suspend () -> Pair<Boolean, Boolean>)? = null,
    onGrantHistoryAccess: (() -> Unit)? = null,
    stepGoal: Long = DEFAULT_STEP_GOAL,
    verticalPadding: Dp = DarkIOSpace.S4,
    reloadTrigger: Any? = null,
    now: () -> LocalDate = LocalDate::now,
) {
    val context = LocalContext.current
    var permissionAttempt by remember { mutableIntStateOf(0) }

    val actualHasPermissions: suspend () -> Pair<Boolean, Boolean> = hasPermissions ?: {
        checkHealthHistoryPermissions(context)
    }
    val actualQuerySteps: suspend (LocalDate, LocalDate) -> Map<LocalDate, Long> = queryStepsByDate ?: { start, end ->
        queryHealthConnectStepsByPeriod(context, start, end)
    }

    val launcher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) {
        permissionAttempt++
    }

    LoadedCard(
        load = {
            loadActivityGridCardState(
                dao = dao,
                hasPermissions = actualHasPermissions,
                queryStepsByDate = actualQuerySteps,
                stepGoal = stepGoal,
                today = now(),
            )
        },
        modifier = modifier,
        verticalPadding = verticalPadding,
        reloadTrigger = permissionAttempt to (reloadTrigger to stepGoal),
    ) { data ->
        val goalDaysText = pluralStringResource(
            R.plurals.activity_grid_goal_days,
            data.goalDaysCount,
            data.goalDaysCount,
        )
        val workoutsText = pluralStringResource(
            R.plurals.activity_grid_workouts,
            data.workoutsCount,
            data.workoutsCount,
        )
        val talkbackDesc = stringResource(
            R.string.activity_grid_talkback_desc,
            goalDaysText,
            workoutsText,
        )

        val actualOnGrantHistoryAccess: () -> Unit = onGrantHistoryAccess ?: {
            val permissionsToRequest = buildSet {
                if (!data.hasStepsAccess) {
                    addAll(STEPS_PERMISSIONS)
                }
                add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
            }
            try {
                launcher.launch(permissionsToRequest)
            } catch (_: Throwable) {
                permissionAttempt++
            }
        }

        Column(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {
                        contentDescription = talkbackDesc
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = stringResource(R.string.activity_grid_title),
                        style = DarkIOType.Item.copy(color = DarkIOColors.Ink),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(DarkIOSpace.S2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            text = goalDaysText,
                            style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft),
                            modifier = Modifier.testTag("activityGridGoalDays"),
                        )
                        BasicText(
                            text = "·",
                            style = DarkIOType.Data.copy(color = DarkIOColors.InkMuted),
                        )
                        BasicText(
                            text = workoutsText,
                            style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft),
                            modifier = Modifier.testTag("activityGridWorkouts"),
                        )
                    }
                }
                Spacer(Modifier.height(DarkIOSpace.S1))
                ActivityGridCanvas(
                    cells = data.cells,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            if (!data.hasHistoryAccess) {
                BasicText(
                    text = stringResource(R.string.activity_grid_grant_history),
                    modifier = Modifier
                        .heightIn(min = DarkIOSize.TouchMin)
                        .clickable(role = Role.Button, onClick = actualOnGrantHistoryAccess)
                        .padding(vertical = DarkIOSpace.S2)
                        .align(Alignment.Start)
                        .testTag("activityGridGrantHistoryButton"),
                    style = DarkIOType.Button.copy(color = DarkIOColors.Accent),
                )
            }
        }
    }
}

/**
 * Tegner 20 × 7 gitteret via Canvas.
 * - Skridtmål nået: fyldt firkant i pastel-mint
 * - Gennemført træning: prik i pastel-coral (tydelig formforskel)
 * - Tom dag: line
 * - I dag: ramme i accent
 * - Ældre dage uden historikadgang: stiplet ramme
 */
@Composable
fun ActivityGridCanvas(
    cells: List<ActivityGridCell>,
    modifier: Modifier = Modifier,
    cellSize: Dp = DarkIOSize.GridCellSize,
    cellGap: Dp = DarkIOSize.GridCellGap,
    dotSize: Dp = DarkIOSize.GridDotSize,
    dashLength: Dp = DarkIOSize.GridDash,
    lineWidth: Dp = DarkIOSize.LineWidth,
) {
    val totalWidth = cellSize * 20 + cellGap * 19
    val totalHeight = cellSize * 7 + cellGap * 6
    val density = LocalDensity.current
    val (dashedStroke, solidStroke) = remember(density, lineWidth, dashLength) {
        with(density) {
            val strokeWidthPx = lineWidth.toPx()
            val dashPx = dashLength.toPx()
            Stroke(
                width = strokeWidthPx,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx), 0f),
            ) to Stroke(width = strokeWidthPx)
        }
    }

    Canvas(
        modifier = modifier
            .size(totalWidth, totalHeight)
            .testTag("activityGridCanvas"),
    ) {
        val cellSizePx = cellSize.toPx()
        val cellGapPx = cellGap.toPx()
        val dotRadiusPx = dotSize.toPx() / 2f
        val strokeWidthPx = lineWidth.toPx()

        for (cell in cells) {
            if (cell.isFuture) continue

            val x = cell.weekIndex * (cellSizePx + cellGapPx)
            val y = (cell.dayOfWeek - 1) * (cellSizePx + cellGapPx)
            val topLeft = Offset(x, y)
            val size = Size(cellSizePx, cellSizePx)
            val center = Offset(x + cellSizePx / 2f, y + cellSizePx / 2f)

            if (cell.lacksHistory) {
                val insetTopLeft = Offset(x + strokeWidthPx / 2f, y + strokeWidthPx / 2f)
                val insetSize = Size(cellSizePx - strokeWidthPx, cellSizePx - strokeWidthPx)
                drawRect(
                    color = DarkIOColors.Line,
                    topLeft = insetTopLeft,
                    size = insetSize,
                    style = dashedStroke,
                )
            } else if (cell.isGoalReached) {
                drawRect(
                    color = DarkIOColors.Mint,
                    topLeft = topLeft,
                    size = size,
                )
            } else {
                drawRect(
                    color = DarkIOColors.Line,
                    topLeft = topLeft,
                    size = size,
                )
            }

            if (cell.isWorkoutCompleted) {
                drawCircle(
                    color = DarkIOColors.Coral,
                    radius = dotRadiusPx,
                    center = center,
                )
            }

            if (cell.isToday) {
                val insetTopLeft = Offset(x + strokeWidthPx / 2f, y + strokeWidthPx / 2f)
                val insetSize = Size(cellSizePx - strokeWidthPx, cellSizePx - strokeWidthPx)
                drawRect(
                    color = DarkIOColors.Accent,
                    topLeft = insetTopLeft,
                    size = insetSize,
                    style = solidStroke,
                )
            }
        }
    }
}
