package app.darkio

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.onNodeWithTag
import app.darkio.dashboard.CardState
import app.darkio.dashboard.ClockCard
import app.darkio.dashboard.LoadedCard
import app.darkio.dashboard.dashboardCardCache
import app.darkio.data.AppEntry
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test

/** Kortenes hukommelse (N1): sidste værdi med det samme, uret uden indlæsning, naboer forudkomponeret. */
class DashboardCacheUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val monday = LocalDate.of(2026, 11, 23)

    @Test
    fun lastValueIsShownInFirstFrameAfterLeavingAndReturning() {
        var visible by mutableStateOf(true)
        var slowSecondLoad by mutableStateOf(false)
        rule.setContent {
            if (visible) {
                LoadedCard<String>(
                    load = { if (slowSecondLoad) awaitCancellation() else CardState.Content("seneste") },
                    cacheKey = "test_leave_return",
                    today = { monday },
                ) { BasicText(it) }
            }
        }
        rule.onNodeWithText("seneste").assertIsDisplayed()
        visible = false
        rule.waitForIdle()
        slowSecondLoad = true
        rule.mainClock.autoAdvance = false
        visible = true
        rule.mainClock.advanceTimeByFrame()
        // Hentningen hænger, så værdien kan kun komme fra hukommelsen.
        rule.onNodeWithText("seneste").assertIsDisplayed()
    }

    @Test
    fun valueFromYesterdayIsNotShownAsToday() {
        var day by mutableStateOf(monday)
        var visible by mutableStateOf(true)
        var hang by mutableStateOf(false)
        rule.setContent {
            if (visible) {
                LoadedCard<String>(
                    load = { if (hang) awaitCancellation() else CardState.Content("skridt i går") },
                    cacheKey = "test_midnight",
                    today = { day },
                ) { BasicText(it) }
            }
        }
        rule.onNodeWithText("skridt i går").assertIsDisplayed()
        visible = false
        rule.waitForIdle()
        hang = true
        day = monday.plusDays(1)
        visible = true
        rule.waitForIdle()
        rule.onNodeWithText("skridt i går").assertDoesNotExist()
    }

    @Test
    fun clockIsDrawnInFirstFrameWithoutLoading() {
        rule.mainClock.autoAdvance = false
        rule.setContent { ClockCard(now = { LocalDateTime.of(2026, 11, 23, 9, 5) }) }
        rule.onNodeWithText("09:05", substring = true).assertIsDisplayed()
    }

    @Test
    fun neighbourPageIsComposedAndLoadedBeforeSwipe() {
        val alfa = AppEntry("alfa/A#0", "Alfa")
        dashboardCardCache.clear() // ellers kan en tidligere test have fyldt "workout"
        rule.setContent {
            HomeScreen(
                apps = splitApps(listOf(alfa), emptySet()),
                hintSeen = true,
                onHintSeen = {}, onLaunch = {}, onSetHidden = { _, _ -> }, onSetFavorites = {},
                onSetCategory = { _, _ -> }, onAppInfo = {}, onUninstall = {},
            )
        }
        // Træningskortet ligger på side 2 (nabo til Today); sidens kort fylder hukommelsen uden swipe.
        rule.waitUntil(10_000) { dashboardCardCache.get<Any>("workout", LocalDate.now()) != null }
        rule.onNodeWithTag("homePager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithTag("card_Workout").assertIsDisplayed()
    }
}
