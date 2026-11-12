package app.darkio.dashboard

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import app.darkio.R
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FocusData(
    val screenTimeMillis: Long,
    val unlockCount: Int,
)

data class FocusUsageEvent(
    val timestampMillis: Long,
    val eventType: Int,
    val packageName: String = "",
)

/** Tidsinterval for dagens fokusdata fra midnat (00:00:00) i [zoneId] til [now]. */
fun dayFocusTimeRange(
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Pair<Instant, Instant> {
    val startOfDay = now.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant()
    return startOfDay to now
}

/** Formaterer skærmtid i timer og minutter, fx '1 t 24 m', '45 m' eller '0 m'. */
fun formatScreenTime(totalMillis: Long): String {
    val totalMinutes = (totalMillis / 1000 / 60).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours} t ${minutes} m"
        hours > 0 -> "${hours} t"
        else -> "${minutes} m"
    }
}

/** Formaterer antal oplåsninger på dansk, fx '1 oplåsning', '34 oplåsninger'. */
fun formatUnlockCount(count: Int): String {
    val safeCount = count.coerceAtLeast(0)
    return if (safeCount == 1) "1 oplåsning" else "$safeCount oplåsninger"
}

/**
 * Beregner skærmtid og antal oplåsninger for et givet tidsinterval [startTimeMillis, endTimeMillis].
 * Ignorerer DarkIO selv hvis angivet i [excludedPackage].
 */
fun calculateFocusData(
    events: List<FocusUsageEvent>,
    startTimeMillis: Long,
    endTimeMillis: Long,
    excludedPackage: String? = null,
): FocusData {
    if (endTimeMillis <= startTimeMillis) return FocusData(0L, 0)
    var unlockCount = 0
    var totalScreenTime = 0L

    var activePackage: String? = null
    var activeStart: Long? = null

    val sortedEvents = events.sortedBy { it.timestampMillis }

    fun closeActive(timestamp: Long) {
        val start = activeStart ?: return
        val clampedStart = start.coerceIn(startTimeMillis, endTimeMillis)
        val clampedEnd = timestamp.coerceIn(startTimeMillis, endTimeMillis)
        if (clampedEnd > clampedStart) {
            totalScreenTime += (clampedEnd - clampedStart)
        }
        activePackage = null
        activeStart = null
    }

    for (event in sortedEvents) {
        if (event.timestampMillis < startTimeMillis || event.timestampMillis > endTimeMillis) continue

        when (event.eventType) {
            UsageEvents.Event.KEYGUARD_HIDDEN -> {
                unlockCount++
            }
            UsageEvents.Event.ACTIVITY_RESUMED -> {
                if (excludedPackage != null && event.packageName == excludedPackage) {
                    closeActive(event.timestampMillis)
                } else {
                    if (activePackage != event.packageName) {
                        closeActive(event.timestampMillis)
                        activePackage = event.packageName
                        activeStart = event.timestampMillis
                    }
                }
            }
            UsageEvents.Event.ACTIVITY_PAUSED -> {
                if (activePackage == event.packageName) {
                    closeActive(event.timestampMillis)
                } else if (activePackage == null && (excludedPackage == null || event.packageName != excludedPackage)) {
                    val clampedStart = startTimeMillis
                    val clampedEnd = event.timestampMillis.coerceIn(startTimeMillis, endTimeMillis)
                    if (clampedEnd > clampedStart) {
                        totalScreenTime += (clampedEnd - clampedStart)
                    }
                }
            }
            UsageEvents.Event.SCREEN_NON_INTERACTIVE,
            UsageEvents.Event.KEYGUARD_SHOWN -> {
                closeActive(event.timestampMillis)
            }
        }
    }

    closeActive(endTimeMillis)

    return FocusData(
        screenTimeMillis = totalScreenTime.coerceAtLeast(0L),
        unlockCount = unlockCount.coerceAtLeast(0),
    )
}

