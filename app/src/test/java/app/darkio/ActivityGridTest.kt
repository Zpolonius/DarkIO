package app.darkio

import app.darkio.dashboard.DEFAULT_STEP_GOAL
import app.darkio.dashboard.buildActivityGrid
import app.darkio.dashboard.calculateGridMonday
import app.darkio.dashboard.parseDailyStepsFromGroups
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityGridTest {

    private val cphZone = ZoneId.of("Europe/Copenhagen")

    /** Dag-opdeling over sommertid (forår 23t og efterår 25t). */
    @Test
    fun daylightSavingSpringAndFallTimeDivision() {
        // Forårsovergang i Danmark (sidste søndag i marts): 2026-03-29
        val springDate = LocalDate.of(2026, 3, 29)
        val springStart = springDate.atStartOfDay(cphZone).toInstant()
        val springEnd = springDate.plusDays(1).atStartOfDay(cphZone).toInstant()
        val springHours = Duration.between(springStart, springEnd).toHours()
        assertEquals(23L, springHours)

        // Efterårsovergang i Danmark (sidste søndag i oktober): 2026-10-25
        val fallDate = LocalDate.of(2026, 10, 25)
        val fallStart = fallDate.atStartOfDay(cphZone).toInstant()
        val fallEnd = fallDate.plusDays(1).atStartOfDay(cphZone).toInstant()
        val fallHours = Duration.between(fallStart, fallEnd).toHours()
        assertEquals(25L, fallHours)

        // Verificér at gitteret opdeler dagene uafbrudt over forårsovergangen uden tidsforskydning
        val aprilToday = LocalDate.of(2026, 4, 15)
        val springGrid = buildActivityGrid(
            today = aprilToday,
            stepGoal = 10_000L,
            stepsByDate = mapOf(springDate to 10_000L),
            completedWorkoutDates = emptySet(),
            hasHistoryAccess = true,
        )
        assertEquals(140, springGrid.cells.size)
        val springCell = springGrid.cells.first { it.date == springDate }
        assertEquals(7, springCell.dayOfWeek) // Søndag
        assertTrue(springCell.isGoalReached)

        // Verificér at gitteret opdeler dagene uafbrudt over efterårsovergangen
        val novToday = LocalDate.of(2026, 11, 15)
        val fallGrid = buildActivityGrid(
            today = novToday,
            stepGoal = 10_000L,
            stepsByDate = mapOf(fallDate to 10_000L),
            completedWorkoutDates = emptySet(),
            hasHistoryAccess = true,
        )
        assertEquals(140, fallGrid.cells.size)
        val fallCell = fallGrid.cells.first { it.date == fallDate }
        assertEquals(7, fallCell.dayOfWeek) // Søndag
        assertTrue(fallCell.isGoalReached)

        // Alle celler er fortløbende med præcis 1 dags forskel
        for (i in 0 until fallGrid.cells.size - 1) {
            assertEquals(fallGrid.cells[i].date.plusDays(1), fallGrid.cells[i + 1].date)
        }

        // Test at parseDailyStepsFromGroups mapper aggregations-starttider til korrekte kalenderdatoer
        // selv ved 23 timers (forår) og 25 timers (efterår) dage i Europe/Copenhagen
        val groups = listOf(
            springDate.atStartOfDay() to 11_500L,
            fallDate.atStartOfDay() to 10_200L,
        )
        val mappedSteps = parseDailyStepsFromGroups(groups)
        assertEquals(11_500L, mappedSteps[springDate])
        assertEquals(10_200L, mappedSteps[fallDate])
    }

    /** Præcis 10.000 tæller som nået (9.999 gør ikke). */
    @Test
    fun exact10000StepsCountsAsReachedAnd9999DoesNot() {
        val today = LocalDate.of(2026, 11, 15)
        val yesterday = today.minusDays(1)

        val steps = mapOf(
            today to 10_000L,
            yesterday to 9_999L,
        )

        val grid = buildActivityGrid(
            today = today,
            stepGoal = DEFAULT_STEP_GOAL,
            stepsByDate = steps,
            completedWorkoutDates = emptySet(),
            hasHistoryAccess = true,
        )

        val todayCell = grid.cells.first { it.date == today }
        val yesterdayCell = grid.cells.first { it.date == yesterday }

        assertTrue(todayCell.isGoalReached)
        assertFalse(yesterdayCell.isGoalReached)
        assertEquals(1, grid.goalDaysCount)
    }

    /** Uge starter mandag (række 1..7 svarende til man..søn). */
    @Test
    fun weekStartsOnMonday() {
        val today = LocalDate.of(2026, 11, 15) // Søndag
        val grid = buildActivityGrid(
            today = today,
            stepGoal = 10_000L,
            stepsByDate = emptyMap(),
            completedWorkoutDates = emptySet(),
            hasHistoryAccess = true,
        )

        assertEquals(140, grid.cells.size)

        for (week in 0 until 20) {
            val weekCells = grid.cells.filter { it.weekIndex == week }
            assertEquals(7, weekCells.size)
            assertEquals(DayOfWeek.MONDAY, weekCells[0].date.dayOfWeek)
            assertEquals(1, weekCells[0].dayOfWeek)
            assertEquals(DayOfWeek.SUNDAY, weekCells[6].date.dayOfWeek)
            assertEquals(7, weekCells[6].dayOfWeek)
        }

        // Nyeste uge er uge 19 (til højre)
        val latestWeek = grid.cells.filter { it.weekIndex == 19 }
        assertTrue(latestWeek.any { it.isToday })
        assertEquals(calculateGridMonday(today), latestWeek.first().date)
    }

    /** Tomme dage (hverken nået skridtmål eller gennemført træning). */
    @Test
    fun emptyDaysAreNeitherGoalReachedNorWorkoutCompleted() {
        val today = LocalDate.of(2026, 11, 15)
        val grid = buildActivityGrid(
            today = today,
            stepGoal = 10_000L,
            stepsByDate = emptyMap(),
            completedWorkoutDates = emptySet(),
            hasHistoryAccess = true,
        )

        assertEquals(0, grid.goalDaysCount)
        assertEquals(0, grid.workoutsCount)

        val todayCell = grid.cells.first { it.date == today }
        assertFalse(todayCell.isGoalReached)
        assertFalse(todayCell.isWorkoutCompleted)
        assertTrue(todayCell.isToday)
        assertFalse(todayCell.isFuture)
    }

    /** Ændret skridtmål opdaterer måldage korrekt. */
    @Test
    fun changingStepGoalUpdatesGoalReached() {
        val today = LocalDate.of(2026, 11, 15)
        val steps = mapOf(today to 8_000L)

        // Med standard 10.000 er 8.000 ikke nået
        val grid10k = buildActivityGrid(
            today = today,
            stepGoal = 10_000L,
            stepsByDate = steps,
            completedWorkoutDates = emptySet(),
            hasHistoryAccess = true,
        )
        assertFalse(grid10k.cells.first { it.date == today }.isGoalReached)
        assertEquals(0, grid10k.goalDaysCount)

        // Med ændret mål til 8.000 er 8.000 nået
        val grid8k = buildActivityGrid(
            today = today,
            stepGoal = 8_000L,
            stepsByDate = steps,
            completedWorkoutDates = emptySet(),
            hasHistoryAccess = true,
        )
        assertTrue(grid8k.cells.first { it.date == today }.isGoalReached)
        assertEquals(1, grid8k.goalDaysCount)

        // Med mål på 5.000 er 8.000 også nået
        val grid5k = buildActivityGrid(
            today = today,
            stepGoal = 5_000L,
            stepsByDate = steps,
            completedWorkoutDates = emptySet(),
            hasHistoryAccess = true,
        )
        assertTrue(grid5k.cells.first { it.date == today }.isGoalReached)
        assertEquals(1, grid5k.goalDaysCount)
    }

    /** Dage ældre end 30 dage (date < today.minusDays(30)) uden historikadgang mangler historik. */
    @Test
    fun olderThan30DaysWithoutHistoryAccessRequiresPermission() {
        val today = LocalDate.of(2026, 11, 15)
        val cutoff = today.minusDays(30) // 2026-10-16
        val oldDate = today.minusDays(31) // 2026-10-15 (< cutoff)
        val recentDate = today.minusDays(29) // 2026-10-17 (>= cutoff)

        val steps = mapOf(
            oldDate to 15_000L,
            cutoff to 12_000L,
            recentDate to 11_000L,
        )

        // UDEN historikadgang: dage ældre end 30 dage markeres med lacksHistory
        val gridNoHistory = buildActivityGrid(
            today = today,
            stepGoal = 10_000L,
            stepsByDate = steps,
            completedWorkoutDates = emptySet(),
            hasHistoryAccess = false,
        )

        val oldCellNoHist = gridNoHistory.cells.first { it.date == oldDate }
        val cutoffCellNoHist = gridNoHistory.cells.first { it.date == cutoff }
        val recentCellNoHist = gridNoHistory.cells.first { it.date == recentDate }

        assertTrue(oldCellNoHist.lacksHistory)
        assertEquals(0L, oldCellNoHist.steps) // Udelades uden adgang
        assertFalse(oldCellNoHist.isGoalReached)

        assertFalse(cutoffCellNoHist.lacksHistory)
        assertTrue(cutoffCellNoHist.isGoalReached)

        assertFalse(recentCellNoHist.lacksHistory)
        assertTrue(recentCellNoHist.isGoalReached)

        // MED historikadgang: ældre dage har adgang og tæller med
        val gridWithHistory = buildActivityGrid(
            today = today,
            stepGoal = 10_000L,
            stepsByDate = steps,
            completedWorkoutDates = emptySet(),
            hasHistoryAccess = true,
        )

        val oldCellWithHist = gridWithHistory.cells.first { it.date == oldDate }
        assertFalse(oldCellWithHist.lacksHistory)
        assertEquals(15_000L, oldCellWithHist.steps)
        assertTrue(oldCellWithHist.isGoalReached)
    }

    /** Fremtidige dage udelades og tælles ikke med i periodens tal. */
    @Test
    fun futureDaysAreOmittedAndNotCounted() {
        val wednesday = LocalDate.of(2026, 11, 11) // Onsdag
        val thursday = wednesday.plusDays(1) // Torsdag (fremtid)

        val grid = buildActivityGrid(
            today = wednesday,
            stepGoal = 10_000L,
            stepsByDate = mapOf(thursday to 12_000L),
            completedWorkoutDates = setOf(thursday),
            hasHistoryAccess = true,
        )

        val thursCell = grid.cells.first { it.date == thursday }
        assertTrue(thursCell.isFuture)
        assertFalse(thursCell.isGoalReached)
        assertFalse(thursCell.isWorkoutCompleted)
        assertEquals(0, grid.goalDaysCount)
        assertEquals(0, grid.workoutsCount)
    }

    /** Træninger tælles korrekt for 20-ugers perioden. */
    @Test
    fun workoutsCountAndBothGoalAndWorkoutOnSameDay() {
        val today = LocalDate.of(2026, 11, 15)
        val yesterday = today.minusDays(1)
        val outsidePeriod = today.minusWeeks(25)

        val grid = buildActivityGrid(
            today = today,
            stepGoal = 10_000L,
            stepsByDate = mapOf(today to 10_000L, yesterday to 5_000L),
            completedWorkoutDates = setOf(today, yesterday, outsidePeriod),
            hasHistoryAccess = true,
        )

        val todayCell = grid.cells.first { it.date == today }
        assertTrue(todayCell.isGoalReached)
        assertTrue(todayCell.isWorkoutCompleted)

        val yesterdayCell = grid.cells.first { it.date == yesterday }
        assertFalse(yesterdayCell.isGoalReached)
        assertTrue(yesterdayCell.isWorkoutCompleted)

        assertEquals(1, grid.goalDaysCount)
        assertEquals(2, grid.workoutsCount) // today + yesterday (outsidePeriod ikke talt med)
    }
}
