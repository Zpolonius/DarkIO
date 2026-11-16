package app.darkio.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.Settings as AndroidSettings
import androidx.core.content.ContextCompat
import app.darkio.dashboard.CalendarEvent
import app.darkio.dashboard.checkStepsPermission
import app.darkio.dashboard.queryHealthConnectSteps
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dataoversigt til godnat-visningen på side 1 (F24).
 */
data class BedtimeSummary(
    val alarmTriggerMillis: Long? = null,
    val firstEventTomorrow: CalendarEvent? = null,
    val steps: Long? = null,
    val workoutCompleted: Boolean = false,
)

/**
 * Afgør om godnat-tilstand (Sengetid) er aktiv.
 * Sand når Forstyr ikke er aktivt (filter er hverken ALL eller UNKNOWN)
 * OG [now] falder i [settings.bedtimeStartMinutes, settings.bedtimeEndMinutes).
 * Håndterer midnat/wrap-around svarende til [isWorkHours].
 */
fun isBedtimeMode(interruptionFilter: Int, settings: Settings, now: ZonedDateTime): Boolean {
    val dndActive = interruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
        interruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    if (!dndActive) return false

    val nowMinutes = now.hour * 60 + now.minute
    val start = settings.bedtimeStartMinutes
    val end = settings.bedtimeEndMinutes
    return if (start < end) {
        nowMinutes in start until end
    } else if (start > end) {
        nowMinutes >= start || nowMinutes < end
    } else {
        false
    }
}

/**
 * Formaterer tidspunkt for næste vækkeur.
 * Returnerer [noAlarmText] hvis [triggerTimeMillis] er null eller ikke-positivt.
 */
fun formatNextAlarm(
    triggerTimeMillis: Long?,
    zoneId: ZoneId,
    noAlarmText: String,
    prefix: String = "vækkeur",
): String {
    if (triggerTimeMillis == null || triggerTimeMillis <= 0L) {
        return noAlarmText
    }
    val dt = Instant.ofEpochMilli(triggerTimeMillis).atZone(zoneId).toLocalDateTime()
    val timeStr = DateTimeFormatter.ofPattern("HH:mm").format(dt)
    return if (prefix.isNotBlank()) "$prefix $timeStr" else timeStr
}

/**
 * Åbner Androids sengetids- eller Forstyr ikke-indstillinger:
 * prøver com.google.android.apps.wellbeing.action.BEDTIME_SETTINGS,
 * fallback til android.settings.ZEN_MODE_SETTINGS, derefter android.settings.SETTINGS.
 */
fun openBedtimeSettings(context: Context) {
    val wellbeingIntent = Intent("com.google.android.apps.wellbeing.action.BEDTIME_SETTINGS").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val zenIntent = Intent("android.settings.ZEN_MODE_SETTINGS").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val settingsIntent = Intent(AndroidSettings.ACTION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(wellbeingIntent)
    } catch (_: Throwable) {
        try {
            context.startActivity(zenIntent)
        } catch (_: Throwable) {
            try {
                context.startActivity(settingsIntent)
            } catch (_: Throwable) {}
        }
    }
}

/**
 * Læser enhedens nuværende interruption filter.
 */
fun getInterruptionFilter(context: Context): Int {
    val nm = context.getSystemService(NotificationManager::class.java) ?: return NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    return try {
        nm.currentInterruptionFilter
    } catch (_: Throwable) {
        NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }
}

/**
 * Henter triggerTime for næste vækkeur via AlarmManager.
 */
fun queryNextAlarmTrigger(context: Context): Long? {
    val am = context.getSystemService(AlarmManager::class.java) ?: return null
    return try {
        am.nextAlarmClock?.triggerTime
    } catch (_: Throwable) {
        null
    }
}

/**
 * Vælger første aftale i morgen blandt givne begivenheder.
 */
