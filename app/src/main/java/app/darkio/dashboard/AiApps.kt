package app.darkio.dashboard

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import app.darkio.data.AppEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI-apps understøttet på byg-siden i fast rækkefølge (Claude, ChatGPT, Gemini).
 */
val AI_PACKAGES = listOf(
    "com.anthropic.claude",
    "com.openai.chatgpt",
    "com.google.android.apps.bard",
)

/**
 * Repræsentation af en app-genvej til AI-kortet.
 */
data class AiShortcut(
    val id: String,
    val packageName: String,
    val label: String,
    val isDynamic: Boolean,
    val lastChangedTimestamp: Long = 0L,
    val rank: Int = 0,
)

/**
 * Data for et enkelt AI-app kort.
 */
data class AiAppCardData(
    val app: AppEntry,
    val latestConversation: String?,
    val shortcuts: List<AiShortcut>,
    val lastTimeUsedMillis: Long,
    val todayUsageMillis: Long,
)

/**
 * Samlet tilstand for AI-apps på byg-siden.
 */
data class AiPageData(
    val cards: List<AiAppCardData>,
    val totalTodayMillis: Long,
    val hasUsageAccess: Boolean,
)

/**
 * Filtrerer og sorterer installerede apps i henhold til [AI_PACKAGES] rækkefølgen.
 * Ikke-installerede apps udelades.
 */
fun filterAiApps(installed: List<AppEntry>): List<AppEntry> {
    val installedByPkg = installed.associateBy { it.id.substringBefore('/') }
    return AI_PACKAGES.mapNotNull { pkg -> installedByPkg[pkg] }
}

/**
 * Finder seneste samtale blandt appens genveje, defineret som den nyeste dynamiske genvej
 * baseret på [AiShortcut.lastChangedTimestamp]. Returnerer null hvis ingen dynamiske genveje findes.
 */
fun findLatestConversation(shortcuts: List<AiShortcut>): String? {
    return shortcuts
        .filter { it.isDynamic }
        .maxByOrNull { it.lastChangedTimestamp }
        ?.label
}

/**
 * Udvælger højst [limit] genveje til visning som knapper på kortet.
 * Sorteres efter rang.
 */
fun selectShortcutButtons(shortcuts: List<AiShortcut>, limit: Int = 2): List<AiShortcut> {
    return shortcuts.sortedBy { it.rank }.take(limit)
}

/**
 * Summerer dagens forbrug i millisekunder på tværs af de viste AI-kort.
 */
fun sumTodayAiUsage(cards: List<AiAppCardData>): Long {
    return cards.sumOf { it.todayUsageMillis }
}

/**
 * Formaterer tidspunkt for sidst brugt.
 */
fun formatLastUsed(
    lastUsedMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    neverUsedText: String,
    yesterdayText: String,
    todayFormat: String,
): String {
    if (lastUsedMillis <= 0L) return neverUsedText
    val lastUsed = Instant.ofEpochMilli(lastUsedMillis).atZone(zoneId)
    val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
    val lastDate = lastUsed.toLocalDate()
    val nowDate = now.toLocalDate()
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    return when {
        lastDate == nowDate -> String.format(todayFormat, timeFormatter.format(lastUsed))
        lastDate == nowDate.minusDays(1) -> yesterdayText
        lastDate.year == nowDate.year -> DateTimeFormatter.ofPattern("d. MMM", Locale("da")).format(lastDate)
        else -> DateTimeFormatter.ofPattern("d. MMM yyyy", Locale("da")).format(lastDate)
    }
}

/**
 * Formaterer 'sidst brugt · tid i dag'. Ved manglende adgang returneres [noAccessText].
 */
fun formatLastUsedAndToday(
    lastUsedMillis: Long,
    todayMillis: Long,
    hasAccess: Boolean,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    noAccessText: String,
    neverUsedText: String,
    yesterdayText: String,
    todayFormat: String,
    formatDuration: (Long) -> String,
): String {
    if (!hasAccess) return noAccessText
    val lastUsedStr = formatLastUsed(lastUsedMillis, nowMillis, zoneId, neverUsedText, yesterdayText, todayFormat)
    val todayStr = formatDuration(todayMillis)
    return "$lastUsedStr · $todayStr"
}

