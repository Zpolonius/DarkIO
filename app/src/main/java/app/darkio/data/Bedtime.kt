package app.darkio.data

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import app.darkio.dashboard.CalendarEvent
import app.darkio.dashboard.checkStepsPermission
import app.darkio.dashboard.queryCalendarEvents
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
): CalendarEvent? {
    val tomorrow = now.toLocalDate().plusDays(1)
    val startWindow = tomorrow.atStartOfDay(now.zone).toInstant().toEpochMilli()
    val endWindow = tomorrow.plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
    val events = queryCalendarEvents(context, startWindow, endWindow)
    return selectFirstEventTomorrow(events, now)
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
    val workoutCompleted = isWorkoutCompletedOn(dao, now.toLocalDate())

    BedtimeSummary(
        alarmTriggerMillis = alarmMillis,
        firstEventTomorrow = firstTomorrow,
        steps = steps,
        workoutCompleted = workoutCompleted,
    )
}
