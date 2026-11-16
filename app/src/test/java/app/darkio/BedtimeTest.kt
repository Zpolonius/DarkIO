package app.darkio

import android.app.NotificationManager
import app.darkio.dashboard.CalendarEvent
import app.darkio.data.BedtimeSummary
import app.darkio.data.DEFAULT_BEDTIME_END_MINUTES
import app.darkio.data.DEFAULT_BEDTIME_START_MINUTES
import app.darkio.data.Settings
import app.darkio.data.formatNextAlarm
import app.darkio.data.isBedtimeMode
import app.darkio.data.selectFirstEventTomorrow
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-test for godnat-tilstand (F24).
 * Tester isBedtimeMode, tidsgrænser (inkl. over midnat), interruptionFilter,
 * formatNextAlarm og selectFirstEventTomorrow.
 */
class BedtimeTest {
    private val zone = ZoneId.of("Europe/Copenhagen")
    private val defaultSettings = Settings()

    @Test
    fun defaultSettingsHaveExpectedBedtimeInterval() {
        assertEquals(18 * 60, defaultSettings.bedtimeStartMinutes) // 18:00
        assertEquals(DEFAULT_BEDTIME_START_MINUTES, defaultSettings.bedtimeStartMinutes)
        assertEquals(10 * 60, defaultSettings.bedtimeEndMinutes) // 10:00
        assertEquals(DEFAULT_BEDTIME_END_MINUTES, defaultSettings.bedtimeEndMinutes)
    }

    @Test
    fun isBedtimeModeRequiresDndActive() {
        // Kl 22:00 er inden for 18:00–10:00
        val t2200 = ZonedDateTime.of(2026, 11, 16, 22, 0, 0, 0, zone)

        // Forstyr ikke inaktivt -> false
        assertFalse(
            "INTERRUPTION_FILTER_ALL må ikke aktivere godnat",
            isBedtimeMode(NotificationManager.INTERRUPTION_FILTER_ALL, defaultSettings, t2200),
        )
        assertFalse(
            "INTERRUPTION_FILTER_UNKNOWN må ikke aktivere godnat",
            isBedtimeMode(NotificationManager.INTERRUPTION_FILTER_UNKNOWN, defaultSettings, t2200),
        )

        // Forstyr ikke aktivt (priority, none, alarms) -> true
        assertTrue(
            "INTERRUPTION_FILTER_PRIORITY skal aktivere godnat",
            isBedtimeMode(NotificationManager.INTERRUPTION_FILTER_PRIORITY, defaultSettings, t2200),
        )
        assertTrue(
            "INTERRUPTION_FILTER_NONE skal aktivere godnat",
            isBedtimeMode(NotificationManager.INTERRUPTION_FILTER_NONE, defaultSettings, t2200),
        )
        assertTrue(
            "INTERRUPTION_FILTER_ALARMS skal aktivere godnat",
            isBedtimeMode(NotificationManager.INTERRUPTION_FILTER_ALARMS, defaultSettings, t2200),
        )
    }

    @Test
    fun isBedtimeModeDefaultBoundariesOverMidnight() {
        val filter = NotificationManager.INTERRUPTION_FILTER_PRIORITY

        val t1759 = ZonedDateTime.of(2026, 11, 16, 17, 59, 0, 0, zone)
        val t1800 = ZonedDateTime.of(2026, 11, 16, 18, 0, 0, 0, zone)
        val t2359 = ZonedDateTime.of(2026, 11, 16, 23, 59, 0, 0, zone)
        val t0000 = ZonedDateTime.of(2026, 11, 17, 0, 0, 0, 0, zone)
        val t0500 = ZonedDateTime.of(2026, 11, 17, 5, 0, 0, 0, zone)
        val t0959 = ZonedDateTime.of(2026, 11, 17, 9, 59, 0, 0, zone)
        val t1000 = ZonedDateTime.of(2026, 11, 17, 10, 0, 0, 0, zone)
        val t1200 = ZonedDateTime.of(2026, 11, 17, 12, 0, 0, 0, zone)

        assertFalse("17:59 er før sengetid", isBedtimeMode(filter, defaultSettings, t1759))
        assertTrue("18:00 er start på sengetid", isBedtimeMode(filter, defaultSettings, t1800))
        assertTrue("23:59 er sengetid", isBedtimeMode(filter, defaultSettings, t2359))
        assertTrue("00:00 er sengetid efter midnat", isBedtimeMode(filter, defaultSettings, t0000))
        assertTrue("05:00 er sengetid", isBedtimeMode(filter, defaultSettings, t0500))
        assertTrue("09:59 er sidste minut i sengetid", isBedtimeMode(filter, defaultSettings, t0959))
        assertFalse("10:00 er sluttid (eksklusiv)", isBedtimeMode(filter, defaultSettings, t1000))
        assertFalse("12:00 er uden for sengetid", isBedtimeMode(filter, defaultSettings, t1200))
    }

