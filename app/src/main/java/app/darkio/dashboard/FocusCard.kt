package app.darkio.dashboard

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import app.darkio.R
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HourlyBar(val hour: Int, val durationMillis: Long, val isCurrentHour: Boolean = false)
data class TopAppUsage(val packageName: String, val appLabel: String, val durationMillis: Long)
data class FocusData(
    val screenTimeMillis: Long,
    val unlockCount: Int,
    val yesterdayScreenTimeMillis: Long = 0L,
    val hourlyBars: List<HourlyBar> = emptyList(),
    val topApps: List<TopAppUsage> = emptyList(),
)
data class FocusUsageEvent(val timestampMillis: Long, val eventType: Int, val packageName: String = "")
data class FocusSession(val startMillis: Long, val endMillis: Long, val packageName: String)
data class YesterdayDifference(val text: String, val isDecrease: Boolean)

/** Tidsinterval for dagens fokusdata fra midnat (00:00:00) i [zoneId] til [now]. */
fun dayFocusTimeRange(now: Instant = Instant.now(), zoneId: ZoneId = ZoneId.systemDefault()): Pair<Instant, Instant> {
    val startOfDay = now.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant()
    return startOfDay to now
}

/** Tidsinterval for gårsdagens fokusdata fra 00:00:00 til 24:00:00 i [zoneId]. */
fun yesterdayFocusTimeRange(now: Instant = Instant.now(), zoneId: ZoneId = ZoneId.systemDefault()): Pair<Instant, Instant> {
    val today = now.atZone(zoneId).toLocalDate()
    val yesterday = today.minusDays(1)
    return yesterday.atStartOfDay(zoneId).toInstant() to today.atStartOfDay(zoneId).toInstant()
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

/** Formaterer forskellen mod i går. Ved fald er [YesterdayDifference.isDecrease] sand (positive farve). */
fun formatYesterdayDifference(todayMillis: Long, yesterdayMillis: Long): YesterdayDifference {
    if (yesterdayMillis <= 0L && todayMillis == 0L) return YesterdayDifference("0 m mod i går", false)
    if (yesterdayMillis <= 0L) return YesterdayDifference("↑ ${formatScreenTime(todayMillis)} mod i går", false)
    val diff = todayMillis - yesterdayMillis
    return when {
        diff < 0L -> YesterdayDifference("↓ ${formatScreenTime(-diff)} mod i går", true)
        diff > 0L -> YesterdayDifference("↑ ${formatScreenTime(diff)} mod i går", false)
        else -> YesterdayDifference("0 m mod i går", false)
    }
}

/** Udleder skærmtids-sessioner ud fra hændelser i intervallet [startTimeMillis, endTimeMillis]. */
fun extractUsageSessions(events: List<FocusUsageEvent>, startTimeMillis: Long, endTimeMillis: Long, excludedPackage: String? = null): List<FocusSession> {
    if (endTimeMillis <= startTimeMillis) return emptyList()
    val sessions = mutableListOf<FocusSession>()
    var activePackage: String? = null
    var activeStart: Long? = null
    fun close(t: Long) {
        val s = activeStart ?: return
        val cStart = s.coerceIn(startTimeMillis, endTimeMillis)
        val cEnd = t.coerceIn(startTimeMillis, endTimeMillis)
        if (cEnd > cStart && !activePackage.isNullOrEmpty()) sessions.add(FocusSession(cStart, cEnd, activePackage!!))
        activePackage = null
        activeStart = null
    }
    for (event in events.sortedBy { it.timestampMillis }) {
        if (event.timestampMillis !in startTimeMillis..endTimeMillis) continue
        when (event.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> {
                close(event.timestampMillis)
                if (excludedPackage == null || event.packageName != excludedPackage) {
                    activePackage = event.packageName
                    activeStart = event.timestampMillis
                }
            }
            UsageEvents.Event.ACTIVITY_PAUSED -> {
                if (activePackage == event.packageName) close(event.timestampMillis)
                else if (activePackage == null && (excludedPackage == null || event.packageName != excludedPackage)) {
                    val cEnd = event.timestampMillis.coerceIn(startTimeMillis, endTimeMillis)
                    if (cEnd > startTimeMillis && event.packageName.isNotEmpty()) sessions.add(FocusSession(startTimeMillis, cEnd, event.packageName))
                }
            }
            UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN -> close(event.timestampMillis)
        }
    }
    close(endTimeMillis)
    return sessions
}

/** Returnerer start- og sluttidspunkter for alle timer på datoen [date] i tidszonen [zoneId] (23, 24 el. 25 timer). */
fun getDayHourlyRanges(date: LocalDate, zoneId: ZoneId): List<Pair<Instant, Instant>> {
    val ranges = mutableListOf<Pair<Instant, Instant>>()
    var curr = date.atStartOfDay(zoneId)
    val end = date.plusDays(1).atStartOfDay(zoneId)
    while (curr.isBefore(end)) {
        val next = curr.plusHours(1).let { if (it.isAfter(end)) end else it }
        ranges.add(curr.toInstant() to next.toInstant())
        curr = next
    }
    return ranges
}

/** Beregner timesøjler for dagen, inklusive sommertid (23 el. 25 timer). Timen nu markeres med [HourlyBar.isCurrentHour]. */
fun calculateHourlyBars(events: List<FocusUsageEvent>, date: LocalDate, now: Instant, zoneId: ZoneId, excludedPackage: String? = null): List<HourlyBar> {
    val ranges = getDayHourlyRanges(date, zoneId)
    val start = ranges.firstOrNull()?.first?.toEpochMilli() ?: 0L
    val end = ranges.lastOrNull()?.second?.toEpochMilli() ?: 0L
    val sessions = extractUsageSessions(events, start, end, excludedPackage)
    val nowMs = now.toEpochMilli()
    return ranges.mapIndexed { i, (s, e) ->
        val sMs = s.toEpochMilli(); val eMs = e.toEpochMilli()
        val dur = sessions.sumOf { maxOf(0L, minOf(it.endMillis, eMs) - maxOf(it.startMillis, sMs)) }
        val isCur = (nowMs in sMs until eMs) || (i == ranges.lastIndex && nowMs >= eMs)
        HourlyBar(hour = i, durationMillis = dur, isCurrentHour = isCur)
    }
}

/** Beregner top-apps for perioden sorteret efter skærmtid faldende (højst [limit] stk). */
fun calculateTopApps(events: List<FocusUsageEvent>, startTimeMillis: Long, endTimeMillis: Long, excludedPackage: String? = null, resolveLabel: (String) -> String = { it }, limit: Int = 3): List<TopAppUsage> =
    extractUsageSessions(events, startTimeMillis, endTimeMillis, excludedPackage)
        .groupBy { it.packageName }
        .mapValues { (_, s) -> s.sumOf { it.endMillis - it.startMillis } }
        .filter { it.value > 0L }
        .toList()
        .sortedByDescending { it.second }
        .take(limit)
        .map { (pkg, dur) -> TopAppUsage(pkg, resolveLabel(pkg), dur) }

/** Beregner skærmtid og antal oplåsninger for et givet tidsinterval [startTimeMillis, endTimeMillis]. */
fun calculateFocusData(events: List<FocusUsageEvent>, startTimeMillis: Long, endTimeMillis: Long, excludedPackage: String? = null): FocusData {
    if (endTimeMillis <= startTimeMillis) return FocusData(0L, 0)
    val unlocks = events.count { it.timestampMillis in startTimeMillis..endTimeMillis && it.eventType == UsageEvents.Event.KEYGUARD_HIDDEN }
    val totalTime = extractUsageSessions(events, startTimeMillis, endTimeMillis, excludedPackage).sumOf { it.endMillis - it.startMillis }
    return FocusData(screenTimeMillis = totalTime.coerceAtLeast(0L), unlockCount = unlocks)
}

/** Indlæser fokus-kortets tilstand. Returnerer NeedsAccess ved manglende tilladelse eller SecurityException (N3). */
suspend fun loadFocusCardState(hasAccess: suspend () -> Boolean, queryFocus: suspend () -> FocusData): CardState<FocusData> =
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
    val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
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
        val genericIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        try { context.startActivity(genericIntent) } catch (_: Throwable) {}
    }
}