/** Indlæser fokus-kortets tilstand. Returnerer NeedsAccess ved manglende tilladelse eller SecurityException (N3). */
suspend fun loadFocusCardState(
    hasAccess: suspend () -> Boolean,
    queryFocus: suspend () -> FocusData,
): CardState<FocusData> =
    if (!hasAccess()) CardState.NeedsAccess
    else try {
        CardState.Content(queryFocus())
    } catch (e: CancellationException) {
        throw e
    } catch (_: SecurityException) {
        CardState.NeedsAccess
    } catch (_: Throwable) {
        CardState.Error
    }

/** Tjekker om appen har adgang til brugsdata (PACKAGE_USAGE_STATS). */
fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    @Suppress("DEPRECATION")
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName,
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

/** Åbner Android → Adgang til brugsdata i Systemindstillinger. */
fun openUsageAccessSettings(context: Context) {
    val packageIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(packageIntent)
    } catch (_: Throwable) {
        val genericIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(genericIntent)
        } catch (_: Throwable) {}
    }
}

/** Henter dagens fokusdata fra UsageStatsManager på Dispatchers.IO (N5, N10: gemmes ikke). */
suspend fun queryUsageStatsFocusData(
    context: Context,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): FocusData = withContext(Dispatchers.IO) {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        ?: return@withContext FocusData(0L, 0)
    val (startTime, endTime) = dayFocusTimeRange(now, zoneId)
    val events = usageStatsManager.queryEvents(startTime.toEpochMilli(), endTime.toEpochMilli())
    val eventList = mutableListOf<FocusUsageEvent>()
    val event = UsageEvents.Event()
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        eventList.add(
            FocusUsageEvent(
                timestampMillis = event.timeStamp,
                eventType = event.eventType,
                packageName = event.packageName.orEmpty(),
            ),
        )
    }
    calculateFocusData(
        events = eventList,
        startTimeMillis = startTime.toEpochMilli(),
        endTimeMillis = endTime.toEpochMilli(),
        excludedPackage = context.packageName,
    )
}

/**
 * Fokus-kort (kompakt) til hjemskærmens "i dag"-side.
 * Viser dagens skærmtid og antal oplåsninger fra UsageStatsManager (F16, N4, N10).
 * Uden adgang vises "giv adgang", som åbner Android → Adgang til brugsdata.
 */
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    loadFocus: (suspend () -> FocusData)? = null,
    hasPermission: (suspend () -> Boolean)? = null,
    onGrantAccess: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    reloadTrigger: Any? = null,
    now: () -> Instant = Instant::now,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    val context = LocalContext.current
    var permissionAttempt by remember { mutableIntStateOf(0) }

    val actualHasPermission: suspend () -> Boolean = hasPermission ?: {
        hasUsageStatsPermission(context)
    }
    val actualLoadFocus: suspend () -> FocusData = loadFocus ?: {
        queryUsageStatsFocusData(context, now(), zoneId)
    }
    val actualOnGrantAccess: () -> Unit = onGrantAccess ?: {
        openUsageAccessSettings(context)
        permissionAttempt++
    }

    LoadedCard(
        load = {
            loadFocusCardState(
                hasAccess = actualHasPermission,
                queryFocus = actualLoadFocus,
            )
        },
        modifier = if (onClick != null) {
            modifier.clickable(role = Role.Button, onClick = onClick)
        } else {
            modifier
        },
        refreshAfterMs = { millisToNextMinute(LocalDateTime.now()) },
        reloadTrigger = if (reloadTrigger != null) (reloadTrigger to permissionAttempt) else permissionAttempt,
        onGrantAccess = actualOnGrantAccess,
        needsAccessTitle = stringResource(R.string.focus_grant_access_title),
        needsAccessExplanation = stringResource(R.string.focus_grant_access_explanation),
    ) { data ->
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = stringResource(R.string.focus_title),
                    style = DarkIOType.Item.copy(color = DarkIOColors.Ink),
                )
                BasicText(
                    text = formatScreenTime(data.screenTimeMillis),
                    style = DarkIOType.Data.copy(color = DarkIOColors.Ink),
                    modifier = Modifier.testTag("focusScreenTime"),
                )
            }
            Spacer(Modifier.height(DarkIOSpace.S1))
            BasicText(
                text = formatUnlockCount(data.unlockCount),
                style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft),
                modifier = Modifier.testTag("focusUnlockCount"),
            )
        }
    }
}
