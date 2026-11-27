package app.darkio.dashboard

import android.media.session.MediaController
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.darkio.R
import app.darkio.data.AppEntry
import app.darkio.data.MediaNow
import app.darkio.data.NowPlaying
import app.darkio.data.mediaNowFlow
import app.darkio.data.openMediaAccessSettings
import app.darkio.drawer.AppIcon
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Medier-siden (F25): sidens navn og "Spiller nu" som tekst øverst; medie-apps og afspilningsknapper nederst, under skærmens midte (F15).
 * Siden er komponeret, når den vises eller er nabo til den viste side (pageren forudkomponerer); sessionen læses kun, mens hjem er RESUMED. [nowOverride] og [onGrantAccess] bruges af tests.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaPage(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    nowOverride: MediaNow? = null,
    onGrantAccess: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableStateOf(nowOverride) }
    LaunchedEffect(lifecycle, nowOverride) {
        if (nowOverride != null) {
            now = nowOverride
        } else {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { mediaNowFlow(context).collect { now = it } }
        }
    }
    val scope = rememberCoroutineScope()
    val control = { block: MediaController.TransportControls.() -> Unit ->
        val controller = now?.controller
        scope.launch(Dispatchers.IO) { controller?.transportControls?.block() }
        Unit
    }
    Column(
        modifier.fillMaxSize().padding(horizontal = DarkIOSpace.S4).padding(top = DarkIOSpace.TopInset, bottom = DarkIOSpace.S1),
        verticalArrangement = Arrangement.spacedBy(DarkIOSpace.S1),
    ) {
        BasicText(
            stringResource(R.string.page_media),
            style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft),
            modifier = Modifier.testTag("page3").clip(DarkIOShapes.Pill).background(DarkIOColors.SurfaceGlass).padding(horizontal = DarkIOSpace.S3, vertical = DarkIOSpace.S1),
        )
        Column(
            Modifier.fillMaxWidth().testTag("mediaCard").clip(DarkIOShapes.Lg).background(DarkIOColors.SurfaceGlass).border(DarkIOSize.LineStroke, DarkIOColors.Line, DarkIOShapes.Lg).padding(DarkIOSpace.S3),
        ) {
            BasicText(stringResource(R.string.media_now_playing), style = DarkIOType.Label.copy(color = DarkIOColors.InkMuted))
            when (val state = now?.state) {
                is NowPlaying.Playing -> {
                    BasicText(state.title, style = DarkIOType.Title.copy(color = DarkIOColors.Ink), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("mediaTitle"))
                    BasicText(
                        listOf(state.artist, state.appLabel).filter { it.isNotBlank() }.joinToString(" · "),
                        style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("mediaSource"),
                    )
                }
                NowPlaying.Idle -> BasicText(stringResource(R.string.media_idle), style = DarkIOType.Body.copy(color = DarkIOColors.InkSoft), modifier = Modifier.testTag("mediaIdle"))
                NowPlaying.NoAccess -> BasicText(stringResource(R.string.media_no_access), style = DarkIOType.Body.copy(color = DarkIOColors.InkSoft), modifier = Modifier.testTag("mediaNoAccess"))
                null -> {}
            }
        }
        Spacer(Modifier.weight(1f))
        BasicText(stringResource(R.string.media_apps_label), style = DarkIOType.Label.copy(color = DarkIOColors.InkSoft))
        if (apps.isEmpty()) {
            BasicText(stringResource(R.string.media_apps_empty), style = DarkIOType.Body.copy(color = DarkIOColors.InkSoft), modifier = Modifier.testTag("mediaEmptyHint"))
        }
        LazyRow(
            Modifier.fillMaxWidth().testTag("mediaApps"),
            horizontalArrangement = Arrangement.spacedBy(DarkIOSpace.S3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(apps, key = { it.id }) { app ->
                Box(
                    Modifier
                        .testTag("mediaApp_${app.id}")
                        .semantics { contentDescription = app.label }
                        .combinedClickable(role = Role.Button, onLongClickLabel = stringResource(R.string.drawer_app_menu), onLongClick = { onMenu(app) }, onClick = { onLaunch(app) }),
                ) { AppIcon(app, DarkIOSize.IconTileSm) }
            }
            item(key = "add") {
                val add = stringResource(R.string.media_add_app)
                Box(
                    Modifier
                        .size(DarkIOSize.IconTileSm)
                        .testTag("mediaAdd")
                        .border(DarkIOSize.LineStroke, DarkIOColors.LineStrong, DarkIOShapes.Lg)
                        .semantics { contentDescription = add }
                        .clickable(role = Role.Button, onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { BasicText("+", style = DarkIOType.Item.copy(color = DarkIOColors.InkSoft)) }
            }
        }
        Spacer(Modifier.height(DarkIOSpace.S1))
        when (val state = now?.state) {
            NowPlaying.NoAccess -> PillButton(stringResource(R.string.media_grant_access), "mediaGrant", Modifier.fillMaxWidth()) {
                if (onGrantAccess != null) onGrantAccess() else openMediaAccessSettings(context)
            }
            is NowPlaying.Playing -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DarkIOSpace.S2)) {
                PillButton(stringResource(R.string.media_previous), "mediaPrev", Modifier.weight(1f)) { control { skipToPrevious() } }
                PillButton(stringResource(if (state.isPlaying) R.string.media_pause else R.string.media_play), "mediaPlayPause", Modifier.weight(1f)) {
                    control { if (state.isPlaying) pause() else play() }
                }
                PillButton(stringResource(R.string.media_next), "mediaNext", Modifier.weight(1f)) { control { skipToNext() } }
            }
            else -> {}
        }
    }
}

@Composable
private fun PillButton(text: String, tag: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .testTag(tag)
            .heightIn(min = DarkIOSize.TouchMin)
            .clip(DarkIOShapes.Pill)
            .background(DarkIOColors.SurfaceGlass)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = DarkIOSpace.S4, vertical = DarkIOSpace.S2),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = DarkIOType.Button.copy(color = DarkIOColors.Ink)) }
}
