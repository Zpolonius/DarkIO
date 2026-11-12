package app.darkio

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOMotion
import app.darkio.ui.theme.DarkIOSize
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Test

class TokensTest {
    private val json = File("../docs/tokens.json").takeIf { it.exists() } ?: File("docs/tokens.json")

    private val kotlinColors = mapOf(
        "bg" to DarkIOColors.Bg, "surface" to DarkIOColors.Surface, "surface-glass" to DarkIOColors.SurfaceGlass,
        "scrim-top" to DarkIOColors.ScrimTop, "scrim-bottom" to DarkIOColors.ScrimBottom, "scrim-sheet" to DarkIOColors.ScrimSheet,
        "line" to DarkIOColors.Line, "line-strong" to DarkIOColors.LineStrong,
        "ink" to DarkIOColors.Ink, "ink-soft" to DarkIOColors.InkSoft, "ink-muted" to DarkIOColors.InkMuted,
        "accent" to DarkIOColors.Accent, "pastel-coral" to DarkIOColors.Coral, "pastel-lilac" to DarkIOColors.Lilac,
        "pastel-sun" to DarkIOColors.Sun, "pastel-mint" to DarkIOColors.Mint, "pastel-sky" to DarkIOColors.Sky,
        "positive" to DarkIOColors.Positive, "danger" to DarkIOColors.Danger,
    )

    @Test
    fun everyColorTokenMatchesDarkIOColors() {
        val tokens = Regex(""""name":\s*"([a-z-]+)",\s*"value":\s*"([^"]+)"""")
            .findAll(json.readText()).associate { it.groupValues[1] to it.groupValues[2] }
            .filterKeys { it in kotlinColors }
        assertEquals(kotlinColors.keys, tokens.keys)
        tokens.forEach { (name, value) ->
            val resolved = Regex("""\{(.+)}""").matchEntire(value)?.let { tokens.getValue(it.groupValues[1]) } ?: value
            assertEquals(name, parse(resolved).toArgb(), kotlinColors.getValue(name).toArgb())
        }
    }

    @Test
    fun typewriterAndCursorTokensMatch() {
        val tokens = Regex(""""name":\s*"([a-z-]+)",\s*"value":\s*"([^"]+)"""")
            .findAll(json.readText()).associate { it.groupValues[1] to it.groupValues[2] }

        assertEquals("16ms", tokens["typewriter-char-ms"])
        assertEquals("1500ms", tokens["typewriter-max-ms"])
        assertEquals("530ms", tokens["typewriter-blink-ms"])
        assertEquals("7px", tokens["cursor-width"])
        assertEquals("15px", tokens["cursor-height"])

        assertEquals(16L, DarkIOMotion.TypewriterCharMs)
        assertEquals(1500L, DarkIOMotion.TypewriterMaxMs)
        assertEquals(530L, DarkIOMotion.TypewriterBlinkMs)
        assertEquals(7.dp, DarkIOSize.CursorWidth)
        assertEquals(15.dp, DarkIOSize.CursorHeight)
    }

    private fun parse(v: String): Color =
        if (v.startsWith("#")) Color(0xFF000000 or v.drop(1).toLong(16))
        else v.substringAfter("(").substringBefore(")").split(",").map { it.trim() }.let { (r, g, b, a) ->
            Color(r.toInt(), g.toInt(), b.toInt(), (a.toFloat() * 255).roundToInt())
        }
}
