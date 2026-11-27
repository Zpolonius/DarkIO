package app.darkio

import app.darkio.dashboard.CardCache
import app.darkio.dashboard.CardState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardCacheTest {
    private val monday = LocalDate.of(2026, 11, 23)
    private val tuesday = monday.plusDays(1)

    @Test
    fun valueFromTodayIsReturnedToday() {
        val cache = CardCache()
        cache.put("steps", CardState.Content(4200), monday)
        assertEquals(CardState.Content(4200), cache.get<Int>("steps", monday))
    }

    @Test
    fun valueFromYesterdayIsNeverReturnedAsToday() {
        val cache = CardCache()
        cache.put("steps", CardState.Content(4200), monday)
        assertNull(cache.get<Int>("steps", tuesday))
    }

    @Test
    fun errorAndNeedsAccessAreNotRemembered() {
        val cache = CardCache()
        cache.put("a", CardState.Error, monday)
        cache.put("b", CardState.NeedsAccess, monday)
        assertNull(cache.get<Int>("a", monday))
        assertNull(cache.get<Int>("b", monday))
    }

    @Test
    fun errorDoesNotEraseTheLastGoodValue() {
        val cache = CardCache()
        cache.put("k", CardState.Content(1), monday)
        cache.put("k", CardState.Error, monday)
        assertEquals(CardState.Content(1), cache.get<Int>("k", monday))
    }

    @Test
    fun cardsDoNotShareEntries() {
        val cache = CardCache()
        cache.put("steps", CardState.Content(1), monday)
        assertNull(cache.get<Int>("calories", monday))
    }
}
