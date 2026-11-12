package app.darkio.dashboard

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.core.content.ContextCompat
import app.darkio.R
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CalendarEvent(
    val title: String,
    val startMillis: Long,
    val endMillis: Long = startMillis,
    val allDay: Boolean = false,
)

/** Vælger næste aftale de næste 24 timer: ikke afsluttet endnu, tidsfæstet før heldags, tidligste start først (F3). */
fun selectNextEvent(
    events: List<CalendarEvent>,
    nowMillis: Long,
    windowMillis: Long = 24 * 60 * 60 * 1000L,
): CalendarEvent? {
    val windowEnd = nowMillis + windowMillis
    return events
        .filter { it.endMillis > nowMillis && it.startMillis <= windowEnd }
        .minWithOrNull(compareBy({ it.allDay }, { it.startMillis }, { it.endMillis }))
}

/** Formaterer tidspunkt for aftale (fx "14:30", "i morgen 09:15" eller "hele dagen"). */
fun formatEventTime(
    event: CalendarEvent,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    allDayLabel: String = "hele dagen",
    tomorrowLabel: ((String) -> String) = { "i morgen $it" },
): String {
    if (event.allDay) return allDayLabel
    val start = Instant.ofEpochMilli(event.startMillis).atZone(zoneId).toLocalDateTime()
    val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDateTime()
    val timeStr = DateTimeFormatter.ofPattern("HH:mm").format(start)
    return if (start.toLocalDate().isAfter(now.toLocalDate())) {
        tomorrowLabel(timeStr)
    } else {
        timeStr
    }
}

/** Henter kalenderdata uden for main-tråden (Dispatchers.IO). Gemmer intet (N5). */
suspend fun queryNextCalendarEvent(
    context: Context,
    nowMillis: Long = System.currentTimeMillis(),
): CalendarEvent? = withContext(Dispatchers.IO) {
    val windowMillis = 24 * 60 * 60 * 1000L
    val startWindow = nowMillis
    val endWindow = nowMillis + windowMillis

    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, startWindow)
    ContentUris.appendId(builder, endWindow)
    val uri = builder.build()

    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
    )

    val events = mutableListOf<CalendarEvent>()
    context.contentResolver.query(
        uri,
        projection,
        null,
        null,
        "${CalendarContract.Instances.BEGIN} ASC",
    )?.use { cursor ->
        val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
        val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
        val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
        val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)

        while (cursor.moveToNext()) {
            val title = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty() else ""
            val begin = if (beginIdx >= 0) cursor.getLong(beginIdx) else 0L
            val end = if (endIdx >= 0) cursor.getLong(endIdx) else begin
            val allDay = if (allDayIdx >= 0) cursor.getInt(allDayIdx) == 1 else false
            events.add(CalendarEvent(title = title, startMillis = begin, endMillis = end, allDay = allDay))
        }
    }
    selectNextEvent(events, nowMillis, windowMillis)
}

/** Indlæser korttilstand for kalender. Tjekker tilladelse før hentning (F3, N4). */
suspend fun loadCalendarCardState(
    hasPermission: () -> Boolean,
    queryEvent: suspend () -> CalendarEvent?,
): CardState<CalendarEvent?> {
    if (!hasPermission()) return CardState.NeedsAccess
    return try {
        CardState.Content(queryEvent())
    } catch (e: CancellationException) {
        throw e
    } catch (_: SecurityException) {
        CardState.NeedsAccess
    } catch (_: Throwable) {
        CardState.Error
    }
}

/**
 * Kalenderkort til side 0 ("i dag").
 * Viser næste aftale de næste 24 timer (F3), tom tilstand ("ingen aftaler_"), eller "Giv adgang".
 */
@Composable
fun CalendarCard(
    modifier: Modifier = Modifier,
    loadEvent: (suspend () -> CalendarEvent?)? = null,
    hasPermission: (() -> Boolean)? = null,
    nowMillis: () -> Long = System::currentTimeMillis,
) {
    val context = LocalContext.current
    val actualHasPermission = hasPermission ?: {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
    }
    val actualLoadEvent = loadEvent ?: { queryNextCalendarEvent(context, nowMillis()) }

    var permissionAttempt by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionAttempt++
    }

    val tomorrowFormat = stringResource(R.string.calendar_tomorrow)
    val allDayText = stringResource(R.string.calendar_all_day)

    LoadedCard(
        load = {
            loadCalendarCardState(
                hasPermission = actualHasPermission,
                queryEvent = actualLoadEvent,
            )
        },
        modifier = modifier,
        refreshAfterMs = { millisToNextMinute(LocalDateTime.now()) },
        reloadTrigger = permissionAttempt,
        onGrantAccess = { launcher.launch(Manifest.permission.READ_CALENDAR) },
        needsAccessTitle = stringResource(R.string.calendar_grant_access_title),
        needsAccessExplanation = stringResource(R.string.calendar_grant_access_explanation),
    ) { event ->
        if (event != null) {
            BasicText(
                event.title.ifBlank { stringResource(R.string.calendar_untitled) },
                style = DarkIOType.Item.copy(color = DarkIOColors.Ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                formatEventTime(
                    event = event,
                    nowMillis = nowMillis(),
                    allDayLabel = allDayText,
                    tomorrowLabel = { String.format(tomorrowFormat, it) },
                ),
                style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft),
            )
        } else {
            BasicText(
                buildAnnotatedString {
                    append(stringResource(R.string.calendar_empty))
                    withStyle(SpanStyle(color = DarkIOColors.Accent)) { append("_") }
                },
                style = DarkIOType.Item.copy(color = DarkIOColors.InkMuted),
            )
        }
    }
}
