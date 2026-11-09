package app.darkio.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSize

/** Sideprikker: aktiv side er en 20 × 6 pille i accent, de andre 6 × 6 i line-strong; hver har en 44 × 24 trykflade. */
@Composable
fun PageDots(labels: List<String>, current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, label ->
            val active = i == current
            Box(
                Modifier
                    .size(DarkIOSize.TouchMin, DarkIOSize.PageDotHitHeight)
                    .testTag("pageDot$i")
                    .semantics { contentDescription = label; selected = active }
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(if (active) DarkIOSize.PageDotActiveWidth else DarkIOSize.PageDot)
                        .height(DarkIOSize.PageDot)
                        .clip(DarkIOShapes.Pill)
                        .background(if (active) DarkIOColors.Accent else DarkIOColors.LineStrong),
                )
            }
        }
    }
}
