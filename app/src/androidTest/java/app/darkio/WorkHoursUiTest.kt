package app.darkio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.darkio.dashboard.CalendarEvent
import app.darkio.dashboard.DeviceCalendar
import app.darkio.data.AppEntry
import app.darkio.data.HomeCard
import app.darkio.data.Settings
import app.darkio.data.WORK_SHORTCUTS_PAGE
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import app.darkio.settings.SettingsScreen
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** F23: Arbejdstid, genvejsrække, kalenderfiltrering og Fyraften-knap. */
class WorkHoursUiTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun testSettingsScreenWorkHoursChanges() {
        var settings by mutableStateOf(Settings())
        val calendars = listOf(
            DeviceCalendar(id = 1L, displayName = "Work", accountName = "user@work.com", isSuggestedOutlook = true),
            DeviceCalendar(id = 2L, displayName = "Personal", accountName = "user@gmail.com", isSuggestedOutlook = false),
        )
        rule.setContent {
            SettingsScreen(
                settings = settings,
                onChange = { change -> settings = change(settings) },
                onOpenHidden = {},
                onOpenWorkout = {},
                calendarsOverride = calendars,
            )
        }

        // Mon is initially selected; click to toggle off
        rule.onNodeWithTag("workDay_MONDAY").performScrollTo().performClick()
        assertFalse(DayOfWeek.MONDAY in settings.workDays)

        // Sat is initially unselected; click to toggle on
        rule.onNodeWithTag("workDay_SATURDAY").performScrollTo().performClick()
        assertTrue(DayOfWeek.SATURDAY in settings.workDays)

        // Start time: initially 08:00 (480 min). Click workStartPlus -> 08:30 (510 min)
        rule.onNodeWithTag("workStartPlus").performScrollTo().performClick()
        assertEquals(8 * 60 + 30, settings.workStartMinutes)

        // End time: initially 16:00 (960 min). Click workEndMinus -> 15:30 (930 min)
        rule.onNodeWithTag("workEndMinus").performScrollTo().performClick()
        assertEquals(15 * 60 + 30, settings.workEndMinutes)

