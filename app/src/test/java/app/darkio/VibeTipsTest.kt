package app.darkio

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.darkio.data.getOrChooseDailyTip
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VibeTipsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val sampleTips = listOf(
        "Tip 0", "Tip 1", "Tip 2", "Tip 3", "Tip 4"
    )

    @Test
    fun sameTipOnSameDate() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "vibe1.preferences_pb") }
        val date = LocalDate.of(2026, 11, 11)

        val tip1 = getOrChooseDailyTip(store, sampleTips, date)
        val tip2 = getOrChooseDailyTip(store, sampleTips, date)

        assertEquals(tip1, tip2)
    }

    @Test
    fun differentTipOnNextDayAndNoRepeatUntilExhausted() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "vibe2.preferences_pb") }
        val startDate = LocalDate.of(2026, 11, 11)

        val used = mutableSetOf<Int>()
        for (i in 0 until sampleTips.size) {
            val date = startDate.plusDays(i.toLong())
            val tipIndex = getOrChooseDailyTip(store, sampleTips, date)
            assertTrue("Tip $tipIndex repeated before all tips used!", !used.contains(tipIndex))
            used.add(tipIndex)
        }

        assertEquals(sampleTips.size, used.size)

        // Next day after all used should reset pool and pick a valid tip
        val resetDate = startDate.plusDays(sampleTips.size.toLong())
        val resetTipIndex = getOrChooseDailyTip(store, sampleTips, resetDate)
        assertTrue(resetTipIndex in sampleTips.indices)
    }
}
