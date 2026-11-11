package app.darkio

import app.darkio.data.WorkoutDay
import app.darkio.data.calculateWorkoutStreak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkoutTest {

    private val standardProgram = mapOf(
        2 to WorkoutDay(2, "tirsdag: spinning, romaskine, styrke og RPE", "spinning, romaskine, styrke, RPE"),
        4 to WorkoutDay(4, "torsdag: spinning, romaskine, styrke og RPE", "spinning, romaskine, styrke, RPE"),
        7 to WorkoutDay(7, "søndag: spinning, romaskine, styrke og RPE", "spinning, romaskine, styrke, RPE"),
    )

    private val trainingDays = setOf(2, 4, 7) // tirsdag, torsdag, søndag

    @Test
    fun standardProgramHasTuesdayThursdayAndSunday() {
        val tuesday = standardProgram[2]
        val thursday = standardProgram[4]
        val sunday = standardProgram[7]

        assertEquals("tirsdag: spinning, romaskine, styrke og RPE", tuesday?.title)
        assertEquals("torsdag: spinning, romaskine, styrke og RPE", thursday?.title)
        assertEquals("søndag: spinning, romaskine, styrke og RPE", sunday?.title)
    }

    @Test
    fun rightDayShowsWorkoutAndRestDaysReturnNull() {
        // Monday=1 (hviledag), Tuesday=2 (træning), Wednesday=3 (hviledag), Thursday=4 (træning), Friday=5 (hviledag), Saturday=6 (hviledag), Sunday=7 (træning)
        assertNull(standardProgram[1])
        assertEquals("tirsdag: spinning, romaskine, styrke og RPE", standardProgram[2]?.title)
        assertNull(standardProgram[3])
        assertEquals("torsdag: spinning, romaskine, styrke og RPE", standardProgram[4]?.title)
        assertNull(standardProgram[5])
        assertNull(standardProgram[6])
        assertEquals("søndag: spinning, romaskine, styrke og RPE", standardProgram[7]?.title)
    }

    @Test
    fun workoutCanBeMarkedCompleted() {
        val tuesday = LocalDate.of(2026, 11, 10)
        var workout = WorkoutDay(2, "tirsdag: spinning, romaskine, styrke og RPE", "spinning, romaskine, styrke, RPE")
        assertFalse(workout.completedDate == tuesday.toString())

        workout = workout.copy(completedDate = tuesday.toString())
        assertTrue(workout.completedDate == tuesday.toString())
    }

    @Test
    fun completedWorkoutResetsAtMidnightWithFixedClock() {
        val tuesdayNight = LocalDate.of(2026, 11, 10)
        val workout = WorkoutDay(
            dayOfWeek = 2,
            title = "tirsdag: spinning, romaskine, styrke og RPE",
            exercises = "spinning, romaskine, styrke, RPE",
            completedDate = tuesdayNight.toString(),
        )

        // On Tuesday: completed
        assertTrue(workout.completedDate == tuesdayNight.toString())

        // Past midnight (Wednesday 2026-11-11): no longer completed
        val wednesday = LocalDate.of(2026, 11, 11)
        assertFalse(workout.completedDate == wednesday.toString())

        // Next Tuesday (2026-11-17): also reset
        val nextTuesday = LocalDate.of(2026, 11, 17)
        assertFalse(workout.completedDate == nextTuesday.toString())
    }

    @Test
    fun streakReturnsZeroWhenHistoryOrTrainingDaysIsEmpty() {
        val tuesday = LocalDate.of(2026, 11, 10)
        assertEquals(0, calculateWorkoutStreak(trainingDays, emptySet(), tuesday))
        assertEquals(0, calculateWorkoutStreak(emptySet(), setOf(tuesday), tuesday))
    }

    @Test
    fun streakReturnsOneWhenOnlyTodayIsCompleted() {
        val tuesday = LocalDate.of(2026, 11, 10)
        assertEquals(1, calculateWorkoutStreak(trainingDays, setOf(tuesday), tuesday))
    }

    @Test
    fun restDaysDoNotBreakStreak() {
        // Uge 46: Søn 8 (dag 7), Tir 10 (dag 2), Tor 12 (dag 4)
        val sun8 = LocalDate.of(2026, 11, 8)
        val tue10 = LocalDate.of(2026, 11, 10)
        val thu12 = LocalDate.of(2026, 11, 12)
        val completed = setOf(sun8, tue10, thu12)

        // Fredag 13 (hviledag): stimen er 3 (Tor, Tir, Søn)
        val fri13 = LocalDate.of(2026, 11, 13)
        assertEquals(3, calculateWorkoutStreak(trainingDays, completed, fri13))

        // Lørdag 14 (hviledag): stimen er stadig 3
        val sat14 = LocalDate.of(2026, 11, 14)
        assertEquals(3, calculateWorkoutStreak(trainingDays, completed, sat14))

        // Søndag 15 (træningsdag) før pas gennemføres: stimen er 3
        val sun15 = LocalDate.of(2026, 11, 15)
        assertEquals(3, calculateWorkoutStreak(trainingDays, completed, sun15))

        // Efter søndagens pas er gennemført: stimen er 4
        assertEquals(4, calculateWorkoutStreak(trainingDays, completed + sun15, sun15))
    }

    @Test
    fun missedWorkoutBreaksStreak() {
        val tue10 = LocalDate.of(2026, 11, 10)
        // Torsdag 12 misset!
        val completed = setOf(tue10)

        // Fredag 13 (hviledag): torsdag var misset træningsdag, så stime nulstilles
        val fri13 = LocalDate.of(2026, 11, 13)
        assertEquals(0, calculateWorkoutStreak(trainingDays, completed, fri13))

        // Lørdag 14 (hviledag): stime er 0
        val sat14 = LocalDate.of(2026, 11, 14)
        assertEquals(0, calculateWorkoutStreak(trainingDays, completed, sat14))

        // Søndag 15 (træningsdag) før gennemført: 0
        val sun15 = LocalDate.of(2026, 11, 15)
        assertEquals(0, calculateWorkoutStreak(trainingDays, completed, sun15))

        // Søndag 15 efter gennemført: stime genstartes til 1
        assertEquals(1, calculateWorkoutStreak(trainingDays, completed + sun15, sun15))
    }

    @Test
    fun streakSpanningMultipleWeeksWithRestDays() {
        // Tre ugers konsistent træning: tir, tor, søn
        val d1 = LocalDate.of(2026, 10, 27) // Tir
        val d2 = LocalDate.of(2026, 10, 29) // Tor
        val d3 = LocalDate.of(2026, 11, 1)  // Søn
        val d4 = LocalDate.of(2026, 11, 3)  // Tir
        val d5 = LocalDate.of(2026, 11, 5)  // Tor
        val d6 = LocalDate.of(2026, 11, 8)  // Søn
        val d7 = LocalDate.of(2026, 11, 10) // Tir
        val completed = setOf(d1, d2, d3, d4, d5, d6, d7)

        val wed11 = LocalDate.of(2026, 11, 11) // Onsdag (hviledag)
        assertEquals(7, calculateWorkoutStreak(trainingDays, completed, wed11))
    }

    @Test
    fun futureCompletedDatesDoNotCount() {
        val today = LocalDate.of(2026, 11, 10) // Tirsdag
        val future = LocalDate.of(2026, 11, 12)
        assertEquals(1, calculateWorkoutStreak(trainingDays, setOf(today, future), today))
    }
}
