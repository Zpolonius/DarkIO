package app.darkio

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.darkio.data.getDailyAndRecentTips
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
    companion object {
        init {
            try {
                val field = android.os.Build.VERSION::class.java.getField("SDK_INT")
                val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
                unsafeField.isAccessible = true
                val unsafe = unsafeField.get(null) as sun.misc.Unsafe
                val base = unsafe.staticFieldBase(field)
                val offset = unsafe.staticFieldOffset(field)
                unsafe.putInt(base, offset, 35)
            } catch (_: Throwable) {}
        }
    }

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

    @Test
    fun testGetDailyAndRecentTipsFlow() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "vibe3.preferences_pb") }
        val day1 = LocalDate.of(2026, 11, 11)
        val day2 = day1.plusDays(1)
        val day3 = day1.plusDays(2)

        // Day 1
        val data1 = getDailyAndRecentTips(store, sampleTips, day1)
        assertTrue(data1.todayTip.isNotBlank())
        // On day 1 without history, recentTips should still provide 2 different tips from pool
        assertEquals(2, data1.recentTips.size)
        assertTrue(data1.recentTips.all { it != data1.todayTip })

        // Day 2
        val data2 = getDailyAndRecentTips(store, sampleTips, day2)
        assertTrue(data2.todayTip.isNotBlank())
        assertEquals(2, data2.recentTips.size)
        assertTrue(data2.recentTips.all { it != data2.todayTip })
        // Day 1 tip should be among recent tips on day 2
        assertTrue(data2.recentTips.contains(data1.todayTip))

        // Day 3
        val data3 = getDailyAndRecentTips(store, sampleTips, day3)
        assertTrue(data3.todayTip.isNotBlank())
        assertEquals(2, data3.recentTips.size)
        assertTrue(data3.recentTips.all { it != data3.todayTip })
    }
}
