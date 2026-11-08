package app.darkio.drawer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.darkio.data.AppCategory
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOShapes
import app.darkio.ui.theme.DarkIOSize

/** Bundbar i skuffen med 7 kategori-ikoner (mindst 48 dp trykflade). */
@Composable
fun CategoryBar(selectedCategory: AppCategory, onSelectCategory: (AppCategory) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(DarkIOSize.TouchTarget), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
        AppCategory.entries.forEach { cat ->
            val sel = cat == selectedCategory
            val desc = stringResource(cat.labelRes)
            Box(
                Modifier.size(DarkIOSize.TouchTarget).clip(DarkIOShapes.Pill)
                    .then(if (sel) Modifier.background(DarkIOColors.Accent) else Modifier)
                    .clickable(role = Role.Tab) { onSelectCategory(cat) }
                    .semantics { contentDescription = desc; selected = sel },
                Alignment.Center,
            ) { CategoryIcon(cat, tint = if (sel) DarkIOColors.Bg else DarkIOColors.InkMuted) }
        }
    }
}

/** Stregikoner på et 24-gitter med glyph-stroke 1.9, runde ender og hjørner, ingen fyld. */
@Composable
fun CategoryIcon(category: AppCategory, tint: Color, modifier: Modifier = Modifier) {
    val strokePx = with(LocalDensity.current) { DarkIOSize.GlyphStroke.toPx() }
    Canvas(modifier.size(DarkIOSize.GlyphSize)) {
        val s = size.width / 24f
        fun x(v: Float) = v * s
        fun y(v: Float) = v * s
        val stroke = Stroke(strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (category) {
            AppCategory.ALL -> listOf(Offset(x(3.5f), y(3.5f)), Offset(x(14.5f), y(3.5f)), Offset(x(3.5f), y(14.5f)), Offset(x(14.5f), y(14.5f))).forEach {
                drawRoundRect(tint, it, Size(x(6f), y(6f)), CornerRadius(x(1.5f), y(1.5f)), stroke)
            }
            AppCategory.SOCIAL -> drawPath(Path().apply {
                moveTo(x(7f), y(5f)); lineTo(x(17f), y(5f)); quadraticTo(x(20f), y(5f), x(20f), y(8f))
                lineTo(x(20f), y(13f)); quadraticTo(x(20f), y(16f), x(17f), y(16f)); lineTo(x(11f), y(16f))
                lineTo(x(6f), y(19.5f)); lineTo(x(7.5f), y(16f)); lineTo(x(7f), y(16f))
                quadraticTo(x(4f), y(16f), x(4f), y(13f)); lineTo(x(4f), y(8f)); quadraticTo(x(4f), y(5f), x(7f), y(5f)); close()
            }, tint, style = stroke)
            AppCategory.GAME -> {
                drawPath(Path().apply {
                    moveTo(x(7f), y(8f)); lineTo(x(17f), y(8f)); quadraticTo(x(21f), y(8f), x(21f), y(12f))
                    lineTo(x(20f), y(16f)); quadraticTo(x(19f), y(18.5f), x(17f), y(18.5f))
                    quadraticTo(x(15f), y(18.5f), x(14f), y(15f)); lineTo(x(10f), y(15f))
                    quadraticTo(x(9f), y(18.5f), x(7f), y(18.5f)); quadraticTo(x(5f), y(18.5f), x(4f), y(16f))
                    lineTo(x(3f), y(12f)); quadraticTo(x(3f), y(8f), x(7f), y(8f)); close()
                }, tint, style = stroke)
                drawLine(tint, Offset(x(6.5f), y(12f)), Offset(x(9.5f), y(12f)), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(x(8f), y(10.5f)), Offset(x(8f), y(13.5f)), strokePx, StrokeCap.Round)
                drawCircle(tint, x(1f), Offset(x(15.5f), y(11f)), style = stroke)
                drawCircle(tint, x(1f), Offset(x(17.5f), y(13f)), style = stroke)
            }
            AppCategory.PHOTO -> {
                drawRoundRect(tint, Offset(x(3.5f), y(8f)), Size(x(17f), y(11f)), CornerRadius(x(2.5f), y(2.5f)), stroke)
                drawCircle(tint, x(3.5f), Offset(x(12f), y(13.5f)), style = stroke)
                drawPath(Path().apply {
                    moveTo(x(8f), y(8f)); lineTo(x(9.5f), y(5.5f)); lineTo(x(14.5f), y(5.5f)); lineTo(x(16f), y(8f))
                }, tint, style = stroke)
            }
            AppCategory.WORK -> {
                drawRoundRect(tint, Offset(x(3.5f), y(8f)), Size(x(17f), y(11f)), CornerRadius(x(2.5f), y(2.5f)), stroke)
                drawPath(Path().apply {
                    moveTo(x(9f), y(8f)); lineTo(x(9f), y(5.5f)); quadraticTo(x(9f), y(5f), x(9.5f), y(5f))
                    lineTo(x(14.5f), y(5f)); quadraticTo(x(15f), y(5f), x(15f), y(5.5f)); lineTo(x(15f), y(8f))
                }, tint, style = stroke)
                drawLine(tint, Offset(x(3.5f), y(12f)), Offset(x(20.5f), y(12f)), strokePx)
                drawRoundRect(tint, Offset(x(10.5f), y(11f)), Size(x(3f), y(2.5f)), CornerRadius(x(0.8f), y(0.8f)), stroke)
            }
            AppCategory.TOOL -> {
                drawCircle(tint, x(2.2f), Offset(x(6f), y(18f)), style = stroke)
                drawLine(tint, Offset(x(7.5f), y(16.5f)), Offset(x(14f), y(10f)), strokePx, StrokeCap.Round)
                drawPath(Path().apply {
                    moveTo(x(12.5f), y(10f)); lineTo(x(12f), y(6.5f)); lineTo(x(15f), y(4.5f))
                    lineTo(x(17f), y(6.5f)); lineTo(x(19.5f), y(9f)); lineTo(x(17.5f), y(12f)); lineTo(x(14f), y(11.5f))
                }, tint, style = stroke)
            }
            AppCategory.OTHER -> listOf(x(6f), x(12f), x(18f)).forEach {
                drawCircle(tint, x(1.6f), Offset(it, y(12f)), style = stroke)
            }
        }
    }
}
