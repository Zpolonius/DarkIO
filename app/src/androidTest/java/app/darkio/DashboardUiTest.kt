package app.darkio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.dashboard.CardState
import app.darkio.dashboard.ClockCard
import app.darkio.dashboard.LoadedCard
import app.darkio.home.HomeScreen
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DashboardUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val retry = context.getString(R.string.card_retry)
    private val error = context.getString(R.string.card_error)

    @Test
    fun clockAndDateAreShownOnHome() {
        rule.setContent { HomeScreen(apps = emptyList(), hintSeen = true, onHintSeen = {}, onLaunch = {}) }
        val today = java.time.LocalDate.now()
        val date = app.darkio.dashboard.formatDate(today.atStartOfDay(), java.util.Locale.forLanguageTag("da"))
        rule.waitUntil(5_000) { rule.onAllNodesWithTextCount(date) > 0 }
    }

    @Test
    fun clockChangesAtMinuteBoundaryWithoutReload() {
        val start = LocalDateTime.of(2026, 11, 8, 10, 0, 59, 900_000_000)
        val t0 = System.nanoTime()
        val clock = { start.plusNanos(System.nanoTime() - t0) }
        rule.setContent { ClockCard(now = clock) }
        rule.onNodeWithText("10:00", substring = true).assertExists()
        rule.waitUntil(5_000) { rule.onAllNodesWithTextCount("10:01") > 0 }
    }

    @Test
    fun failingCardShowsErrorRetryWorksAndRestOfScreenWorks() {
        var calls = 0
        var clicked = 0
        rule.setContent {
            Column {
                LoadedCard<String>(load = { calls++; if (calls == 1) error("boom") else CardState.Content("ok") }) {
                    BasicText(it)
                }
                BasicText("rest", Modifier.clickable { clicked++ })
            }
        }
        rule.onNodeWithText(error, substring = true).assertExists()
        rule.onNodeWithText("rest").performClick()
        assertEquals(1, clicked)
        rule.onNodeWithText(retry).performClick()
        rule.onNodeWithText("ok").assertExists()
    }

    @Test
    fun needsAccessShowsGrantButton() {
        var asked = 0
        rule.setContent {
            LoadedCard<String>(load = { CardState.NeedsAccess }, onGrantAccess = { asked++ }) { BasicText(it) }
        }
        rule.onNodeWithText(context.getString(R.string.card_grant_access)).performClick()
        assertEquals(1, asked)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(t: String) =
        onAllNodesWithText(t, substring = true).fetchSemanticsNodes().size
}