/**
 * Formaterer overskriften for 'ai i dag'. Ved manglende adgang returneres [noAccessText].
 */
fun formatAiTodayHeader(
    totalTodayMillis: Long,
    hasAccess: Boolean,
    noAccessText: String,
    formatDuration: (Long) -> String,
): String {
    if (!hasAccess) return noAccessText
    return formatDuration(totalTodayMillis)
}

/**
 * Henter genveje for en AI-app via LauncherApps på Dispatchers.IO.
 */
suspend fun queryAiShortcuts(
    context: Context,
    userHandle: UserHandle,
    packageName: String,
): List<AiShortcut> = withContext(Dispatchers.IO) {
    val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return@withContext emptyList()
    if (!launcherApps.hasShortcutHostPermission()) return@withContext emptyList()
    try {
        val query = LauncherApps.ShortcutQuery().apply {
            setPackage(packageName)
            setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        }
        val shortcuts = launcherApps.getShortcuts(query, userHandle) ?: emptyList()
        shortcuts.filter { it.isEnabled }.map {
            AiShortcut(
                id = it.id,
                packageName = it.`package`,
                label = (it.shortLabel ?: it.longLabel ?: it.id).toString(),
                isDynamic = it.isDynamic,
                lastChangedTimestamp = it.lastChangedTimestamp,
                rank = it.rank,
            )
        }
    } catch (_: Throwable) {
        emptyList()
    }
}

/**
 * Beregner (sidst brugt, tid i dag) for [packages] ud fra dagens fokus-hændelser og
 * historisk `lastTimeUsed` uden et ekstra opslag for i dag.
 */
fun calculateAiUsageFromTodayEvents(
    packages: List<String>,
    todayEvents: List<FocusUsageEvent>,
    startTodayMillis: Long,
    endTodayMillis: Long,
    recentLastUsedMillis: Map<String, Long> = emptyMap(),
    excludedPackage: String? = null,
): Map<String, Pair<Long, Long>> {
    if (packages.isEmpty()) return emptyMap()
    val todayUsageByPkg = calculateUsageMillisByPackage(
        events = todayEvents,
        startTimeMillis = startTodayMillis,
        endTimeMillis = endTodayMillis,
        excludedPackage = excludedPackage,
    )
    val todayLastUsedByPkg = todayEvents
        .asSequence()
        .filter {
            it.packageName.isNotEmpty() &&
                it.timestampMillis in startTodayMillis..endTodayMillis &&
                (it.eventType == UsageEvents.Event.ACTIVITY_RESUMED || it.eventType == UsageEvents.Event.ACTIVITY_PAUSED)
        }
        .groupBy { it.packageName }
        .mapValues { (_, evs) -> evs.maxOf { it.timestampMillis } }

    return packages.associateWith { pkg ->
        val todayMillis = todayUsageByPkg[pkg] ?: 0L
        val lastUsed = maxOf(recentLastUsedMillis[pkg] ?: 0L, todayLastUsedByPkg[pkg] ?: 0L)
        lastUsed to todayMillis
    }
}

/**
 * Testbar hjælper der genbruger dagens fokus-cache til "tid i dag" pr. AI-app
 * og kun laver ét 30-dages opslag for `lastTimeUsed`.
 */
