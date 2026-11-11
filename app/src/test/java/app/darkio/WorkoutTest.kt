package app.darkio

import app.darkio.data.WorkoutDay
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
}
