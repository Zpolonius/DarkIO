package app.darkio

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.dashboard.CalendarCard
import app.darkio.dashboard.CalendarEvent
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class CalendarUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("UTC")
    private val now = LocalDateTime.of(2026, 11, 16, 10, 0, 0)
    private val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
    private val oneHour = 60 * 60 * 1000L

    @Test
    fun eventDisplayShowsTitleAndTime() {
        val event = CalendarEvent(
            title = "Design review",
            startMillis = nowMillis + 2 * oneHour, // 12:00
        )
        rule.setContent {
            CalendarCard(
                loadEvent = { event },
                hasPermission = { true },
                nowMillis = { nowMillis },
            )
        }

        rule.onNodeWithText("Design review").assertExists()
        rule.onNodeWithText("12:00", substring = true).assertExists()
    }

    @Test
    fun emptyStateShowsIngenAftaler() {
        rule.setContent {
            CalendarCard(
                loadEvent = { null },
                hasPermission = { true },
                nowMillis = { nowMillis },
            )
        }

        val emptyText = context.getString(R.string.calendar_empty)
        rule.onNodeWithText(emptyText, substring = true).assertExists()
        rule.onNodeWithText("_", substring = true).assertExists()
    }

    @Test
    fun permissionMissingShowsGrantAccessTitleExplanationAndButton() {
        rule.setContent {
            CalendarCard(
                loadEvent = { null },
                hasPermission = { false },
                nowMillis = { nowMillis },
            )
        }

        val title = context.getString(R.string.calendar_grant_access_title)
        val explanation = context.getString(R.string.calendar_grant_access_explanation)
        val grant = context.getString(R.string.card_grant_access)

        rule.onNodeWithText(title).assertExists()
        rule.onNodeWithText(explanation).assertExists()
        rule.onNodeWithText(grant).assertExists()
    }

    @Test
    fun permissionDenialDoesNotCrashAndKeepsNeedsAccessState() {
        var permissionGranted = false
        rule.setContent {
            CalendarCard(
                loadEvent = { null },
                hasPermission = { permissionGranted },
                nowMillis = { nowMillis },
            )
        }

        val grant = context.getString(R.string.card_grant_access)
        val title = context.getString(R.string.calendar_grant_access_title)

        // Tryk på "giv adgang" – afvisning simuleret ved at tilladelse forbliver false
        rule.onNodeWithText(grant).performClick()

        // Intet crasher, og "Giv adgang til kalender" vises stadig
        rule.onNodeWithText(title).assertExists()
        rule.onNodeWithText(grant).assertExists()
    }

    @Test
    fun calendarCardIsPresentOnHomeScreenPageZero() {
        rule.setContent {
            HomeScreen(
                apps = splitApps(emptyList(), emptySet()),
                hintSeen = true,
                onHintSeen = {},
                onLaunch = {},
                onSetHidden = { _, _ -> },
                onSetFavorites = {},
                onSetCategory = { _, _ -> },
                onAppInfo = {},
                onUninstall = {},
            )
        }

        // På side 0 ("i dag") findes kalenderkortet (hvor der som udgangspunkt mangler adgang eller vises tom tilstand)
        val title = context.getString(R.string.calendar_grant_access_title)
        rule.onNodeWithText(title).assertExists()
    }
}
