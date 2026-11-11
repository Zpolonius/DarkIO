package app.darkio

import app.darkio.dashboard.CalendarEvent
import app.darkio.dashboard.CardState
import app.darkio.dashboard.formatEventTime
import app.darkio.dashboard.loadCalendarCardState
import app.darkio.dashboard.selectNextEvent
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarTest {
    private val zone = ZoneId.of("UTC")
    private val now = LocalDateTime.of(2026, 11, 16, 10, 0, 0)
    private val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
    private val oneHour = 60 * 60 * 1000L

    @Test
    fun noEventsReturnsNullForEmptyState() {
        assertNull(selectNextEvent(emptyList(), nowMillis))
    }

    @Test
    fun eventEndingInPastIsExcluded() {
        val pastEvent = CalendarEvent(
            title = "Fortid",
            startMillis = nowMillis - 2 * oneHour,
            endMillis = nowMillis - oneHour,
        )
        assertNull(selectNextEvent(listOf(pastEvent), nowMillis))
    }

    @Test
    fun eventEndingExactlyAtNowIsExcluded() {
        val endingNow = CalendarEvent(
            title = "Slutter nu",
            startMillis = nowMillis - oneHour,
            endMillis = nowMillis,
        )
        assertNull(selectNextEvent(listOf(endingNow), nowMillis))
    }

    @Test
    fun eventStartingBeyond24HoursIsExcluded() {
        val futureEvent = CalendarEvent(
            title = "For langt fremme",
            startMillis = nowMillis + 25 * oneHour,
            endMillis = nowMillis + 26 * oneHour,
        )
        assertNull(selectNextEvent(listOf(futureEvent), nowMillis))
    }

    @Test
    fun singleEventWithin24HoursIsSelected() {
        val event = CalendarEvent(
            title = "Møde med teamet",
            startMillis = nowMillis + 2 * oneHour,
            endMillis = nowMillis + 3 * oneHour,
        )
        val selected = selectNextEvent(listOf(event), nowMillis)
        assertEquals("Møde med teamet", selected?.title)
    }

    @Test
    fun multipleEventsAreSortedByStartTimeEarliestFirst() {
        val eventLate = CalendarEvent("Sent møde", nowMillis + 5 * oneHour)
        val eventEarly = CalendarEvent("Tidligt møde", nowMillis + 1 * oneHour)
        val eventMid = CalendarEvent("Mellem møde", nowMillis + 3 * oneHour)

        val selected = selectNextEvent(listOf(eventLate, eventEarly, eventMid), nowMillis)
        assertEquals("Tidligt møde", selected?.title)
    }

    @Test
    fun ongoingEventIsSelected() {
        val ongoing = CalendarEvent(
            title = "I gang",
            startMillis = nowMillis - 30 * 60 * 1000L,
            endMillis = nowMillis + 30 * 60 * 1000L,
        )
        val selected = selectNextEvent(listOf(ongoing), nowMillis)
        assertEquals("I gang", selected?.title)
    }

    @Test
    fun eventStartingAtBoundaryWindowEndIsSelected() {
        val boundaryEvent = CalendarEvent(
            title = "Lige inden for grænsen",
            startMillis = nowMillis + 24 * oneHour,
            endMillis = nowMillis + 25 * oneHour,
        )
        val selected = selectNextEvent(listOf(boundaryEvent), nowMillis)
        assertEquals("Lige inden for grænsen", selected?.title)
    }

    @Test
    fun eventStartingAfterBoundaryWindowEndIsExcluded() {
        val outsideEvent = CalendarEvent(
            title = "Lige over grænsen",
            startMillis = nowMillis + 24 * oneHour + 1L,
            endMillis = nowMillis + 25 * oneHour,
        )
        assertNull(selectNextEvent(listOf(outsideEvent), nowMillis))
    }

    @Test
    fun formatTimeSameDayReturnsHHmm() {
        val event = CalendarEvent(
            title = "Frokost",
            startMillis = nowMillis + 4 * oneHour + 30 * 60 * 1000L, // 14:30
        )
        assertEquals("14:30", formatEventTime(event, nowMillis, zone))
    }

    @Test
    fun formatTimeTomorrowReturnsTomorrowPrefixAndHHmm() {
        // now is Monday 10:00, tomorrow Tuesday 09:15 is 23 hours and 15 mins later
        val event = CalendarEvent(
            title = "Morgenmøde",
            startMillis = nowMillis + 23 * oneHour + 15 * 60 * 1000L, // Tuesday 09:15
        )
        assertEquals("i morgen 09:15", formatEventTime(event, nowMillis, zone))
    }

    @Test
    fun formatTimeAllDayReturnsHeleDagen() {
        val event = CalendarEvent(
            title = "Fødselsdag",
            startMillis = nowMillis + 2 * oneHour,
            allDay = true,
        )
        assertEquals("hele dagen", formatEventTime(event, nowMillis, zone))
    }

    @Test
    fun loadCalendarStateNeedsAccessWhenNoPermission() = runTest {
        val state = loadCalendarCardState(
            hasPermission = { false },
            queryEvent = { null },
        )
        assertEquals(CardState.NeedsAccess, state)
    }

    @Test
    fun loadCalendarStateContentWhenPermissionGranted() = runTest {
        val event = CalendarEvent("Tandlæge", nowMillis + oneHour)
        val state = loadCalendarCardState(
            hasPermission = { true },
            queryEvent = { event },
        )
        assertEquals(CardState.Content(event), state)
    }

    @Test
    fun loadCalendarStateEmptyContentWhenNoUpcomingEvents() = runTest {
        val state = loadCalendarCardState(
            hasPermission = { true },
            queryEvent = { null },
        )
        assertEquals(CardState.Content(null), state)
    }

    @Test
    fun loadCalendarStateSecurityExceptionBecomesNeedsAccess() = runTest {
        val state = loadCalendarCardState(
            hasPermission = { true },
            queryEvent = { throw SecurityException("Adgang afvist under query") },
        )
        assertEquals(CardState.NeedsAccess, state)
    }

    @Test
    fun loadCalendarStateErrorWhenQueryThrowsOtherException() = runTest {
        val state = loadCalendarCardState(
            hasPermission = { true },
            queryEvent = { error("Databasefejl") },
        )
        assertEquals(CardState.Error, state)
    }

    @Test(expected = CancellationException::class)
    fun loadCalendarStateRethrowsCancellationException() = runTest {
        loadCalendarCardState(
            hasPermission = { true },
            queryEvent = { throw CancellationException("Annulleret") },
        )
    }

    @Test
    fun timedEventTakesPrecedenceOverAllDayEvent() {
        val allDay = CalendarEvent(
            title = "Heldagsbegivenhed",
            startMillis = nowMillis - 10 * oneHour,
            endMillis = nowMillis + 14 * oneHour,
            allDay = true,
        )
        val meeting = CalendarEvent(
            title = "Møde med kunden",
            startMillis = nowMillis + 2 * oneHour,
            endMillis = nowMillis + 3 * oneHour,
            allDay = false,
        )
        val selected = selectNextEvent(listOf(allDay, meeting), nowMillis)
        assertEquals("Møde med kunden", selected?.title)
    }

    @Test
    fun allDayEventIsSelectedWhenNoTimedEvents() {
        val allDay = CalendarEvent(
            title = "Ferie",
            startMillis = nowMillis,
            endMillis = nowMillis + 24 * oneHour,
            allDay = true,
        )
        val selected = selectNextEvent(listOf(allDay), nowMillis)
        assertEquals("Ferie", selected?.title)
    }

    @Test
    fun formatTimeDefaultsToCurrentTime() {
        val event = CalendarEvent(
            title = "Aftale",
            startMillis = System.currentTimeMillis() + oneHour,
        )
        // Default nowMillis is System.currentTimeMillis(), same day -> formats as HH:mm
        val formatted = formatEventTime(event)
        assertEquals(5, formatted.length)
    }
}
