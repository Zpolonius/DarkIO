package app.darkio.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import app.darkio.R
import app.darkio.dashboard.CALORIES_PERMISSIONS
import app.darkio.dashboard.STEPS_PERMISSIONS
import app.darkio.dashboard.checkCaloriesPermission
import app.darkio.dashboard.checkStepsPermission
import app.darkio.dashboard.formatStepNumber
import app.darkio.dashboard.hasUsageStatsPermission
import app.darkio.dashboard.openUsageAccessSettings
import app.darkio.data.AccentChoice
import app.darkio.data.HomeCard
import app.darkio.data.STEP_GOAL_STEP
import app.darkio.data.Settings
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOSpace
import app.darkio.ui.theme.DarkIOType
import kotlinx.coroutines.launch

/**
 * Indstillinger (F13): accentfarve, kort til/fra, skridtmål og links. Ændringer gælder med det samme via [onChange].
 * Slås et kort til, kaldes [requestAccess] (standard: spørger kun hvis adgang mangler, N4); slås det fra, spørges aldrig.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    onChange: ((Settings) -> Settings) -> Unit,
    onOpenHidden: () -> Unit,
    modifier: Modifier = Modifier,
    onResetAll: () -> Unit = {},
    requestAccess: ((HomeCard) -> Unit)? = null,
) {
    val request = requestAccess ?: rememberAccessRequester()
    var confirmingReset by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DarkIOColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(start = DarkIOSpace.S4, end = DarkIOSpace.S4, top = DarkIOSpace.TopInset, bottom = DarkIOSpace.S5),
    ) {
        BasicText(stringResource(R.string.settings_title), style = DarkIOType.Title.copy(color = DarkIOColors.Ink))
        Spacer(Modifier.height(DarkIOSpace.S5))
        SectionLabel(R.string.settings_accent_label)
        Row(horizontalArrangement = Arrangement.spacedBy(DarkIOSpace.S3)) {
            AccentChoice.entries.forEach { choice ->
                val name = stringResource(choice.label)
                val selected = choice == settings.accent
                Box(
                    Modifier
                        .testTag("accent_${choice.name}")
                        .size(DarkIOSize.TouchTarget)
                        .clip(DarkIOShapes.Md)
                        .background(choice.color)
                        .then(if (selected) Modifier.border(DarkIOSize.SwatchRing, DarkIOColors.Ink, DarkIOShapes.Md) else Modifier)
                        .selectable(selected, role = Role.RadioButton) { onChange { it.copy(accent = choice) } }
                        .semantics { contentDescription = name },
                )
            }
        }
        Spacer(Modifier.height(DarkIOSpace.S5))
        SectionLabel(R.string.settings_cards_label)
        Group {
            HomeCard.entries.forEach { card ->
                val on = card in settings.cards
                Row(
                    Modifier
                        .testTag("toggle_${card.name}")
                        .fillMaxWidth()
                        .heightIn(min = DarkIOSize.TouchTarget)
                        .toggleable(on, role = Role.Switch) {
                            onChange { s -> s.copy(cards = if (it) s.cards + card else s.cards - card) }
                            if (it) request(card)
                        }
                        .padding(horizontal = DarkIOSpace.S4, vertical = DarkIOSpace.S2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        BasicText(stringResource(card.title), style = DarkIOType.Item.copy(color = DarkIOColors.Ink))
                        BasicText(stringResource(card.sub), style = DarkIOType.Data.copy(color = DarkIOColors.InkMuted))
                    }
                    Spacer(Modifier.width(DarkIOSpace.S3))
                    Toggle(on, card.name)
                }
            }
        }
        Spacer(Modifier.height(DarkIOSpace.S5))
        Group {
            LinkRow(R.string.settings_workout_program, DarkIOColors.InkMuted, Modifier.testTag("link_workout").semantics { disabled() })
            Row(
                Modifier.fillMaxWidth().heightIn(min = DarkIOSize.TouchTarget).padding(horizontal = DarkIOSpace.S4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(stringResource(R.string.settings_step_goal), Modifier.weight(1f), style = DarkIOType.Item.copy(color = DarkIOColors.Ink))
                StepButton(R.string.settings_step_minus, R.string.settings_step_goal_down, "stepGoalDown") {
                    onChange { it.withStepGoal(it.stepGoal - STEP_GOAL_STEP) }
                }
                BasicText(
                    formatStepNumber(settings.stepGoal),
                    Modifier.padding(horizontal = DarkIOSpace.S3).testTag("settingsStepGoal"),
                    style = DarkIOType.Data.copy(color = DarkIOColors.Ink),
                )
                StepButton(R.string.settings_step_plus, R.string.settings_step_goal_up, "stepGoalUp") {
                    onChange { it.withStepGoal(it.stepGoal + STEP_GOAL_STEP) }
                }
            }
            LinkRow(R.string.settings_background, DarkIOColors.InkMuted, Modifier.testTag("link_background").semantics { disabled() })
            LinkRow(R.string.hidden_apps_title, DarkIOColors.Ink, Modifier.clickable(role = Role.Button, onClick = onOpenHidden))
            LinkRow(
                R.string.settings_reset_all,
                DarkIOColors.Danger,
                Modifier.testTag("link_reset_all").clickable(role = Role.Button) { confirmingReset = true },
            )
        }
    }
    if (confirmingReset) {
        ResetConfirmationSheet(
            onConfirm = {
                confirmingReset = false
                onResetAll()
            },
            onDismiss = { confirmingReset = false },
        )
    }
    }
}

@Composable
private fun SectionLabel(res: Int) {
    BasicText(stringResource(res), style = DarkIOType.Label.copy(color = DarkIOColors.InkMuted))
    Spacer(Modifier.height(DarkIOSpace.S3))
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(DarkIOShapes.Lg).background(DarkIOColors.Surface)) { content() }
}

@Composable
private fun LinkRow(res: Int, color: Color, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = DarkIOSize.TouchTarget).padding(horizontal = DarkIOSpace.S4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(stringResource(res), style = DarkIOType.Item.copy(color = color))
    }
}

@Composable
private fun StepButton(glyph: Int, description: Int, tag: String, onClick: () -> Unit) {
    val label = stringResource(description)
    Box(
        Modifier
            .testTag(tag)
            .size(DarkIOSize.TouchTarget)
            .clip(DarkIOShapes.Md)
            .background(DarkIOColors.Bg)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(stringResource(glyph), style = DarkIOType.Button.copy(color = DarkIOColors.Ink))
    }
}

/** Slukket: ink-muted knop på line-strong-spor. Tændt: bg-knop på accent-spor. */
@Composable
private fun Toggle(on: Boolean, name: String) {
    Box(
        Modifier
            .testTag("track_$name")
            .size(DarkIOSize.ToggleTrackWidth, DarkIOSize.ToggleTrackHeight)
            .clip(DarkIOShapes.Pill)
            .background(if (on) DarkIOColors.Accent else DarkIOColors.LineStrong)
            .padding((DarkIOSize.ToggleTrackHeight - DarkIOSize.ToggleKnob) / 2),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .testTag("knob_$name")
                .size(DarkIOSize.ToggleKnob)
                .clip(DarkIOShapes.Pill)
                .background(if (on) DarkIOColors.Bg else DarkIOColors.InkMuted),
        )
    }
}