        // Toggle work calendar
        rule.onNodeWithTag("workCalendar_1").performScrollTo().performClick()
        assertTrue(1L in settings.workCalendarIds)
    }

    @Test
    fun testHomeScreenDuringWorkHours() {
        val wednesday10am = ZonedDateTime.of(2026, 11, 18, 10, 0, 0, 0, ZoneId.of("Europe/Copenhagen"))
        val nowMillis = wednesday10am.toInstant().toEpochMilli()

        val personalApp = AppEntry("personal/A#0", "PersonalApp")
        val workApp = AppEntry("work/B#0", "WorkApp")
        val appsList = listOf(personalApp, workApp)

        // Personal shortcut on page 0 slot 0, Work shortcut on WORK_SHORTCUTS_PAGE slot 0
        val shortcutSlots = mapOf(
            0 to mapOf(personalApp.id to 0),
            WORK_SHORTCUTS_PAGE to mapOf(workApp.id to 0),
        )
        val appLists = splitApps(appsList, emptySet()).copy(shortcutSlots = shortcutSlots)

        val workEvent = CalendarEvent("Work Meeting", startMillis = nowMillis + 3600000, endMillis = nowMillis + 7200000, calendarId = 101L)
        val personalEvent = CalendarEvent("Dentist", startMillis = nowMillis + 3600000, endMillis = nowMillis + 7200000, calendarId = 202L)

        val settings = Settings(
            workCalendarIds = setOf(101L),
            cards = setOf(HomeCard.Calendar),
        )

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
                nowProvider = { wednesday10am },
                calendarTestEvents = listOf(workEvent, personalEvent),
            )
        }

        rule.onNodeWithTag("workHoursBadge").assertExists()
        rule.onNodeWithTag("fyraftenButton").assertExists()
        rule.onNodeWithContentDescription("WorkApp").assertExists()
        rule.onNodeWithContentDescription("PersonalApp").assertDoesNotExist()
        rule.onNodeWithText("Work Meeting").assertExists()
        rule.onNodeWithText("Dentist").assertDoesNotExist()
    }

    @Test
    fun testHomeScreenOutsideWorkHours() {
        val wednesdayEvening = ZonedDateTime.of(2026, 11, 18, 20, 0, 0, 0, ZoneId.of("Europe/Copenhagen"))
        val saturdayMorning = ZonedDateTime.of(2026, 11, 21, 10, 0, 0, 0, ZoneId.of("Europe/Copenhagen"))
        var now by mutableStateOf(wednesdayEvening)

        val personalApp = AppEntry("personal/A#0", "PersonalApp")
        val workApp = AppEntry("work/B#0", "WorkApp")
        val appsList = listOf(personalApp, workApp)

        val shortcutSlots = mapOf(
            0 to mapOf(personalApp.id to 0),
            WORK_SHORTCUTS_PAGE to mapOf(workApp.id to 0),
        )
        val appLists = splitApps(appsList, emptySet()).copy(shortcutSlots = shortcutSlots)

        val workEvent = CalendarEvent("Work Meeting", startMillis = now.toInstant().toEpochMilli() + 3600000, endMillis = now.toInstant().toEpochMilli() + 7200000, calendarId = 101L)
        val personalEvent = CalendarEvent("Dentist", startMillis = now.toInstant().toEpochMilli() + 3600000, endMillis = now.toInstant().toEpochMilli() + 7200000, calendarId = 202L)

        val settings = Settings(
            workCalendarIds = setOf(101L),
            cards = setOf(HomeCard.Calendar),
        )

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
                calendarTestEvents = listOf(workEvent, personalEvent),
            )
        }

        // Check evening
        rule.onNodeWithTag("workHoursBadge").assertDoesNotExist()
        rule.onNodeWithTag("fyraftenButton").assertDoesNotExist()
        rule.onNodeWithContentDescription("PersonalApp").assertExists()
        rule.onNodeWithContentDescription("WorkApp").assertDoesNotExist()
        rule.onNodeWithText("Dentist").assertExists()
        rule.onNodeWithText("Work Meeting").assertDoesNotExist()

        // Switch to Saturday morning
        now = saturdayMorning
        rule.waitForIdle()

        rule.onNodeWithTag("workHoursBadge").assertDoesNotExist()
        rule.onNodeWithTag("fyraftenButton").assertDoesNotExist()
        rule.onNodeWithContentDescription("PersonalApp").assertExists()
        rule.onNodeWithContentDescription("WorkApp").assertDoesNotExist()
    }

    @Test
    fun testTappingFyraftenSwitchesToPersonalImmediately() {
        val wednesday10am = ZonedDateTime.of(2026, 11, 18, 10, 0, 0, 0, ZoneId.of("Europe/Copenhagen"))
        var currentSettings by mutableStateOf(Settings(workCalendarIds = setOf(101L), cards = setOf(HomeCard.Calendar)))
        var zenRuleInvoked = false

        val personalApp = AppEntry("personal/A#0", "PersonalApp")
        val workApp = AppEntry("work/B#0", "WorkApp")
        val appsList = listOf(personalApp, workApp)
        val shortcutSlots = mapOf(
            0 to mapOf(personalApp.id to 0),
            WORK_SHORTCUTS_PAGE to mapOf(workApp.id to 0),
        )
        val appLists = splitApps(appsList, emptySet()).copy(shortcutSlots = shortcutSlots)

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
                settings = currentSettings,
                onSettingsChange = { change -> currentSettings = change(currentSettings) },
                nowProvider = { wednesday10am },
                onFyraftenZenRule = { active -> zenRuleInvoked = active },
            )
        }

        // Before click: work mode
        rule.onNodeWithTag("workHoursBadge").assertExists()
        rule.onNodeWithTag("fyraftenButton").assertExists()
        rule.onNodeWithContentDescription("WorkApp").assertExists()

        // Tap Fyraften
        rule.onNodeWithTag("fyraftenButton").performClick()
        rule.waitForIdle()

        // Zen rule hook was invoked
        assertTrue(zenRuleInvoked)
        assertEquals(wednesday10am.toLocalDate(), currentSettings.workEndOverrideDate)

        // After click: personal mode immediately
        rule.onNodeWithTag("workHoursBadge").assertDoesNotExist()
        rule.onNodeWithTag("fyraftenButton").assertDoesNotExist()
        rule.onNodeWithContentDescription("PersonalApp").assertExists()
        rule.onNodeWithContentDescription("WorkApp").assertDoesNotExist()
    }
}