suspend fun queryAiUsageStatsWithSources(
    packages: List<String>,
    now: Instant,
    zoneId: ZoneId,
    excludedPackage: String?,
    cache: FocusUsageMemoryCache = sharedFocusUsageCache,
    queryEvents: suspend (startMs: Long, endMs: Long) -> List<FocusUsageEvent>,
    queryRecentLastUsed: suspend (startMs: Long, endMs: Long) -> Map<String, Long>,
): Map<String, Pair<Long, Long>> {
    if (packages.isEmpty()) return emptyMap()
    val (startToday, endToday) = dayFocusTimeRange(now, zoneId)
    val startTodayMs = startToday.toEpochMilli()
    val endTodayMs = endToday.toEpochMilli()
    val todayEvents = cache.getOrQueryTodayEvents(
        now = now,
        zoneId = zoneId,
        excludedPackage = excludedPackage,
        startTodayMs = startTodayMs,
        endTodayMs = endTodayMs,
        queryEvents = queryEvents,
    )
    val thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS).toEpochMilli()
    val recentLastUsed = queryRecentLastUsed(thirtyDaysAgo, endTodayMs)
    return calculateAiUsageFromTodayEvents(
        packages = packages,
        todayEvents = todayEvents,
        startTodayMillis = startTodayMs,
        endTodayMillis = endTodayMs,
        recentLastUsedMillis = recentLastUsed,
        excludedPackage = excludedPackage,
    )
}

/**
 * Henter brugsstatistik for AI-apps fra UsageStatsManager på Dispatchers.IO.
 * Genbruger dagens delte fokus-hændelser til "tid i dag" og nøjes med ét 30-dages opslag til sidst brugt.
 * Returnerer et map fra packageName til Pair(lastTimeUsedMillis, todayUsageMillis).
 */
suspend fun queryAiUsageStats(
    context: Context,
    packages: List<String>,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Map<String, Pair<Long, Long>> = withContext(Dispatchers.IO) {
    if (packages.isEmpty()) return@withContext emptyMap()
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        ?: return@withContext emptyMap()
    try {
        queryAiUsageStatsWithSources(
            packages = packages,
            now = now,
            zoneId = zoneId,
            excludedPackage = context.packageName,
            cache = sharedFocusUsageCache,
            queryEvents = { startMs, endMs -> queryUsageEventsList(usageStatsManager, startMs, endMs) },
            queryRecentLastUsed = { startMs, endMs ->
                usageStatsManager.queryAndAggregateUsageStats(startMs, endMs)
                    .mapValues { (_, stats) -> stats.lastTimeUsed }
            },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        emptyMap()
    }
}

/**
 * Starter en genvej via LauncherApps på Dispatchers.IO.
 */
suspend fun startAiShortcut(
    context: Context,
    userHandle: UserHandle,
    packageName: String,
    shortcutId: String,
) = withContext(Dispatchers.IO) {
    val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return@withContext
    try {
        launcherApps.startShortcut(packageName, shortcutId, null, null, userHandle)
    } catch (_: Throwable) {}
}

/**
 * Indlæser alle data for AI-byg-siden på Dispatchers.IO.
 */
suspend fun loadAiPageData(
    context: Context,
    installedAiApps: List<AppEntry>,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): AiPageData = withContext(Dispatchers.IO) {
    val hasAccess = hasUsageStatsPermission(context)
    val pkgs = installedAiApps.map { it.id.substringBefore('/') }
    val usageMap = if (hasAccess) queryAiUsageStats(context, pkgs, now, zoneId) else emptyMap()

    val cards = installedAiApps.map { app ->
        val pkg = app.id.substringBefore('/')
        val user = app.info?.user ?: Process.myUserHandle()
        val allShortcuts = queryAiShortcuts(context, user, pkg)
        val latestConvo = findLatestConversation(allShortcuts)
        val buttons = selectShortcutButtons(allShortcuts)
        val (lastUsed, todayTime) = usageMap[pkg] ?: (0L to 0L)
        AiAppCardData(
            app = app,
            latestConversation = latestConvo,
            shortcuts = buttons,
            lastTimeUsedMillis = lastUsed,
            todayUsageMillis = todayTime,
        )
    }
    val totalToday = sumTodayAiUsage(cards)
    AiPageData(
        cards = cards,
        totalTodayMillis = totalToday,
        hasUsageAccess = hasAccess,
    )
}