/** Spørger først om adgang, når kortet slås til: kalender, brugsdata og Health Connect. Givet adgang spørges ikke igen. */
@Composable
private fun rememberAccessRequester(): (HomeCard) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val calendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val health = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {}
    return { card ->
        when (card) {
            HomeCard.Calendar ->
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                    calendar.launch(Manifest.permission.READ_CALENDAR)
                }
            HomeCard.Focus -> if (!hasUsageStatsPermission(context)) openUsageAccessSettings(context)
            HomeCard.Steps -> scope.launch { if (!checkStepsPermission(context)) runCatching { health.launch(STEPS_PERMISSIONS) } }
            HomeCard.Calories -> scope.launch { if (!checkCaloriesPermission(context)) runCatching { health.launch(CALORIES_PERMISSIONS) } }
            else -> Unit
        }
    }
}

@Composable
private fun ResetConfirmationSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(DarkIOColors.ScrimSheet)
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .testTag("resetConfirmationSheet")
                .clip(DarkIOShapes.Sheet)
                .background(DarkIOColors.Bg)
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Bottom))
                .padding(DarkIOSpace.S4),
        ) {
            BasicText(
                stringResource(R.string.settings_reset_confirm_title),
                style = DarkIOType.Label.copy(color = DarkIOColors.InkMuted),
            )
            Spacer(Modifier.height(DarkIOSpace.S2))
            BasicText(
                stringResource(R.string.settings_reset_confirm_desc),
                style = DarkIOType.Body.copy(color = DarkIOColors.InkSoft),
            )
            Spacer(Modifier.height(DarkIOSpace.S4))
            SheetActionButton("confirmReset", R.string.settings_reset_confirm_action, DarkIOColors.Danger, onConfirm)
            Spacer(Modifier.height(DarkIOSpace.S2))
            SheetActionButton("cancelReset", R.string.settings_reset_cancel, DarkIOColors.Ink, onDismiss)
        }
    }
}

@Composable
private fun SheetActionButton(
    tag: String,
    labelRes: Int,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .testTag(tag)
            .fillMaxWidth()
            .heightIn(min = DarkIOSize.TouchTarget)
            .clip(DarkIOShapes.Md)
            .background(DarkIOColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = DarkIOSpace.S4),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(stringResource(labelRes), style = DarkIOType.Button.copy(color = color))
    }
}
