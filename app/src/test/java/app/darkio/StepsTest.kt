package app.darkio

import app.darkio.dashboard.CardState
import app.darkio.dashboard.DEFAULT_STEP_GOAL
import app.darkio.dashboard.StepsData
import app.darkio.dashboard.calculateStepProgress
import app.darkio.dashboard.dayStepTimeRange
import app.darkio.dashboard.formatStepNumber
import app.darkio.dashboard.loadStepsCardState
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StepsTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun defaultGoalIs8000() {
        assertEquals(8_000L, DEFAULT_STEP_GOAL)
        val data = StepsData(steps = 1_234L)
        assertEquals(8_000L, data.goal)
        assertEquals(1_234L, data.steps)
    }

    @Test
    fun dayStepTimeRangeStartsAtMidnightOfCurrentDay() {
        val now = LocalDateTime.of(2026, 11, 16, 14, 30, 0).atZone(zone).toInstant()
        val (start, end) = dayStepTimeRange(now, zone)

        val expectedStart = LocalDateTime.of(2026, 11, 16, 0, 0, 0).atZone(zone).toInstant()
        assertEquals(expectedStart, start)
        assertEquals(now, end)
    }

    @Test
    fun stepCountResetsAtMidnight() {
        // Før midnat (kl. 23:59:59) dækker intervallet dag 1 fra 00:00:00
        val beforeMidnight = LocalDateTime.of(2026, 11, 16, 23, 59, 59).atZone(zone).toInstant()
        val (startBefore, endBefore) = dayStepTimeRange(beforeMidnight, zone)
        val expectedStartBefore = LocalDateTime.of(2026, 11, 16, 0, 0, 0).atZone(zone).toInstant()
        assertEquals(expectedStartBefore, startBefore)
        assertEquals(beforeMidnight, endBefore)

        // Lige efter midnat (kl. 00:00:01 på dag 2) nulstilles intervallets start til dag 2 kl. 00:00:00
        val pastMidnight = LocalDateTime.of(2026, 11, 17, 0, 0, 1).atZone(zone).toInstant()
        val (startAfter, endAfter) = dayStepTimeRange(pastMidnight, zone)
        val expectedStartAfter = LocalDateTime.of(2026, 11, 17, 0, 0, 0).atZone(zone).toInstant()
        assertEquals(expectedStartAfter, startAfter)
        assertEquals(pastMidnight, endAfter)

        // Dag 2 starter efter dag 1s afslutning, så gårsdagens aggregering ikke medregnes
        assertEquals(true, startAfter.isAfter(endBefore))
    }

    @Test
    fun calculateStepProgressCalculatesCorrectRatio() {
        assertEquals(0.0f, calculateStepProgress(steps = 0L, goal = 8_000L), 0.001f)
        assertEquals(0.5f, calculateStepProgress(steps = 4_000L, goal = 8_000L), 0.001f)
        assertEquals(1.0f, calculateStepProgress(steps = 8_000L, goal = 8_000L), 0.001f)
        // Progress over målet afkortes til 1.0f
        assertEquals(1.0f, calculateStepProgress(steps = 12_000L, goal = 8_000L), 0.001f)
        // Negative værdier afkortes til 0.0f
        assertEquals(0.0f, calculateStepProgress(steps = -50L, goal = 8_000L), 0.001f)
        // Guard mod division med nul
        assertEquals(0.0f, calculateStepProgress(steps = 5_000L, goal = 0L), 0.001f)
    }

    @Test
    fun formatStepNumberFormatsWithDanishGrouping() {
        val locale = Locale.forLanguageTag("da")
        assertEquals("0", formatStepNumber(0L, locale))
        assertEquals("500", formatStepNumber(500L, locale))
        assertEquals("8.000", formatStepNumber(8_000L, locale))
        assertEquals("12.345", formatStepNumber(12_345L, locale))
    }

    @Test
    fun loadStepsCardStateReturnsNeedsAccessWhenNoAccess() = runTest {
        val state = loadStepsCardState(
            hasAccess = { false },
            querySteps = { 5_000L },
        )
        assertEquals(CardState.NeedsAccess, state)
    }

    @Test
    fun loadStepsCardStateReturnsContentWhenAccessGranted() = runTest {
        val state = loadStepsCardState(
            hasAccess = { true },
            querySteps = { 6_420L },
            goal = 8_000L,
        )
        assertEquals(CardState.Content(StepsData(steps = 6_420L, goal = 8_000L)), state)
    }

    @Test
    fun loadStepsCardStateReturnsNeedsAccessOnSecurityException() = runTest {
        val state = loadStepsCardState(
            hasAccess = { true },
            querySteps = { throw SecurityException("Mangler READ_STEPS") },
        )
        assertEquals(CardState.NeedsAccess, state)
    }

    @Test
    fun loadStepsCardStateReturnsErrorOnOtherThrowable() = runTest {
        val state = loadStepsCardState(
            hasAccess = { true },
            querySteps = { error("Health Connect servicefejl") },
        )
        assertEquals(CardState.Error, state)
    }

    @Test(expected = CancellationException::class)
    fun loadStepsCardStateRethrowsCancellationException() = runTest {
        loadStepsCardState(
            hasAccess = { true },
            querySteps = { throw CancellationException("Annulleret") },
        )
    }
}
