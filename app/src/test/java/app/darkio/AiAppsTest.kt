package app.darkio

import android.app.usage.UsageEvents
import app.darkio.dashboard.AiAppCardData
import app.darkio.dashboard.AiShortcut
import app.darkio.dashboard.FocusUsageEvent
import app.darkio.dashboard.FocusUsageMemoryCache
import app.darkio.dashboard.filterAiApps
import app.darkio.dashboard.findLatestConversation
import app.darkio.dashboard.formatAiTodayHeader
import app.darkio.dashboard.formatLastUsed
import app.darkio.dashboard.formatLastUsedAndToday
import app.darkio.dashboard.formatScreenTime
import app.darkio.dashboard.queryAiUsageStatsWithSources
import app.darkio.dashboard.queryFocusDataWithSource
import app.darkio.dashboard.selectShortcutButtons
import app.darkio.dashboard.sumTodayAiUsage
import app.darkio.data.AppEntry
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
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
        val formatDuration: (Long) -> String = { ms -> formatScreenTime(ms, "%1\$d t %2\$d m", "%1\$d t", "%1\$d m") }
        val noAccessText = "giv adgang til brugsdata"
        val neverUsedText = "aldrig brugt"
        val yesterdayText = "i går"
        val todayFormat = "kl. %s"

        // Uden adgang til brugsdata
        val noAccess = formatLastUsedAndToday(
            lastUsedMillis = nowMillis,
            todayMillis = 1800_000L,
            hasAccess = false,
            nowMillis = nowMillis,
            zoneId = zone,
            noAccessText = noAccessText,
            neverUsedText = neverUsedText,
            yesterdayText = yesterdayText,
            todayFormat = todayFormat,
            formatDuration = formatDuration,
        )
        assertEquals("giv adgang til brugsdata", noAccess)

        // Overskrift uden adgang
        val headerNoAccess = formatAiTodayHeader(1800_000L, hasAccess = false, noAccessText = noAccessText, formatDuration = formatDuration)
        assertEquals("giv adgang til brugsdata", headerNoAccess)

        // Overskrift med adgang
        val headerWithAccess = formatAiTodayHeader(70 * 60 * 1000L, hasAccess = true, noAccessText = noAccessText, formatDuration = formatDuration)
        assertEquals("1 t 10 m", headerWithAccess)

        // Brugt i dag kl. 14:15, forbrug 25 minutter
        val usedToday = instant(16, 14, 15).toEpochMilli()
        val formattedToday = formatLastUsedAndToday(
            lastUsedMillis = usedToday,
            todayMillis = 25 * 60 * 1000L,
            hasAccess = true,
            nowMillis = nowMillis,
            zoneId = zone,
            noAccessText = noAccessText,
            neverUsedText = neverUsedText,
            yesterdayText = yesterdayText,
            todayFormat = todayFormat,
            formatDuration = formatDuration,
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
            noAccessText = noAccessText,
            neverUsedText = neverUsedText,
            yesterdayText = yesterdayText,
            todayFormat = todayFormat,
            formatDuration = formatDuration,
        )
        assertEquals("i går · 0 m", formattedYesterday)

        // Aldrig brugt
        val formattedNever = formatLastUsedAndToday(
            lastUsedMillis = 0L,
            todayMillis = 0L,
            hasAccess = true,
            nowMillis = nowMillis,
            zoneId = zone,
            noAccessText = noAccessText,
            neverUsedText = neverUsedText,
            yesterdayText = yesterdayText,
            todayFormat = todayFormat,
            formatDuration = formatDuration,
        )
        assertEquals("aldrig brugt · 0 m", formattedNever)
    }

    @Test
    fun formatLastUsedHandlesDateBoundaries() {
        val now = instant(16, 12, 0)
        val nowMillis = now.toEpochMilli()
        val neverUsedText = "aldrig brugt"
        val yesterdayText = "i går"
        val todayFormat = "kl. %s"

        // Samme dag
        assertEquals("kl. 09:30", formatLastUsed(instant(16, 9, 30).toEpochMilli(), nowMillis, zone, neverUsedText, yesterdayText, todayFormat))

        // I går
        assertEquals("i går", formatLastUsed(instant(15, 22, 10).toEpochMilli(), nowMillis, zone, neverUsedText, yesterdayText, todayFormat))

        // Tidligere samme år
        val earlierThisYear = instant(10, 10, 0).toEpochMilli()
        assertEquals("10. nov.", formatLastUsed(earlierThisYear, nowMillis, zone, neverUsedText, yesterdayText, todayFormat))

        // Aldrig
        assertEquals("aldrig brugt", formatLastUsed(0L, nowMillis, zone, neverUsedText, yesterdayText, todayFormat))
    }

    @Test
    fun aiUsageReusesTodayFocusCacheWithoutExtraTodayQuery() = runTest {
        val cache = FocusUsageMemoryCache()
        val eventQueries = mutableListOf<Pair<Long, Long>>()
        val recentQueries = mutableListOf<Pair<Long, Long>>()

        val nowFocus = instant(16, 15, 0)
        val nowBuild = instant(16, 15, 0).plusSeconds(20)
        val geminiThreeDaysAgo = instant(13, 18, 0).toEpochMilli()

        val events = listOf(
            // Claude: 25 min i dag
            FocusUsageEvent(instant(16, 10, 0).toEpochMilli(), UsageEvents.Event.ACTIVITY_RESUMED, "com.anthropic.claude"),
            FocusUsageEvent(instant(16, 10, 25).toEpochMilli(), UsageEvents.Event.ACTIVITY_PAUSED, "com.anthropic.claude"),
            // ChatGPT: 10 min i dag
            FocusUsageEvent(instant(16, 11, 0).toEpochMilli(), UsageEvents.Event.ACTIVITY_RESUMED, "com.openai.chatgpt"),
            FocusUsageEvent(instant(16, 11, 10).toEpochMilli(), UsageEvents.Event.ACTIVITY_PAUSED, "com.openai.chatgpt"),
            // Anden app: 30 min i dag (må ikke tælle med i AI)
            FocusUsageEvent(instant(16, 12, 0).toEpochMilli(), UsageEvents.Event.ACTIVITY_RESUMED, "app.browser"),
            FocusUsageEvent(instant(16, 12, 30).toEpochMilli(), UsageEvents.Event.ACTIVITY_PAUSED, "app.browser"),
        )

        val eventSource: suspend (Long, Long) -> List<FocusUsageEvent> = { startMs, endMs ->
            eventQueries.add(startMs to endMs)
            events.filter { it.timestampMillis in startMs..endMs }
        }

        // Fokus-kortet henter dagens (og gårsdagens) hændelser
        queryFocusDataWithSource(
            now = nowFocus,
            zoneId = zone,
            excludedPackage = "app.darkio",
            includeDetails = false,
            cache = cache,
            queryEvents = eventSource,
        )
        assertEquals(2, eventQueries.size)

        // Byg-siden henter AI-brugsdata i samme minut: må IKKE lave et nyt dagens event-opslag
        val usageMap = queryAiUsageStatsWithSources(
            packages = listOf("com.anthropic.claude", "com.openai.chatgpt", "com.google.android.apps.bard"),
            now = nowBuild,
            zoneId = zone,
            excludedPackage = "app.darkio",
            cache = cache,
            queryEvents = eventSource,
            queryRecentLastUsed = { startMs, endMs ->
                recentQueries.add(startMs to endMs)
                mapOf("com.google.android.apps.bard" to geminiThreeDaysAgo)
            },
        )

        // 0 ekstra event-opslag, kun 1 30-dages opslag til sidst brugt
        assertEquals(2, eventQueries.size)
        assertEquals(1, recentQueries.size)
        assertEquals(nowBuild.minus(30, java.time.temporal.ChronoUnit.DAYS).toEpochMilli(), recentQueries[0].first)
        assertEquals(nowBuild.toEpochMilli(), recentQueries[0].second)

        assertEquals(instant(16, 10, 25).toEpochMilli() to 25 * 60_000L, usageMap["com.anthropic.claude"])
        assertEquals(instant(16, 11, 10).toEpochMilli() to 10 * 60_000L, usageMap["com.openai.chatgpt"])
        assertEquals(geminiThreeDaysAgo to 0L, usageMap["com.google.android.apps.bard"])
    }

    @Test
    fun aiUsageSkipsQueriesWhenPackageListIsEmpty() = runTest {
        val cache = FocusUsageMemoryCache()
        var eventQueries = 0
        var recentQueries = 0

        val result = queryAiUsageStatsWithSources(
            packages = emptyList(),
            now = instant(16, 15, 0),
            zoneId = zone,
            excludedPackage = "app.darkio",
            cache = cache,
            queryEvents = { _, _ -> eventQueries++; emptyList() },
            queryRecentLastUsed = { _, _ -> recentQueries++; emptyMap() },
        )

        assertEquals(emptyMap<String, Pair<Long, Long>>(), result)
        assertEquals(0, eventQueries)
        assertEquals(0, recentQueries)
    }
}
