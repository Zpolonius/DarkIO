package app.darkio.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.darkio.R
import app.darkio.data.darkIoPrefs
import app.darkio.data.getOrChooseDailyTip
import app.darkio.ui.TypewriterText
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import java.time.LocalDate
import kotlinx.coroutines.delay

/**
 * TipCard displayed on Page 1 ("i dag") below ClockCard.
 * Shows "$ " in DarkIOColors.Accent followed by the tip in DarkIOColors.Ink.
 * Long press copies tip to clipboard and shows confirmation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TipCard(modifier: Modifier = Modifier, now: () -> LocalDate = LocalDate::now) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val tips = stringArrayResource(R.array.vibe_tips).toList()
    val tipCopiedText = stringResource(R.string.tip_copied)

    var copiedMessage by remember { mutableStateOf(false) }

    LaunchedEffect(copiedMessage) {
        if (copiedMessage) {
            delay(2500)
            copiedMessage = false
        }
    }

    LoadedCard(
        load = {
            val index = getOrChooseDailyTip(context.darkIoPrefs, tips, now())
            val tip = tips.getOrNull(index) ?: tips.firstOrNull() ?: ""
            CardState.Content(tip)
        },
        modifier = modifier,
        refreshAfterMs = { 86_400_000L }
    ) { tip ->
        Column(
            modifier = Modifier
                .combinedClickable(
                    role = Role.Button,
                    onClick = {},
                    onLongClick = {
                        clipboardManager.setText(AnnotatedString(tip))
                        copiedMessage = true
                    }
                )
        ) {
            TypewriterText(
                text = tip,
                prefix = "$ ",
                prefixColor = DarkIOColors.Accent,
                style = DarkIOType.Data.copy(color = DarkIOColors.Ink),
                now = now,
            )
            if (copiedMessage) {
                Spacer(Modifier.height(DarkIOSpace.S2))
                BasicText(
                    "// ${tipCopiedText}_",
                    style = DarkIOType.Data.copy(color = DarkIOColors.Accent),
                )
            }
        }
    }
}
