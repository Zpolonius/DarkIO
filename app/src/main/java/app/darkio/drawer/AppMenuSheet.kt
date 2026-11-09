package app.darkio.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.darkio.R
import app.darkio.data.AppCategory
import app.darkio.data.AppEntry
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType

/** Bundark ved langt tryk på en app. [modifier] bestemmer højden (nederste halvdel); kaldstedet lukker arket efter valg. */
@Composable
fun AppMenuSheet(
    app: AppEntry,
    onDismiss: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onHide: () -> Unit,
    onSetCategory: (AppCategory) -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingCategory by rememberSaveable { mutableStateOf(false) }
    // Tastaturet ville dække arket (der ligger i nederste halvdel), så søgefeltet mister fokus.
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.clearFocus() }
    // Scrim lukker arket ved tryk udenfor; arket æder selv tryk, så de ikke når scrim. Ingen semantik, så TalkBack kun ser rækkerne.
    Box(
        Modifier.fillMaxSize().background(DarkIOColors.ScrimSheet)
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Column(
            modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .testTag("appMenu")
                .clip(DarkIOShapes.Sheet)
                .background(DarkIOColors.Bg)
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(vertical = DarkIOSpace.S2),
        ) {
            BasicText(
                app.label,
                style = DarkIOType.Label.copy(color = DarkIOColors.InkMuted),
                modifier = Modifier.padding(horizontal = DarkIOSpace.S4, vertical = DarkIOSpace.S2),
            )
            if (pickingCategory) {
                AppCategory.entries.filter { it != AppCategory.ALL }.forEach { category ->
                    MenuRow(stringResource(category.labelRes), DarkIOColors.Ink, Modifier.testTag("menuCategory_${category.slug}")) { onSetCategory(category) }
                }
            } else {
                MenuRow(stringResource(if (isFavorite) R.string.menu_unfavorite else R.string.menu_favorite), DarkIOColors.Ink, Modifier.testTag("menuFavorite"), onToggleFavorite)
                MenuRow(stringResource(R.string.menu_hide), DarkIOColors.Ink, Modifier.testTag("menuHide"), onHide)
                MenuRow(stringResource(R.string.menu_move_category), DarkIOColors.Ink, Modifier.testTag("menuMoveCategory")) { pickingCategory = true }
                MenuRow(stringResource(R.string.menu_app_info), DarkIOColors.Ink, Modifier.testTag("menuAppInfo"), onAppInfo)
                MenuRow(stringResource(R.string.menu_uninstall), DarkIOColors.Danger, Modifier.testTag("menuUninstall"), onUninstall)
            }
        }
    }
}

@Composable
private fun MenuRow(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().heightIn(min = DarkIOSize.TouchMin).clickable(role = Role.Button, onClick = onClick).padding(horizontal = DarkIOSpace.S4),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(text, style = DarkIOType.Item.copy(color = color))
    }
}
