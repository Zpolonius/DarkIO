package app.darkio.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import app.darkio.R
import app.darkio.data.AppEntry
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType

/** Skjulte, installerede apps med "Vis igen". Teksten om at skjul ikke er en lås (N7) står altid synligt. */
@Composable
fun HiddenAppsScreen(apps: List<AppEntry>, onShowAgain: (AppEntry) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .background(DarkIOColors.Bg)
            .padding(start = DarkIOSpace.S4, end = DarkIOSpace.S4, top = DarkIOSpace.TopInset)
            .testTag("hiddenApps"),
    ) {
        BasicText(stringResource(R.string.hidden_apps_title), style = DarkIOType.Title.copy(color = DarkIOColors.Ink))
        Spacer(Modifier.height(DarkIOSpace.S3))
        BasicText(stringResource(R.string.hidden_apps_note), style = DarkIOType.Body.copy(color = DarkIOColors.InkSoft))
        Spacer(Modifier.height(DarkIOSpace.S5))
        if (apps.isEmpty()) {
            BasicText(stringResource(R.string.hidden_apps_empty), style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(DarkIOSpace.S2)) {
            items(apps, key = { it.id }) { app ->
                Row(Modifier.fillMaxWidth().heightIn(min = DarkIOSize.TouchMin), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        app.label,
                        modifier = Modifier.weight(1f),
                        style = DarkIOType.Item.copy(color = DarkIOColors.Ink),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        Modifier
                            .heightIn(min = DarkIOSize.TouchMin)
                            .clip(DarkIOShapes.Md)
                            .background(DarkIOColors.Surface)
                            .clickable(role = Role.Button) { onShowAgain(app) }
                            .padding(horizontal = DarkIOSpace.S4),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(stringResource(R.string.hidden_apps_show_again), style = DarkIOType.Button.copy(color = DarkIOColors.Ink))
                    }
                }
            }
        }
    }
}
