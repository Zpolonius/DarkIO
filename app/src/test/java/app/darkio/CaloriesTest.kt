package app.darkio

import app.darkio.dashboard.CalorieSample
import app.darkio.dashboard.CaloriesData
import app.darkio.dashboard.CardState
import app.darkio.dashboard.calculateCaloriesSum
import app.darkio.dashboard.dayCaloriesTimeRange
import app.darkio.dashboard.formatCaloriesNumber
import app.darkio.dashboard.loadCaloriesCardState
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaloriesTest {
    private val zone = ZoneId.of("UTC")
    private fun instant(day: Int, hour: Int, minute: Int = 0, second: Int = 0) =
        LocalDateTime.of(2026, 11, day, hour, minute, second).atZone(zone).toInstant()

    @Test
    fun dayCaloriesTimeRangeStartsAtMidnightOfCurrentDay() {
        val now = instant(16, 14, 30)
        val (start, end) = dayCaloriesTimeRange(now, zone)
        assertEquals(instant(16, 0), start)
        assertEquals(now, end)
    }

    @Test
    fun caloriesResetsAtMidnight() {
        val beforeMidnight = instant(16, 23, 59, 59)
        val (startBefore, endBefore) = dayCaloriesTimeRange(beforeMidnight, zone)
        assertEquals(instant(16, 0), startBefore)
        assertEquals(beforeMidnight, endBefore)

        val pastMidnight = instant(17, 0, 0, 1)
        val (startAfter, endAfter) = dayCaloriesTimeRange(pastMidnight, zone)
        assertEquals(instant(17, 0), startAfter)
        assertEquals(pastMidnight, endAfter)
        assertTrue(startAfter.isAfter(endBefore))
    }

    @Test
    fun calculateCaloriesSumAggregatesOnlyCurrentDay() {
        val range = instant(16, 0) to instant(16, 18)
        val samples = listOf(
            CalorieSample(instant(15, 22), instant(15, 23), 150.0),
            CalorieSample(instant(16, 7), instant(16, 8), 450.4),
            CalorieSample(instant(16, 13), instant(16, 14), 320.3),
            CalorieSample(instant(16, 19), instant(16, 20), 200.0),
        )
        assertEquals(771L, calculateCaloriesSum(samples, range))
    }

    @Test
    fun formatCaloriesNumberFormatsWithDanishGrouping() {
        val da = Locale.forLanguageTag("da")
        assertEquals("0", formatCaloriesNumber(0L, da))
        assertEquals("500", formatCaloriesNumber(500L, da))
        assertEquals("1.840", formatCaloriesNumber(1_840L, da))
        assertEquals("12.345", formatCaloriesNumber(12_345L, da))
    }

    @Test
    fun loadCaloriesCardStateReturnsNeedsAccessWhenNoAccess() = runTest {
        val state = loadCaloriesCardState({ false }, { 1_840L }, { true })
        assertEquals(CardState.NeedsAccess, state)
    }

    @Test
    fun loadCaloriesCardStateReturnsContentWhenAccessGranted() = runTest {
        val state = loadCaloriesCardState({ true }, { 1_840L }, { true })
        assertEquals(CardState.Content(CaloriesData(1_840L, true)), state)
    }

    @Test
    fun loadCaloriesCardStateReturnsContentWithFoodAdvisorNotInstalled() = runTest {
        val state = loadCaloriesCardState({ true }, { 1_840L }, { false })
        assertEquals(CardState.Content(CaloriesData(1_840L, false)), state)
    }

    @Test
    fun loadCaloriesCardStateReturnsNeedsAccessOnSecurityException() = runTest {
        val state = loadCaloriesCardState({ true }, { throw SecurityException("Denied") })
        assertEquals(CardState.NeedsAccess, state)
    }

    @Test
    fun loadCaloriesCardStateReturnsErrorOnOtherThrowable() = runTest {
        val state = loadCaloriesCardState({ true }, { error("Failure") })
        assertEquals(CardState.Error, state)
    }

    @Test(expected = CancellationException::class)
    fun loadCaloriesCardStateRethrowsCancellationException() = runTest {
        loadCaloriesCardState({ true }, { throw CancellationException("Cancelled") })
    }
}
