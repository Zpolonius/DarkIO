package app.darkio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.data.AppEntry
import app.darkio.data.MediaNow
import app.darkio.data.NowPlaying
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import app.darkio.ui.theme.DarkIOSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** F25, F15: medier-siden med Spiller nu og brugerens egne medie-apps. */
class MediaPageUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alfa = AppEntry("alfa/A#0", "Alfa")
    private val bravo = AppEntry("bravo/B#0", "Bravo")
    private val installed = listOf(alfa, bravo)
    private var media by mutableStateOf(emptyList<String>())
    private var launched = emptyList<String>()
    private var grantTaps = 0

    private fun show(now: NowPlaying) {
        rule.setContent {
            HomeScreen(
                apps = splitApps(installed, emptySet()).copy(mediaApps = media.mapNotNull { id -> installed.firstOrNull { it.id == id } }),
                hintSeen = true,
                onHintSeen = {}, onLaunch = { launched = launched + it.id }, onSetHidden = { _, _ -> }, onSetFavorites = {},
                onSetCategory = { _, _ -> }, onAppInfo = {}, onUninstall = {},
                onToggleMedia = { app, add -> media = if (add) media + app.id else media - app.id },
                mediaOverride = MediaNow(now),
                onGrantMediaAccess = { grantTaps++ },
            )
        }
    }

    private fun swipeToMedia() = repeat(3) {
        rule.onNodeWithTag("homePager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
    }

    private fun assertBelowMiddle(tag: String) {
        val middle = rule.onRoot().getUnclippedBoundsInRoot().height / 2
        val top: Dp = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top
        assertTrue("$tag (top $top) skal ligge under skærmens midte ($middle)", top >= middle)
    }

    @Test
    fun swipeReachesMediaPageWithEmptyHintAndDiscreetGrantButton() {
        show(NowPlaying.NoAccess)
        swipeToMedia()
        rule.onNodeWithTag("page3").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.page_media)).assertIsDisplayed()
        rule.onNodeWithTag("pageDot3").assertIsSelected()
        rule.onNodeWithTag("mediaEmptyHint").assertIsDisplayed()
        rule.onNodeWithTag("shortcuts").assertIsDisplayed()
        listOf("mediaAdd", "mediaGrant").forEach { assertBelowMiddle(it) }
        assertTrue(rule.onNodeWithTag("mediaGrant").getUnclippedBoundsInRoot().height >= DarkIOSize.TouchMin)
        rule.onNodeWithTag("mediaGrant").performClick()
        assertEquals(1, grantTaps)
    }

    @Test
    fun addAppFromPickerAndMenuLaunchItAndRemoveItAgain() {
        show(NowPlaying.Idle)
        rule.onNodeWithTag("pageDot3").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("mediaIdle").assertIsDisplayed()
        // Tilføj fra siden: knappen åbner app-listen, et tryk vælger appen.
        rule.onNodeWithTag("mediaAdd").performClick()
        rule.onNodeWithTag("mediaPicker").assertIsDisplayed()
        rule.onNodeWithText("Alfa").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("mediaPicker").assertDoesNotExist()
        rule.onNodeWithTag("mediaApp_${alfa.id}").assertIsDisplayed()
        rule.onNodeWithTag("mediaEmptyHint").assertDoesNotExist()
        assertBelowMiddle("mediaApp_${alfa.id}")
        rule.onNodeWithTag("mediaApp_${alfa.id}").performClick()
        assertEquals(listOf(alfa.id), launched)
        // Tilføj via app-menuen fra skuffen.
        rule.onNodeWithTag("homePager").performTouchInput { swipeUp() }
        rule.onNodeWithText("Bravo").performTouchInput { longClick() }
        rule.onNodeWithText(context.getString(R.string.menu_media)).performClick()
        rule.onNodeWithTag("appMenu").assertDoesNotExist()
        rule.onNodeWithTag("drawerGrip").performTouchInput { swipe(Offset(width / 2f, height / 2f), Offset(width / 2f, height / 2f + 200f)) }
        rule.waitForIdle()
        rule.onNodeWithTag("mediaApp_${bravo.id}").assertIsDisplayed()
        // Fjern fra siden: langt tryk åbner menuen, som nu tilbyder at fjerne.
        rule.onNodeWithTag("mediaApp_${alfa.id}").performTouchInput { longClick() }
        rule.onNodeWithText(context.getString(R.string.menu_unmedia)).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("mediaApp_${alfa.id}").assertDoesNotExist()
        rule.onNodeWithTag("mediaApp_${bravo.id}").assertIsDisplayed()
        assertEquals(listOf(bravo.id), media)
    }

    @Test
    fun playingCardShowsTextAndHasThumbReachableControls() {
        show(NowPlaying.Playing("Hvidt støj", "Podcast Dansk", "Spotify", isPlaying = true))
        rule.onNodeWithTag("pageDot3").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Hvidt støj").assertIsDisplayed()
        rule.onNodeWithText("Podcast Dansk · Spotify").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.media_pause)).assertIsDisplayed()
        listOf("mediaPrev", "mediaPlayPause", "mediaNext", "mediaAdd").forEach { assertBelowMiddle(it) }
        listOf("mediaPrev", "mediaPlayPause", "mediaNext").forEach {
            assertTrue("$it skal være mindst 44 dp", rule.onNodeWithTag(it).getUnclippedBoundsInRoot().height >= DarkIOSize.TouchMin)
        }
    }
}
