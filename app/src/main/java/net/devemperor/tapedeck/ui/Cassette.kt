package net.devemperor.tapedeck.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** The shell colours of mixtapes, one per tape, picked in turn (see Library.MIXTAPE_COLORS). */
val MixtapeColors = listOf(
    Color(0xFFFF6B57), // Tapedeck coral
    Color(0xFF2EC4B6), // teal
    Color(0xFFFFB547), // amber
    Color(0xFF9B7BFF), // violet
    Color(0xFF4DA8FF), // sky
    Color(0xFF7BD389), // mint
)

fun mixtapeColor(index: Int): Color = MixtapeColors[Math.floorMod(index, MixtapeColors.size)]

private val LabelColor = Color(0xFFF4EDE3)
private val InkColor = Color(0xFF2B2622)
private val WindowColor = Color(0xFF231F1C)
private val TapeColor = Color(0xFF5B4535)

/**
 * A compact cassette with the mixtape's name on its label. The reels turn while [spinning] (a song of
 * this mixtape is playing), and [progress] winds the tape from the left reel onto the right one.
 */
@Composable
fun Cassette(name: String, color: Color, modifier: Modifier = Modifier, spinning: Boolean = false, progress: Float = 0f) {
    val angle = if (spinning) {
        val transition = rememberInfiniteTransition(label = "reels")
        transition.animateFloat(0f, 360f, infiniteRepeatable(tween(2_400, easing = LinearEasing)), label = "reel-angle").value
    } else {
        0f
    }
    val wound by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "tape")
    BoxWithConstraints(modifier.aspectRatio(1.58f)) {
        val w = maxWidth
        Canvas(Modifier.fillMaxSize()) { drawCassette(color, angle, wound) }
        // The name is written on the label, above the window.
        Text(
            name,
            color = InkColor,
            fontSize = (w.value * 0.075f).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().offset(y = w * 0.085f).padding(horizontal = w * 0.12f),
        )
    }
}

private fun DrawScope.drawCassette(color: Color, angle: Float, wound: Float) {
    val w = size.width
    val h = size.height
    val dark = lerp(color, Color.Black, 0.28f)

    // Shell, with its four screws.
    drawRoundRect(color, cornerRadius = CornerRadius(w * 0.07f))
    listOf(Offset(0.045f, 0.07f), Offset(0.955f, 0.07f), Offset(0.045f, 0.93f), Offset(0.955f, 0.93f)).forEach {
        drawCircle(dark, radius = w * 0.016f, center = Offset(it.x * w, it.y * h))
    }

    // Label with a coloured stripe and the window for the reels.
    val labelTop = h * 0.08f
    drawRoundRect(LabelColor, topLeft = Offset(w * 0.08f, labelTop), size = Size(w * 0.84f, h * 0.58f), cornerRadius = CornerRadius(w * 0.035f))
    drawRect(color.copy(alpha = 0.85f), topLeft = Offset(w * 0.08f, h * 0.555f), size = Size(w * 0.84f, h * 0.05f))
    val window = Size(w * 0.5f, h * 0.24f)
    val windowTopLeft = Offset((w - window.width) / 2, h * 0.29f)
    drawRoundRect(WindowColor, topLeft = windowTopLeft, size = window, cornerRadius = CornerRadius(window.height / 2))

    // The tape: a spool on each reel, the left one emptying as the right one fills.
    val centreY = windowTopLeft.y + window.height / 2
    val left = Offset(w * 0.37f, centreY)
    val right = Offset(w * 0.63f, centreY)
    val hub = w * 0.042f
    val full = w * 0.1f
    val leftSpool = hub + (full - hub) * (1f - wound)
    val rightSpool = hub + (full - hub) * wound
    // A full spool is larger than the window, as on a real cassette: only the part behind the window shows.
    val windowPath = Path().apply { addRoundRect(RoundRect(Rect(windowTopLeft, window), CornerRadius(window.height / 2))) }
    clipPath(windowPath) {
        drawCircle(TapeColor, radius = leftSpool, center = left)
        drawCircle(TapeColor, radius = rightSpool, center = right)
    }
    reel(left, hub, angle)
    reel(right, hub, angle)

    // The bottom bridge where the head reads the tape.
    val bridge = Path().apply {
        moveTo(w * 0.2f, h)
        lineTo(w * 0.27f, h * 0.76f)
        lineTo(w * 0.73f, h * 0.76f)
        lineTo(w * 0.8f, h)
        close()
    }
    drawPath(bridge, dark)
    listOf(0.36f, 0.64f).forEach { drawCircle(lerp(dark, Color.Black, 0.35f), radius = w * 0.022f, center = Offset(w * it, h * 0.88f)) }
}

/** A white reel hub whose six teeth point into the dark spindle hole, turned by [angle]. */
private fun DrawScope.reel(centre: Offset, radius: Float, angle: Float) {
    drawCircle(Color.White, radius = radius, center = centre)
    drawCircle(WindowColor, radius = radius * 0.62f, center = centre)
    rotate(angle, pivot = centre) {
        for (i in 0 until 6) {
            val a = Math.toRadians(i * 60.0)
            val inner = Offset(centre.x + (radius * 0.3f * cos(a)).toFloat(), centre.y + (radius * 0.3f * sin(a)).toFloat())
            val outer = Offset(centre.x + (radius * 0.64f * cos(a)).toFloat(), centre.y + (radius * 0.64f * sin(a)).toFloat())
            drawLine(Color.White, inner, outer, strokeWidth = radius * 0.2f)
        }
    }
    drawCircle(lerpGrey, radius = radius, center = centre, style = Stroke(width = radius * 0.1f))
}

private val lerpGrey = Color(0xFFD9D4CE)
