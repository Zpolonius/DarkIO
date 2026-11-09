package app.darkio.drawer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import app.darkio.R
import app.darkio.data.AppCategory
import app.darkio.data.AppEntry
import app.darkio.ui.onVerticalSwipe
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App-skuffe: 4-kolonners gitter, søgning og kategori-bar, bundforankret. [onDismiss] kaldes ved swipe ned, [onMenu] ved langt tryk på en app. */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onShowHidden: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(AppCategory.ALL) }
    val filteredApps = remember(apps, selectedCategory) {
        if (selectedCategory == AppCategory.ALL) apps
        else apps.filter { it.category == selectedCategory }
    }
    val results = remember(query, apps) { searchApps(apps, query) }
    val swipePx = with(LocalDensity.current) { DarkIOSize.TouchMin.toPx() }
    Column(
        modifier
            .fillMaxWidth()
            .background(DarkIOColors.Bg)
            .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Bottom)),
    ) {
        // Håndtaget er det eneste swipe-område til at lukke; gitteret scroller og tager ikke swipe ned.
        Box(
            Modifier
                .fillMaxWidth()
                .height(DarkIOSize.TouchMin)
                .testTag("drawerGrip")
                .onVerticalSwipe { if (it > swipePx) onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = DarkIOSize.GripWidth, height = DarkIOSize.GripHeight).clip(DarkIOShapes.Pill).background(DarkIOColors.LineStrong))
        }
        if (query.isBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DarkIOSpace.S4, vertical = DarkIOSpace.S1)
                    .testTag("drawerHeader"),
            ) {
                BasicText(
                    stringResource(R.string.drawer_header, selectedCategory.slug, filteredApps.size),
                    style = DarkIOType.Label.copy(color = DarkIOColors.InkMuted),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).testTag("drawerGrid"),
                contentPadding = PaddingValues(horizontal = DarkIOSpace.S4, vertical = DarkIOSpace.S4),
                verticalArrangement = Arrangement.spacedBy(DarkIOSpace.S3),
            ) {
                items(filteredApps, key = { it.id }) { app ->
                    AppTile(app, onClick = { onLaunch(app) }, onLongClick = { onMenu(app) })
                }
            }
        } else {
            SearchResultsPane(results, onLaunch, onMenu, Modifier.weight(1f, fill = false))
        }
        SearchField(
            query = query,
            onQueryChange = { query = it },
            onSearch = { results.firstOrNull()?.let(onLaunch) },
            modifier = Modifier.padding(horizontal = DarkIOSpace.S4, vertical = DarkIOSpace.S2),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = DarkIOSize.TouchMin)
                .clickable(role = Role.Button, onClick = onShowHidden),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(stringResource(R.string.hidden_apps_title), style = DarkIOType.Button.copy(color = DarkIOColors.InkSoft))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(DarkIOSize.LineStroke)
                .background(DarkIOColors.Line),
        )
        CategoryBar(
            selectedCategory = selectedCategory,
            onSelectCategory = { selectedCategory = it },
            modifier = Modifier.fillMaxWidth().testTag("categoryBar"),
        )
    }
}

@Composable
private fun AppTile(app: AppEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(role = Role.Button, onLongClickLabel = stringResource(R.string.drawer_app_menu), onLongClick = onLongClick, onClick = onClick)
            .padding(vertical = DarkIOSpace.S1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app)
        Spacer(Modifier.height(DarkIOSpace.S1))
        BasicText(
            app.label,
            style = DarkIOType.Body.copy(color = DarkIOColors.InkSoft),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Ikonet hentes uden for main-tråden; falske apps (uden info) får et tomt felt. */
@Composable
internal fun AppIcon(app: AppEntry, size: Dp = DarkIOSize.IconTile) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val dpi = LocalConfiguration.current.densityDpi
    val icon by produceState<ImageBitmap?>(null, app.id) {
        value = withContext(Dispatchers.IO) {
            app.info?.getIcon(dpi)?.toBitmap(px, px)?.asImageBitmap()
        }
    }
    Box(Modifier.size(size).clip(DarkIOShapes.Lg).background(DarkIOColors.Surface)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
