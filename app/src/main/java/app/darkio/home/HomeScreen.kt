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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import app.darkio.R
import app.darkio.data.AppCategory
import app.darkio.data.AppEntry
import app.darkio.data.AppLists
import app.darkio.data.dockSlots
import app.darkio.data.firstFreeSlot
import app.darkio.data.reorderedSlots
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.darkio.drawer.AppDrawer
import app.darkio.drawer.AppMenuSheet
import app.darkio.drawer.SearchField
import app.darkio.drawer.SearchResultsPane
import app.darkio.drawer.searchApps
import app.darkio.settings.HiddenAppsScreen
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
) {
    val pageLabels = listOf(R.string.page_today, R.string.page_training, R.string.page_build).map { stringResource(it) }
    val pager = rememberPagerState { pageLabels.size }
    val scope = rememberCoroutineScope()
    // Hjem-knappen (ny MAIN/HOME-intent) vender altid tilbage til side 1.
    LaunchedEffect(homeSignal) { if (homeSignal > 0) pager.scrollToPage(0) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, apps.visible) { searchApps(apps.visible, query) }
    val openDrawer = {
        if (!hintSeen) onHintSeen()
        drawerOpen = true
    }
    var hiddenOpen by rememberSaveable { mutableStateOf(false) }
    var menuAppId by rememberSaveable { mutableStateOf<String?>(null) }
    val dock = remember(apps.visible, apps.favoriteSlots) { dockSlots(apps.visible, apps.favoriteSlots) }
    var dockFull by remember { mutableStateOf(false) }
    LaunchedEffect(dockFull) { if (dockFull) { delay(3000); dockFull = false } }
    // Rækken tegnes pr. side i pageren; menuen bruger den viste side. Skjulte apps beholder deres plads, som i docken.
    val page = pager.currentPage
    val pageSlots = apps.shortcutSlots[page].orEmpty()
    val row = remember(apps.visible, pageSlots) { dockSlots(apps.visible, pageSlots) }
    var rowFull by remember { mutableStateOf(false) }
    LaunchedEffect(rowFull) { if (rowFull) { delay(3000); rowFull = false } }
    LaunchedEffect(page) { rowFull = false }
    var bottomHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val menuApp = menuAppId?.let { id -> apps.visible.firstOrNull { it.id == id } }
    if (menuApp == null) menuAppId = null
    BackHandler(enabled = drawerOpen || query.isNotEmpty()) {
        if (drawerOpen) drawerOpen = false else query = ""
    }
    BackHandler(enabled = hiddenOpen) { hiddenOpen = false }
    BackHandler(enabled = menuApp != null) { menuAppId = null }
    // Skuffen og hjem komponeres ikke bag skærmen, så tryk og TalkBack ikke når dem.
    if (hiddenOpen) {
        HiddenAppsScreen(apps.hidden, onShowAgain = { onSetHidden(it, false) })
        return
    }
    val swipePx = with(LocalDensity.current) { DarkIOSize.TouchMin.toPx() }
    val motion = tween<IntOffset>(DarkIOMotion.DrawerMs)
    val fade = tween<Float>(DarkIOMotion.DrawerMs)

    BoxWithConstraints(Modifier.fillMaxSize().background(DarkIOColors.Bg)) {
        // Swipe-området slutter over systemGestures-zonen, så swipe op ikke kolliderer med Hjem-gesten.
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Bottom))
                .onVerticalSwipe { if (it < -swipePx) openDrawer() else if (it > swipePx) drawerOpen = false },
        ) {
            // Siden og dens genvejsrække skifter side sammen; dokken og søgefeltet står fast nedenfor.
            HorizontalPager(pager, Modifier.fillMaxSize().testTag("homePager")) { p ->
                val slots = apps.shortcutSlots[p].orEmpty()
                val shown = remember(apps.visible, slots) { dockSlots(apps.visible, slots) }
                Box(Modifier.fillMaxSize().padding(bottom = bottomHeight), contentAlignment = Alignment.Center) {
                    BasicText(pageLabels[p], style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft), modifier = Modifier.testTag("page$p"))
                    if (query.isBlank()) {
                        Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = DarkIOSpace.S5)) {
                            Dock(
                                shown,
                                onLaunch,
                                onMenu = { menuAppId = it.id },
                                onMove = { from, to -> onSetShortcuts(p, reorderedSlots(shown, from, to, apps.hidden.mapNotNull { slots[it.id] }.toSet()).map { (id, slot) -> shown.filterNotNull().first { it.id == id } to slot }) },
                                tag = "shortcuts",
                                emptyLabel = R.string.shortcut_empty_slot,
                                label = R.string.shortcuts_label,
                                dragAfterLongPress = true,
                            )
                        }
                    }
                }
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { bottomHeight = with(density) { it.height.toDp() } }
                    .fillMaxWidth()
                    .heightIn(max = maxHeight / 2)
                    .imePadding()
                    .padding(horizontal = DarkIOSpace.S5, vertical = DarkIOSpace.S5),
            ) {
                if (query.isBlank()) {
                    PageDots(pageLabels, pager.currentPage, { scope.launch { pager.animateScrollToPage(it) } }, Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(DarkIOSpace.S2))
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
                    if (!hintSeen) {
                        BasicText(stringResource(R.string.hint_swipe_up), style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft))
                        Spacer(Modifier.height(DarkIOSpace.S3))
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(DarkIOSize.FieldHeight)
                            .clip(DarkIOShapes.Md)
                            .background(DarkIOColors.Surface)
                            .clickable(role = Role.Button, onClick = openDrawer),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(stringResource(R.string.home_all_apps), style = DarkIOType.Button.copy(color = DarkIOColors.Ink))
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
                onShowHidden = { hiddenOpen = true },
                onDismiss = { drawerOpen = false },
                modifier = Modifier.imePadding().heightIn(max = maxHeight / 2),
            )
        }
        if (menuApp != null) {
            val close = { menuAppId = null }
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
                isShortcut = row.any { it?.id == menuApp.id },
                onToggleShortcut = {
                    val slot = firstFreeSlot((apps.visible + apps.hidden).mapNotNull { pageSlots[it.id] })
                    if (row.any { it?.id == menuApp.id }) onSetShortcuts(page, listOf(menuApp to null))
                    else if (slot != null) onSetShortcuts(page, listOf(menuApp to slot))
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
