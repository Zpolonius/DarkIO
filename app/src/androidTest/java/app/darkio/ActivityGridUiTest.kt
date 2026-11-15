package app.darkio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.dashboard.ActivityGridCanvas
import app.darkio.dashboard.ActivityGridCard
import app.darkio.dashboard.ActivityGridCell
import app.darkio.data.AppEntry
import app.darkio.data.WorkoutDao
import app.darkio.data.WorkoutDay
import app.darkio.data.WorkoutRecord
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOSize
import java.time.LocalDate
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class TestWorkoutDao(
    private val completed: Set<String> = emptySet(),
    private val days: List<WorkoutDay> = emptyList(),
) : WorkoutDao() {
    override suspend fun getWorkoutForDay(dayOfWeek: Int): WorkoutDay? = days.firstOrNull { it.dayOfWeek == dayOfWeek }
    override suspend fun getAllWorkouts(): List<WorkoutDay> = days
    override suspend fun getCompletedDates(): List<String> = completed.toList()
    override suspend fun upsert(workout: WorkoutDay) {}
    override suspend fun insertCompleted(record: WorkoutRecord) {}
    override suspend fun deleteCompleted(date: String) {}
    override suspend fun clearAllRecords() {}
    override suspend fun clearAllDays() {}
    override suspend fun deleteDay(dayOfWeek: Int) {}
}

class ActivityGridUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Viser rigtige tal og TalkBack læser én samlet beskrivelse med de to tal (N8). */
    @Test
    fun activityGridDisplaysCorrectNumbersAndMergedTalkBackDescription() {
        val today = LocalDate.of(2026, 11, 15) // Søndag
        val day1 = today.minusDays(1) // Lørdag
        val day2 = today.minusDays(2) // Fredag
        val day3 = today.minusDays(3) // Torsdag
        val day4 = today.minusDays(4) // Onsdag

        val steps = mapOf(
            today to 10_000L,
            day1 to 12_500L,
            day2 to 10_000L,
            day3 to 14_000L,
            day4 to 4_000L, // under målet
        )

        val completedDates = setOf(today.toString(), day3.toString())
        val dao = TestWorkoutDao(completed = completedDates)

        rule.setContent {
            ActivityGridCard(
                dao = dao,
                hasPermissions = { true to true },
                queryStepsByDate = { _, _ -> steps },
                stepGoal = 10_000L,
                now = { today },
            )
        }

        // 4 dage med mål nået (today, day1, day2, day3)
        rule.onNodeWithTag("activityGridGoalDays", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("activityGridGoalDays", useUnmergedTree = true).assertTextContains("4 dage med mål", substring = true)

        // 2 gennemførte træninger (today, day3)
        rule.onNodeWithTag("activityGridWorkouts", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("activityGridWorkouts", useUnmergedTree = true).assertTextContains("2 træninger", substring = true)

        // TalkBack læser én samlet beskrivelse med de to tal (N8)
        rule.onNodeWithContentDescription("Aktivitet: 4 dage med mål, 2 træninger").assertIsDisplayed()

        // Verificér at de underliggende tal ikke eksponeres separat i merged tree (TalkBack læser uden duplikering)
        rule.onNodeWithTag("activityGridGoalDays").assertDoesNotExist()
        rule.onNodeWithTag("activityGridWorkouts").assertDoesNotExist()

        // Med fuld historikadgang vises knappen ikke
        rule.onNodeWithTag("activityGridGrantHistoryButton", useUnmergedTree = true).assertDoesNotExist()
    }

    /** "Giv adgang til historik"-knap ved manglende historik (mindst 44 dp høj jf. N8) og klik udløser flow. */
    @Test
    fun activityGridWithoutHistoryAccessDisplaysButtonAndTriggersCallback() {
        val today = LocalDate.of(2026, 11, 15)
        var accessRequested = false

        rule.setContent {
            ActivityGridCard(
                dao = TestWorkoutDao(),
                hasPermissions = { true to false }, // har skridt, men mangler historik (>30 dage)
                queryStepsByDate = { _, _ -> emptyMap() },
                onGrantHistoryAccess = { accessRequested = true },
                now = { today },
            )
        }

        // Knappen vises (også i merged tree som separat tilgængelig TalkBack-handling)
        val button = rule.onNodeWithTag("activityGridGrantHistoryButton")
        button.assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.activity_grid_grant_history)).assertIsDisplayed()

        // Mindst 44 dp høj (DarkIOSize.TouchMin)
        button.assertHeightIsAtLeast(DarkIOSize.TouchMin)

        // Klik udløser flow
        button.performClick()
        rule.waitForIdle()
        assertTrue(accessRequested)
    }

    /**
     * Rigtige farver (pastel-mint, pastel-coral, line, accent) og former (fyldt firkant vs. prik).
     * - pastel-mint: fyldt firkant ved nået skridtmål
     * - pastel-coral: prik (cirkel) ved gennemført træning
     * - line: tom dag / stiplet ramme
     * - accent: ramme om i dag
     */
    @Test
    fun activityGridDrawsCorrectColorsAndShapes() {
        val today = LocalDate.of(2026, 11, 15) // Søndag (weekIndex 19, dayOfWeek 7)
        val cells = listOf(
            // Celle A: Mål nået, ingen træning (fyldt firkant i pastel-mint)
            ActivityGridCell(
                date = today.minusDays(1),
                weekIndex = 18,
                dayOfWeek = 1,
                steps = 10_000L,
                isWorkoutCompleted = false,
                isGoalReached = true,
                isToday = false,
                isFuture = false,
                lacksHistory = false,
            ),
            // Celle B: Træning gennemført, IKKE nået skridtmål (line-firkant med coral-prik)
            ActivityGridCell(
                date = today.minusDays(2),
                weekIndex = 18,
                dayOfWeek = 2,
                steps = 0L,
                isWorkoutCompleted = true,
                isGoalReached = false,
                isToday = false,
                isFuture = false,
                lacksHistory = false,
            ),
            // Celle C: Både mål nået OG træning gennemført (pastel-mint firkant med coral-prik)
            ActivityGridCell(
                date = today.minusDays(3),
                weekIndex = 18,
                dayOfWeek = 3,
                steps = 12_000L,
                isWorkoutCompleted = true,
                isGoalReached = true,
                isToday = false,
                isFuture = false,
                lacksHistory = false,
            ),
            // Celle D: I dag, tom (line med accent-ramme)
            ActivityGridCell(
                date = today,
                weekIndex = 19,
                dayOfWeek = 7,
                steps = 0L,
                isWorkoutCompleted = false,
                isGoalReached = false,
                isToday = true,
                isFuture = false,
                lacksHistory = false,
            ),
            // Celle E: Ældre end 30 dage uden historikadgang (stiplet ramme i line)
            ActivityGridCell(
                date = today.minusDays(35),
                weekIndex = 14,
                dayOfWeek = 1,
                steps = 0L,
                isWorkoutCompleted = false,
                isGoalReached = false,
                isToday = false,
                isFuture = false,
                lacksHistory = true,
            ),
        )

        rule.setContent {
            Box(Modifier.background(DarkIOColors.Surface)) {
                ActivityGridCanvas(cells = cells)
            }
        }

        val img = rule.onNodeWithTag("activityGridCanvas").captureToImage()
        val pixels = IntArray(img.width * img.height)
        img.readPixels(pixels, 0, 0, img.width, img.height)
        val pixelSet = pixels.toSet()

        val mintArgb = DarkIOColors.Mint.toArgb()
        val coralArgb = DarkIOColors.Coral.toArgb()
        val lineArgb = DarkIOColors.Line.toArgb()
        val accentArgb = DarkIOColors.Accent.toArgb()
        val surfaceArgb = DarkIOColors.Surface.toArgb()

        // 1. Verificér at alle fire nøglefarver er til stede på canvaset
        assertTrue("Skal indeholde pastel-mint (skridtmål)", pixelSet.contains(mintArgb))
        assertTrue("Skal indeholde pastel-coral (træningsprik)", pixelSet.contains(coralArgb))
        assertTrue("Skal indeholde line (tomme felter / rammer)", pixelSet.contains(lineArgb))
        assertTrue("Skal indeholde accent (i dag ramme)", pixelSet.contains(accentArgb))

        // 2. Præcise pixel-koordinater ud fra tokens og densitet
        val density = rule.density
        val cellSizePx = with(density) { DarkIOSize.GridCellSize.toPx() }
        val cellGapPx = with(density) { DarkIOSize.GridCellGap.toPx() }
        val strokeWidthPx = with(density) { DarkIOSize.LineWidth.toPx() }
        val offset1Px = with(density) { 1.dp.toPx() }

        fun cellPixel(x: Float, y: Float): Int {
            val px = x.roundToInt().coerceIn(0, img.width - 1)
            val py = y.roundToInt().coerceIn(0, img.height - 1)
            return pixels[py * img.width + px]
        }

        fun cellCenter(weekIndex: Int, dayOfWeek: Int): Int {
            val cx = weekIndex * (cellSizePx + cellGapPx) + cellSizePx / 2f
            val cy = (dayOfWeek - 1) * (cellSizePx + cellGapPx) + cellSizePx / 2f
            return cellPixel(cx, cy)
        }

        fun cellCorner(weekIndex: Int, dayOfWeek: Int): Int {
            val cx = weekIndex * (cellSizePx + cellGapPx) + offset1Px
            val cy = (dayOfWeek - 1) * (cellSizePx + cellGapPx) + offset1Px
            return cellPixel(cx, cy)
        }

        // Celle A (week 18, dayOfWeek 1): kun nået mål (mint firkant)
        assertEquals("Celle A centrum skal være pastel-mint", mintArgb, cellCenter(18, 1))
        assertEquals("Celle A hjørne skal være pastel-mint (fyldt firkant)", mintArgb, cellCorner(18, 1))

        // Celle B (week 18, dayOfWeek 2): kun træning (coral prik i line firkant)
        assertEquals("Celle B centrum skal være pastel-coral (prik)", coralArgb, cellCenter(18, 2))
        assertEquals("Celle B hjørne skal være line (ikke fyldt firkant, kun prik i midten)", lineArgb, cellCorner(18, 2))

        // Celle C (week 18, dayOfWeek 3): både mål nået OG træning (coral prik i midten, mint fyld i hjørnet)
        assertEquals("Celle C centrum skal være pastel-coral (prik)", coralArgb, cellCenter(18, 3))
        assertEquals("Celle C hjørne skal være pastel-mint (fyldt firkant)", mintArgb, cellCorner(18, 3))

        // Celle D (week 19, dayOfWeek 7): i dag (accent-ramme på kanten)
        val cellDX = 19 * (cellSizePx + cellGapPx)
        val cellDY = (7 - 1) * (cellSizePx + cellGapPx)
        val cellDEdge = cellPixel(cellDX + strokeWidthPx / 2f, cellDY + cellSizePx / 2f)
        assertEquals("Celle D kant skal have accent-ramme", accentArgb, cellDEdge)

        // Celle E (week 14, dayOfWeek 1): ældre end 30 dage uden historikadgang (stiplet line-kant og surface-baggrund indeni)
        val cellEX = 14 * (cellSizePx + cellGapPx)
        val cellEY = (1 - 1) * (cellSizePx + cellGapPx)
        val topEdgeY = (cellEY + strokeWidthPx / 2f).roundToInt().coerceIn(0, img.height - 1)
        val topEdgePixels = (cellEX.roundToInt()..(cellEX + cellSizePx - 1f).roundToInt()).map { px ->
            pixels[topEdgeY * img.width + px.coerceIn(0, img.width - 1)]
        }
        assertTrue("Celle E øverste kant skal indeholde line-streger", topEdgePixels.contains(lineArgb))
        assertTrue("Celle E øverste kant skal være stiplet (indeholde Surface-mellemrum)", topEdgePixels.contains(surfaceArgb))
        assertEquals("Celle E indre skal have Surface-baggrund (ikke fyldt)", surfaceArgb, cellCenter(14, 1))
    }

    /** ActivityGridCard vises på side 1 ("træning") i HomeScreen. */
    @Test
    fun activityGridCardIsPresentOnHomeScreenPageOne() {
        val entry = AppEntry("app.darkio/app.darkio.MainActivity#0", "DarkIO")
        rule.setContent {
            HomeScreen(
                apps = splitApps(listOf(entry), emptySet()),
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

        // Swipe til side 1 ("træning", pager-index 1)
        rule.onNodeWithTag("homePager").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        // Aktivitetsgitteret er synligt på træningssiden
        rule.onNodeWithTag("card_ActivityGrid").assertIsDisplayed()
    }
}
