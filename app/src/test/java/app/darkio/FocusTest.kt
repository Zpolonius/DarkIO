package app.darkio

import android.app.usage.UsageEvents
import app.darkio.dashboard.CardState
import app.darkio.dashboard.FocusData
import app.darkio.dashboard.FocusUsageEvent
import app.darkio.dashboard.calculateFocusData
import app.darkio.dashboard.dayFocusTimeRange
import app.darkio.dashboard.formatScreenTime
import app.darkio.dashboard.formatUnlockCount
import app.darkio.dashboard.loadFocusCardState
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTest {
    private val zone = ZoneId.of("UTC")
    private val cphZone = ZoneId.of("Europe/Copenhagen")

    private fun instant(day: Int, hour: Int, minute: Int = 0, second: Int = 0, z: ZoneId = zone) =
        LocalDateTime.of(2026, 11, day, hour, minute, second).atZone(z).toInstant()

    @Test
    fun dayFocusTimeRangeStartsAtMidnightOfCurrentDay() {
        val now = instant(16, 14, 30)
        val (start, end) = dayFocusTimeRange(now, zone)
        assertEquals(instant(16, 0), start)
        assertEquals(now, end)
    }

    @Test
    fun focusResetsAtMidnight() {
        val beforeMidnight = instant(16, 23, 59, 59)
        val (startBefore, endBefore) = dayFocusTimeRange(beforeMidnight, zone)
        assertEquals(instant(16, 0), startBefore)
        assertEquals(beforeMidnight, endBefore)

        val pastMidnight = instant(17, 0, 0, 1)
        val (startAfter, endAfter) = dayFocusTimeRange(pastMidnight, zone)
        assertEquals(instant(17, 0), startAfter)
        assertEquals(pastMidnight, endAfter)
        assertTrue(startAfter.isAfter(endBefore))
    }

    @Test
    fun calculateFocusDataHandlesDaylightSavingTimeTransitionSpring23Hours() {
        // Sidste søndag i marts 2026 (29. marts 2026): skift til sommertid kl. 02:00 -> 03:00 (23-timers dag)
        val springNow = ZonedDateTime.of(2026, 3, 29, 15, 0, 0, 0, cphZone).toInstant()
        val (startSpring, endSpring) = dayFocusTimeRange(springNow, cphZone)
        val expectedStart = ZonedDateTime.of(2026, 3, 29, 0, 0, 0, 0, cphZone).toInstant()
        assertEquals(expectedStart, startSpring)
        assertEquals(springNow, endSpring)

        val t0 = startSpring.toEpochMilli()
        val tMidday = springNow.toEpochMilli()

        // Test hændelser under sommertidsskiftet
        val events = listOf(
            FocusUsageEvent(t0 + 1 * 3600_000L, UsageEvents.Event.KEYGUARD_HIDDEN), // 01:00
            FocusUsageEvent(t0 + 1 * 3600_000L, UsageEvents.Event.ACTIVITY_RESUMED, "app.browser"),
            FocusUsageEvent(t0 + 3 * 3600_000L, UsageEvents.Event.ACTIVITY_PAUSED, "app.browser"), // 04:00 (1 time reel varighed pga. 02->03 spring)
            FocusUsageEvent(t0 + 4 * 3600_000L, UsageEvents.Event.KEYGUARD_HIDDEN),
        )

        val result = calculateFocusData(events, t0, tMidday)
        assertEquals(2, result.unlockCount)
        assertEquals(2 * 3600_000L, result.screenTimeMillis)
    }

    @Test
    fun calculateFocusDataHandlesDaylightSavingTimeTransitionAutumn25Hours() {
        // Sidste søndag i oktober 2026 (25. oktober 2026): skift til vintertid kl. 03:00 -> 02:00 (25-timers dag)
        val autumnNow = ZonedDateTime.of(2026, 10, 25, 23, 0, 0, 0, cphZone).toInstant()
        val (startAutumn, endAutumn) = dayFocusTimeRange(autumnNow, cphZone)
        val expectedStart = ZonedDateTime.of(2026, 10, 25, 0, 0, 0, 0, cphZone).toInstant()
        assertEquals(expectedStart, startAutumn)
        assertEquals(autumnNow, endAutumn)

        val t0 = startAutumn.toEpochMilli()
        val tEnd = autumnNow.toEpochMilli()

        val events = listOf(
            FocusUsageEvent(t0 + 2 * 3600_000L, UsageEvents.Event.KEYGUARD_HIDDEN),
            FocusUsageEvent(t0 + 3 * 3600_000L, UsageEvents.Event.ACTIVITY_RESUMED, "app.notes"),
            FocusUsageEvent(t0 + 4 * 3600_000L, UsageEvents.Event.ACTIVITY_PAUSED, "app.notes"),
        )
        val result = calculateFocusData(events, t0, tEnd)
        assertEquals(1, result.unlockCount)
        assertEquals(3600_000L, result.screenTimeMillis)
    }

    @Test
    fun calculateFocusDataAggregatesScreenTimeAndUnlocks() {
        val start = instant(16, 0).toEpochMilli()
        val end = instant(16, 18).toEpochMilli()

        val events = listOf(
            // Gårsdagens hændelse ignoreres
            FocusUsageEvent(instant(15, 23).toEpochMilli(), UsageEvents.Event.KEYGUARD_HIDDEN),
            // Dagens oplåsninger
            FocusUsageEvent(instant(16, 8).toEpochMilli(), UsageEvents.Event.KEYGUARD_HIDDEN),
            FocusUsageEvent(instant(16, 12).toEpochMilli(), UsageEvents.Event.KEYGUARD_HIDDEN),
            // App A: 09:00 - 09:30 (30 min)
            FocusUsageEvent(instant(16, 9, 0).toEpochMilli(), UsageEvents.Event.ACTIVITY_RESUMED, "app.a"),
            FocusUsageEvent(instant(16, 9, 30).toEpochMilli(), UsageEvents.Event.ACTIVITY_PAUSED, "app.a"),
            // App B: 10:00 - 10:45 (45 min)
            FocusUsageEvent(instant(16, 10, 0).toEpochMilli(), UsageEvents.Event.ACTIVITY_RESUMED, "app.b"),
            FocusUsageEvent(instant(16, 10, 45).toEpochMilli(), UsageEvents.Event.ACTIVITY_PAUSED, "app.b"),
            // App C: 11:00 - skærm slukkes kl. 11:15 (15 min)
            FocusUsageEvent(instant(16, 11, 0).toEpochMilli(), UsageEvents.Event.ACTIVITY_RESUMED, "app.c"),
            FocusUsageEvent(instant(16, 11, 15).toEpochMilli(), UsageEvents.Event.SCREEN_NON_INTERACTIVE),
            // DarkIO selv ignoreres
            FocusUsageEvent(instant(16, 14, 0).toEpochMilli(), UsageEvents.Event.ACTIVITY_RESUMED, "app.darkio"),
            FocusUsageEvent(instant(16, 14, 30).toEpochMilli(), UsageEvents.Event.ACTIVITY_PAUSED, "app.darkio"),
        )

        val result = calculateFocusData(events, start, end, excludedPackage = "app.darkio")
        assertEquals(2, result.unlockCount)
        // 30 min + 45 min + 15 min = 90 min = 5_400_000 ms
        assertEquals(90 * 60 * 1000L, result.screenTimeMillis)
    }

    @Test
    fun calculateFocusDataClampsMidnightCrossingSessions() {
        val start = instant(16, 0).toEpochMilli()
        val end = instant(16, 6).toEpochMilli()

        // App startet før midnat og afsluttet 00:20 i dag
        val events = listOf(
            FocusUsageEvent(instant(16, 0, 20).toEpochMilli(), UsageEvents.Event.ACTIVITY_PAUSED, "app.night"),
        )

        val result = calculateFocusData(events, start, end)
        assertEquals(20 * 60 * 1000L, result.screenTimeMillis)
    }

    @Test
    fun formatScreenTimeFormatsMinutesAndHours() {
        assertEquals("0 m", formatScreenTime(0L))
        assertEquals("45 m", formatScreenTime(45 * 60 * 1000L))
        assertEquals("1 t", formatScreenTime(60 * 60 * 1000L))
        assertEquals("1 t 24 m", formatScreenTime((84 * 60 * 1000L)))
        assertEquals("2 t 15 m", formatScreenTime((135 * 60 * 1000L)))
    }

    @Test
    fun formatUnlockCountFormatsDanishSingularAndPlural() {
        assertEquals("0 oplåsninger", formatUnlockCount(0))
        assertEquals("1 oplåsning", formatUnlockCount(1))
        assertEquals("2 oplåsninger", formatUnlockCount(2))
        assertEquals("34 oplåsninger", formatUnlockCount(34))
    }

    @Test
    fun loadFocusCardStateReturnsNeedsAccessWhenNoAccess() = runTest {
        val state = loadFocusCardState({ false }, { FocusData(3600_000L, 5) })
        assertEquals(CardState.NeedsAccess, state)
    }

    @Test
    fun loadFocusCardStateReturnsContentWhenAccessGranted() = runTest {
        val state = loadFocusCardState({ true }, { FocusData(3600_000L, 5) })
        assertEquals(CardState.Content(FocusData(3600_000L, 5)), state)
    }

    @Test
    fun loadFocusCardStateReturnsNeedsAccessOnSecurityException() = runTest {
        val state = loadFocusCardState({ true }, { throw SecurityException("Denied") })
        assertEquals(CardState.NeedsAccess, state)
    }

    @Test
    fun loadFocusCardStateReturnsErrorOnOtherThrowable() = runTest {
        val state = loadFocusCardState({ true }, { error("Failure") })
        assertEquals(CardState.Error, state)
    }

    @Test(expected = CancellationException::class)
    fun loadFocusCardStateRethrowsCancellationException() = runTest {
        loadFocusCardState({ true }, { throw CancellationException("Cancelled") })
    }
}
