package app.darkio.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.darkio.R
import app.darkio.data.darkIoPrefs
import app.darkio.data.getDailyAndRecentTips
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import java.time.LocalDate
import kotlinx.coroutines.delay

/**
 * Side 3 ("byg"): Dagens tip i stort format, de to seneste tips, og en kopier-knap i nederste halvdel.
 */
@Composable
fun BuildPage(
    modifier: Modifier = Modifier,
    now: () -> LocalDate = LocalDate::now,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val tips = stringArrayResource(R.array.vibe_tips).toList()
    var todayTip by remember { mutableStateOf("") }
    var recentTips by remember { mutableStateOf(listOf<String>()) }
    var copied by remember { mutableStateOf(false) }
    val currentDate = remember { now() }

    LaunchedEffect(currentDate) {
        val data = getDailyAndRecentTips(context.darkIoPrefs, tips, currentDate)
        todayTip = data.todayTip
        recentTips = data.recentTips
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2500)
            copied = false
        }
    }

    Box(modifier.fillMaxSize()) {
        // Øverste zone (læsezonen): dagens tip i stort format + de to seneste tips
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = DarkIOSpace.S5)
                .padding(top = DarkIOSpace.TopInset),
        ) {
            // Kort 1: Dagens tip i stort format
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(DarkIOShapes.Lg)
                    .background(DarkIOColors.SurfaceGlass)
                    .border(DarkIOSize.LineWidth, DarkIOColors.Line, DarkIOShapes.Lg)
                    .padding(DarkIOSpace.S4),
            ) {
                BasicText(
                    "// dagens tip",
                    style = DarkIOType.Label.copy(color = DarkIOColors.InkMuted),
                )
                Spacer(Modifier.height(DarkIOSpace.S2))
                BasicText(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = DarkIOColors.Accent)) { append("$ ") }
                        append(todayTip)
                    },
                    style = DarkIOType.Metric.copy(color = DarkIOColors.Ink),
                    modifier = Modifier.testTag("buildTodayTip"),
                )
            }

            Spacer(Modifier.height(DarkIOSpace.S3))

            // Kort 2: De to seneste tips
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(DarkIOShapes.Lg)
                    .background(DarkIOColors.SurfaceGlass)
                    .border(DarkIOSize.LineWidth, DarkIOColors.Line, DarkIOShapes.Lg)
                    .padding(DarkIOSpace.S4),
            ) {
                BasicText(
                    "// seneste tips",
                    style = DarkIOType.Label.copy(color = DarkIOColors.InkMuted),
                )
                Spacer(Modifier.height(DarkIOSpace.S2))
                Column(Modifier.fillMaxWidth().testTag("buildRecentTips")) {
                    recentTips.forEachIndexed { index, tip ->
                        if (index > 0) Spacer(Modifier.height(DarkIOSpace.S2))
                        BasicText(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = DarkIOColors.InkMuted)) { append("$ ") }
                                append(tip)
                            },
                            style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft),
                            modifier = Modifier.testTag("recentTip$index"),
                        )
                    }
                }
            }
        }

        // Nederste zone (tommelfingerzone): Kopier-knap i nederste halvdel med trykflade på mindst 48 dp
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = DarkIOSpace.S5)
                .padding(bottom = 92.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = DarkIOSize.TouchTarget)
                    .height(DarkIOSize.TouchTarget)
                    .clip(DarkIOShapes.Md)
                    .background(DarkIOColors.SurfaceGlass)
                    .border(DarkIOSize.LineWidth, DarkIOColors.Line, DarkIOShapes.Md)
                    .clickable(
                        role = Role.Button,
                        onClick = {
                            if (todayTip.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(todayTip))
                                copied = true
                            }
                        },
                    )
                    .testTag("copyTipButton"),
                contentAlignment = Alignment.Center,
            ) {
                val label = if (copied) {
                    "// ${stringResource(R.string.tip_copied)}_"
                } else {
                    stringResource(R.string.tip_copy)
                }
                BasicText(
                    label,
                    style = DarkIOType.Button.copy(color = if (copied) DarkIOColors.Accent else DarkIOColors.Ink),
                )
            }
        }
    }
}
