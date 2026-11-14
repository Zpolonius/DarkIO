package app.darkio

import android.view.KeyEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.dashboard.StepsCard
import app.darkio.data.AccentChoice
import app.darkio.data.AppEntry
import app.darkio.data.HomeCard
import app.darkio.data.Settings
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import app.darkio.settings.SettingsScreen
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** F13 og F11: indstillingsskærmen, kort til/fra, accent, skridtmål og langt tryk på tom flade. */
class SettingsUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val title = context.getString(R.string.settings_title)
    private val installed = listOf(AppEntry("a/A#0", "Alfa"))
    private val requested = mutableListOf<HomeCard>()
    private var current = Settings()
    private var homeSignal by mutableIntStateOf(0)
    private var resetCalled = false

    @After
    fun resetAccent() {
        DarkIOColors.Accent = DarkIOColors.AccentLime
    }

    private fun show(initial: Settings = Settings()) = rule.setContent {
        var s by rememberSaveable { mutableStateOf(Triple(initial.cards, initial.accent, initial.stepGoal)) }
        current = Settings(s.second, s.first, s.third)
        HomeScreen(
            apps = splitApps(installed, emptySet()),
            hintSeen = true,
            onHintSeen = {},
            onLaunch = {},
            onSetHidden = { _, _ -> },
            onSetFavorites = {},
            onSetCategory = { _, _ -> },
            onAppInfo = {},
            onUninstall = {},
            homeSignal = homeSignal,
            settings = current,
            onSettingsChange = { change ->
                val n = change(current)
                DarkIOColors.Accent = n.accent.color
                s = Triple(n.cards, n.accent, n.stepGoal)
            },
            onResetAll = { resetCalled = true },
            requestAccess = { requested += it },
        )
    }

    private fun longPressMargin() = rule.onNodeWithTag("homePager").performTouchInput {
        longClick(Offset(10.dp.toPx(), height / 2f))
    }

    private fun back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    private fun cardTag(c: HomeCard) = if (c == HomeCard.Focus) "focusCard" else "card_${c.name}"

    private fun pixel(tag: String, xFraction: Float): Int {
        val img = rule.onNodeWithTag(tag, useUnmergedTree = true).captureToImage()
        val px = IntArray(img.width * img.height)
        img.readPixels(px, 0, 0, img.width, img.height)
        return px[(img.height / 2) * img.width + (img.width * xFraction).toInt()]
    }

    @Test
    fun longPressOnEmptyHomeOpensSettingsButTapAndCardsDoNot() {
        show()
        rule.onNodeWithTag("homePager").performTouchInput { click(Offset(10.dp.toPx(), height / 2f)) }
        rule.onNodeWithText(title).assertDoesNotExist()
        rule.onNodeWithTag("focusCard").performTouchInput { longClick() }
        rule.onNodeWithText(title).assertDoesNotExist()
        longPressMargin()
        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.settings_accent_label)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.settings_cards_label)).assertIsDisplayed()
    }

    @Test
    fun settingsNotReachableWhileDrawerOpen() {
        show()
        rule.onNodeWithTag("homePager").performTouchInput { swipeUp() }
        rule.onNodeWithTag("drawerGrid").assertIsDisplayed()
        longPressMargin()
        rule.onNodeWithText(title).assertDoesNotExist()
    }

    @Test
    fun existingInstallWithNoSavedSettingsShowsAllCardsAndLimeAccent() {
        show()
        listOf(HomeCard.Clock, HomeCard.Calendar, HomeCard.Focus, HomeCard.Tip).forEach {
            rule.onNodeWithTag(cardTag(it)).assertIsDisplayed()
        }
        assertEquals(DarkIOColors.AccentLime, DarkIOColors.Accent)
        longPressMargin()
        HomeCard.entries.forEach { rule.onNodeWithTag("toggle_${it.name}").assertIsOn() }
        rule.onNodeWithTag("accent_Lime").assertIsSelected()
        rule.onNodeWithTag("settingsStepGoal").assertTextContains("8.000", substring = true)
    }

    @Test
    fun everyCardToggleHidesAndShowsItsCard() {
        show()
        HomeCard.entries.forEach { card ->
            if (card == HomeCard.Workout) rule.onNodeWithTag("homePager").performTouchInput { swipeLeft() }
            rule.onNodeWithTag(cardTag(card)).assertIsDisplayed()
            longPressMargin()
            rule.onNodeWithTag("toggle_${card.name}").performClick()
            rule.onNodeWithTag("toggle_${card.name}").assertIsOff()
            back()
            rule.onNodeWithTag(cardTag(card)).assertDoesNotExist()
            longPressMargin()
            rule.onNodeWithTag("toggle_${card.name}").performClick()
            rule.onNodeWithTag("toggle_${card.name}").assertIsOn()
            back()
            rule.onNodeWithTag(cardTag(card)).assertIsDisplayed()
        }
    }

    @Test
    fun toggleVisualsFollowDesign() {
        show(Settings(cards = setOf(HomeCard.Clock)))
        longPressMargin()
        rule.onNodeWithTag("track_Clock", useUnmergedTree = true).assertWidthIsEqualTo(DarkIOSize.ToggleTrackWidth)
        rule.onNodeWithTag("toggle_Calendar").assertHeightIsAtLeast(DarkIOSize.TouchMin)
        // Tændt: accent-spor, bg-knop. Slukket: line-strong-spor, ink-muted-knop.
        assertEquals(DarkIOColors.Accent.toArgb(), pixel("track_Clock", 0.04f))
        assertEquals(DarkIOColors.Bg.toArgb(), pixel("knob_Clock", 0.5f))
        assertEquals(DarkIOColors.LineStrong.toArgb(), pixel("track_Calendar", 0.04f))
        assertEquals(DarkIOColors.InkMuted.toArgb(), pixel("knob_Calendar", 0.5f))
    }

    @Test
    fun everyAccentSwatchChangesAccentImmediately() {
        show(Settings(cards = setOf(HomeCard.Clock)))
        longPressMargin()
        AccentChoice.entries.forEach { choice ->
            val swatch = rule.onNodeWithTag("accent_${choice.name}")
            swatch.assertHeightIsAtLeast(DarkIOSize.TouchTarget)
            swatch.assertWidthIsEqualTo(DarkIOSize.TouchTarget)
            swatch.performClick()
            assertEquals(choice.name, choice.color, DarkIOColors.Accent)
            swatch.assertIsSelected()
            assertEquals(choice, current.accent)
            AccentChoice.entries.filter { it != choice }.forEach { rule.onNodeWithTag("accent_${it.name}").assertIsNotSelected() }
        }
    }

    @Test
    fun stepGoalButtonsChangeGoalShownOnStepsCardAndStopAtLimit() {
        rule.setContent {
            var goal by rememberSaveable { mutableStateOf(Settings().stepGoal) }
            Column(Modifier.fillMaxSize()) {
                StepsCard(hasPermission = { true }, loadSteps = { 100L }, goal = goal)
                SettingsScreen(Settings(stepGoal = goal), { change -> goal = change(Settings(stepGoal = goal)).stepGoal }, {}, {}, Modifier.fillMaxWidth().weight(1f), requestAccess = {})
            }
        }
        rule.onNodeWithTag("stepsCount").assertTextContains("8.000", substring = true)
        rule.onNodeWithTag("stepGoalUp").performClick()
        rule.onNodeWithTag("settingsStepGoal").assertTextContains("8.500", substring = true)
        rule.onNodeWithTag("stepsCount").assertTextContains("8.500", substring = true)
        repeat(2) { rule.onNodeWithTag("stepGoalDown").performClick() }
        rule.onNodeWithTag("stepsCount").assertTextContains("7.500", substring = true)
        rule.onNodeWithTag("stepGoalUp").assertHeightIsAtLeast(DarkIOSize.TouchTarget).assertWidthIsAtLeast(DarkIOSize.TouchTarget)
        repeat(14) { rule.onNodeWithTag("stepGoalDown").performClick() }
        rule.onNodeWithTag("settingsStepGoal").assertTextContains("1.000", substring = true)
    }

    @Test
    fun enablingCardsThatNeedAccessRequestsItOnlyWhenSwitchedOn() {
        val needAccess = listOf(HomeCard.Calendar, HomeCard.Focus, HomeCard.Steps, HomeCard.Calories)
        show(Settings(cards = emptySet()))
        longPressMargin()
        needAccess.forEach { rule.onNodeWithTag("toggle_${it.name}").performClick() }
        assertEquals(needAccess, requested)
        requested.clear()
        needAccess.forEach { rule.onNodeWithTag("toggle_${it.name}").performClick() }
        assertEquals(emptyList<HomeCard>(), requested)
    }

    @Test
    fun hiddenAppsLinkBackAndHomeButtonNavigateAsDesigned() {
        show()
        longPressMargin()
        rule.onNodeWithTag("link_background").assertIsNotEnabled()
        rule.onNodeWithText(context.getString(R.string.hidden_apps_title)).performClick()
        rule.onNodeWithTag("hiddenApps").assertIsDisplayed()
        back()
        rule.onNodeWithText(title).assertIsDisplayed()
        back()
        rule.onNodeWithText(title).assertDoesNotExist()
        rule.onNodeWithTag("homePager").assertIsDisplayed()
        // Hjem-knappen lukker både Indstillinger og Skjulte apps.
        longPressMargin()
        rule.onNodeWithText(context.getString(R.string.hidden_apps_title)).performClick()
        rule.runOnIdle { homeSignal++ }
        rule.onNodeWithTag("hiddenApps").assertDoesNotExist()
        rule.onNodeWithText(title).assertDoesNotExist()
        rule.onNodeWithTag("homePager").assertIsDisplayed()
    }

    @Test
    fun resetAllActionRequiresConfirmationAndCanBeCancelled() {
        show()
        longPressMargin()
        val link = rule.onNodeWithTag("link_reset_all")
        link.assertHeightIsAtLeast(DarkIOSize.TouchTarget)
        link.performClick()
        val confirm = rule.onNodeWithTag("confirmReset")
        val cancel = rule.onNodeWithTag("cancelReset")
        confirm.assertIsDisplayed()
        confirm.assertHeightIsAtLeast(DarkIOSize.TouchTarget)
        assertEquals(DarkIOColors.Surface.toArgb(), pixel("confirmReset", 0.05f))
        cancel.assertIsDisplayed()
        cancel.assertHeightIsAtLeast(DarkIOSize.TouchTarget)
        cancel.performClick()
        confirm.assertDoesNotExist()
        assertFalse(resetCalled)

        link.performClick()
        confirm.assertIsDisplayed()
        back()
        confirm.assertDoesNotExist()
        assertFalse(resetCalled)

        link.performClick()
        confirm.assertIsDisplayed()
        confirm.performClick()
        confirm.assertDoesNotExist()
        assertTrue(resetCalled)
        rule.onNodeWithText(title).assertDoesNotExist()
        rule.onNodeWithTag("homePager").assertIsDisplayed()
    }

    @Test
    fun workoutProgramLinkBackAndHomeButtonNavigateAsDesigned() {
        show()
        longPressMargin()
        rule.onNodeWithTag("link_workout").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("workoutProgram").fetchSemanticsNodes().isNotEmpty() }
        back()
        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithTag("link_workout").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("workoutProgram").fetchSemanticsNodes().isNotEmpty() }
        rule.runOnIdle { homeSignal++ }
        rule.onNodeWithTag("workoutProgram").assertDoesNotExist()
        rule.onNodeWithText(title).assertDoesNotExist()
        rule.onNodeWithTag("homePager").assertIsDisplayed()
    }
}
