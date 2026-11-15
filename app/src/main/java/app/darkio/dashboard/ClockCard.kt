package app.darkio.dashboard

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShadow
import app.darkio.ui.theme.DarkIOType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DANISH = Locale.forLanguageTag("da")

fun formatTime(t: LocalDateTime): String = DateTimeFormatter.ofPattern("HH:mm").format(t)

/** Fx "søndag 8. november". */
fun formatDate(t: LocalDateTime, locale: Locale = DANISH): String =
    DateTimeFormatter.ofPattern("EEEE d. MMMM", locale).format(t)

/** Millisekunder til næste minutskift (aldrig 0). */
fun millisToNextMinute(t: LocalDateTime): Long = 60_000L - (t.second * 1_000L + t.nano / 1_000_000)

/** Ur og dato; skifter ved minutskift mens hjemskærmen er synlig. [now] kan udskiftes i tests. */
@Composable
fun ClockCard(modifier: Modifier = Modifier, now: () -> LocalDateTime = LocalDateTime::now) {
    LoadedCard(
        load = { CardState.Content(now()) },
        modifier = modifier,
        refreshAfterMs = { millisToNextMinute(now()) },
    ) { t ->
        BasicText(
            buildAnnotatedString {
                append(formatTime(t))
                withStyle(SpanStyle(color = DarkIOColors.Accent)) { append("_") }
            },
            style = DarkIOType.Clock.copy(color = DarkIOColors.Ink, shadow = DarkIOShadow.Text),
        )
        BasicText(formatDate(t), style = DarkIOType.Data.copy(color = DarkIOColors.InkMuted, shadow = DarkIOShadow.Text))
    }
}
