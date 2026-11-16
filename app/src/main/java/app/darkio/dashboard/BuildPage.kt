package app.darkio.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.darkio.R
import app.darkio.data.AppEntry
import app.darkio.data.darkIoPrefs
import app.darkio.data.getOrChooseDailyTip
import app.darkio.drawer.AppIcon
import app.darkio.ui.TypewriterText
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Side 3 ("byg"): Side for AI-apps (Claude, ChatGPT, Gemini).
 * Øverst: "ai i dag" og dagens vibe-coding-tip.
 * Nederst: AI-kort for installerede AI-apps i fast rækkefølge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BuildPage(
    apps: List<AppEntry> = emptyList(),
    onLaunch: (AppEntry) -> Unit = {},
    modifier: Modifier = Modifier,
    now: () -> LocalDate = LocalDate::now,
    nowInstant: () -> Instant = Instant::now,
    zoneId: ZoneId = ZoneId.systemDefault(),
    loadData: (suspend (List<AppEntry>) -> AiPageData)? = null,
    onStartShortcut: ((packageName: String, shortcutId: String) -> Unit)? = null,
    onGrantAccess: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val tips = stringArrayResource(R.array.vibe_tips).toList()
    var todayTip by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    val currentDate = remember { now() }
    LaunchedEffect(currentDate) {
        val index = getOrChooseDailyTip(context.darkIoPrefs, tips, currentDate)
        todayTip = tips.getOrNull(index) ?: tips.firstOrNull() ?: ""
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2500)
            copied = false
        }
    }

    val installedAiApps = remember(apps) { filterAiApps(apps) }
    var pageData by remember { mutableStateOf<AiPageData?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(installedAiApps, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val data = loadData?.invoke(installedAiApps)
                ?: loadAiPageData(context, installedAiApps, nowInstant(), zoneId)
            pageData = data
        }
    }

    val actualGrantAccess = onGrantAccess ?: { openUsageAccessSettings(context) }
    val actualStartShortcut: (String, String) -> Unit = onStartShortcut ?: { pkg, shortcutId ->
        scope.launch {
            val app = installedAiApps.firstOrNull { it.id.substringBefore('/') == pkg }
            val user = app?.info?.user ?: android.os.Process.myUserHandle()
            startAiShortcut(context, user, pkg, shortcutId)
        }
    }

    val hasAccess = pageData?.hasUsageAccess ?: hasUsageStatsPermission(context)
    val totalAiToday = pageData?.totalTodayMillis ?: 0L
    val cards = pageData?.cards ?: installedAiApps.map {
        AiAppCardData(it, null, emptyList(), 0L, 0L)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // Øverste zone (læsezonen): "ai i dag" + dagens tip
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = DarkIOSpace.S5)
                .padding(top = DarkIOSpace.TopInset),
            verticalArrangement = Arrangement.spacedBy(DarkIOSpace.S2),
        ) {
            // Kort 1: "ai i dag"
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(DarkIOShapes.Lg)
                    .background(DarkIOColors.SurfaceGlass)
                    .border(DarkIOSize.LineWidth, DarkIOColors.Line, DarkIOShapes.Lg)
                    .padding(DarkIOSpace.S4)
                    .testTag("aiTodayCard"),
            ) {
                BasicText(
                    stringResource(R.string.ai_today_label),
                    style = DarkIOType.Label.copy(color = DarkIOColors.InkMuted),
                )
                Spacer(Modifier.height(DarkIOSpace.S1))
                if (!hasAccess) {
                    BasicText(
                        stringResource(R.string.ai_grant_access),
                        style = DarkIOType.Data.copy(color = DarkIOColors.Accent),
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = actualGrantAccess)
                            .testTag("aiTodayGrantAccess"),
                    )
                } else {
                    BasicText(
                        formatScreenTime(totalAiToday),
                        style = DarkIOType.Metric.copy(color = DarkIOColors.Ink),
                        modifier = Modifier.testTag("aiTodayScreenTime"),
                    )
                }
            }

            // Kort 2: Dagens tip (langt tryk kopierer)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(DarkIOShapes.Lg)
                    .background(DarkIOColors.SurfaceGlass)
                    .border(DarkIOSize.LineWidth, DarkIOColors.Line, DarkIOShapes.Lg)
                    .padding(DarkIOSpace.S4)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = {},
                        onLongClick = {
                            if (todayTip.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(todayTip))
                                copied = true
                            }
                        },
                    )
                    .testTag("buildTipCard"),
            ) {
                BasicText(
                    "// dagens tip",
                    style = DarkIOType.Label.copy(color = DarkIOColors.InkMuted),
                )
                Spacer(Modifier.height(DarkIOSpace.S2))
                TypewriterText(
                    text = todayTip,
                    prefix = "$ ",
                    prefixColor = DarkIOColors.Accent,
                    style = DarkIOType.Body.copy(color = DarkIOColors.Ink),
                    now = now,
                    modifier = Modifier.testTag("buildTodayTip"),
                )
                if (copied) {
                    Spacer(Modifier.height(DarkIOSpace.S2))
                    BasicText(
                        "// ${stringResource(R.string.tip_copied)}_",
                        style = DarkIOType.Data.copy(color = DarkIOColors.Accent),
                        modifier = Modifier.testTag("tipCopiedMessage"),
                    )
                }
            }
        }

        // Nederste zone (tommelfingerzonen): AI-apps kort (F15)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = DarkIOSpace.S5)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(DarkIOSpace.S2),
        ) {
            if (installedAiApps.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = DarkIOSpace.S4),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        stringResource(R.string.ai_empty),
                        style = DarkIOType.Data.copy(color = DarkIOColors.InkMuted),
                        modifier = Modifier.testTag("aiEmptyState"),
                    )
                }
            } else {
                for (card in cards) {
                    AiAppCard(
                        card = card,
                        hasUsageAccess = hasAccess,
                        onLaunch = onLaunch,
                        onStartShortcut = actualStartShortcut,
                        onGrantAccess = actualGrantAccess,
                        nowInstant = nowInstant,
                        zoneId = zoneId,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiAppCard(
    card: AiAppCardData,
    hasUsageAccess: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onStartShortcut: (packageName: String, shortcutId: String) -> Unit,
    onGrantAccess: () -> Unit,
    nowInstant: () -> Instant,
    zoneId: ZoneId,
) {
    val app = card.app
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DarkIOShapes.Lg)
            .background(DarkIOColors.SurfaceGlass)
            .border(DarkIOSize.LineWidth, DarkIOColors.Line, DarkIOShapes.Lg)
            .padding(DarkIOSpace.S3)
            .testTag("aiCard_${app.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DarkIOShapes.Md)
                .clickable(role = Role.Button, onClick = { onLaunch(app) })
                .testTag("aiAppRow_${app.id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app, size = DarkIOSize.IconTileSm)
            Spacer(Modifier.width(DarkIOSpace.S3))
            Column(Modifier.weight(1f)) {
                BasicText(
                    app.label,
                    style = DarkIOType.Item.copy(color = DarkIOColors.Ink),
                    modifier = Modifier.testTag("aiAppName_${app.id}"),
                )
                val convoText = card.latestConversation ?: stringResource(R.string.ai_no_conversations)
                BasicText(
                    convoText,
                    style = DarkIOType.Body.copy(color = DarkIOColors.InkSoft),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("aiLatestConvo_${app.id}"),
                )
                val usageText = formatLastUsedAndToday(
                    card.lastTimeUsedMillis,
                    card.todayUsageMillis,
                    hasUsageAccess,
                    nowMillis = nowInstant().toEpochMilli(),
                    zoneId = zoneId,
                    noAccessText = stringResource(R.string.ai_grant_access),
                )
                BasicText(
                    usageText,
                    style = DarkIOType.Data.copy(
                        color = if (!hasUsageAccess) DarkIOColors.Accent else DarkIOColors.InkMuted,
                    ),
                    modifier = Modifier
                        .testTag("aiUsage_${app.id}")
                        .let {
                            if (!hasUsageAccess) it.clickable(role = Role.Button, onClick = onGrantAccess)
                            else it
                        },
                )
            }
        }

        if (card.shortcuts.isNotEmpty()) {
            Spacer(Modifier.height(DarkIOSpace.S2))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("aiShortcutsRow_${app.id}"),
                horizontalArrangement = Arrangement.spacedBy(DarkIOSpace.S2),
            ) {
                for (shortcut in card.shortcuts) {
                    val talkBackLabel = stringResource(R.string.ai_shortcut_talkback, shortcut.label, app.label)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = DarkIOSize.TouchMin)
                            .height(DarkIOSize.TouchMin)
                            .clip(DarkIOShapes.Sm)
                            .background(DarkIOColors.Surface)
                            .border(DarkIOSize.LineWidth, DarkIOColors.Line, DarkIOShapes.Sm)
                            .clickable(
                                role = Role.Button,
                                onClick = { onStartShortcut(shortcut.packageName, shortcut.id) },
                            )
                            .semantics {
                                contentDescription = talkBackLabel
                            }
                            .testTag("aiShortcut_${shortcut.id}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            shortcut.label,
                            style = DarkIOType.Button.copy(color = DarkIOColors.Ink),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = DarkIOSpace.S2),
                        )
                    }
                }
            }
        }
    }
}
