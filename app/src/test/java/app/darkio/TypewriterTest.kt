package app.darkio

import androidx.compose.ui.unit.dp
import app.darkio.ui.TypewriterPlayed
import app.darkio.ui.calculateTypewriterCharDelay
import app.darkio.ui.theme.DarkIOMotion
import app.darkio.ui.theme.DarkIOSize
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TypewriterTest {

    @Before
    fun setUp() {
        TypewriterPlayed.resetForTest()
    }

    @Test
    fun tokensMatchDesignSystemTokens() {
        assertEquals(16L, DarkIOMotion.TypewriterCharMs)
        assertEquals(1500L, DarkIOMotion.TypewriterMaxMs)
        assertEquals(530L, DarkIOMotion.TypewriterBlinkMs)
        assertEquals(7.dp, DarkIOSize.CursorWidth)
        assertEquals(15.dp, DarkIOSize.CursorHeight)
    }

    @Test
    fun charDelayIsAtMost16msAndTotalDurationAtMost1500ms() {
        assertEquals(0L, calculateTypewriterCharDelay(0))
        assertEquals(16L, calculateTypewriterCharDelay(10))
        assertEquals(16L, calculateTypewriterCharDelay(50))
        assertEquals(16L, calculateTypewriterCharDelay(93))

        // Ved 100 tegn skal intervallet falde så det samlede tidsforbrug er <= 1500 ms
        val delay100 = calculateTypewriterCharDelay(100)
        assertEquals(15L, delay100)
        assertTrue(100L * delay100 <= 1500L)

        // Ved 150 tegn: 1500 / 150 = 10 ms
        val delay150 = calculateTypewriterCharDelay(150)
        assertEquals(10L, delay150)
        assertTrue(150L * delay150 <= 1500L)

        // Tjek alle længder op til 500 tegn
        for (len in 1..500) {
            val delay = calculateTypewriterCharDelay(len)
            assertTrue("Delay over 16ms for len=$len: $delay", delay <= 16L)
            assertTrue("Total tid over 1500ms for len=$len: ${len * delay}", len.toLong() * delay <= 1500L)
        }
    }

    @Test
    fun playedStateIsTrackedOncePerTextPerDay() {
        val today = LocalDate.of(2026, 11, 12)
        val tomorrow = today.plusDays(1)
        val key = "tip-dag-1"

        assertFalse(TypewriterPlayed.hasPlayed(key, today))

        TypewriterPlayed.markPlayed(key, today)
        assertTrue(TypewriterPlayed.hasPlayed(key, today))

        // Anden tekst er ikke spillet
        assertFalse(TypewriterPlayed.hasPlayed("tip-dag-2", today))

        // Næste dag nulstilles spillet tilstand (effekten kører én gang pr. tekst pr. dag)
        assertFalse(TypewriterPlayed.hasPlayed(key, tomorrow))
    }
}
