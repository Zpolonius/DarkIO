package app.darkio.dashboard

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.darkio.R
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val DEFAULT_STEP_GOAL = 10_000L

data class StepsData(
    val steps: Long,
    val goal: Long = DEFAULT_STEP_GOAL,
    val sources: List<String> = emptyList(),
)

val STEPS_PERMISSIONS: Set<String> = setOf(HealthPermission.getReadPermission(StepsRecord::class))

/** Tidsinterval for dagens skridt fra midnat (00:00:00) i [zoneId] til [now]. */
fun dayStepTimeRange(
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Pair<Instant, Instant> {
    val localDate = now.atZone(zoneId).toLocalDate()
    val startOfDay = localDate.atStartOfDay(zoneId).toInstant()
    return startOfDay to now
}

/** Beregner fremskridt mod skridtmålet som en brøkdel mellem 0.0 og 1.0. */
fun calculateStepProgress(steps: Long, goal: Long): Float {
    if (goal <= 0L || steps <= 0L) return 0f
    val fraction = steps.toFloat() / goal.toFloat()
    return fraction.coerceIn(0f, 1f)
}

/** Formaterer tal med dansk tusindtalsseparator. */
fun formatStepNumber(
    number: Long,
    locale: Locale = Locale.forLanguageTag("da"),
): String = String.format(locale, "%,d", number)

/** Indlæser skridtkortets tilstand. Returnerer NeedsAccess ved manglende adgang eller SecurityException. */
suspend fun loadStepsCardState(
    hasAccess: suspend () -> Boolean,
    querySteps: suspend () -> Long,
    goal: Long = DEFAULT_STEP_GOAL,
    querySources: suspend () -> List<String> = { emptyList() },
): CardState<StepsData> {
    if (!hasAccess()) return CardState.NeedsAccess
    return try {
        val steps = querySteps()
        val sources = querySources()
        CardState.Content(StepsData(steps = steps, goal = goal, sources = sources))
    } catch (e: CancellationException) {
        throw e
    } catch (_: SecurityException) {
        CardState.NeedsAccess
    } catch (_: Throwable) {
        CardState.Error
    }
}

/** Tjekker om Health Connect er tilgængelig og READ_STEPS-tilladelsen er givet. */
suspend fun checkStepsPermission(context: Context): Boolean =
    try {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            false
        } else {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(STEPS_PERMISSIONS)
        }
    } catch (_: Throwable) {
        false
    }

/** Slår appnavn op for en Health Connect-kilde med pakkenavn som fallback. */
internal fun resolveStepSourceLabel(
    context: Context,
    packageName: String,
): String =
    runCatching {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString().takeIf { it.isNotBlank() } ?: packageName
    }.getOrDefault(packageName)

/** Henter dagens aggregerede skridt og deres datakilder fra Health Connect på Dispatchers.IO (N5: gemmes ikke). */
suspend fun queryHealthConnectStepsWithSources(
    context: Context,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Pair<Long, List<String>> = withContext(Dispatchers.IO) {
    val client = HealthConnectClient.getOrCreate(context)
    val (startTime, endTime) = dayStepTimeRange(now, zoneId)
    val response = client.aggregate(
        AggregateRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
        ),
    )
    val steps = response[StepsRecord.COUNT_TOTAL] ?: 0L
    val sources = response.dataOrigins
        .map { resolveStepSourceLabel(context, it.packageName) }
        .distinct()
        .sorted()
    steps to sources
}

