package app.darkio

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.data.AppEntry
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * F17, F20: Side 3 ("byg") viser dagens tip i stort format med kopier-knap i nederste halvdel,
 * og under det vises de to seneste tips.
 */
class BuildPageUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alfa = AppEntry("alfa/A#0", "Alfa")

    private fun show() {
        rule.setContent {
            HomeScreen(
                apps = splitApps(listOf(alfa), emptySet()).copy(favoriteSlots = mapOf(alfa.id to 0)),
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
        // Skift til side 3 ("byg")
        rule.onNodeWithTag("pageDot2").performClick()
        rule.waitForIdle()
    }

    @Test
    fun side3ShowsTodayTipAndCopyButton() {
        show()
        rule.onNodeWithTag("buildTodayTip").assertIsDisplayed()
        rule.onNodeWithTag("copyTipButton").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.tip_copy)).assertIsDisplayed()
    }

    @Test
    fun side3ShowsTwoRecentTipsUnderTodayTip() {
        show()
        val todayBounds = rule.onNodeWithTag("buildTodayTip").getUnclippedBoundsInRoot()
        val recentBounds = rule.onNodeWithTag("buildRecentTips").getUnclippedBoundsInRoot()

        rule.onNodeWithTag("buildRecentTips").assertIsDisplayed()
        rule.onNodeWithTag("recentTip0").assertIsDisplayed()
        rule.onNodeWithTag("recentTip1").assertIsDisplayed()

        // De to seneste tips vises under dagens tip
        assertTrue(
            "recentTips starter ikke under todayTip (today: ${todayBounds.bottom}, recent: ${recentBounds.top})",
            recentBounds.top >= todayBounds.bottom
        )
    }

    @Test
    fun copyButtonIsInBottomHalfAndHasAtLeast48dpTouchTarget() {
        show()
        val rootBounds = rule.onRoot().getUnclippedBoundsInRoot()
        val buttonBounds = rule.onNodeWithTag("copyTipButton").getUnclippedBoundsInRoot()
        val middle = rootBounds.height / 2

        // Kopier-knappen ligger i nederste halvdel
        assertTrue(
            "kopier-knap (${buttonBounds.top}) ligger ikke i nederste halvdel (midte: $middle)",
            buttonBounds.top >= middle
        )

        // Trykflade på mindst 48 dp
        assertTrue(
            "kopier-knap højde (${buttonBounds.height.value}) er under 48 dp",
            buttonBounds.height.value >= 47.9f
        )
    }

    @Test
    fun copyButtonCopiesTipToClipboardAndShowsConfirmation() {
        show()
        rule.onNodeWithTag("copyTipButton").performClick()
        rule.waitForIdle()

        // Tjek at udklipsholderen modtog teksten
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            assertNotNull("Udklipsholder må ikke være tom", clip)
            assertTrue("Udklipsholder skal indeholde mindst ét element", clip!!.itemCount > 0)
            val copiedText = clip.getItemAt(0).text.toString()
            assertTrue("Kopieret tekst må ikke være tom", copiedText.isNotBlank())
        }

        // Tjek bekræftelsestekst på knappen
        rule.onNodeWithText("// ${context.getString(R.string.tip_copied)}_").assertIsDisplayed()
    }
}
