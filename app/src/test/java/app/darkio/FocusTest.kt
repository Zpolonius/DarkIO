package app.darkio

import android.app.usage.UsageEvents
import app.darkio.dashboard.CardState
import app.darkio.dashboard.FocusData
import app.darkio.dashboard.FocusUsageEvent
import app.darkio.dashboard.calculateFocusData
import app.darkio.dashboard.calculateHourlyBars
import app.darkio.dashboard.calculateTopApps
import app.darkio.dashboard.dayFocusTimeRange
import app.darkio.dashboard.formatScreenTime
import app.darkio.dashboard.formatUnlockCount
import app.darkio.dashboard.formatYesterdayDifference
import app.darkio.dashboard.loadFocusCardState
import app.darkio.dashboard.yesterdayFocusTimeRange
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun yesterdayFocusTimeRangeCoversFullYesterdayMidnightToMidnight() {
        val now = instant(16, 14, 30)
        val (startYesterday, endYesterday) = yesterdayFocusTimeRange(now, zone)
        assertEquals(instant(15, 0), startYesterday)
        assertEquals(instant(16, 0), endYesterday)
    }

    @Test
    fun calculateHourlyBarsForStandardDayProduces24BarsAndAggregatesSessions() {
        val date = LocalDate.of(2026, 11, 16)
        val now = instant(16, 14, 30)
        val events = listOf(
            // App i time 9: 09:15 - 09:45 (30m)
            FocusUsageEvent(instant(16, 9, 15).toEpochMilli(), UsageEvents.Event.ACTIVITY_RESUMED, "app.browser"),
            FocusUsageEvent(instant(16, 9, 45).toEpochMilli(), UsageEvents.Event.ACTIVITY_PAUSED, "app.browser"),
            // App på tværs af time 10 og 11: 10:45 - 11:15 (15m i time 10, 15m i time 11)
            FocusUsageEvent(instant(16, 10, 45).toEpochMilli(), UsageEvents.Event.ACTIVITY_RESUMED, "app.chat"),
            FocusUsageEvent(instant(16, 11, 15).toEpochMilli(), UsageEvents.Event.ACTIVITY_PAUSED, "app.chat"),
        )

        val bars = calculateHourlyBars(events, date, now, zone)
        assertEquals(24, bars.size)
        assertEquals(30 * 60 * 1000L, bars[9].durationMillis)
        assertEquals(15 * 60 * 1000L, bars[10].durationMillis)
        assertEquals(15 * 60 * 1000L, bars[11].durationMillis)
        assertEquals(0L, bars[8].durationMillis)
        assertEquals(0L, bars[12].durationMillis)
        assertEquals(60 * 60 * 1000L, bars.sumOf { it.durationMillis })
    }

    @Test
    fun calculateHourlyBarsHandlesDaylightSavingTimeTransitionSpring23Hours() {
        // Sidste søndag i marts 2026: 23 timer i Europe/Copenhagen (kl. 02:00 -> 03:00 springes over)
        val date = LocalDate.of(2026, 3, 29)
        val springNow = ZonedDateTime.of(2026, 3, 29, 15, 0, 0, 0, cphZone).toInstant()
        val t0 = date.atStartOfDay(cphZone).toInstant().toEpochMilli()

        val events = listOf(
            // Hændelse kl. 01:00 CET (time index 1)
            FocusUsageEvent(t0 + 1 * 3600_000L, UsageEvents.Event.ACTIVITY_RESUMED, "app.reader"),
            FocusUsageEvent(t0 + 1 * 3600_000L + 20 * 60_000L, UsageEvents.Event.ACTIVITY_PAUSED, "app.reader"),
            // Hændelse kl. 04:00 CEST (time index 3 i 23-timers dagen)
            FocusUsageEvent(t0 + 3 * 3600_000L, UsageEvents.Event.ACTIVITY_RESUMED, "app.reader"),
            FocusUsageEvent(t0 + 3 * 3600_000L + 40 * 60_000L, UsageEvents.Event.ACTIVITY_PAUSED, "app.reader"),
        )

        val bars = calculateHourlyBars(events, date, springNow, cphZone)
        assertEquals(23, bars.size)
        assertEquals(20 * 60 * 1000L, bars[1].durationMillis)
        assertEquals(40 * 60 * 1000L, bars[3].durationMillis)
        assertEquals(60 * 60 * 1000L, bars.sumOf { it.durationMillis })
    }

    @Test
    fun calculateHourlyBarsHandlesDaylightSavingTimeTransitionAutumn25Hours() {
        // Sidste søndag i oktober 2026: 25 timer i Europe/Copenhagen (kl. 02:00 -> 03:00 gentages)
        val date = LocalDate.of(2026, 10, 25)
        val autumnNow = ZonedDateTime.of(2026, 10, 25, 23, 0, 0, 0, cphZone).toInstant()
        val t0 = date.atStartOfDay(cphZone).toInstant().toEpochMilli()

        val events = listOf(
            // Time index 2 (første 02:00-03:00)
            FocusUsageEvent(t0 + 2 * 3600_000L, UsageEvents.Event.ACTIVITY_RESUMED, "app.night"),
            FocusUsageEvent(t0 + 2 * 3600_000L + 25 * 60_000L, UsageEvents.Event.ACTIVITY_PAUSED, "app.night"),
            // Time index 3 (gentaget 02:00-03:00)
            FocusUsageEvent(t0 + 3 * 3600_000L, UsageEvents.Event.ACTIVITY_RESUMED, "app.night"),
            FocusUsageEvent(t0 + 3 * 3600_000L + 35 * 60_000L, UsageEvents.Event.ACTIVITY_PAUSED, "app.night"),
        )

        val bars = calculateHourlyBars(events, date, autumnNow, cphZone)
        assertEquals(25, bars.size)
        assertEquals(25 * 60 * 1000L, bars[2].durationMillis)
        assertEquals(35 * 60 * 1000L, bars[3].durationMillis)
        assertEquals(60 * 60 * 1000L, bars.sumOf { it.durationMillis })
    }

    @Test
    fun calculateHourlyBarsMarksCurrentHour() {
        val date = LocalDate.of(2026, 11, 16)
        val now = instant(16, 14, 30) // Time index 14
        val bars = calculateHourlyBars(emptyList(), date, now, zone)
        assertEquals(24, bars.size)
        assertTrue(bars[14].isCurrentHour)
        assertFalse(bars[13].isCurrentHour)
        assertFalse(bars[15].isCurrentHour)
    }

    @Test
    fun calculateTopAppsLimitsToTop3AndSortsDescending() {
        val start = instant(16, 0).toEpochMilli()
        val end = instant(16, 20).toEpochMilli()
        val events = listOf(
            FocusUsageEvent(instant(16, 1).toEpochMilli(), UsageEvents.Event.ACTIVITY_RESUMED, "app.d"),
            FocusUsageEvent(instant(16, 1, 10).toEpochMilli(), UsageEvents.Event.ACTIVITY_PAUSED, "app.d"), // 10m
            FocusUsageEvent(instant(16, 2).toEpochMilli(), UsageEvents.Event.ACTIVITY_RESUMED, "app.b"),
            FocusUsageEvent(instant(16, 2, 40).toEpochMilli(), UsageEvents.Event.ACTIVITY_PAUSED, "app.b"), // 40m
            FocusUsageEvent(instant(16, 3).toEpochMilli(), UsageEvents.Event.ACTIVITY_RESUMED, "app.a"),
            FocusUsageEvent(instant(16, 3, 50).toEpochMilli(), UsageEvents.Event.ACTIVITY_PAUSED, "app.a"), // 50m
            FocusUsageEvent(instant(16, 4).toEpochMilli(), UsageEvents.Event.ACTIVITY_RESUMED, "app.c"),
            FocusUsageEvent(instant(16, 4, 30).toEpochMilli(), UsageEvents.Event.ACTIVITY_PAUSED, "app.c"), // 30m
            FocusUsageEvent(instant(16, 5).toEpochMilli(), UsageEvents.Event.ACTIVITY_RESUMED, "app.darkio"),
            FocusUsageEvent(instant(16, 5, 20).toEpochMilli(), UsageEvents.Event.ACTIVITY_PAUSED, "app.darkio"), // DarkIO ekskluderes
        )

        val topApps = calculateTopApps(
            events = events,
            startTimeMillis = start,
            endTimeMillis = end,
            excludedPackage = "app.darkio",
            resolveLabel = { it.removePrefix("app.").uppercase() },
        )

        assertEquals(3, topApps.size)
        assertEquals("A", topApps[0].appLabel)
        assertEquals(50 * 60 * 1000L, topApps[0].durationMillis)
        assertEquals("B", topApps[1].appLabel)
        assertEquals(40 * 60 * 1000L, topApps[1].durationMillis)
        assertEquals("C", topApps[2].appLabel)
        assertEquals(30 * 60 * 1000L, topApps[2].durationMillis)
    }

    @Test
    fun formatYesterdayDifferenceReturnsPositiveOnDecrease() {
        // Fald i skærmtid -> isDecrease = true (positive-farve)
        val decrease = formatYesterdayDifference(todayMillis = 60 * 60 * 1000L, yesterdayMillis = 80 * 60 * 1000L)
        assertEquals("↓ 20 m mod i går", decrease.text)
        assertTrue(decrease.isDecrease)

        // Stigning i skærmtid -> isDecrease = false (ikke positive)
        val increase = formatYesterdayDifference(todayMillis = 90 * 60 * 1000L, yesterdayMillis = 60 * 60 * 1000L)
        assertEquals("↑ 30 m mod i går", increase.text)
        assertFalse(increase.isDecrease)

        // Samme skærmtid
        val same = formatYesterdayDifference(todayMillis = 60 * 60 * 1000L, yesterdayMillis = 60 * 60 * 1000L)
        assertEquals("0 m mod i går", same.text)
        assertFalse(same.isDecrease)

        // Ingen data i går
        val noYesterday = formatYesterdayDifference(todayMillis = 45 * 60 * 1000L, yesterdayMillis = 0L)
        assertEquals("↑ 45 m mod i går", noYesterday.text)
        assertFalse(noYesterday.isDecrease)
    }
}
