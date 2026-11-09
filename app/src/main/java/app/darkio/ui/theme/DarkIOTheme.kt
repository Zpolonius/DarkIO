package app.darkio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.darkio.R

// Spejler docs/tokens.json. Ret tokens.json først; TokensTest fanger afvigelser.

object DarkIOColors {
    val Bg = Color(0xFF0D0D11)
    val Surface = Color(0xFF16161C)
    val SurfaceGlass = Color(0xE616161C)
    val ScrimTop = Color(0xCC0D0D11)
    val ScrimBottom = Color(0xF20D0D11)
    val ScrimSheet = Color(0xCC0D0D11)
    val Line = Color(0xFF26262F)
    val LineStrong = Color(0xFF33333E)
    val Ink = Color(0xFFEDEDF2)
    val InkSoft = Color(0xFFC4C4CF)
    val InkMuted = Color(0xFFA0A0AE)
    val Accent = Color(0xFFC8F25A)
    val Coral = Color(0xFFFF8A5B)
    val Lilac = Color(0xFFC7B8FF)
    val Sun = Color(0xFFFFD66B)
    val Mint = Color(0xFF8BE3C1)
    val Sky = Color(0xFF8EC5FF)
    val Positive = Mint
    val Danger = Color(0xFFFF9E9E)
}

object DarkIOShapes {
    val Sm = RoundedCornerShape(8.dp)
    val Md = RoundedCornerShape(14.dp)
    val Lg = RoundedCornerShape(18.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

object DarkIOSpace {
    val S1 = 4.dp
    val S2 = 8.dp
    val S3 = 12.dp
    val S4 = 16.dp
    val S5 = 20.dp
    val S6 = 24.dp
    val TopInset = 60.dp
}

object DarkIOMotion {
    const val DrawerMs = 180
}

object DarkIOSize {
    val TouchMin = 44.dp
    val IconTile = 56.dp
    val IconTileSm = 44.dp
    val CardChip = 40.dp
    val FieldHeight = 56.dp
    val GripWidth = 40.dp
    val GripHeight = 4.dp
    val GlyphStroke = 1.9.dp
    val GlyphSize = 24.dp
    val TouchTarget = 48.dp
    val LineStroke = 1.dp
}

private val Display = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.space_grotesk, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)
private val Mono = FontFamily(
    Font(R.font.jetbrains_mono_regular),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

object DarkIOType {
    val Clock = TextStyle(fontFamily = Display, fontSize = 72.sp, lineHeight = 68.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-3).sp)
    val Title = TextStyle(fontFamily = Display, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
    val Metric = TextStyle(fontFamily = Display, fontSize = 24.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
    val Item = TextStyle(fontFamily = Display, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontFamily = Display, fontSize = 14.sp, lineHeight = 20.sp)
    val Input = TextStyle(fontFamily = Mono, fontSize = 15.sp, lineHeight = 20.sp)
    val Data = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 18.sp)
    val Button = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    val Label = TextStyle(fontFamily = Mono, fontSize = 11.sp, lineHeight = 14.sp)
}
