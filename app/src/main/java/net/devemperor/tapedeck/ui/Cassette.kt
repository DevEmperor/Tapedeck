package net.devemperor.tapedeck.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import androidx.compose.runtime.withFrameNanos
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

/** How fast the reels turn, in degrees per second. */
object ReelSpeed {
    /** Playing at 1×; scaled by the playback speed, so 1.5× turns visibly faster. */
    const val PLAY = 140f
    /** Fast forward / rewind while scrubbing the seek bar. */
    const val SCRUB = 900f
    /** A download coming in. */
    const val DOWNLOAD = 220f
}

private val LabelColor = Color(0xFFF4EDE3)
private val InkColor = Color(0xFF2B2622)
private val WindowColor = Color(0xFF231F1C)
private val TapeColor = Color(0xFF5B4535)
private val HubRing = Color(0xFFD9D4CE)
/** Where the label starts and the reel window begins, as parts of the cassette's height. */
private const val LABEL_TOP = 0.08f
private const val WINDOW_TOP = 0.32f
private val DeckTop = Color(0xFF34313A)
private val DeckBottom = Color(0xFF16141A)

/**
 * The reels' angle. The speed eases towards [velocity] (degrees per second, negative turns backwards):
 * they start up when playback starts and run down when it pauses, like a real deck — no jump to a halt.
 * The angle is a State so drawing reads it without recomposing; frames only run while the reels move.
 */
@Composable
fun rememberReelAngle(velocity: Float): State<Float> {
    val speed by animateFloatAsState(velocity, tween(700, easing = FastOutSlowInEasing), label = "reel-speed")
    val angle = remember { mutableFloatStateOf(0f) }
    val moving = velocity != 0f || speed != 0f
    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) angle.floatValue = (angle.floatValue + speed * (now - last) / 1_000_000_000f) % 360f
                last = now
            }
        }
    }
    return angle
}

/**
 * A compact cassette with [name] on its label. [velocity] turns the reels (see [ReelSpeed]) and
 * [progress] winds the tape from the left reel onto the right one.
 */
