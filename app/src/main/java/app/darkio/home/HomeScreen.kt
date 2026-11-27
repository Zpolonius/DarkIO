package app.darkio.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import app.darkio.R
import app.darkio.dashboard.ActivityGridCard
import app.darkio.dashboard.CalendarCard
import app.darkio.dashboard.CalendarEvent
import app.darkio.dashboard.CaloriesCard
import app.darkio.dashboard.ClockCard
import app.darkio.dashboard.DeviceCalendar
import app.darkio.dashboard.FocusCard
import app.darkio.dashboard.MediaPage
import app.darkio.dashboard.StepsCard
import app.darkio.dashboard.TipCard
import app.darkio.dashboard.WorkoutCard
import app.darkio.dashboard.BuildPage
import app.darkio.dashboard.sharedFocusUsageCache
import app.darkio.data.AppCategory
import app.darkio.data.AppEntry
import app.darkio.data.AppLists
import app.darkio.data.BedtimeSummary
import app.darkio.data.HomeCard
import app.darkio.data.MEDIA_PAGE
import app.darkio.data.MediaNow
import app.darkio.data.Settings
import app.darkio.data.WORK_SHORTCUTS_PAGE
import app.darkio.data.dockSlots
import app.darkio.data.firstFreeSlot
import app.darkio.data.getInterruptionFilter
import app.darkio.data.isBedtimeMode
import app.darkio.data.isWorkHours
import app.darkio.data.openBedtimeSettings
import app.darkio.data.queryBedtimeSummary
import app.darkio.data.reorderedSlots
import java.time.ZonedDateTime
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.darkio.drawer.AppDrawer
import app.darkio.drawer.AppMenuSheet
import app.darkio.drawer.SearchField
import app.darkio.drawer.SearchResultsPane
import app.darkio.drawer.searchApps
import app.darkio.settings.HiddenAppsScreen
import app.darkio.settings.SettingsScreen
import app.darkio.settings.WorkoutProgramScreen
import app.darkio.ui.TypewriterText
import app.darkio.ui.onVerticalSwipe
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOMotion
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType

