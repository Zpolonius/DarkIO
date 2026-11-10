package app.darkio

import app.darkio.dashboard.CardState
import app.darkio.dashboard.formatDate
import app.darkio.dashboard.formatTime
import app.darkio.dashboard.loadCard
import app.darkio.dashboard.millisToNextMinute
import java.time.LocalDateTime
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DashboardTest {
    private val da = Locale.forLanguageTag("da")

    @Test
    fun dateIsDanishWeekdayDayMonth() {
        assertEquals("søndag 8. november", formatDate(LocalDateTime.of(2026, 11, 8, 9, 5), da))
    }

    @Test
    fun timeIsTwentyFourHour() {
        assertEquals("09:05", formatTime(LocalDateTime.of(2026, 11, 8, 9, 5, 59)))
    }

    @Test
    fun waitsUntilNextMinuteStarts() {
        assertEquals(1_000L, millisToNextMinute(LocalDateTime.of(2026, 11, 8, 9, 5, 59)))
        assertEquals(60_000L, millisToNextMinute(LocalDateTime.of(2026, 11, 8, 9, 5, 0)))
    }

    @Test
    fun loaderThatThrowsBecomesErrorState() = runTest {
        assertTrue(loadCard<Int> { error("boom") } is CardState.Error)
    }

    @Test
    fun loaderSuccessBecomesContent() = runTest {
        assertEquals(CardState.Content(7), loadCard { CardState.Content(7) })
    }

    @Test
    fun cancellationIsNotSwallowed() = runTest {
        try {
            loadCard<Int> { throw CancellationException("stop") }
            fail("CancellationException skulle forplante sig")
        } catch (_: CancellationException) {
        }
    }
}