@Composable
fun Cassette(name: String, color: Color, modifier: Modifier = Modifier, velocity: Float = 0f, progress: Float = 0f) {
    val angle = rememberReelAngle(velocity)
    val wound by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "tape")
    BoxWithConstraints(modifier.aspectRatio(1.58f)) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) { drawCassette(color, angle.value, wound) }
        // The name is written on the label, in the app's own type (it has to stay readable), centred in the
        // strip between the label's top edge and the reel window — never touching the window.
        Box(
            Modifier.offset(y = h * LABEL_TOP + h * 0.015f).fillMaxWidth().height(h * (WINDOW_TOP - LABEL_TOP) - h * 0.03f)
                .padding(horizontal = w * 0.12f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name,
                color = InkColor,
                style = TextStyle(
                    fontSize = (w.value * 0.066f).sp,
                    lineHeight = (w.value * 0.074f).sp,
                    fontWeight = FontWeight.SemiBold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
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
    drawRoundRect(LabelColor, topLeft = Offset(w * 0.08f, h * LABEL_TOP), size = Size(w * 0.84f, h * 0.58f), cornerRadius = CornerRadius(w * 0.035f))
    drawRect(color.copy(alpha = 0.85f), topLeft = Offset(w * 0.08f, h * 0.575f), size = Size(w * 0.84f, h * 0.045f))
    val window = Size(w * 0.5f, h * 0.22f)
    val windowTopLeft = Offset((w - window.width) / 2, h * WINDOW_TOP)
    drawReelWindow(windowTopLeft, window, angle, wound, hub = w * 0.042f, full = w * 0.1f)

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

/**
 * The dark window with both reels: a spool on each, the left one emptying as the right one fills. A full
 * spool is larger than the window, as on a real cassette — only the part behind the window shows.
 */
private fun DrawScope.drawReelWindow(topLeft: Offset, window: Size, angle: Float, wound: Float, hub: Float, full: Float) {
    drawRoundRect(WindowColor, topLeft = topLeft, size = window, cornerRadius = CornerRadius(window.height / 2))
    val centreY = topLeft.y + window.height / 2
    val left = Offset(topLeft.x + window.width * 0.26f, centreY)
    val right = Offset(topLeft.x + window.width * 0.74f, centreY)
    val path = Path().apply { addRoundRect(RoundRect(Rect(topLeft, window), CornerRadius(window.height / 2))) }
    clipPath(path) {
        drawCircle(TapeColor, radius = hub + (full - hub) * (1f - wound), center = left)
        drawCircle(TapeColor, radius = hub + (full - hub) * wound, center = right)
    }
    drawReel(left, hub, angle)
    drawReel(right, hub, angle)
}

/** A white reel hub whose six teeth point into the dark spindle hole, turned by [angle]. */
private fun DrawScope.drawReel(centre: Offset, radius: Float, angle: Float) {
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
    drawCircle(HubRing, radius = radius, center = centre, style = Stroke(width = radius * 0.1f))
}

/**
 * A download as tape running from one reel to the other: the bar is the tape, the left spool empties
 * and the right one fills as the file comes in, and both turn while data flows.
 */
@Composable
fun TapeTransport(fraction: Float?, running: Boolean, modifier: Modifier = Modifier) {
    val angle = rememberReelAngle(if (running) ReelSpeed.DOWNLOAD else 0f)
    val wound by animateFloatAsState((fraction ?: 0f).coerceIn(0f, 1f), tween(600), label = "tape")
    val reel = 22.dp
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(reel)) { drawSpoolReel(angle.value, 1f - wound) }
        Spacer(Modifier.width(6.dp))
        TapeProgressBar(fraction, Modifier.weight(1f), active = running, height = 6.dp)
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.size(reel)) { drawSpoolReel(angle.value, wound) }
    }
}

/** One reel with its spool of tape, [amount] from bare hub (0) to full (1). */
private fun DrawScope.drawSpoolReel(angle: Float, amount: Float) {
    val c = center
    val full = size.minDimension / 2
    val hub = full * 0.45f
    drawCircle(TapeColor, radius = hub + (full - hub) * amount, center = c)
    drawReel(c, hub, angle)
}

/**
 * The player's tape deck for audio: the cassette (label = title, shell in the cover's colour) behind
 * the deck's smoked-glass door, a counter and a play light. Turns at the playback speed and runs down
 * on pause; while scrubbing it spools fast forward or back.
 */
@Composable
fun CassetteDeck(
    title: String,
    color: Color,
    velocity: Float,
    progress: Float,
    counter: String,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    // A deck is a device: the same dark gunmetal in the light and the dark theme.
    Box(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(DeckTop, DeckBottom))),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
            // Engraving and play light.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "TAPEDECK",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.5f),
                )
                Spacer(Modifier.weight(1f))
                val light = if (playing) Color(0xFFFF6B57) else Color.White.copy(alpha = 0.18f)
                Box(Modifier.size(8.dp).shadow(if (playing) 6.dp else 0.dp, CircleShape, ambientColor = light, spotColor = light).background(light, CircleShape))
            }
            Spacer(Modifier.height(12.dp))
            // The door: a recess with the cassette behind smoked glass.
            Box(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Cassette(title, color, Modifier.fillMaxWidth(0.86f).shadow(10.dp, RoundedCornerShape(12.dp)), velocity, progress)
                Box(
                    Modifier.matchParentSize().background(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.10f),
                            0.35f to Color.White.copy(alpha = 0.02f),
                            0.6f to Color.Transparent,
                        ),
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            // The tape counter (the right corner stays free for the cover/cassette switch).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    counter,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFFFD9A0),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "TYPE I · NORMAL",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = Color.White.copy(alpha = 0.42f),
                )
            }
        }
    }
}
