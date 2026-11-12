package app.darkio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.darkio.ui.TypewriterPlayed
import app.darkio.ui.TypewriterText
import java.time.LocalDate
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TypewriterUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val testDate = LocalDate.of(2026, 11, 12)

    @Before
    fun setUp() {
        TypewriterPlayed.resetForTest()
    }

    @Test
    fun fullTextAppearsAfterAtMost1500ms() {
        rule.mainClock.autoAdvance = false
        val sample = "Skriv testen først — koden følger"

        rule.setContent {
            TypewriterText(
                text = sample,
                key = "test-1",
                now = { testDate },
                animationsDisabled = false,
            )
        }

        // Før animationen er færdig findes hele teksten ikke som visuel tekst endnu
        rule.onNode(hasText(sample)).assertDoesNotExist()

        // Efter 1500 ms er hele teksten skrevet frem
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()

        rule.onNode(hasText(sample, substring = true)).assertIsDisplayed()
        rule.onNode(hasText("_", substring = true)).assertIsDisplayed()
    }

    @Test
    fun tapShowsFullTextImmediately() {
        rule.mainClock.autoAdvance = false
        val sample = "En god fejltekst fortæller hvad du kan gøre"

        rule.setContent {
            TypewriterText(
                text = sample,
                key = "test-tap",
                now = { testDate },
                animationsDisabled = false,
            )
        }

        rule.onNode(hasText(sample)).assertDoesNotExist()

        // Tryk springer over og viser hele teksten med det samme
        rule.onNode(hasContentDescription(sample)).performClick()
        rule.mainClock.advanceTimeBy(50)
        rule.waitForIdle()

        rule.onNode(hasText(sample, substring = true)).assertIsDisplayed()
    }

    @Test
    fun runsOnlyOncePerTextPerDay() {
        rule.mainClock.autoAdvance = false
        val sample = "Mindre kode er færre fejl"

        // Første gang: markér som spillet
        TypewriterPlayed.markPlayed("test-once", testDate)

        rule.setContent {
            TypewriterText(
                text = sample,
                key = "test-once",
                now = { testDate },
                animationsDisabled = false,
            )
        }

        // Da den allerede er spillet i dag, står teksten der med det samme (uden ventetid)
        rule.onNode(hasText(sample, substring = true)).assertIsDisplayed()
    }

    @Test
    fun reducedAnimationsShowsTextImmediatelyAndTalkBackGetsFullText() {
        val sample = "Hold animationer korte og diskrete"

        rule.setContent {
            TypewriterText(
                text = sample,
                key = "test-reduced",
                now = { testDate },
                animationsDisabled = true,
            )
        }

        // Vises straks
        rule.onNode(hasText(sample, substring = true)).assertIsDisplayed()

        // Skærmlæseren (TalkBack) får hele teksten med det samme
        rule.onNodeWithContentDescription(sample).assertIsDisplayed()
    }
}
