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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import app.darkio.R
import app.darkio.data.AppEntry
import app.darkio.data.AppLists
import app.darkio.drawer.AppDrawer
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
) {
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    val openDrawer = {
        if (!hintSeen) onHintSeen()
        drawerOpen = true
    }
    var hiddenOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }
    BackHandler(enabled = hiddenOpen) { hiddenOpen = false }
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
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Bottom))
                .onVerticalSwipe { if (it < -swipePx) openDrawer() else if (it > swipePx) drawerOpen = false },
        ) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = DarkIOSpace.S5, vertical = DarkIOSpace.S5)) {
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
                onHide = { onSetHidden(it, true) },
                onShowHidden = { hiddenOpen = true },
                onDismiss = { drawerOpen = false },
                modifier = Modifier.heightIn(max = maxHeight / 2),
            )
        }
    }
}