/** Åbner Digital Wellbeing på telefonen med fallback til adgang til brugsdata. */
fun openDigitalWellbeing(context: Context) {
    val wellbeingIntent = Intent("com.google.android.apps.wellbeing.action.HOME").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    try {
        context.startActivity(wellbeingIntent)
    } catch (_: Throwable) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.wellbeing")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(launchIntent) } catch (_: Throwable) { openUsageAccessSettings(context) }
        } else {
            openUsageAccessSettings(context)
        }
    }
}

/** Henter dagens og gårsdagens fokusdata fra UsageStatsManager på Dispatchers.IO (N5, N10: gemmes ikke). */
suspend fun queryUsageStatsFocusData(context: Context, now: Instant = Instant.now(), zoneId: ZoneId = ZoneId.systemDefault()): FocusData = withContext(Dispatchers.IO) {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return@withContext FocusData(0L, 0)
    val (startYesterday, endYesterday) = yesterdayFocusTimeRange(now, zoneId)
    val (startToday, endToday) = dayFocusTimeRange(now, zoneId)

    val events = usageStatsManager.queryEvents(startYesterday.toEpochMilli(), endToday.toEpochMilli())
    val eventList = mutableListOf<FocusUsageEvent>()
    val event = UsageEvents.Event()
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        eventList.add(FocusUsageEvent(timestampMillis = event.timeStamp, eventType = event.eventType, packageName = event.packageName.orEmpty()))
    }

    val yesterdayData = calculateFocusData(eventList, startYesterday.toEpochMilli(), endYesterday.toEpochMilli(), context.packageName)
    val todayData = calculateFocusData(eventList, startToday.toEpochMilli(), endToday.toEpochMilli(), context.packageName)
    val todayDate = now.atZone(zoneId).toLocalDate()
    val hourlyBars = calculateHourlyBars(eventList, todayDate, now, zoneId, context.packageName)

    val pm = context.packageManager
    val topApps = calculateTopApps(eventList, startToday.toEpochMilli(), endToday.toEpochMilli(), context.packageName, resolveLabel = { pkg ->
        try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Throwable) {
            pkg.substringAfterLast('.')
        }
    })

    FocusData(todayData.screenTimeMillis, todayData.unlockCount, yesterdayData.screenTimeMillis, hourlyBars, topApps)
}

