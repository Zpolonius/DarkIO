package app.darkio.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.darkio.R
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

/** Et korts tre tilstande: indhold, mangler adgang, eller fejl (N3). */
sealed interface CardState<out T> {
    data class Content<T>(val value: T) : CardState<T>
    data object NeedsAccess : CardState<Nothing>
    data object Error : CardState<Nothing>
}

/** Kører [load]; en fejl bliver til [CardState.Error] så resten af skærmen er upåvirket. Annullering forplanter sig. */
suspend fun <T> loadCard(load: suspend () -> CardState<T>): CardState<T> =
    try {
        load()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        CardState.Error
    }

/**
 * Kort med fælles ramme. Data hentes kun mens hjemskærmen er synlig (STARTED); der kører intet, når den er skjult (N10).
 * Er [refreshAfterMs] sat, hentes der igen efter så mange ms, så længe skærmen er synlig.
 */
@Composable
fun <T> LoadedCard(
    load: suspend () -> CardState<T>,
    modifier: Modifier = Modifier,
    refreshAfterMs: (() -> Long)? = null,
    onGrantAccess: () -> Unit = {},
    needsAccessTitle: String? = null,
    needsAccessExplanation: String? = null,
    reloadTrigger: Any? = null,
    content: @Composable (T) -> Unit,
) {
    var state by remember { mutableStateOf<CardState<T>?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val currentLoad by rememberUpdatedState(load)
    val currentRefresh by rememberUpdatedState(refreshAfterMs)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(attempt, reloadTrigger) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                state = loadCard(currentLoad)
                delay(currentRefresh?.invoke() ?: break)
            }
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(DarkIOShapes.Lg)
            .background(DarkIOColors.SurfaceGlass)
            .border(DarkIOSize.LineWidth, DarkIOColors.Line, DarkIOShapes.Lg)
            .padding(DarkIOSpace.S4),
    ) {
        when (val s = state) {
            is CardState.Content -> content(s.value)
            CardState.NeedsAccess -> {
                if (needsAccessTitle != null) {
                    BasicText(
                        needsAccessTitle,
                        modifier = Modifier.padding(bottom = DarkIOSpace.S1),
                        style = DarkIOType.Item.copy(color = DarkIOColors.Ink),
                    )
                }
                if (needsAccessExplanation != null) {
                    BasicText(
                        needsAccessExplanation,
                        modifier = Modifier.padding(bottom = DarkIOSpace.S2),
                        style = DarkIOType.Data.copy(color = DarkIOColors.InkMuted),
                    )
                }
                CardMessage(null, stringResource(R.string.card_grant_access), onGrantAccess)
            }
            CardState.Error -> CardMessage(stringResource(R.string.card_error), stringResource(R.string.card_retry)) { attempt++ }
            null -> Unit
        }
    }
}

@Composable
private fun ColumnScope.CardMessage(message: String?, action: String, onClick: () -> Unit) {
    if (message != null) BasicText(message, style = DarkIOType.Data.copy(color = DarkIOColors.Danger))
    BasicText(
        action,
        modifier = Modifier
            .heightIn(min = DarkIOSize.TouchMin)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = DarkIOSpace.S2)
            .align(Alignment.Start),
        style = DarkIOType.Button.copy(color = DarkIOColors.Accent),
    )
}
