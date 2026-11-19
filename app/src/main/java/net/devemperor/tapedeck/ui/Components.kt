package net.devemperor.tapedeck.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.devemperor.tapedeck.Format
import net.devemperor.tapedeck.data.Entry
import java.io.File

/** Thumbnail with the length in the corner, like on YouTube, and an audio/video badge. */
@Composable
fun Thumbnail(entry: Entry, modifier: Modifier = Modifier, showDuration: Boolean = true, showKind: Boolean = true, corner: Dp = 12.dp) {
    val shape = RoundedCornerShape(corner)
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        val model: Any? = entry.thumbnailFile?.let(::File)?.takeIf { it.exists() } ?: entry.thumbnailUrl
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                CassetteIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.Center).size(28.dp),
            )
        }
        if (showKind) {
            Box(
                Modifier.align(Alignment.TopStart).padding(5.dp).size(20.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (entry.isVideo) Icons.Rounded.Movie else Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
        if (showDuration && entry.durationMs > 0) {
            Text(
                Format.clock(entry.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * The download bar: a coral-to-amber fill that eases to each new value, with a light sweeping over
 * it while data flows. Without a value (preparing, converting) a short segment glides back and forth.
 */
@Composable
fun TapeProgressBar(fraction: Float?, modifier: Modifier = Modifier, active: Boolean = true, height: Dp = 8.dp) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val start = MaterialTheme.colorScheme.primary
    val end = MaterialTheme.colorScheme.secondary
    val animated by animateFloatAsState(
        targetValue = (fraction ?: 0f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "progress",
    )
    val transition = rememberInfiniteTransition(label = "tape")
    val sweep by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "sweep",
    )
    val glide by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "glide",
    )
    Canvas(modifier.fillMaxWidth().height(height)) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = track, cornerRadius = radius)
        val gradient = Brush.horizontalGradient(listOf(start, end), startX = 0f, endX = size.width)
        if (fraction == null) {
            val segment = size.width * 0.28f
            val x = (size.width - segment) * glide
            drawRoundRect(brush = gradient, topLeft = Offset(x, 0f), size = Size(segment, size.height), cornerRadius = radius)
        } else {
            val width = (size.width * animated).coerceAtLeast(size.height)
            drawRoundRect(brush = gradient, size = Size(width, size.height), cornerRadius = radius)
            if (active && animated < 1f) {
                val shine = size.width * 0.18f
                val sx = -shine + (width + shine) * sweep
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                        startX = sx, endX = sx + shine,
                    ),
                    size = Size(width, size.height),
                    cornerRadius = radius,
                )
            }
        }
    }
}

/**
 * For screens laid over the library (player, settings): touches on their empty areas would otherwise
 * fall through to the list below and start or open whatever card sits there.
 */
fun Modifier.blockTouchesBelow(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

/** A cassette as a line icon (24 × 24): body, two reels joined by the tape, and the head opening. */
val CassetteIcon: ImageVector by lazy {
    ImageVector.Builder("Cassette", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // Body
            moveTo(4.5f, 5f); lineTo(19.5f, 5f)
            arcTo(2f, 2f, 0f, false, true, 21.5f, 7f); lineTo(21.5f, 17f)
            arcTo(2f, 2f, 0f, false, true, 19.5f, 19f); lineTo(4.5f, 19f)
            arcTo(2f, 2f, 0f, false, true, 2.5f, 17f); lineTo(2.5f, 7f)
            arcTo(2f, 2f, 0f, false, true, 4.5f, 5f); close()
            // Reels
            moveTo(8f, 8.5f); arcTo(2f, 2f, 0f, true, true, 8f, 12.5f); arcTo(2f, 2f, 0f, true, true, 8f, 8.5f); close()
            moveTo(16f, 8.5f); arcTo(2f, 2f, 0f, true, true, 16f, 12.5f); arcTo(2f, 2f, 0f, true, true, 16f, 8.5f); close()
            // Tape between them
            moveTo(8f, 12.5f); lineTo(16f, 12.5f)
            // Head opening
            moveTo(6.5f, 19f); lineTo(8f, 15.8f); lineTo(16f, 15.8f); lineTo(17.5f, 19f)
        }
    }.build()
}
