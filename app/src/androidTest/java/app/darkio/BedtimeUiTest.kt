package app.darkio

import android.app.NotificationManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.height
import app.darkio.dashboard.CalendarEvent
import app.darkio.data.AppEntry
import app.darkio.data.BedtimeSummary
import app.darkio.data.HomeCard
import app.darkio.data.Settings
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import app.darkio.settings.SettingsScreen
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * UI-test for godnat-tilstand (F24).
 * Tester godnat-visning med testdata (ur, vækkeur, første aftale, skridt, træning, ingen tip, ingen genvejsrække,
 * dock + søgefelt synlige, "Sengetid-indstillinger"-knap klikbar og i nederste halvdel af skærmen),
 * tom tilstand ("intet vækkeur", "fri i morgen_"), samt at Sengetid-tidsrum kan ændres i SettingsScreen.
 */
class BedtimeUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val copenhagenZone = ZoneId.of("Europe/Copenhagen")

    @Test
    fun testBedtimeViewWithData() {
        // Kl. 22:30 (sengetid 18:00-10:00)
        val now = ZonedDateTime.of(2026, 11, 16, 22, 30, 0, 0, copenhagenZone)
        val alarmTrigger = ZonedDateTime.of(2026, 11, 17, 7, 0, 0, 0, copenhagenZone).toInstant().toEpochMilli()
        val tomorrowMeeting = CalendarEvent(
            title = "Morgenmøde",
            startMillis = ZonedDateTime.of(2026, 11, 17, 9, 0, 0, 0, copenhagenZone).toInstant().toEpochMilli(),
            endMillis = ZonedDateTime.of(2026, 11, 17, 10, 0, 0, 0, copenhagenZone).toInstant().toEpochMilli(),
        )

        val summary = BedtimeSummary(
            alarmTriggerMillis = alarmTrigger,
            firstEventTomorrow = tomorrowMeeting,
            steps = 8420L,
            workoutCompleted = true,
        )

        val testApp = AppEntry("test/A#0", "TestApp")
        val appLists = splitApps(listOf(testApp), emptySet()).copy(
            shortcutSlots = mapOf(0 to mapOf(testApp.id to 0)),
        )

        val settings = Settings(
            cards = HomeCard.entries.toSet(), // Alle kort aktiveret normalt
        )

        var settingsOpened = false

        rule.setContent {
            HomeScreen(
                apps = appLists,
                hintSeen = true,
                onHintSeen = {},
                onLaunch = {},
                onSetHidden = { _, _ -> },
                onSetFavorites = {},
                onSetCategory = { _, _ -> },
                onAppInfo = {},
                onUninstall = {},
                settings = settings,
                nowProvider = { now },
                interruptionFilterProvider = { NotificationManager.INTERRUPTION_FILTER_PRIORITY },
                bedtimeDataOverride = summary,
                onOpenBedtimeSettings = { settingsOpened = true },
            )
        }

        // 1. Dæmpet ur og dato
        rule.onNodeWithText("22:30").assertExists()
        rule.onNodeWithTag("card_Clock").assertExists()
        rule.onNodeWithTag("bedtimeDate").assertExists()

        // 2. Vækkeur
        rule.onNodeWithText("vækkeur 07:00").assertExists()
        rule.onNodeWithTag("bedtimeAlarm").assertExists()

        // 3. Første aftale i morgen
        rule.onNodeWithText("Morgenmøde · 09:00").assertExists()
        rule.onNodeWithTag("bedtimeCalendar").assertExists()

        // 4. Dagens skridt og træningsstatus
        rule.onNodeWithText("8.420 skridt").assertExists()
        rule.onNodeWithText("træning gennemført").assertExists()

        // 5. Ingen TipCard, ingen genvejsrække, ingen arbejdstid-badge, ingen Fyraften-knap
        rule.onNodeWithTag("card_Tip").assertDoesNotExist()
        rule.onNodeWithTag("shortcuts").assertDoesNotExist()
        rule.onNodeWithTag("workHoursBadge").assertDoesNotExist()
        rule.onNodeWithTag("fyraftenButton").assertDoesNotExist()

        // 6. Dock og søgefelt synlige
        rule.onNodeWithTag("dock").assertExists()
        rule.onNodeWithTag("searchField").assertExists()

        // 7. "Sengetid-indstillinger"-knap klikbar og i nederste halvdel af skærmen
        val button = rule.onNodeWithTag("bedtimeSettingsButton")
        button.assertExists()
        rule.onNodeWithText("Sengetid-indstillinger").assertExists()

        val rootBounds = rule.onRoot().getUnclippedBoundsInRoot()
        val middle = rootBounds.height / 2
        val buttonBounds = button.getUnclippedBoundsInRoot()

        assertTrue(
            "Knap (${buttonBounds.top}) skal ligge i nederste halvdel af skærmen (midte: $middle)",
            buttonBounds.top >= middle,
        )
        assertTrue(
            "Knap højde (${buttonBounds.height.value}) skal være mindst 44 dp (DarkIOSize.TouchMin)",
            buttonBounds.height.value >= 43.9f,
        )

        button.performClick()
        assertTrue("Sengetid-indstillinger skal kaldes ved tryk", settingsOpened)
    }

    @Test
    fun testBedtimeEmptyState() {
        val now = ZonedDateTime.of(2026, 11, 16, 23, 0, 0, 0, copenhagenZone)
        val emptySummary = BedtimeSummary(
            alarmTriggerMillis = null,
            firstEventTomorrow = null,
            steps = null,
            workoutCompleted = false,
        )

        val appLists = splitApps(emptyList(), emptySet())

        rule.setContent {
            HomeScreen(
                apps = appLists,
                hintSeen = true,
                onHintSeen = {},
                onLaunch = {},
                onSetHidden = { _, _ -> },
                onSetFavorites = {},
                onSetCategory = { _, _ -> },
                onAppInfo = {},
                onUninstall = {},
                settings = Settings(),
                nowProvider = { now },
                interruptionFilterProvider = { NotificationManager.INTERRUPTION_FILTER_PRIORITY },
                bedtimeDataOverride = emptySummary,
            )
        }

        // Tom tilstand for vækkeur og kalender
        rule.onNodeWithText("intet vækkeur").assertExists()
        rule.onNodeWithText("fri i morgen_").assertExists()
        rule.onNodeWithText("ingen skridt").assertExists()
        rule.onNodeWithText("træning ikke gennemført").assertExists()
    }

    @Test
    fun testSettingsScreenBedtimeRangeChanges() {
        var settings by mutableStateOf(Settings())

        rule.setContent {
            SettingsScreen(
                settings = settings,
                onChange = { change -> settings = change(settings) },
                onOpenHidden = {},
                onOpenWorkout = {},
            )
        }

        // Standard er 18:00 (1080 min) til 10:00 (600 min)
        rule.onNodeWithTag("bedtimeStartTime").performScrollTo().assertExists()
        rule.onNodeWithText("18:00").assertExists()
        rule.onNodeWithTag("bedtimeEndTime").performScrollTo().assertExists()
        rule.onNodeWithText("10:00").assertExists()

        // Sænk start med 30 min -> 17:30 (1050 min)
        rule.onNodeWithTag("bedtimeStartMinus").performScrollTo().performClick()
        assertEquals(17 * 60 + 30, settings.bedtimeStartMinutes)

        // Hæv start med 30 min -> 18:00 (1080 min)
        rule.onNodeWithTag("bedtimeStartPlus").performScrollTo().performClick()
        assertEquals(18 * 60, settings.bedtimeStartMinutes)

        // Sænk slut med 30 min -> 09:30 (570 min)
        rule.onNodeWithTag("bedtimeEndMinus").performScrollTo().performClick()
        assertEquals(9 * 60 + 30, settings.bedtimeEndMinutes)

        // Hæv slut med 30 min -> 10:00 (600 min)
        rule.onNodeWithTag("bedtimeEndPlus").performScrollTo().performClick()
        assertEquals(10 * 60, settings.bedtimeEndMinutes)
    }
}
