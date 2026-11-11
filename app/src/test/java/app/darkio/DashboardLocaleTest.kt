package app.darkio

import app.darkio.dashboard.formatDate
import app.darkio.dashboard.formatTime
import java.time.LocalDateTime
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tester: produktionskaldet formatDate(t) uden locale skal give dansk dato uanset enhedssprog. */
class DashboardLocaleTest {
    private val original = Locale.getDefault()

    @After
    fun restore() = Locale.setDefault(original)

    @Test
    fun dateIsDanishEvenWhenDeviceIsEnglishUs() {
        Locale.setDefault(Locale.US)
        val t = LocalDateTime.of(2026, 11, 8, 21, 5)
        assertEquals("søndag 8. november", formatDate(t))
        assertEquals("21:05", formatTime(t))
    }
}