fun selectFirstEventTomorrow(
    events: List<CalendarEvent>,
    now: ZonedDateTime,
): CalendarEvent? {
    val tomorrow = now.toLocalDate().plusDays(1)
    val startWindow = tomorrow.atStartOfDay(now.zone).toInstant().toEpochMilli()
    val endWindow = tomorrow.plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
    return events
        .filter { it.startMillis in startWindow until endWindow || (it.startMillis < endWindow && it.endMillis > startWindow) }
        .minWithOrNull(compareBy({ it.allDay }, { it.startMillis }, { it.endMillis }))
}

/**
 * Henter første aftale i morgen via CalendarContract.Instances på Dispatchers.IO.
 * Returnerer null hvis tilladelse mangler eller ingen aftaler findes.
 */
suspend fun queryFirstEventTomorrow(
    context: Context,
    now: ZonedDateTime = ZonedDateTime.now(),
): CalendarEvent? = withContext(Dispatchers.IO) {
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasPermission) return@withContext null

    val tomorrow = now.toLocalDate().plusDays(1)
    val startWindow = tomorrow.atStartOfDay(now.zone).toInstant().toEpochMilli()
    val endWindow = tomorrow.plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()

    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, startWindow)
    ContentUris.appendId(builder, endWindow)
    val uri = builder.build()

    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.CALENDAR_ID,
    )

    val events = mutableListOf<CalendarEvent>()
    try {
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
            val calIdIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)

            while (cursor.moveToNext()) {
                val title = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty() else ""
                val begin = if (beginIdx >= 0) cursor.getLong(beginIdx) else 0L
                val end = if (endIdx >= 0) cursor.getLong(endIdx) else begin
                val allDay = if (allDayIdx >= 0) cursor.getInt(allDayIdx) == 1 else false
                val calendarId = if (calIdIdx >= 0) cursor.getLong(calIdIdx) else 0L
                events.add(CalendarEvent(title = title, startMillis = begin, endMillis = end, allDay = allDay, calendarId = calendarId))
            }
        }
    } catch (_: SecurityException) {
        return@withContext null
    } catch (_: Throwable) {
        return@withContext null
    }

    events.minWithOrNull(compareBy({ it.allDay }, { it.startMillis }, { it.endMillis }))
}

/**
 * Tjekker om dagens træning er gennemført.
 */
suspend fun queryIsWorkoutCompletedToday(
    dao: WorkoutDao,
    today: LocalDate = LocalDate.now(),
): Boolean = withContext(Dispatchers.IO) {
    try {
        val dow = today.dayOfWeek.value
        val allWorkouts = dao.getAllWorkouts()
        val todayWorkout = allWorkouts.firstOrNull { it.dayOfWeek == dow } ?: dao.getWorkoutForDay(dow)
        val completedDates = dao.getCompletedDates().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        today in completedDates || todayWorkout?.completedDate == today.toString()
    } catch (_: Throwable) {
        false
    }
}

/**
 * Henter samlet BedtimeSummary på Dispatchers.IO (N10: kaldes når hjemskærmen vises / ON_RESUME).
 */
suspend fun queryBedtimeSummary(
    context: Context,
    now: ZonedDateTime = ZonedDateTime.now(),
    workoutDao: WorkoutDao? = null,
): BedtimeSummary = withContext(Dispatchers.IO) {
    val alarmMillis = queryNextAlarmTrigger(context)
    val firstTomorrow = queryFirstEventTomorrow(context, now)

    val steps = if (checkStepsPermission(context)) {
        try {
            queryHealthConnectSteps(context, now.toInstant(), now.zone)
        } catch (_: Throwable) {
            null
        }
    } else {
        null
    }

    val dao = workoutDao ?: AppDatabase.open(context).workouts()
    val workoutCompleted = queryIsWorkoutCompletedToday(dao, now.toLocalDate())

    BedtimeSummary(
        alarmTriggerMillis = alarmMillis,
        firstEventTomorrow = firstTomorrow,
        steps = steps,
        workoutCompleted = workoutCompleted,
    )
}
