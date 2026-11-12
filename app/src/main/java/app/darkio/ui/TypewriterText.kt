package app.darkio.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.darkio.ui.theme.DarkIOColors
import app.darkio.ui.theme.DarkIOMotion
import app.darkio.ui.theme.DarkIOSize
import app.darkio.ui.theme.DarkIOType
import java.time.LocalDate
import kotlinx.coroutines.delay

/**
 * Holder styr på hvilke skrivemaskinetekster der allerede er vist i dag.
 * Nulstilles automatisk ved datoskift (F19).
 */
object TypewriterPlayed {
    private var lastDate: LocalDate = LocalDate.MIN
    private val playedKeys = mutableSetOf<String>()

    @Synchronized
    fun hasPlayed(key: String, today: LocalDate = LocalDate.now()): Boolean {
        if (today != lastDate) {
            lastDate = today
            playedKeys.clear()
        }
        return key in playedKeys
    }

    @Synchronized
    fun markPlayed(key: String, today: LocalDate = LocalDate.now()) {
        if (today != lastDate) {
            lastDate = today
            playedKeys.clear()
        }
        playedKeys.add(key)
    }

    @Synchronized
    fun resetForTest() {
        lastDate = LocalDate.MIN
        playedKeys.clear()
    }
}

/** 16 ms pr. tegn og højst 1,5 s i alt; længere tekst skrives hurtigere (F19). */
fun calculateTypewriterCharDelay(length: Int): Long =
    if (length <= 0) 0L
    else minOf(DarkIOMotion.TypewriterCharMs, DarkIOMotion.TypewriterMaxMs / length)

/** Tjekker om systemets "Fjern animationer" er slået til (F19, N8). */
fun areAnimationsDisabled(context: Context): Boolean = try {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
} catch (_: Throwable) {
    false
}

/**
 * Skrivemaskine-effekt (F19):
 * Tekst skrives frem med 16 ms pr. tegn og højst 1,5 s i alt, med en 7 × 15 dp accent-markør
 * der blinker hvert 530 ms bagefter. Et tryk viser hele teksten straks.
 * Kører én gang pr. tekst pr. dag. Med "Fjern animationer" vises teksten straks og markøren står stille.
 * Skærmlæseren får hele teksten med det samme.
 */
@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    prefix: String = "",
    prefixColor: Color = DarkIOColors.Accent,
    style: TextStyle = DarkIOType.Data.copy(color = DarkIOColors.Ink),
    now: () -> LocalDate = LocalDate::now,
    key: String = text,
    animationsDisabled: Boolean? = null,
    onTap: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val today = remember(now) { now() }
    val reduceMotion = animationsDisabled ?: remember(context) { areAnimationsDisabled(context) }
    val alreadyPlayed = remember(key, today) { TypewriterPlayed.hasPlayed(key, today) }

    var isTyping by remember(key, today) { mutableStateOf(!alreadyPlayed && !reduceMotion && text.isNotEmpty()) }
    var displayedChars by remember(key, today) {
        mutableIntStateOf(if (alreadyPlayed || reduceMotion) text.length else 0)
    }

    LaunchedEffect(key, today, text, reduceMotion) {
        if (alreadyPlayed || reduceMotion || text.isEmpty()) {
            displayedChars = text.length
            isTyping = false
            return@LaunchedEffect
        }
        val charDelay = calculateTypewriterCharDelay(text.length)
        while (displayedChars < text.length && isTyping) {
            delay(charDelay)
            if (isTyping) {
                displayedChars++
            }
        }
        if (isTyping) {
            isTyping = false
            TypewriterPlayed.markPlayed(key, today)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "cursorBlink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = (DarkIOMotion.TypewriterBlinkMs * 2).toInt() // 1060ms
                1f at 0
                1f at (DarkIOMotion.TypewriterBlinkMs - 1).toInt()
                0f at DarkIOMotion.TypewriterBlinkMs.toInt()
                0f at (DarkIOMotion.TypewriterBlinkMs * 2 - 1).toInt()
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "cursorAlpha",
    )

    val inlineContent = mapOf(
        "cursor" to InlineTextContent(
            Placeholder(
                width = (DarkIOSize.CursorWidth + 2.dp).value.sp,
                height = DarkIOSize.CursorHeight.value.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            ),
        ) {
            Box(
                Modifier
                    .padding(start = 2.dp)
                    .size(DarkIOSize.CursorWidth, DarkIOSize.CursorHeight)
                    .graphicsLayer {
                        alpha = if (!isTyping && !reduceMotion) cursorAlpha else 1f
                    }
                    .background(DarkIOColors.Accent),
            )
        },
    )

    val fullAccessibleText = prefix + text

    BasicText(
        text = buildAnnotatedString {
            if (prefix.isNotEmpty()) {
                withStyle(SpanStyle(color = prefixColor)) { append(prefix) }
            }
            append(text.take(displayedChars))
            if (text.isNotEmpty()) {
                appendInlineContent("cursor", alternateText = "_")
            }
        },
        style = style,
        inlineContent = inlineContent,
        modifier = modifier
            .semantics { contentDescription = fullAccessibleText }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (isTyping) {
                    displayedChars = text.length
                    isTyping = false
                    TypewriterPlayed.markPlayed(key, today)
                }
                onTap?.invoke()
            },
    )
}