    @Test
    fun isBedtimeModeCustomRangeSameDay() {
        val filter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val customSettings = defaultSettings.copy(
            bedtimeStartMinutes = 21 * 60, // 21:00
            bedtimeEndMinutes = 23 * 60, // 23:00
        )

        val t2059 = ZonedDateTime.of(2026, 11, 16, 20, 59, 0, 0, zone)
        val t2100 = ZonedDateTime.of(2026, 11, 16, 21, 0, 0, 0, zone)
        val t2215 = ZonedDateTime.of(2026, 11, 16, 22, 15, 0, 0, zone)
        val t2300 = ZonedDateTime.of(2026, 11, 16, 23, 0, 0, 0, zone)

        assertFalse("20:59 er før", isBedtimeMode(filter, customSettings, t2059))
        assertTrue("21:00 er start", isBedtimeMode(filter, customSettings, t2100))
        assertTrue("22:15 er i intervallet", isBedtimeMode(filter, customSettings, t2215))
        assertFalse("23:00 er sluttidspunkt", isBedtimeMode(filter, customSettings, t2300))
    }

    @Test
    fun isBedtimeModeCustomRangeOverMidnight() {
        val filter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val customSettings = defaultSettings.copy(
            bedtimeStartMinutes = 22 * 60 + 30, // 22:30
            bedtimeEndMinutes = 7 * 60, // 07:00
        )

        val t2229 = ZonedDateTime.of(2026, 11, 16, 22, 29, 0, 0, zone)
        val t2230 = ZonedDateTime.of(2026, 11, 16, 22, 30, 0, 0, zone)
        val t0300 = ZonedDateTime.of(2026, 11, 17, 3, 0, 0, 0, zone)
        val t0659 = ZonedDateTime.of(2026, 11, 17, 6, 59, 0, 0, zone)
        val t0700 = ZonedDateTime.of(2026, 11, 17, 7, 0, 0, 0, zone)

        assertFalse("22:29 er før", isBedtimeMode(filter, customSettings, t2229))
        assertTrue("22:30 er start", isBedtimeMode(filter, customSettings, t2230))
        assertTrue("03:00 er i intervallet", isBedtimeMode(filter, customSettings, t0300))
        assertTrue("06:59 er sidste minut", isBedtimeMode(filter, customSettings, t0659))
        assertFalse("07:00 er slut", isBedtimeMode(filter, customSettings, t0700))
    }

    @Test
    fun isBedtimeModeEqualStartAndEndIsNeverActive() {
        val filter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val customSettings = defaultSettings.copy(
            bedtimeStartMinutes = 22 * 60,
            bedtimeEndMinutes = 22 * 60,
        )
        val t2200 = ZonedDateTime.of(2026, 11, 16, 22, 0, 0, 0, zone)
        assertFalse("Ens start og slut er tomt interval", isBedtimeMode(filter, customSettings, t2200))
    }

    @Test
    fun formatNextAlarmFormatting() {
        val noAlarm = "intet vækkeur"

        // null og negative værdier giver noAlarmText
        assertEquals(noAlarm, formatNextAlarm(null, zone, noAlarm))
        assertEquals(noAlarm, formatNextAlarm(0L, zone, noAlarm))
        assertEquals(noAlarm, formatNextAlarm(-1L, zone, noAlarm))

        // Gyldigt tidspunkt kl. 07:15
        val t0715 = ZonedDateTime.of(2026, 11, 17, 7, 15, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("vækkeur 07:15", formatNextAlarm(t0715, zone, noAlarm))

        // Uden præfiks
        assertEquals("07:15", formatNextAlarm(t0715, zone, noAlarm, prefix = ""))

        // Aften vækkeur kl. 23:45
        val t2345 = ZonedDateTime.of(2026, 11, 17, 23, 45, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("vækkeur 23:45", formatNextAlarm(t2345, zone, noAlarm))
    }

    @Test
    fun selectFirstEventTomorrowSelection() {
        val now = ZonedDateTime.of(2026, 11, 16, 22, 0, 0, 0, zone) // Monday 22:00
        val tomorrow = now.toLocalDate().plusDays(1) // Tuesday 2026-11-17

        val eventToday = CalendarEvent(
            title = "Aftensmad i dag",
            startMillis = now.toLocalDate().atTime(19, 0).atZone(zone).toInstant().toEpochMilli(),
            endMillis = now.toLocalDate().atTime(20, 0).atZone(zone).toInstant().toEpochMilli(),
        )
        val eventTomorrowMorning = CalendarEvent(
            title = "Morgenmøde",
            startMillis = tomorrow.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
            endMillis = tomorrow.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
        )
        val eventTomorrowAfternoon = CalendarEvent(
            title = "Frokost",
            startMillis = tomorrow.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            endMillis = tomorrow.atTime(13, 0).atZone(zone).toInstant().toEpochMilli(),
        )
        val eventDayAfterTomorrow = CalendarEvent(
            title = "Onsdagsmøde",
            startMillis = tomorrow.plusDays(1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
            endMillis = tomorrow.plusDays(1).atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
        )

        val list = listOf(eventToday, eventTomorrowAfternoon, eventTomorrowMorning, eventDayAfterTomorrow)
        val selected = selectFirstEventTomorrow(list, now)

        assertEquals("Morgenmøde", selected?.title)
        assertEquals(eventTomorrowMorning.startMillis, selected?.startMillis)

        // Tom liste
        assertNull(selectFirstEventTomorrow(emptyList(), now))
        // Kun begivenheder i dag
        assertNull(selectFirstEventTomorrow(listOf(eventToday), now))
    }
}
