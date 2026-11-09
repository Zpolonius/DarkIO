package app.darkio.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import app.darkio.R
import app.darkio.data.AppEntry
import app.darkio.data.DOCK_SIZE
import app.darkio.drawer.AppIcon
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import kotlin.math.abs
import kotlin.math.roundToInt

/** Dock med faste felter. Tryk starter appen, langt tryk åbner menuen, og træk flytter en favorit til et andet felt via [onMove] (fra, til). */
@Composable
fun Dock(
    slots: List<AppEntry?>,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onMove: (Int, Int) -> Unit,
    tag: String = "dock",
    emptyLabel: Int = R.string.dock_empty_slot,
    label: Int = R.string.dock_label,
    dragAfterLongPress: Boolean = false,
) {
    val group = stringResource(label)
    Row(
        Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = group }.clip(DarkIOShapes.Dock).background(DarkIOColors.SurfaceGlass).padding(DarkIOSpace.S3),
    ) {
        slots.forEachIndexed { index, app ->
            if (app == null) {
                val empty = stringResource(emptyLabel)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(DarkIOSize.IconTile).border(DarkIOSize.LineStroke, DarkIOColors.LineStrong, DarkIOShapes.Lg)
                            .semantics { contentDescription = empty }.testTag("${tag}Empty_$index"),
                    )
                }
            } else {
                DockItem(app, index, tag, dragAfterLongPress, onLaunch, onMenu, onMove, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DockItem(app: AppEntry, index: Int, tag: String, dragAfterLongPress: Boolean, onLaunch: (AppEntry) -> Unit, onMenu: (AppEntry) -> Unit, onMove: (Int, Int) -> Unit, modifier: Modifier) {
    var dragX by remember { mutableFloatStateOf(0f) }
    // Dock og onMove skifter, når to favoritter bytter plads; pointerInput må ikke beholde de gamle.
    val latestMove by rememberUpdatedState(onMove)
    val latestMenu by rememberUpdatedState(onMenu)
    var longPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val menuLabel = stringResource(R.string.drawer_app_menu)
    val left = stringResource(R.string.dock_move_left)
    val right = stringResource(R.string.dock_move_right)
    // Træk og tryk sidder uden på graphicsLayer, så berøringen ikke flytter sig med ikonet. Feltet er lige så bredt som ét af de fire felter.
    Box(
        modifier
            .zIndex(if (dragX != 0f) 1f else 0f)
            .testTag("${tag}_${app.id}")
            .semantics {
                contentDescription = app.label
                if (dragAfterLongPress) onLongClick(menuLabel) { latestMenu(app); true }
                // Samme flytning som træk, for TalkBack og andre uden træk.
                customActions = listOfNotNull(
                    if (index > 0) CustomAccessibilityAction(left) { latestMove(index, index - 1); true } else null,
                    if (index < DOCK_SIZE - 1) CustomAccessibilityAction(right) { latestMove(index, index + 1); true } else null,
                )
            }
            .pointerInput(app.id, index, dragAfterLongPress) {
                val end = {
                    latestMove(index, (index + (dragX / size.width).roundToInt()).coerceIn(0, DOCK_SIZE - 1))
                    dragX = 0f
                }
                if (dragAfterLongPress) {
                    // Rækken ligger i pageren: et almindeligt swipe skal skifte side. Langt tryk og træk flytter; langt tryk uden træk åbner menuen.
                    // Slippes fingeren efter langt tryk, følger et tryk-event; [longPressed] holder det fra at starte appen.
                    val done = { scope.launch { delay(150); longPressed = false } }
                    // Efter langt tryk er der ingen slop: en lille rystelse med fingeren er stadig et langt tryk, ikke et træk.
                    val still = { abs(dragX) < viewConfiguration.touchSlop }
                    detectDragGesturesAfterLongPress(
                        onDragStart = { longPressed = true },
                        onDragEnd = { if (still()) { dragX = 0f; latestMenu(app) } else end(); done() },
                        onDragCancel = { if (still()) latestMenu(app); dragX = 0f; done() },
                        onDrag = { change, amount -> change.consume(); dragX += amount.x },
                    )
                } else {
                    detectHorizontalDragGestures(
                        onDragEnd = end,
                        onDragCancel = { dragX = 0f },
                        onHorizontalDrag = { change, amount -> change.consume(); dragX += amount },
                    )
                }
            }
            .combinedClickable(role = Role.Button, onLongClickLabel = menuLabel, onLongClick = if (dragAfterLongPress) null else { { onMenu(app) } }, onClick = { if (!longPressed) onLaunch(app) }),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { translationX = dragX }) { AppIcon(app) }
    }
}
