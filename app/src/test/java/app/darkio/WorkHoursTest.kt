package app.darkio

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.darkio.dashboard.CalendarEvent
import app.darkio.dashboard.filterCalendarEvents
import app.darkio.data.AppEntry
import app.darkio.data.DEFAULT_WORK_DAYS
import app.darkio.data.DEFAULT_WORK_END_MINUTES
import app.darkio.data.DEFAULT_WORK_START_MINUTES
import app.darkio.data.FYRAFTEN_RULE_NAME
import app.darkio.data.PageShortcut
import app.darkio.data.Settings
import app.darkio.data.WORK_SHORTCUTS_PAGE
import app.darkio.data.ZenRuleInfo
import app.darkio.data.desiredZenRuleActive
import app.darkio.data.ensureFyraftenZenRuleWith
import app.darkio.data.isWorkHours
import app.darkio.data.setFyraftenZenRuleActiveWith
import app.darkio.data.settings
import app.darkio.data.updateSettings
import app.darkio.data.workShortcutsSeed
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkHoursTest {
    private val copenhagenZone = ZoneId.of("Europe/Copenhagen")
    private val defaultSettings = Settings()

    // --- 1. isWorkHours tests ---

    @Test
    fun defaultSettingsHaveExpectedWorkHours() {
        assertEquals(
            setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            defaultSettings.workDays,
        )
        assertEquals(DEFAULT_WORK_DAYS, defaultSettings.workDays)
        assertEquals(480, defaultSettings.workStartMinutes) // 08:00
        assertEquals(DEFAULT_WORK_START_MINUTES, defaultSettings.workStartMinutes)
        assertEquals(960, defaultSettings.workEndMinutes) // 16:00
        assertEquals(DEFAULT_WORK_END_MINUTES, defaultSettings.workEndMinutes)
        assertNull(defaultSettings.workEndOverrideDate)
        assertEquals(emptySet<Long>(), defaultSettings.workCalendarIds)
        assertNull(defaultSettings.workZenRuleId)
    }

    @Test
    fun isWorkHoursBoundariesOnWorkday() {
        // Monday 2026-11-16
        val mon0759 = ZonedDateTime.of(2026, 11, 16, 7, 59, 0, 0, copenhagenZone)
        val mon0800 = ZonedDateTime.of(2026, 11, 16, 8, 0, 0, 0, copenhagenZone)
        val mon1200 = ZonedDateTime.of(2026, 11, 16, 12, 0, 0, 0, copenhagenZone)
        val mon1559 = ZonedDateTime.of(2026, 11, 16, 15, 59, 0, 0, copenhagenZone)
        val mon1600 = ZonedDateTime.of(2026, 11, 16, 16, 0, 0, 0, copenhagenZone)
        val mon1601 = ZonedDateTime.of(2026, 11, 16, 16, 1, 0, 0, copenhagenZone)

        assertFalse("07:59 er før arbejdstid", isWorkHours(defaultSettings, mon0759))
        assertTrue("08:00 er start på arbejdstid", isWorkHours(defaultSettings, mon0800))
        assertTrue("12:00 er midt i arbejdstid", isWorkHours(defaultSettings, mon1200))
        assertTrue("15:59 er sidste minut i arbejdstid", isWorkHours(defaultSettings, mon1559))
        assertFalse("16:00 er fyraften (arbejdstid slut)", isWorkHours(defaultSettings, mon1600))
        assertFalse("16:01 er efter arbejdstid", isWorkHours(defaultSettings, mon1601))
    }

    @Test
    fun isWorkHoursWeekend() {
        // Saturday 2026-11-21 and Sunday 2026-11-22
        val sat1000 = ZonedDateTime.of(2026, 11, 21, 10, 0, 0, 0, copenhagenZone)
        val sun1400 = ZonedDateTime.of(2026, 11, 22, 14, 0, 0, 0, copenhagenZone)

        assertFalse("Lørdag er ikke arbejdsdag", isWorkHours(defaultSettings, sat1000))
        assertFalse("Søndag er ikke arbejdsdag", isWorkHours(defaultSettings, sun1400))
    }

    @Test
    fun isWorkHoursOverrideDateAndMidnightReset() {
        val mondayDate = LocalDate.of(2026, 11, 16)
        val settingsWithOverride = defaultSettings.copy(workEndOverrideDate = mondayDate)

        val monday1000 = ZonedDateTime.of(2026, 11, 16, 10, 0, 0, 0, copenhagenZone)
        val monday2359 = ZonedDateTime.of(2026, 11, 16, 23, 59, 0, 0, copenhagenZone)
        val tuesday0001 = ZonedDateTime.of(2026, 11, 17, 0, 1, 0, 0, copenhagenZone)
        val tuesday0800 = ZonedDateTime.of(2026, 11, 17, 8, 0, 0, 0, copenhagenZone)
        val tuesday1200 = ZonedDateTime.of(2026, 11, 17, 12, 0, 0, 0, copenhagenZone)

        assertFalse("Fyraften-override gælder mandag kl. 10", isWorkHours(settingsWithOverride, monday1000))
        assertFalse("Fyraften-override gælder mandag kl. 23:59", isWorkHours(settingsWithOverride, monday2359))
        assertFalse("Tirsdag kl. 00:01 er uden for arbejdstid", isWorkHours(settingsWithOverride, tuesday0001))
        assertTrue("Tirsdag kl. 08:00 nulstilles fyraften automatisk ved midnat", isWorkHours(settingsWithOverride, tuesday0800))
        assertTrue("Tirsdag kl. 12:00 er normal arbejdstid igen", isWorkHours(settingsWithOverride, tuesday1200))
    }

    @Test
    fun isWorkHoursDstSpringForwardAndFallBack() {
        // Europe/Copenhagen:
        // Sommer-tidsskift forår 2026: søndag 29. marts 2026 (02:00 -> 03:00)
        val mondayAfterSpringDst = ZonedDateTime.of(2026, 3, 30, 8, 0, 0, 0, copenhagenZone)
        val mondayAfterSpringDstEnd = ZonedDateTime.of(2026, 3, 30, 16, 0, 0, 0, copenhagenZone)

        assertTrue("Mandag efter sommertid kl. 08:00 er arbejdstid", isWorkHours(defaultSettings, mondayAfterSpringDst))
        assertFalse("Mandag efter sommertid kl. 16:00 er fyraften", isWorkHours(defaultSettings, mondayAfterSpringDstEnd))

        // Vinter-tidsskift efterår 2026: søndag 25. oktober 2026 (03:00 -> 02:00)
        val mondayAfterFallDst = ZonedDateTime.of(2026, 10, 26, 8, 0, 0, 0, copenhagenZone)
        val mondayAfterFallDstEnd = ZonedDateTime.of(2026, 10, 26, 16, 0, 0, 0, copenhagenZone)

        assertTrue("Mandag efter vintertid kl. 08:00 er arbejdstid", isWorkHours(defaultSettings, mondayAfterFallDst))
        assertFalse("Mandag efter vintertid kl. 16:00 er fyraften", isWorkHours(defaultSettings, mondayAfterFallDstEnd))
    }

    // --- 2. workShortcutsSeed tests ---

    @Test
    fun workShortcutsSeedAllThreePresent() {
        val installed = listOf(
            AppEntry("com.microsoft.office.outlook/com.microsoft.office.outlook.MainActivity#0", "Outlook"),
            AppEntry("com.microsoft.teams/com.microsoft.teams.Main#0", "Teams"),
            AppEntry("com.Slack/com.Slack.ui.HomeActivity#0", "Slack"),
            AppEntry("com.google.android.calculator/Main#0", "Lommeregner"),
        )
        val seed = workShortcutsSeed(installed)
        assertEquals(3, seed.size)
        assertEquals(
            listOf(
                PageShortcut(WORK_SHORTCUTS_PAGE, "com.microsoft.office.outlook", "com.microsoft.office.outlook.MainActivity", 0, 0),
                PageShortcut(WORK_SHORTCUTS_PAGE, "com.microsoft.teams", "com.microsoft.teams.Main", 0, 1),
                PageShortcut(WORK_SHORTCUTS_PAGE, "com.Slack", "com.Slack.ui.HomeActivity", 0, 2),
            ),
            seed,
        )
    }

    @Test
    fun workShortcutsSeedSomeMissing() {
        // Teams mangler
        val installed = listOf(
            AppEntry("com.microsoft.office.outlook/Main#0", "Outlook"),
            AppEntry("com.Slack/Main#0", "Slack"),
            AppEntry("com.spotify.music/Main#0", "Spotify"),
        )
        val seed = workShortcutsSeed(installed)
        assertEquals(2, seed.size)
        assertEquals(
            listOf(
                PageShortcut(WORK_SHORTCUTS_PAGE, "com.microsoft.office.outlook", "Main", 0, 0),
                PageShortcut(WORK_SHORTCUTS_PAGE, "com.Slack", "Main", 0, 1),
            ),
            seed,
        )
    }

    @Test
    fun workShortcutsSeedNonePresent() {
        val installed = listOf(
            AppEntry("com.spotify.music/Main#0", "Spotify"),
            AppEntry("com.google.android.calculator/Main#0", "Lommeregner"),
        )
        val seed = workShortcutsSeed(installed)
        assertTrue("Ingen arbejds-apps installeret giver tom liste", seed.isEmpty())
    }

    @Test
    fun workShortcutsSeedSlotOrdering() {
        // Installeret rækkefølge er omvendt: Slack før Teams før Outlook
        val installed = listOf(
            AppEntry("com.Slack/Main#0", "Slack"),
            AppEntry("com.microsoft.teams/Main#0", "Teams"),
            AppEntry("com.microsoft.office.outlook/Main#0", "Outlook"),
        )
        val seed = workShortcutsSeed(installed)
        assertEquals(3, seed.size)
        // Skal stadig seedes i fast rækkefølge: Outlook (0), Teams (1), Slack (2)
        assertEquals("com.microsoft.office.outlook", seed[0].packageName)
        assertEquals(0, seed[0].slot)
        assertEquals("com.microsoft.teams", seed[1].packageName)
        assertEquals(1, seed[1].slot)
        assertEquals("com.Slack", seed[2].packageName)
        assertEquals(2, seed[2].slot)
    }

    // --- 3. desiredZenRuleActive tests ---

    @Test
    fun desiredZenRuleActiveDecisions() {
        // Fyraften klikket -> tænder altid Fyraften-reglen (true)
        assertTrue(desiredZenRuleActive(isWorkHours = true, wasWorkHours = true, fyraftenClicked = true) == true)
        assertTrue(desiredZenRuleActive(isWorkHours = false, wasWorkHours = false, fyraftenClicked = true) == true)

        // Arbejdstid starter (overgang fra ikke-arbejdstid eller ved opstart) -> slukker Fyraften-reglen (false)
        assertFalse(desiredZenRuleActive(isWorkHours = true, wasWorkHours = false, fyraftenClicked = false) == true)
        assertEquals(false, desiredZenRuleActive(isWorkHours = true, wasWorkHours = false, fyraftenClicked = false))
        assertEquals(false, desiredZenRuleActive(isWorkHours = true, wasWorkHours = null, fyraftenClicked = false))

        // Uændret tilstand -> null
        assertNull(desiredZenRuleActive(isWorkHours = true, wasWorkHours = true, fyraftenClicked = false))
        assertNull(desiredZenRuleActive(isWorkHours = false, wasWorkHours = false, fyraftenClicked = false))
        assertNull(desiredZenRuleActive(isWorkHours = false, wasWorkHours = true, fyraftenClicked = false))
    }

    // --- 4. filterCalendarEvents tests ---

    @Test
    fun filterCalendarEventsWorkHoursWithSelectedCalendars() {
        val workCalendarIds = setOf(1L, 3L)
        val events = listOf(
            CalendarEvent(title = "Arbejdsmøde", startMillis = 1000L, calendarId = 1L),
            CalendarEvent(title = "Tandlæge", startMillis = 2000L, calendarId = 2L),
            CalendarEvent(title = "Sprint planlægning", startMillis = 3000L, calendarId = 3L),
            CalendarEvent(title = "Fødselsdag", startMillis = 4000L, calendarId = 4L),
        )

        val filtered = filterCalendarEvents(events, isWorkHours = true, workCalendarIds)
        assertEquals(2, filtered.size)
        assertEquals(listOf("Arbejdsmøde", "Sprint planlægning"), filtered.map { it.title })
    }

    @Test
    fun filterCalendarEventsWorkHoursWithEmptySelection() {
        val events = listOf(
            CalendarEvent(title = "Arbejdsmøde", startMillis = 1000L, calendarId = 1L),
            CalendarEvent(title = "Tandlæge", startMillis = 2000L, calendarId = 2L),
        )

        val filtered = filterCalendarEvents(events, isWorkHours = true, workCalendarIds = emptySet())
        assertTrue("I arbejdstid uden valgte arbejdskalendere vises ingen aftaler", filtered.isEmpty())
    }

    @Test
    fun filterCalendarEventsOutsideWorkHoursExcludingWorkCalendars() {
        val workCalendarIds = setOf(1L, 3L)
        val events = listOf(
            CalendarEvent(title = "Arbejdsmøde", startMillis = 1000L, calendarId = 1L),
            CalendarEvent(title = "Tandlæge", startMillis = 2000L, calendarId = 2L),
            CalendarEvent(title = "Sprint planlægning", startMillis = 3000L, calendarId = 3L),
            CalendarEvent(title = "Fødselsdag", startMillis = 4000L, calendarId = 4L),
        )

        val filtered = filterCalendarEvents(events, isWorkHours = false, workCalendarIds)
        assertEquals(2, filtered.size)
        assertEquals(listOf("Tandlæge", "Fødselsdag"), filtered.map { it.title })
    }

    // --- 5. ensureFyraftenZenRule / setFyraftenZenRuleActive tests ---

    @Test
    fun ensureFyraftenZenRuleReturnsNullWithoutPolicyAccess() {
        var created = false
        val result = ensureFyraftenZenRuleWith(
            existingId = "old-id",
            hasPolicyAccess = { false },
            getRule = { null },
            listRules = { emptyList() },
            updateRuleConfigurationActivity = { false },
            createRuleWithConfigurationActivity = {
                created = true
                "new-id"
            },
        )
        assertNull(result)
        assertFalse(created)
    }

    @Test
    fun ensureFyraftenZenRuleCreatesRuleWithConfigActivityWhenMissing() {
        var created = false
        val result = ensureFyraftenZenRuleWith(
            existingId = null,
            hasPolicyAccess = { true },
            getRule = { null },
            listRules = { emptyList() },
            updateRuleConfigurationActivity = { false },
            createRuleWithConfigurationActivity = {
                created = true
                "created-rule-1"
            },
        )
        assertTrue(created)
        assertEquals("created-rule-1", result)
    }

    @Test
    fun ensureFyraftenZenRuleReplacesStaleIdWhenRuleNoLongerExists() {
        var created = false
        val result = ensureFyraftenZenRuleWith(
            existingId = "stale-id",
            hasPolicyAccess = { true },
            getRule = { null },
            listRules = { emptyList() },
            updateRuleConfigurationActivity = { false },
            createRuleWithConfigurationActivity = {
                created = true
                "fresh-rule-2"
            },
        )
        assertTrue(created)
        assertEquals("fresh-rule-2", result)
    }

    @Test
    fun ensureFyraftenZenRuleUpdatesLegacyRuleMissingConfigActivity() {
        var updatedId: String? = null
        var created = false
        val legacyRule = ZenRuleInfo(
            id = "legacy-id",
            name = FYRAFTEN_RULE_NAME,
            hasExpectedConfigurationActivity = false,
        )
        val result = ensureFyraftenZenRuleWith(
            existingId = "legacy-id",
            hasPolicyAccess = { true },
            getRule = { if (it == "legacy-id") legacyRule else null },
            listRules = { listOf(legacyRule) },
            updateRuleConfigurationActivity = { id ->
                updatedId = id
                true
            },
            createRuleWithConfigurationActivity = {
                created = true
                "new-id"
            },
        )
        assertEquals("legacy-id", updatedId)
        assertFalse(created)
        assertEquals("legacy-id", result)
    }

    @Test
    fun ensureFyraftenZenRuleCreatesNewRuleIfLegacyUpdateFails() {
        var updateAttempts = 0
        var created = false
        val legacyRule = ZenRuleInfo(
            id = "legacy-id",
            name = FYRAFTEN_RULE_NAME,
            hasExpectedConfigurationActivity = false,
        )
        val result = ensureFyraftenZenRuleWith(
            existingId = "legacy-id",
            hasPolicyAccess = { true },
            getRule = { if (it == "legacy-id") legacyRule else null },
            listRules = { listOf(legacyRule) },
            updateRuleConfigurationActivity = {
                updateAttempts++
                false
            },
            createRuleWithConfigurationActivity = {
                created = true
                "replacement-id"
            },
        )
        assertEquals(1, updateAttempts)
        assertTrue(created)
        assertEquals("replacement-id", result)
    }

    @Test
    fun ensureFyraftenZenRuleReturnsNullWhenRuleCreationFails() {
        val result = ensureFyraftenZenRuleWith(
            existingId = null,
            hasPolicyAccess = { true },
            getRule = { null },
            listRules = { emptyList() },
            updateRuleConfigurationActivity = { false },
            createRuleWithConfigurationActivity = { throw IllegalArgumentException("missing config activity") },
        )
        assertNull(result)
    }

    @Test
    fun setFyraftenZenRuleActiveReturnsTrueOnSuccessAndFalseOnFailure() {
        var recordedState: Pair<String, Boolean>? = null
        val success = setFyraftenZenRuleActiveWith(
            ruleId = "rule-1",
            active = true,
            hasPolicyAccess = { true },
            setRuleState = { id, active -> recordedState = id to active },
        )
        assertTrue(success)
        assertEquals("rule-1" to true, recordedState)

        val noPermission = setFyraftenZenRuleActiveWith(
            ruleId = "rule-1",
            active = true,
            hasPolicyAccess = { false },
            setRuleState = { _, _ -> },
        )
        assertFalse(noPermission)

        val failure = setFyraftenZenRuleActiveWith(
            ruleId = "rule-1",
            active = false,
            hasPolicyAccess = { true },
            setRuleState = { _, _ -> throw SecurityException("denied") },
        )
        assertFalse(failure)
    }

    private fun inMemoryPreferencesStore(): DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())
        return object : DataStore<Preferences> {
            override val data: Flow<Preferences> = state
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val updated = transform(state.value)
                state.value = updated
                return updated
            }
        }
    }

    @Test
    fun updatingWorkZenRuleIdPreservesWorkHoursDaysAndCalendars() = runBlocking {
        val customDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
        val initial = Settings(
            workDays = customDays,
            workStartMinutes = 9 * 60,
            workEndMinutes = 17 * 60 + 30,
            workCalendarIds = setOf(10L, 20L),
            workZenRuleId = "stale-id",
        )
        val store = inMemoryPreferencesStore()
        store.updateSettings { initial }
        store.updateSettings { it.copy(workZenRuleId = "fresh-id") }
        val updated = store.settings().first()
        assertEquals("fresh-id", updated.workZenRuleId)
        assertEquals(customDays, updated.workDays)
        assertEquals(9 * 60, updated.workStartMinutes)
        assertEquals(17 * 60 + 30, updated.workEndMinutes)
        assertEquals(setOf(10L, 20L), updated.workCalendarIds)
    }
}