/** Henter dagens aggregerede skridt fra Health Connect på Dispatchers.IO (N5: gemmes ikke). */
suspend fun queryHealthConnectSteps(
    context: Context,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long = queryHealthConnectStepsWithSources(context, now, zoneId).first

/**
 * Skridtkort til træningssiden (side 1).
 * Viser dagens skridt fra Health Connect (READ_STEPS, aggregeret) og en bjælke i pastel-mint mod målet (F5).
 * Uden adgang vises "giv adgang", som starter Health Connects tilladelsesflow.
 */
@Composable
fun StepsCard(
    modifier: Modifier = Modifier,
    loadSteps: (suspend () -> Long)? = null,
    loadSources: (suspend () -> List<String>)? = null,
    hasPermission: (suspend () -> Boolean)? = null,
    onGrantAccess: (() -> Unit)? = null,
    goal: Long = DEFAULT_STEP_GOAL,
    verticalPadding: Dp = DarkIOSpace.S4,
    now: () -> Instant = Instant::now,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    val context = LocalContext.current
    var permissionAttempt by remember { mutableIntStateOf(0) }
    var showSource by rememberSaveable { mutableStateOf(false) }

    val actualHasPermission: suspend () -> Boolean = hasPermission ?: {
        checkStepsPermission(context)
    }
    val actualLoadStepsData: suspend () -> Pair<Long, List<String>> = when {
        loadSteps != null && loadSources != null -> {
            { loadSteps() to loadSources() }
        }
        loadSteps != null -> {
            { loadSteps() to emptyList() }
        }
        else -> {
            { queryHealthConnectStepsWithSources(context, now(), zoneId) }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) {
        permissionAttempt++
    }

    val actualOnGrantAccess: () -> Unit = onGrantAccess ?: {
        try {
            launcher.launch(STEPS_PERMISSIONS)
        } catch (_: Throwable) {
            permissionAttempt++
        }
        Unit
    }

    LoadedCard(
        load = {
            var cachedResult: Pair<Long, List<String>>? = null
            val getResult: suspend () -> Pair<Long, List<String>> = {
                cachedResult ?: actualLoadStepsData().also { cachedResult = it }
            }
            loadStepsCardState(
                hasAccess = actualHasPermission,
                querySteps = { getResult().first },
                goal = goal,
                querySources = { getResult().second },
            )
        },
        modifier = modifier,
        verticalPadding = verticalPadding,
        refreshAfterMs = { millisToNextMinute(LocalDateTime.now()) },
        reloadTrigger = permissionAttempt to goal,
        onGrantAccess = actualOnGrantAccess,
        needsAccessTitle = stringResource(R.string.steps_grant_access_title),
        needsAccessExplanation = stringResource(R.string.steps_grant_access_explanation),
    ) { data ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = { showSource = !showSource })
                .testTag("stepsCardContent"),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = stringResource(R.string.steps_title),
                    style = DarkIOType.Item.copy(color = DarkIOColors.Ink),
                )
                BasicText(
                    text = buildAnnotatedString {
                        append(formatStepNumber(data.steps))
                        withStyle(SpanStyle(color = DarkIOColors.InkMuted)) {
                            append(" / ${formatStepNumber(data.goal)}")
                        }
                    },
                    style = DarkIOType.Data.copy(color = DarkIOColors.Ink),
                    modifier = Modifier.testTag("stepsCount"),
                )
            }
            Spacer(Modifier.height(DarkIOSpace.S2))
            StepProgressBar(
                progress = calculateStepProgress(data.steps, data.goal),
                modifier = Modifier.testTag("stepsProgressBar"),
            )
            if (showSource) {
                Spacer(Modifier.height(DarkIOSpace.S2))
                val sourceText = if (data.sources.isNotEmpty()) {
                    stringResource(R.string.steps_source_with_origins, data.sources.joinToString(", "))
                } else {
                    stringResource(R.string.steps_source_default)
                }
                BasicText(
                    text = sourceText,
                    style = DarkIOType.Label.copy(color = DarkIOColors.InkMuted),
                    modifier = Modifier.testTag("stepsSource"),
                )
            }
        }
    }
}

@Composable
fun StepProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DarkIOSpace.S2)
            .clip(DarkIOShapes.Pill)
            .background(DarkIOColors.LineStrong),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                .clip(DarkIOShapes.Pill)
                .background(DarkIOColors.Mint),
        )
    }
}
