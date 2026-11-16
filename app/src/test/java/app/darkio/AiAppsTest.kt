package app.darkio

import app.darkio.dashboard.AiAppCardData
import app.darkio.dashboard.AiShortcut
import app.darkio.dashboard.filterAiApps
import app.darkio.dashboard.findLatestConversation
import app.darkio.dashboard.formatAiTodayHeader
import app.darkio.dashboard.formatLastUsed
import app.darkio.dashboard.formatLastUsedAndToday
import app.darkio.dashboard.selectShortcutButtons
import app.darkio.dashboard.sumTodayAiUsage
import app.darkio.data.AppEntry
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiAppsTest {

    private val zone = ZoneId.of("Europe/Copenhagen")

    private fun instant(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant()

    // 1. Fast rækkefølge + filtrering af ikke-installerede apps
    @Test
    fun filterAiAppsPreservesOrderAndFiltersUninstalled() {
        val claude = AppEntry("com.anthropic.claude/MainActivity#0", "Claude")
        val chatgpt = AppEntry("com.openai.chatgpt/MainActivity#0", "ChatGPT")
        val gemini = AppEntry("com.google.android.apps.bard/MainActivity#0", "Gemini")
        val slack = AppEntry("com.Slack/MainActivity#0", "Slack")
        val notes = AppEntry("com.notes/MainActivity#0", "Notes")

        // Vilkårlig rækkefølge i installeret liste
        val installed = listOf(notes, gemini, slack, chatgpt, claude)
        val filtered = filterAiApps(installed)

        // Forventet fast rækkefølge: Claude, ChatGPT, Gemini
        assertEquals(listOf(claude, chatgpt, gemini), filtered)

        // Test kun en delmængde installeret
        val onlyGeminiAndClaude = listOf(gemini, claude, slack)
        assertEquals(listOf(claude, gemini), filterAiApps(onlyGeminiAndClaude))

        // Test ingen AI-apps installeret
        val noAi = listOf(slack, notes)
        assertEquals(emptyList<AppEntry>(), filterAiApps(noAi))
    }

    // 2. Udvælgelse af højst 2 genveje
    @Test
    fun selectShortcutButtonsSelectsAtMostTwoOrderedByRank() {
        val s1 = AiShortcut("1", "pkg", "Ny chat", isDynamic = false, rank = 3)
        val s2 = AiShortcut("2", "pkg", "Søg", isDynamic = false, rank = 1)
        val s3 = AiShortcut("3", "pkg", "Stemme", isDynamic = true, rank = 2)
        val s4 = AiShortcut("4", "pkg", "Historik", isDynamic = true, rank = 0)

        val selected = selectShortcutButtons(listOf(s1, s2, s3, s4), limit = 2)
        assertEquals(2, selected.size)
        // Rang 0 og 1 skal vælges
        assertEquals(listOf(s4, s2), selected)

        // Færre end 2 genveje
        assertEquals(listOf(s1), selectShortcutButtons(listOf(s1), limit = 2))
        assertEquals(emptyList<AiShortcut>(), selectShortcutButtons(emptyList(), limit = 2))
    }

    // 3. Nyeste dynamiske genvej / "deler ingen samtaler"
    @Test
    fun findLatestConversationPicksNewestDynamicShortcutOrNull() {
        val dynamicOld = AiShortcut("1", "pkg", "Samtale 1", isDynamic = true, lastChangedTimestamp = 1000L)
        val dynamicNew = AiShortcut("2", "pkg", "Samtale 2", isDynamic = true, lastChangedTimestamp = 5000L)
        val dynamicMid = AiShortcut("3", "pkg", "Samtale 3", isDynamic = true, lastChangedTimestamp = 3000L)
        val manifest = AiShortcut("4", "pkg", "Ny chat", isDynamic = false, lastChangedTimestamp = 9999L)

        // Vælger den med højeste lastChangedTimestamp blandt dynamiske
        val convo = findLatestConversation(listOf(dynamicOld, manifest, dynamicNew, dynamicMid))
        assertEquals("Samtale 2", convo)

        // Kun manifest-genveje -> null ("deler ingen samtaler")
        val onlyManifest = listOf(manifest)
        assertNull(findLatestConversation(onlyManifest))

        // Tom liste -> null
        assertNull(findLatestConversation(emptyList()))
    }

    // 4. Sum af "ai i dag"
    @Test
    fun sumTodayAiUsageCalculatesSumAcrossAllCards() {
        val claude = AppEntry("com.anthropic.claude/MainActivity#0", "Claude")
        val chatgpt = AppEntry("com.openai.chatgpt/MainActivity#0", "ChatGPT")
        val gemini = AppEntry("com.google.android.apps.bard/MainActivity#0", "Gemini")

        val cards = listOf(
            AiAppCardData(claude, "Chat", emptyList(), 0L, todayUsageMillis = 25 * 60 * 1000L), // 25m
            AiAppCardData(chatgpt, "Python", emptyList(), 0L, todayUsageMillis = 40 * 60 * 1000L), // 40m
            AiAppCardData(gemini, null, emptyList(), 0L, todayUsageMillis = 5 * 60 * 1000L), // 5m
        )

        // 25 + 40 + 5 = 70 min = 4_200_000 ms
        assertEquals(70 * 60 * 1000L, sumTodayAiUsage(cards))

        // Tom liste
        assertEquals(0L, sumTodayAiUsage(emptyList()))
    }

    // 5. Formatering af sidst brugt/tid i dag og manglende brugsdata-adgang
    @Test
    fun formattingOfLastUsedAndTodayUsageHandlesAccessAndDates() {
        val now = instant(16, 15, 0)
        val nowMillis = now.toEpochMilli()

        // Uden adgang til brugsdata
        val noAccess = formatLastUsedAndToday(
            lastUsedMillis = nowMillis,
            todayMillis = 1800_000L,
            hasAccess = false,
            nowMillis = nowMillis,
            zoneId = zone,
        )
        assertEquals("giv adgang til brugsdata", noAccess)

        // Overskrift uden adgang
        val headerNoAccess = formatAiTodayHeader(1800_000L, hasAccess = false)
        assertEquals("giv adgang til brugsdata", headerNoAccess)

        // Overskrift med adgang
        val headerWithAccess = formatAiTodayHeader(70 * 60 * 1000L, hasAccess = true)
        assertEquals("1 t 10 m", headerWithAccess)

        // Brugt i dag kl. 14:15, forbrug 25 minutter
        val usedToday = instant(16, 14, 15).toEpochMilli()
        val formattedToday = formatLastUsedAndToday(
            lastUsedMillis = usedToday,
            todayMillis = 25 * 60 * 1000L,
            hasAccess = true,
            nowMillis = nowMillis,
            zoneId = zone,
        )
        assertEquals("kl. 14:15 · 25 m", formattedToday)

        // Brugt i går, 0 min i dag
        val usedYesterday = instant(15, 20, 0).toEpochMilli()
        val formattedYesterday = formatLastUsedAndToday(
            lastUsedMillis = usedYesterday,
            todayMillis = 0L,
            hasAccess = true,
            nowMillis = nowMillis,
            zoneId = zone,
        )
        assertEquals("i går · 0 m", formattedYesterday)

        // Aldrig brugt
        val formattedNever = formatLastUsedAndToday(
            lastUsedMillis = 0L,
            todayMillis = 0L,
            hasAccess = true,
            nowMillis = nowMillis,
            zoneId = zone,
        )
        assertEquals("aldrig brugt · 0 m", formattedNever)
    }

    @Test
    fun formatLastUsedHandlesDateBoundaries() {
        val now = instant(16, 12, 0)
        val nowMillis = now.toEpochMilli()

        // Samme dag
        assertEquals("kl. 09:30", formatLastUsed(instant(16, 9, 30).toEpochMilli(), nowMillis, zone))

        // I går
        assertEquals("i går", formatLastUsed(instant(15, 22, 10).toEpochMilli(), nowMillis, zone))

        // Tidligere samme år
        val earlierThisYear = instant(10, 10, 0).toEpochMilli()
        assertEquals("10. nov.", formatLastUsed(earlierThisYear, nowMillis, zone))

        // Aldrig
        assertEquals("aldrig brugt", formatLastUsed(0L, nowMillis, zone))
    }
}