/**
 * Fokus-kort til hjemskærmens "i dag"-side (F16, N4, N10).
 * Kompakt visning: dagens skærmtid og oplåsninger.
 * Ved tryk: detaljevisning med forskel mod i går, timesøjler (nu i accent, andre i pastel-sky), top 3 apps og link til Digital Wellbeing.
 */
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    loadFocus: (suspend () -> FocusData)? = null,
    hasPermission: (suspend () -> Boolean)? = null,
    onGrantAccess: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onOpenWellbeing: (() -> Unit)? = null,
    initialExpanded: Boolean = false,
    reloadTrigger: Any? = null,
    now: () -> Instant = Instant::now,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    val context = LocalContext.current
    var permissionAttempt by remember { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(initialExpanded) }

    val actualHasPermission = hasPermission ?: { hasUsageStatsPermission(context) }
    val actualLoadFocus = loadFocus ?: { queryUsageStatsFocusData(context, now(), zoneId) }
    val actualOnGrantAccess = onGrantAccess ?: { openUsageAccessSettings(context); permissionAttempt++; Unit }
    val actualOnOpenWellbeing = onOpenWellbeing ?: { openDigitalWellbeing(context) }

    LoadedCard(
        load = { loadFocusCardState(actualHasPermission, actualLoadFocus) },
        modifier = modifier.testTag("focusCard").clickable(role = Role.Button) {
            if (onClick != null) onClick() else expanded = !expanded
        },
        refreshAfterMs = { millisToNextMinute(LocalDateTime.now()) },
        reloadTrigger = if (reloadTrigger != null) (reloadTrigger to permissionAttempt) else permissionAttempt,
        onGrantAccess = actualOnGrantAccess,
        needsAccessTitle = stringResource(R.string.focus_grant_access_title),
        needsAccessExplanation = stringResource(R.string.focus_grant_access_explanation),
    ) { data ->
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                BasicText(stringResource(R.string.focus_title), style = DarkIOType.Item.copy(color = DarkIOColors.Ink))
                BasicText(formatScreenTime(data.screenTimeMillis), style = DarkIOType.Data.copy(color = DarkIOColors.Ink), modifier = Modifier.testTag("focusScreenTime"))
            }
            Spacer(Modifier.height(DarkIOSpace.S1))
            if (!expanded) {
                BasicText(formatUnlockCount(data.unlockCount), style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft), modifier = Modifier.testTag("focusUnlockCount"))
            } else {
                val diff = formatYesterdayDifference(data.screenTimeMillis, data.yesterdayScreenTimeMillis)
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    BasicText(formatUnlockCount(data.unlockCount), style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft), modifier = Modifier.testTag("focusUnlockCount"))
                    BasicText(diff.text, style = DarkIOType.Data.copy(color = if (diff.isDecrease) DarkIOColors.Positive else DarkIOColors.InkSoft), modifier = Modifier.testTag("focusDifference"))
                }
                if (data.hourlyBars.isNotEmpty()) {
                    Spacer(Modifier.height(DarkIOSpace.S2))
                    val maxHourly = (data.hourlyBars.maxOfOrNull { it.durationMillis } ?: 0L).coerceAtLeast(1L)
                    Row(Modifier.fillMaxWidth().height(28.dp).testTag("focusHourlyChart"), Arrangement.spacedBy(2.dp), Alignment.Bottom) {
                        for (bar in data.hourlyBars) {
                            val fraction = (bar.durationMillis.toFloat() / maxHourly).coerceIn(0f, 1f)
                            Box(Modifier.weight(1f).height(maxOf(3.dp, 28.dp * fraction)).clip(DarkIOShapes.Sm).background(if (bar.isCurrentHour) DarkIOColors.Accent else DarkIOColors.Sky).testTag("focusBar_${bar.hour}"))
                        }
                    }
                }
                if (data.topApps.isNotEmpty()) {
                    Spacer(Modifier.height(DarkIOSpace.S2))
                    Column(Modifier.fillMaxWidth().testTag("focusTopApps"), verticalArrangement = Arrangement.spacedBy(DarkIOSpace.S1)) {
                        for ((i, app) in data.topApps.withIndex()) {
                            Row(Modifier.fillMaxWidth().testTag("focusTopApp_$i"), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                BasicText(app.appLabel, style = DarkIOType.Body.copy(color = DarkIOColors.Ink), modifier = Modifier.weight(1f, fill = false))
                                BasicText(formatScreenTime(app.durationMillis), style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(DarkIOSpace.S2))
                BasicText(
                    stringResource(R.string.focus_digital_wellbeing),
                    style = DarkIOType.Button.copy(color = DarkIOColors.Accent),
                    modifier = Modifier.heightIn(min = DarkIOSize.TouchMin).clickable(role = Role.Button, onClick = actualOnOpenWellbeing).padding(vertical = DarkIOSpace.S2).testTag("focusDigitalWellbeingLink"),
                )
            }
        }
    }
}
