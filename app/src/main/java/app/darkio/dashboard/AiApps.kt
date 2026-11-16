package app.darkio.dashboard

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import app.darkio.data.AppEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
): String {
    if (lastUsedMillis <= 0L) return "aldrig brugt"
    val lastUsed = Instant.ofEpochMilli(lastUsedMillis).atZone(zoneId)
    val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
    val lastDate = lastUsed.toLocalDate()
    val nowDate = now.toLocalDate()
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    return when {
        lastDate == nowDate -> "kl. ${timeFormatter.format(lastUsed)}"
        lastDate == nowDate.minusDays(1) -> "i går"
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
    noAccessText: String = "giv adgang til brugsdata",
): String {
    if (!hasAccess) return noAccessText
    val lastUsedStr = formatLastUsed(lastUsedMillis, nowMillis, zoneId)
    val todayStr = formatScreenTime(todayMillis)
    return "$lastUsedStr · $todayStr"
}

/**
 * Formaterer overskriften for 'ai i dag'. Ved manglende adgang returneres [noAccessText].
 */
fun formatAiTodayHeader(
    totalTodayMillis: Long,
    hasAccess: Boolean,
    noAccessText: String = "giv adgang til brugsdata",
): String {
    if (!hasAccess) return noAccessText
    return formatScreenTime(totalTodayMillis)
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
 * Henter brugsstatistik for AI-apps fra UsageStatsManager på Dispatchers.IO.
 * Returnerer et map fra packageName til Pair(lastTimeUsedMillis, todayUsageMillis).
 */
suspend fun queryAiUsageStats(
    context: Context,
    packages: List<String>,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Map<String, Pair<Long, Long>> = withContext(Dispatchers.IO) {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        ?: return@withContext emptyMap()
    val (startToday, endToday) = dayFocusTimeRange(now, zoneId)
    val thirtyDaysAgo = now.minus(30, java.time.temporal.ChronoUnit.DAYS).toEpochMilli()
    try {
        val todayStats = usageStatsManager.queryAndAggregateUsageStats(startToday.toEpochMilli(), endToday.toEpochMilli())
        val recentStats = usageStatsManager.queryAndAggregateUsageStats(thirtyDaysAgo, endToday.toEpochMilli())
        packages.associateWith { pkg ->
            val todayMillis = todayStats[pkg]?.totalTimeInForeground ?: 0L
            val lastUsed = recentStats[pkg]?.lastTimeUsed ?: todayStats[pkg]?.lastTimeUsed ?: 0L
            lastUsed to todayMillis
        }
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
