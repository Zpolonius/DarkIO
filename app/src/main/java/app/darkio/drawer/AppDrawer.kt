package app.darkio.drawer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.graphics.drawable.toBitmap
import app.darkio.data.AppEntry
import app.darkio.ui.onVerticalSwipe
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App-skuffe: 4-kolonners gitter, bundforankret. [onDismiss] kaldes ved swipe ned. */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().testTag("drawerGrid"),
            contentPadding = PaddingValues(horizontal = DarkIOSpace.S4, vertical = DarkIOSpace.S4),
            verticalArrangement = Arrangement.spacedBy(DarkIOSpace.S3),
        ) {
            items(apps, key = { it.id }) { app ->
                AppTile(app, onClick = { onLaunch(app) })
            }
        }
    }
}

@Composable
private fun AppTile(app: AppEntry, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = DarkIOSpace.S1),
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
private fun AppIcon(app: AppEntry) {
    val px = with(LocalDensity.current) { DarkIOSize.IconTile.roundToPx() }
    val dpi = LocalConfiguration.current.densityDpi
    val icon by produceState<ImageBitmap?>(null, app.id) {
        value = withContext(Dispatchers.IO) {
            app.info?.getIcon(dpi)?.toBitmap(px, px)?.asImageBitmap()
        }
    }
    Box(Modifier.size(DarkIOSize.IconTile).clip(DarkIOShapes.Lg).background(DarkIOColors.Surface)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
