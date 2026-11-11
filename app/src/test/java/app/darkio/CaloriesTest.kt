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

    @Test
    fun dayCaloriesTimeRangeStartsAtMidnightOfCurrentDay() {
        val now = LocalDateTime.of(2026, 11, 16, 14, 30, 0).atZone(zone).toInstant()
        val (start, end) = dayCaloriesTimeRange(now, zone)

        val expectedStart = LocalDateTime.of(2026, 11, 16, 0, 0, 0).atZone(zone).toInstant()
        assertEquals(expectedStart, start)
        assertEquals(now, end)
    }

    @Test
    fun caloriesResetsAtMidnight() {
        // Før midnat (kl. 23:59:59) dækker intervallet dag 1 fra 00:00:00
        val beforeMidnight = LocalDateTime.of(2026, 11, 16, 23, 59, 59).atZone(zone).toInstant()
        val (startBefore, endBefore) = dayCaloriesTimeRange(beforeMidnight, zone)
        val expectedStartBefore = LocalDateTime.of(2026, 11, 16, 0, 0, 0).atZone(zone).toInstant()
        assertEquals(expectedStartBefore, startBefore)
        assertEquals(beforeMidnight, endBefore)

        // Lige efter midnat (kl. 00:00:01 på dag 2) nulstilles intervallets start til dag 2 kl. 00:00:00
        val pastMidnight = LocalDateTime.of(2026, 11, 17, 0, 0, 1).atZone(zone).toInstant()
        val (startAfter, endAfter) = dayCaloriesTimeRange(pastMidnight, zone)
        val expectedStartAfter = LocalDateTime.of(2026, 11, 17, 0, 0, 0).atZone(zone).toInstant()
        assertEquals(expectedStartAfter, startAfter)
        assertEquals(pastMidnight, endAfter)

        // Dag 2 starter efter dag 1s afslutning, så gårsdagens aggregering ikke medregnes
        assertTrue(startAfter.isAfter(endBefore))
    }

    @Test
    fun calculateCaloriesSumAggregatesOnlyCurrentDay() {
        val day1Start = LocalDateTime.of(2026, 11, 16, 0, 0, 0).atZone(zone).toInstant()
        val now = LocalDateTime.of(2026, 11, 16, 18, 0, 0).atZone(zone).toInstant()
        val dayRange = day1Start to now

        val samples = listOf(
            // Gårsdagens forbrænding (før midnat) - skal ignoreres
            CalorieSample(
                startTime = LocalDateTime.of(2026, 11, 15, 22, 0, 0).atZone(zone).toInstant(),
                endTime = LocalDateTime.of(2026, 11, 15, 23, 0, 0).atZone(zone).toInstant(),
                kilocalories = 150.0,
            ),
            // Dagens morgentræning
            CalorieSample(
                startTime = LocalDateTime.of(2026, 11, 16, 7, 0, 0).atZone(zone).toInstant(),
                endTime = LocalDateTime.of(2026, 11, 16, 8, 0, 0).atZone(zone).toInstant(),
                kilocalories = 450.4,
            ),
            // Dagens eftermiddagstur
            CalorieSample(
                startTime = LocalDateTime.of(2026, 11, 16, 13, 0, 0).atZone(zone).toInstant(),
                endTime = LocalDateTime.of(2026, 11, 16, 14, 0, 0).atZone(zone).toInstant(),
                kilocalories = 320.3,
            ),
            // Fremtidig registrering efter 'now' - skal ignoreres
            CalorieSample(
                startTime = LocalDateTime.of(2026, 11, 16, 19, 0, 0).atZone(zone).toInstant(),
                endTime = LocalDateTime.of(2026, 11, 16, 20, 0, 0).atZone(zone).toInstant(),
                kilocalories = 200.0,
            ),
        )

        // Forventet sum: 450.4 + 320.3 = 770.7 -> afrundet 771
        val sum = calculateCaloriesSum(samples, dayRange)
        assertEquals(771L, sum)
    }

    @Test
    fun formatCaloriesNumberFormatsWithDanishGrouping() {
        val locale = Locale.forLanguageTag("da")
        assertEquals("0", formatCaloriesNumber(0L, locale))
        assertEquals("500", formatCaloriesNumber(500L, locale))
        assertEquals("1.840", formatCaloriesNumber(1_840L, locale))
        assertEquals("12.345", formatCaloriesNumber(12_345L, locale))
    }

    @Test
    fun loadCaloriesCardStateReturnsNeedsAccessWhenNoAccess() = runTest {
        val state = loadCaloriesCardState(
            hasAccess = { false },
            queryCalories = { 1_840L },
            isFoodAdvisorInstalled = { true },
        )
        assertEquals(CardState.NeedsAccess, state)
    }

    @Test
    fun loadCaloriesCardStateReturnsContentWhenAccessGranted() = runTest {
        val state = loadCaloriesCardState(
            hasAccess = { true },
            queryCalories = { 1_840L },
            isFoodAdvisorInstalled = { true },
        )
        assertEquals(CardState.Content(CaloriesData(calories = 1_840L, isFoodAdvisorInstalled = true)), state)
    }

    @Test
    fun loadCaloriesCardStateReturnsContentWithFoodAdvisorNotInstalled() = runTest {
        val state = loadCaloriesCardState(
            hasAccess = { true },
            queryCalories = { 1_840L },
            isFoodAdvisorInstalled = { false },
        )
        assertEquals(CardState.Content(CaloriesData(calories = 1_840L, isFoodAdvisorInstalled = false)), state)
        val content = state as CardState.Content
        assertFalse(content.value.isFoodAdvisorInstalled)
    }

    @Test
    fun loadCaloriesCardStateReturnsNeedsAccessOnSecurityException() = runTest {
        val state = loadCaloriesCardState(
            hasAccess = { true },
            queryCalories = { throw SecurityException("Mangler READ_TOTAL_CALORIES_BURNED") },
        )
        assertEquals(CardState.NeedsAccess, state)
    }

    @Test
    fun loadCaloriesCardStateReturnsErrorOnOtherThrowable() = runTest {
        val state = loadCaloriesCardState(
            hasAccess = { true },
            queryCalories = { error("Health Connect servicefejl") },
        )
        assertEquals(CardState.Error, state)
    }

    @Test(expected = CancellationException::class)
    fun loadCaloriesCardStateRethrowsCancellationException() = runTest {
        loadCaloriesCardState(
            hasAccess = { true },
            queryCalories = { throw CancellationException("Annulleret") },
        )
    }
}
