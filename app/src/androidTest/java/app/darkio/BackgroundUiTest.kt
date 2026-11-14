package app.darkio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.darkio.data.AppEntry
import app.darkio.data.Settings
import app.darkio.data.splitApps
import app.darkio.home.HomeScreen
import app.darkio.ui.theme.DarkIOColors
import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * F21 og N8: toningen over brugerens baggrund. Android tegner den rigtige baggrund; her ligger en ensfarvet testbaggrund bag HomeScreen,
 * og pixlerne aflæses efter toningen. Den rigtige baggrund røres aldrig.
 */
class BackgroundUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val installed = listOf(AppEntry("a/A#0", "Alfa"))

    private fun show(wallpaper: Color, settings: Settings = Settings(), hintSeen: Boolean = true) = rule.setContent {
        Box(Modifier.fillMaxSize().background(wallpaper)) {
            HomeScreen(
                apps = splitApps(installed, emptySet()),
                hintSeen = hintSeen,
                onHintSeen = {},
                onLaunch = {},
                onSetHidden = { _, _ -> },
                onSetFavorites = {},
                onSetCategory = { _, _ -> },
                onAppInfo = {},
                onUninstall = {},
                settings = settings,
            )
        }
    }

    private fun pixels(tag: String) = pixelsOf(rule.onNodeWithTag(tag, useUnmergedTree = true).captureToImage())

    private fun screenPixels() = pixelsOf(rule.onRoot().captureToImage())

    private fun pixelsOf(img: androidx.compose.ui.graphics.ImageBitmap): Triple<IntArray, Int, Int> {
        val px = IntArray(img.width * img.height)
        img.readPixels(px, 0, 0, img.width, img.height)
        return Triple(px, img.width, img.height)
    }

    /** Baggrunden bag teksten i en node: den mest almindelige pixel (teksten fylder kun en lille del). */
    private fun background(px: IntArray) = px.groupBy { it }.maxByOrNull { it.value.size }!!.key

    private fun luminance(argb: Int): Double {
        fun lin(c: Int) = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lin((argb shr 16) and 0xFF) + 0.7152 * lin((argb shr 8) and 0xFF) + 0.0722 * lin(argb and 0xFF)
    }

    /** WCAG-kontrast. */
    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = luminance(a).let { la -> luminance(b).let { lb -> maxOf(la, lb) to minOf(la, lb) } }
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun red(argb: Int) = (argb shr 16) and 0xFF

    private fun assertReadable(wallpaperName: String, tag: String, text: Color) {
        val bg = background(pixels(tag).first)
        val ratio = contrast(text.toArgb(), bg)
        assertTrue("$tag på $wallpaperName: ${"%.2f".format(ratio)}:1", ratio >= 4.5)
    }

    private fun checkAllText(wallpaper: Color, name: String) {
        show(wallpaper, Settings().withScrimTop(60), hintSeen = false)
        rule.waitForIdle()
        // Tekst oven på kort og søgefelt (surface-glass).
        assertReadable(name, "card_Clock", DarkIOColors.InkMuted)
        assertReadable(name, "dock", DarkIOColors.InkSoft)
        assertReadable(name, "searchField", DarkIOColors.InkMuted)
        // Tekst direkte på baggrunden: sidelabel og hint.
        val hint = context.getString(R.string.hint_swipe_up)
        val hintBg = background(pixelsOf(rule.onNodeWithText(hint).captureToImage()).first)
        assertTrue("hint på $name", contrast(DarkIOColors.InkSoft.toArgb(), hintBg) >= 4.5)
    }

    /** Uden kort ligger sidelabelen direkte over toningen, ikke oven på et korts glas. */
    private fun checkPageLabel(wallpaper: Color, name: String) {
        show(wallpaper, Settings(cards = emptySet()).withScrimTop(60))
        rule.waitForIdle()
        assertReadable(name, "page0", DarkIOColors.InkSoft)
    }

    @Test
    fun pageLabelIsReadableOnWhiteWallpaper() = checkPageLabel(Color.White, "hvid")

    @Test
    fun pageLabelIsReadableOnBlackWallpaper() = checkPageLabel(Color.Black, "sort")

    @Test
    fun textIsReadableOnWhiteWallpaperAtMinimumScrim() = checkAllText(Color.White, "hvid")

    @Test
    fun textIsReadableOnBlackWallpaperAtMinimumScrim() = checkAllText(Color.Black, "sort")

    @Test
    fun scrimIs80PercentAtTop35InMiddleAnd95AtBottom() {
        show(Color.White)
        rule.waitForIdle()
        val (px, w, h) = screenPixels()
        val x = (10.dp.value * context.resources.displayMetrics.density).toInt().coerceAtMost(w - 1)
        // Hvid bag toning med dækning a giver rød = 255 - 242a.
        fun expected(a: Double) = 255 - 242 * a
        fun at(y: Int) = red(px[y * w + x]).toDouble()
        assertTrue("top ${at(2)}", abs(at(2) - expected(0.80)) <= 6)
        assertTrue("midt ${at(h / 2)}", abs(at(h / 2) - expected(0.35)) <= 6)
        assertTrue("bund ${at(h - 3)}", abs(at(h - 3) - expected(0.95)) <= 6)
    }

    @Test
    fun topScrimFollowsTheSetting() {
        show(Color.White, Settings().withScrimTop(60))
        rule.waitForIdle()
        val (px, w, _) = screenPixels()
        assertTrue("top ${red(px[2 * w + 5])}", abs(red(px[2 * w + 5]) - (255 - 242 * 0.60)) <= 6)
    }

    @Test
    fun drawerAndSearchAreFullyDark() {
        show(Color.White)
        rule.waitForIdle()
        fun probe(): Int {
            val (px, w, h) = screenPixels()
            return px[(h / 4) * w + 5]
        }
        assertTrue("hjem viser baggrunden", red(probe()) > 100)
        rule.onNodeWithTag("homePager").performTouchInput { swipeUp() }
        rule.onNodeWithTag("drawerGrid").assertExists()
        rule.waitForIdle()
        assertEquals(DarkIOColors.Bg.toArgb(), probe())
    }

    @Test
    fun searchResultsHideTheWallpaper() {
        show(Color.White)
        rule.onNodeWithTag("searchField").performTextInput("a")
        rule.waitForIdle()
        val (px, w, h) = screenPixels()
        assertEquals(DarkIOColors.Bg.toArgb(), px[(h / 4) * w + 5])
    }
}
