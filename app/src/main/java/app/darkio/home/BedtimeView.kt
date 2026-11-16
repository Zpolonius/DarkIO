package app.darkio.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.darkio.R
import app.darkio.dashboard.formatDate
import app.darkio.dashboard.formatEventTime
import app.darkio.dashboard.formatStepNumber
import app.darkio.dashboard.formatTime
import app.darkio.data.BedtimeSummary
import app.darkio.data.formatNextAlarm
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOShadow
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import java.time.ZonedDateTime

/**
 * Godnat-visning til side 1 ("i dag") når sengetid er aktiv (F24).
 * Viser stort dæmpet ur, dato, næste vækkeur, første aftale i morgen, dagens skridt og om træning er gennemført.
 * Ingen TipCard, ingen TypewriterText.
 */
@Composable
fun BedtimeView(
    summary: BedtimeSummary,
    now: ZonedDateTime,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("bedtimeView"),
        verticalArrangement = Arrangement.spacedBy(DarkIOSpace.S3),
    ) {
        // Stort dæmpet ur og dato i toppen
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DarkIOShapes.Lg)
                .background(DarkIOColors.SurfaceGlass)
                .border(DarkIOSize.LineWidth, DarkIOColors.Line, DarkIOShapes.Lg)
                .padding(horizontal = DarkIOSpace.S4, vertical = DarkIOSpace.S4)
                .testTag("card_Clock"),
        ) {
            BasicText(
                formatTime(now.toLocalDateTime()),
                style = DarkIOType.Clock.copy(color = DarkIOColors.InkSoft, shadow = DarkIOShadow.Text),
                modifier = Modifier.testTag("bedtimeClock"),
            )
            BasicText(
                formatDate(now.toLocalDateTime()),
                style = DarkIOType.Data.copy(color = DarkIOColors.InkMuted, shadow = DarkIOShadow.Text),
                modifier = Modifier.testTag("bedtimeDate"),
            )
        }

        // Statuskort: næste vækkeur, første aftale i morgen, skridt og træning
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DarkIOShapes.Lg)
                .background(DarkIOColors.SurfaceGlass)
                .border(DarkIOSize.LineWidth, DarkIOColors.Line, DarkIOShapes.Lg)
                .padding(horizontal = DarkIOSpace.S4, vertical = DarkIOSpace.S4)
                .testTag("bedtimeCard"),
            verticalArrangement = Arrangement.spacedBy(DarkIOSpace.S3),
        ) {
            // Næste vækkeur
            BasicText(
                formatNextAlarm(
                    triggerTimeMillis = summary.alarmTriggerMillis,
                    zoneId = now.zone,
                    noAlarmText = stringResource(R.string.bedtime_no_alarm),
                ),
                style = DarkIOType.Item.copy(color = DarkIOColors.Ink),
                modifier = Modifier.testTag("bedtimeAlarm"),
            )

            // Første aftale i morgen
            val event = summary.firstEventTomorrow
            if (event != null) {
                val timeStr = formatEventTime(
                    event = event,
                    nowMillis = now.toInstant().toEpochMilli(),
                    zoneId = now.zone,
                    allDayLabel = stringResource(R.string.calendar_all_day),
                    tomorrowLabel = { it },
                )
                val title = event.title.ifBlank { stringResource(R.string.calendar_untitled) }
                BasicText(
                    "$title · $timeStr",
                    style = DarkIOType.Item.copy(color = DarkIOColors.Ink),
                    modifier = Modifier.testTag("bedtimeCalendar"),
                )
            } else {
                BasicText(
                    stringResource(R.string.bedtime_free_tomorrow),
                    style = DarkIOType.Item.copy(color = DarkIOColors.InkMuted),
                    modifier = Modifier.testTag("bedtimeCalendar"),
                )
            }

            // Dagens skridt og træningsstatus
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val stepsText = if (summary.steps != null) {
                    stringResource(R.string.bedtime_steps, formatStepNumber(summary.steps))
                } else {
                    stringResource(R.string.bedtime_no_steps)
                }
                BasicText(
                    stepsText,
                    style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft),
                    modifier = Modifier.testTag("bedtimeSteps"),
                )

                val workoutText = if (summary.workoutCompleted) {
                    stringResource(R.string.bedtime_workout_completed)
                } else {
                    stringResource(R.string.bedtime_workout_not_completed)
                }
                BasicText(
                    workoutText,
                    style = DarkIOType.Data.copy(color = DarkIOColors.InkSoft),
                    modifier = Modifier.testTag("bedtimeWorkout"),
                )
            }
        }
    }
}