/** Hjemskærm med knap og swipe op til app-skuffen. [onHintSeen] kaldes første gang skuffen åbnes. */
@Composable
fun HomeScreen(
    apps: AppLists,
    hintSeen: Boolean,
    onHintSeen: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onSetHidden: (AppEntry, Boolean) -> Unit,
    onSetFavorites: (List<Pair<AppEntry, Int?>>) -> Unit,
    onSetCategory: (AppEntry, AppCategory) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    homeSignal: Int = 0,
    onSetShortcuts: (page: Int, changes: List<Pair<AppEntry, Int?>>) -> Unit = { _, _ -> },
    onOpenNotifications: () -> Unit = {},
    settings: Settings = Settings(),
    onSettingsChange: ((Settings) -> Settings) -> Unit = {},
    onResetAll: () -> Unit = {},
    requestAccess: ((HomeCard) -> Unit)? = null,
    nowProvider: () -> ZonedDateTime = { ZonedDateTime.now() },
    onFyraftenZenRule: (Boolean) -> Unit = {},
    calendarTestEvents: List<CalendarEvent>? = null,
    calendarsOverride: List<DeviceCalendar>? = null,
    interruptionFilterProvider: (() -> Int)? = null,
    bedtimeDataOverride: BedtimeSummary? = null,
    onOpenBedtimeSettings: (() -> Unit)? = null,
    onToggleMedia: (AppEntry, Boolean) -> Unit = { _, _ -> },
    mediaOverride: MediaNow? = null,
    onGrantMediaAccess: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME, lifecycleOwner) {
        resumeTick++
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP, lifecycleOwner) {
        sharedFocusUsageCache.invalidateToday()
    }
    val now = remember(nowProvider, resumeTick) { nowProvider() }
    val isWorkHours = remember(settings, now) { isWorkHours(settings, now) }
    val actualFilterProvider = remember(interruptionFilterProvider, context) {
        interruptionFilterProvider ?: { getInterruptionFilter(context) }
    }
    val interruptionFilter = remember(actualFilterProvider, resumeTick) { actualFilterProvider() }
    val isBedtime = remember(interruptionFilter, settings, now) { isBedtimeMode(interruptionFilter, settings, now) }

    var bedtimeSummary by remember { mutableStateOf<BedtimeSummary?>(bedtimeDataOverride) }
    LaunchedEffect(resumeTick, isBedtime, bedtimeDataOverride) {
        if (bedtimeDataOverride != null) {
            bedtimeSummary = bedtimeDataOverride
        } else if (isBedtime) {
            bedtimeSummary = queryBedtimeSummary(context, now)
        }
    }
    val effectiveBedtimeData = bedtimeDataOverride ?: bedtimeSummary ?: BedtimeSummary()

    val pageLabels = listOf(R.string.page_today, R.string.page_training, R.string.page_build, R.string.page_media).map { stringResource(it) }
    val pager = rememberPagerState { pageLabels.size }
    val scope = rememberCoroutineScope()
    // Hjem-knappen (ny MAIN/HOME-intent) vender altid tilbage til side 1.
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, apps.visible) { searchApps(apps.visible, query) }
    val openDrawer = {
        if (!hintSeen) onHintSeen()
        drawerOpen = true
    }
    var mediaPickerOpen by rememberSaveable { mutableStateOf(false) }
    var hiddenOpen by rememberSaveable { mutableStateOf(false) }
    var workoutOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var workoutReloadCounter by remember { mutableIntStateOf(0) }
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            settingsOpen = false
            hiddenOpen = false
            workoutOpen = false
            pager.scrollToPage(0)
        }
    }
    var menuAppId by rememberSaveable { mutableStateOf<String?>(null) }
    val dock = remember(apps.visible, apps.favoriteSlots) { dockSlots(apps.visible, apps.favoriteSlots) }
    var dockFull by remember { mutableStateOf(false) }
    LaunchedEffect(dockFull) { if (dockFull) { delay(3000); dockFull = false } }
    // Rækken tegnes pr. side i pageren; menuen bruger den viste side. Skjulte apps beholder deres plads, som i docken.
    val page = pager.currentPage
    var rowFull by remember { mutableStateOf(false) }
    LaunchedEffect(rowFull) { if (rowFull) { delay(3000); rowFull = false } }
    LaunchedEffect(page) { rowFull = false }
    var bottomHeight by remember { mutableStateOf(0.dp) }
    var shortcutsHeight by remember { mutableStateOf(DarkIOSize.IconTile + DarkIOSpace.S3 * 2) }
    val density = LocalDensity.current
    val menuApp = menuAppId?.let { id -> apps.visible.firstOrNull { it.id == id } }
    if (menuApp == null) menuAppId = null
    BackHandler(enabled = drawerOpen || query.isNotEmpty()) {
        if (drawerOpen) drawerOpen = false else query = ""
    }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = hiddenOpen) { hiddenOpen = false }
    BackHandler(enabled = workoutOpen) { workoutOpen = false }
    BackHandler(enabled = menuApp != null) { menuAppId = null }
    BackHandler(enabled = mediaPickerOpen) { mediaPickerOpen = false }
    // Skuffen og hjem komponeres ikke bag skærmen, så tryk og TalkBack ikke når dem.
    if (hiddenOpen) {
        HiddenAppsScreen(apps.hidden, onShowAgain = { onSetHidden(it, false) })
        return
    }
    if (workoutOpen) {
        WorkoutProgramScreen()
        return
    }
    if (settingsOpen) {
        SettingsScreen(
            settings = settings,
            onChange = onSettingsChange,
            onOpenHidden = { hiddenOpen = true },
            onOpenWorkout = { workoutOpen = true },
            onResetAll = {
                settingsOpen = false
                scope.launch { pager.scrollToPage(0) }
                onResetAll()
            },
            requestAccess = requestAccess,
            calendarsOverride = calendarsOverride,
        )
        return
    }
    val canOpenSettings = !drawerOpen && !mediaPickerOpen && query.isEmpty() && menuApp == null
    val swipePx = with(LocalDensity.current) { DarkIOSize.TouchMin.toPx() }
    val motion = tween<IntOffset>(DarkIOMotion.DrawerMs)
    val fade = tween<Float>(DarkIOMotion.DrawerMs)

    // Android tegner brugerens baggrund (windowShowWallpaper); her ligger toningen. Skuffe og søgning dækker den helt (F21).
    val scrim = if (drawerOpen || query.isNotBlank()) SolidColor(DarkIOColors.Bg) else Brush.verticalGradient(
        0f to DarkIOColors.Bg.copy(alpha = settings.scrimTop / 100f),
        0.5f to DarkIOColors.ScrimMid,
        1f to DarkIOColors.ScrimBottom,
    )
    BoxWithConstraints(Modifier.fillMaxSize().background(scrim)) {
        // Swipe-området slutter over systemGestures-zonen, så swipe op ikke kolliderer med Hjem-gesten.
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Bottom))
                .onVerticalSwipe {
                    if (it < -swipePx) openDrawer()
                    else if (it > swipePx) {
                        if (drawerOpen) drawerOpen = false else if (query.isEmpty()) onOpenNotifications()
                    }
                },
        ) {
            // Siden og dens genvejsrække skifter side sammen; sideprikker over genvejsrækken samt dokken og søgefeltet nedenfor står fast.
            val pageBottomPadding = bottomHeight + shortcutsHeight + DarkIOSpace.S4 + DarkIOSize.PageDotHitHeight + DarkIOSpace.S3
            HorizontalPager(
                pager,
                Modifier
                    .fillMaxSize()
                    .testTag("homePager")
                    .then(if (drawerOpen || mediaPickerOpen || query.isNotBlank()) Modifier.clearAndSetSemantics {} else Modifier),
            ) { p ->
                val effectivePage = if (p == 0 && isWorkHours) WORK_SHORTCUTS_PAGE else p
                val slots = apps.shortcutSlots[effectivePage].orEmpty()
                val shown = remember(apps.visible, slots) { dockSlots(apps.visible, slots) }
                Box(Modifier.fillMaxSize()) {
                    // Bag kort og genveje: langt tryk på tom flade åbner Indstillinger (F11).
                    Box(Modifier.fillMaxSize().pointerInput(canOpenSettings) { detectTapGestures(onLongPress = { if (canOpenSettings) settingsOpen = true }) })
                    Box(Modifier.fillMaxSize().padding(bottom = pageBottomPadding), contentAlignment = Alignment.Center) {
                        if (p == 0) {
                            if (isBedtime) {
                                BedtimeView(
                                    summary = effectiveBedtimeData,
                                    now = now,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(horizontal = DarkIOSpace.S5)
                                        .padding(top = DarkIOSpace.TopInset),
                                )
                            } else {
                                Column(
                                    Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = DarkIOSpace.S5)
                                        .padding(top = DarkIOSpace.TopInset),
                                    verticalArrangement = Arrangement.spacedBy(DarkIOSpace.S2),
                                ) {
                                    if (HomeCard.Clock in settings.cards) ClockCard(Modifier.testTag("card_Clock"))
                                    if (HomeCard.Calendar in settings.cards) CalendarCard(
                                        modifier = Modifier.testTag("card_Calendar"),
                                        isWorkHours = isWorkHours,
                                        workCalendarIds = settings.workCalendarIds,
                                        testEvents = calendarTestEvents,
                                        nowMillis = { now.toInstant().toEpochMilli() },
                                    )
                                    if (HomeCard.Focus in settings.cards) FocusCard()
                                    if (HomeCard.Tip in settings.cards) TipCard(Modifier.testTag("card_Tip"))
                                }
                            }
                        } else if (p == 1) {
                            Column(
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = DarkIOSpace.S4)
                                    .padding(top = DarkIOSpace.TopInset - DarkIOSpace.S2, bottom = DarkIOSpace.S6),
                                verticalArrangement = Arrangement.spacedBy(DarkIOSpace.S1),
                            ) {
                                if (HomeCard.Workout in settings.cards) WorkoutCard(
                                    modifier = Modifier.testTag("card_Workout"),
                                    onCompletedChanged = { workoutReloadCounter++ },
                                    verticalPadding = DarkIOSpace.S2,
                                )
                                if (HomeCard.Workout in settings.cards || HomeCard.Steps in settings.cards) ActivityGridCard(
                                    modifier = Modifier.testTag("card_ActivityGrid"),
                                    stepGoal = settings.stepGoal,
                                    reloadTrigger = workoutReloadCounter,
                                    verticalPadding = DarkIOSpace.S2,
                                )
                                if (HomeCard.Steps in settings.cards) StepsCard(
                                    modifier = Modifier.fillMaxWidth().testTag("card_Steps"),
                                    goal = settings.stepGoal,
                                    verticalPadding = DarkIOSpace.S2,
                                )
                                if (HomeCard.Calories in settings.cards) CaloriesCard(
                                    modifier = Modifier.fillMaxWidth().testTag("card_Calories"),
                                    verticalPadding = DarkIOSpace.S2,
                                )
                            }
                        } else if (p == 2) {
                            BuildPage(
                                apps = apps.visible,
                                onLaunch = onLaunch,
                            )
                        } else if (p == MEDIA_PAGE) {
                            MediaPage(
                                apps = apps.mediaApps,
                                onLaunch = onLaunch,
                                onMenu = { menuAppId = it.id },
                                onAdd = { mediaPickerOpen = true },
                                nowOverride = mediaOverride,
                                onGrantAccess = onGrantMediaAccess,
                            )
                        }
                        // Fast plads over sideprikkerne; på glas, så den kan læses på lyse billeder (N8).
                        // Medier-siden har brug for pladsen nederst (F15) og viser sit eget navn øverst.
                        if (p != MEDIA_PAGE) Column(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(DarkIOSpace.S2),
                        ) {
                            if (p == 0 && isWorkHours && !isBedtime) {
                                Box(
                                    modifier = Modifier
                                        .testTag("fyraftenButton")
                                        .heightIn(min = DarkIOSize.TouchMin)
                                        .clip(DarkIOShapes.Pill)
                                        .background(DarkIOColors.SurfaceGlass)
                                        .clickable(role = Role.Button) {
                                            onSettingsChange { it.copy(workEndOverrideDate = now.toLocalDate()) }
                                            onFyraftenZenRule(true)
                                        }
                                        .padding(horizontal = DarkIOSpace.S4, vertical = DarkIOSpace.S2),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    BasicText(
                                        stringResource(R.string.work_fyraften_button),
                                        style = DarkIOType.Button.copy(color = DarkIOColors.Ink),
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(DarkIOSpace.S2),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BasicText(
                                    pageLabels[p],
                                    style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft),
                                    modifier = Modifier.testTag("page$p").clip(DarkIOShapes.Pill).background(DarkIOColors.SurfaceGlass).padding(horizontal = DarkIOSpace.S3, vertical = DarkIOSpace.S1),
                                )
                                if (p == 0 && isWorkHours && !isBedtime) {
                                    BasicText(
                                        stringResource(R.string.work_hours_badge),
                                        style = DarkIOType.Data.copy(color = DarkIOColors.Accent),
                                        modifier = Modifier
                                            .testTag("workHoursBadge")
                                            .clip(DarkIOShapes.Pill)
                                            .background(DarkIOColors.SurfaceGlass)
                                            .padding(horizontal = DarkIOSpace.S3, vertical = DarkIOSpace.S1),
                                    )
                                }
                            }
                        }
                    }
                    if (query.isBlank() && p != 2) {
                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = DarkIOSpace.S5)
                                .padding(bottom = bottomHeight)
                                .onSizeChanged { shortcutsHeight = with(density) { it.height.toDp() } },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(DarkIOSpace.S2),
                        ) {
                            if (p == 0 && isBedtime) {
                                Box(
                                    modifier = Modifier
                                        .testTag("bedtimeSettingsButton")
                                        .heightIn(min = DarkIOSize.TouchMin)
                                        .clip(DarkIOShapes.Pill)
                                        .background(DarkIOColors.SurfaceGlass)
                                        .clickable(role = Role.Button) {
                                            if (onOpenBedtimeSettings != null) {
                                                onOpenBedtimeSettings()
                                            } else {
                                                openBedtimeSettings(context)
                                            }
                                        }
                                        .padding(horizontal = DarkIOSpace.S4, vertical = DarkIOSpace.S2),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    BasicText(
                                        stringResource(R.string.bedtime_settings_button),
                                        style = DarkIOType.Button.copy(color = DarkIOColors.Ink),
                                    )
                                }
                            } else {
                                Dock(
                                    shown,
                                    onLaunch,
                                    onMenu = { menuAppId = it.id },
                                    onMove = { from, to -> onSetShortcuts(effectivePage, reorderedSlots(shown, from, to, apps.hidden.mapNotNull { slots[it.id] }.toSet()).map { (id, slot) -> shown.filterNotNull().first { it.id == id } to slot }) },
                                    tag = "shortcuts",
                                    emptyLabel = R.string.shortcut_empty_slot,
                                    label = R.string.shortcuts_label,
                                    dragAfterLongPress = true,
                                )
                            }
                        }
                    }
                }
            }
            if (query.isBlank()) {
                PageDots(
                    pageLabels,
                    pager.currentPage,
                    { scope.launch { pager.animateScrollToPage(it) } },
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomHeight + shortcutsHeight + DarkIOSpace.S4),
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { bottomHeight = with(density) { it.height.toDp() } }
                    .fillMaxWidth()
                    .heightIn(max = maxHeight / 2)
                    .imePadding()
                    .padding(horizontal = DarkIOSpace.S5)
                    .padding(top = DarkIOSpace.S1, bottom = DarkIOSpace.S5),
            ) {
                if (query.isBlank()) {
                    if (dockFull) {
                        BasicText(stringResource(R.string.dock_full), style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft), modifier = Modifier.testTag("dockFull"))
                        Spacer(Modifier.height(DarkIOSpace.S3))
                    }
                    if (rowFull) {
                        BasicText(stringResource(R.string.shortcuts_full), style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft), modifier = Modifier.testTag("shortcutsFull"))
                        Spacer(Modifier.height(DarkIOSpace.S3))
                    }
                    Dock(
                        dock,
                        onLaunch,
                        onMenu = { menuAppId = it.id },
                        onMove = { from, to -> onSetFavorites(reorderedSlots(dock, from, to, apps.hidden.mapNotNull { apps.favoriteSlots[it.id] }.toSet()).map { (id, slot) -> dock.filterNotNull().first { it.id == id } to slot }) },
                    )
                    Spacer(Modifier.height(DarkIOSpace.S3))
                    if (!hintSeen && !isBedtime) {
                        TypewriterText(
                            text = stringResource(R.string.welcome_prompt),
                            prefix = "$ ",
                            style = DarkIOType.Data.copy(color = DarkIOColors.Ink),
                        )
                        Spacer(Modifier.height(DarkIOSpace.S1))
                        BasicText(stringResource(R.string.hint_swipe_up), style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft))
                        Spacer(Modifier.height(DarkIOSpace.S3))
                    }
                } else {
                    SearchResultsPane(results, onLaunch, { menuAppId = it.id }, Modifier.weight(1f, fill = false))
                }
                Spacer(Modifier.height(DarkIOSpace.S2))
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { results.firstOrNull()?.let(onLaunch) },
                    background = DarkIOColors.SurfaceGlass,
                    placeholder = stringResource(R.string.home_search_placeholder),
                )
            }
        }
        AnimatedVisibility(
            visible = drawerOpen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(motion) { it } + fadeIn(fade),
            exit = slideOutVertically(motion) { it } + fadeOut(fade),
        ) {
            AppDrawer(
                apps = apps.visible,
                onLaunch = onLaunch,
                onMenu = { menuAppId = it.id },
                onDismiss = { drawerOpen = false },
                modifier = Modifier.imePadding().heightIn(max = maxHeight * DarkIOSize.DrawerMaxFraction),
            )
        }
        if (mediaPickerOpen) {
            // Samme app-skuffe som hjemmets, men et tryk vælger appen til medier i stedet for at starte den.
            AppDrawer(
                apps = apps.visible.filter { app -> apps.mediaApps.none { it.id == app.id } },
                onLaunch = { onToggleMedia(it, true); mediaPickerOpen = false },
                onMenu = {},
                onDismiss = { mediaPickerOpen = false },
                modifier = Modifier.align(Alignment.BottomCenter).imePadding().heightIn(max = maxHeight * DarkIOSize.DrawerMaxFraction).testTag("mediaPicker"),
            )
        }
        if (menuApp != null) {
            val close = { menuAppId = null }
            val menuShortcutPage = if (page == 0 && isWorkHours) WORK_SHORTCUTS_PAGE else page
            val menuShortcutSlots = apps.shortcutSlots[menuShortcutPage].orEmpty()
            val menuRow = remember(apps.visible, menuShortcutSlots) { dockSlots(apps.visible, menuShortcutSlots) }
            AppMenuSheet(
                app = menuApp,
                onDismiss = close,
                isFavorite = dock.any { it?.id == menuApp.id },
                onToggleFavorite = {
                    val slot = firstFreeSlot((apps.visible + apps.hidden).mapNotNull { apps.favoriteSlots[it.id] })
                    if (dock.any { it?.id == menuApp.id }) onSetFavorites(listOf(menuApp to null))
                    else if (slot != null) onSetFavorites(listOf(menuApp to slot))
                    else dockFull = true
                    close()
                },
                isMedia = apps.mediaApps.any { it.id == menuApp.id },
                onToggleMedia = {
                    onToggleMedia(menuApp, apps.mediaApps.none { it.id == menuApp.id })
                    close()
                },
                isShortcut = menuRow.any { it?.id == menuApp.id },
                onToggleShortcut = {
                    val slot = firstFreeSlot((apps.visible + apps.hidden).mapNotNull { menuShortcutSlots[it.id] })
                    if (menuRow.any { it?.id == menuApp.id }) onSetShortcuts(menuShortcutPage, listOf(menuApp to null))
                    else if (slot != null) onSetShortcuts(menuShortcutPage, listOf(menuApp to slot))
                    else rowFull = true
                    close()
                },
                onHide = { onSetHidden(menuApp, true); close() },
                onSetCategory = { onSetCategory(menuApp, it); close() },
                onAppInfo = { onAppInfo(menuApp); close() },
                onUninstall = { onUninstall(menuApp); close() },
                modifier = Modifier.heightIn(max = maxHeight / 2),
            )
        }
    }
}
