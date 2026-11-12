package app.darkio.dashboard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.darkio.R
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.round
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val FOODVISOR_PACKAGE = "io.foodvisor.foodvisor"

data class CalorieSample(
    val startTime: Instant,
    val endTime: Instant,
    val kilocalories: Double,
)

data class CaloriesData(
    val calories: Long,
    val isFoodAdvisorInstalled: Boolean = true,
)

val CALORIES_PERMISSIONS: Set<String> = setOf(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))

/** Tidsinterval for dagens forbrændte kalorier fra midnat (00:00:00) i [zoneId] til [now]. */
fun dayCaloriesTimeRange(
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Pair<Instant, Instant> = now.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant() to now

/** Beregner summen af forbrændte kilokalorier for et givet interval [dayRange]. */
fun calculateCaloriesSum(
    samples: List<CalorieSample>,
    dayRange: Pair<Instant, Instant>,
): Long {
    val (start, end) = dayRange
    return round(samples.filter { it.startTime >= start && it.endTime <= end }.sumOf { it.kilocalories }).toLong()
}

/** Formaterer tal med dansk tusindtalsseparator. */
fun formatCaloriesNumber(
    number: Long,
    locale: Locale = Locale.forLanguageTag("da"),
): String = String.format(locale, "%,d", number)

/** Indlæser forbrændt-kortets tilstand. Returnerer NeedsAccess ved manglende adgang eller SecurityException. */
suspend fun loadCaloriesCardState(
    hasAccess: suspend () -> Boolean,
    queryCalories: suspend () -> Long,
    isFoodAdvisorInstalled: suspend () -> Boolean = { true },
): CardState<CaloriesData> =
    if (!hasAccess()) CardState.NeedsAccess
    else try {
        CardState.Content(CaloriesData(queryCalories(), isFoodAdvisorInstalled()))
    } catch (e: CancellationException) {
        throw e
    } catch (_: SecurityException) {
        CardState.NeedsAccess
    } catch (_: Throwable) {
        CardState.Error
    }

/** Tjekker om Health Connect er tilgængelig og READ_TOTAL_CALORIES_BURNED-tilladelsen er givet. */
suspend fun checkCaloriesPermission(context: Context): Boolean =
    try {
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE &&
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(CALORIES_PERMISSIONS)
    } catch (_: Throwable) {
        false
    }

/** Henter dagens aggregerede forbrændte kalorier fra Health Connect på Dispatchers.IO (N5: gemmes ikke). */
suspend fun queryHealthConnectCalories(
    context: Context,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long = withContext(Dispatchers.IO) {
    val (startTime, endTime) = dayCaloriesTimeRange(now, zoneId)
    val response = HealthConnectClient.getOrCreate(context).aggregate(
        AggregateRequest(setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL), TimeRangeFilter.between(startTime, endTime)),
    )
    response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.let { round(it).toLong() } ?: 0L
}

/** Tjekker om FoodAdvisor (io.foodvisor.foodvisor) er installeret på enheden (F18). */
fun isFoodAdvisorInstalled(context: Context): Boolean =
    try {
        context.packageManager.getPackageInfo(FOODVISOR_PACKAGE, PackageManager.PackageInfoFlags.of(0))
        true
    } catch (_: Throwable) {
        false
    }

/** Åbner FoodAdvisor hvis appen er installeret. */
fun openFoodAdvisor(context: Context) {
    try {
        context.packageManager.getLaunchIntentForPackage(FOODVISOR_PACKAGE)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    } catch (_: Throwable) {
        // Ignorer fejl ved åbning
    }
}

/**
 * Forbrændt-kort til træningssiden (side 1) under StepsCard (F18, F20).
 * Viser forbrændte kalorier i dag fra Health Connect (READ_TOTAL_CALORIES_BURNED).
 * Hele kortet er et link med ↗ og "+ log mad", der åbner FoodAdvisor (io.foodvisor.foodvisor).
 * Er FoodAdvisor ikke installeret, skjules "+ log mad".
 * Uden adgang vises "giv adgang"; indtag og kcal tilbage vises ikke.
 */
@Composable
fun CaloriesCard(
    modifier: Modifier = Modifier,
    loadCalories: (suspend () -> Long)? = null,
    hasPermission: (suspend () -> Boolean)? = null,
    onGrantAccess: (() -> Unit)? = null,
    isFoodAdvisorInstalled: (suspend () -> Boolean)? = null,
    onOpenFoodAdvisor: (() -> Unit)? = null,
    now: () -> Instant = Instant::now,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    val context = LocalContext.current
    var permissionAttempt by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        permissionAttempt++
    }

    LoadedCard(
        load = {
            loadCaloriesCardState(
                hasAccess = hasPermission ?: { checkCaloriesPermission(context) },
                queryCalories = loadCalories ?: { queryHealthConnectCalories(context, now(), zoneId) },
                isFoodAdvisorInstalled = isFoodAdvisorInstalled ?: { isFoodAdvisorInstalled(context) },
            )
        },
        modifier = modifier,
        refreshAfterMs = { millisToNextMinute(LocalDateTime.now()) },
        reloadTrigger = permissionAttempt,
        onGrantAccess = onGrantAccess ?: {
            try { launcher.launch(CALORIES_PERMISSIONS) } catch (_: Throwable) { permissionAttempt++ }
            Unit
        },
        needsAccessTitle = stringResource(R.string.calories_grant_access_title),
        needsAccessExplanation = stringResource(R.string.calories_grant_access_explanation),
    ) { data ->
        val cardModifier = if (data.isFoodAdvisorInstalled) {
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpenFoodAdvisor ?: { openFoodAdvisor(context) })
        } else {
            Modifier.fillMaxWidth()
        }

        Column(modifier = cardModifier.testTag("caloriesCardContent")) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(stringResource(R.string.calories_title), style = DarkIOType.Item.copy(color = DarkIOColors.Ink))
                BasicText(
                    text = buildAnnotatedString {
                        append(formatCaloriesNumber(data.calories))
                        withStyle(SpanStyle(color = DarkIOColors.InkMuted)) { append(" kcal") }
                    },
                    style = DarkIOType.Data.copy(color = DarkIOColors.Ink),
                    modifier = Modifier.testTag("caloriesCount"),
                )
            }
            if (data.isFoodAdvisorInstalled) {
                Spacer(Modifier.height(DarkIOSpace.S2))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DarkIOSpace.S1),
                    modifier = Modifier.testTag("caloriesLogFood"),
                ) {
                    BasicText(stringResource(R.string.calories_log_food), style = DarkIOType.Button.copy(color = DarkIOColors.Sun))
                    BasicText("↗", style = DarkIOType.Button.copy(color = DarkIOColors.Sun), modifier = Modifier.testTag("caloriesArrow"))
                }
            }
        }
    }
}
