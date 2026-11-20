package net.devemperor.tapedeck.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.devemperor.tapedeck.data.Entry
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The app's shared-transition scope (set up in TapedeckRoot): an element that sits on two screens —
 * the cover in the mini player and in the player, a cassette on the shelf and atop its mixtape —
 * travels from one place to the other instead of vanishing and reappearing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * How long a screen change and the elements travelling with it take. They must match: a shared element
 * only travels while the screens' own enter/exit animations run, and is cut off when those end.
 */
const val TRAVEL_MS = 420

fun <T> travelSpec() = tween<T>(TRAVEL_MS, easing = FastOutSlowInEasing)

@OptIn(ExperimentalSharedTransitionApi::class)
private val travel = BoundsTransform { _, _ -> travelSpec() }

/**
 * Marks this element as [key] on the screen whose [visibility] it belongs to. Without a scope (previews,
 * screens shown without transition) it does nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedTransition(key: String, visibility: AnimatedVisibilityScope?): Modifier {
    val shared = LocalSharedScope.current ?: return this
    if (visibility == null) return this
    return with(shared) {
        this@sharedTransition.sharedBounds(
            rememberSharedContentState(key),
            visibility,
            boundsTransform = travel,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

/**
 * A list card's picture that can travel into the player: opened from this card, it flies up into the
 * player ([away] while the player is open) and back into the card when the player closes.
 */
@Composable
fun TravellingThumbnail(entry: Entry, travels: Boolean, away: Boolean, modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    Box(modifier) {
        AnimatedVisibility(!away, enter = fadeIn(travelSpec()), exit = fadeOut(travelSpec())) {
            Thumbnail(
                entry,
                (if (travels) Modifier.sharedTransition(SharedKeys.artwork(entry.id), this) else Modifier).fillMaxSize(),
                corner = corner,
            )
        }
    }
}

/** Keys of the travelling elements. */
object SharedKeys {
    fun artwork(entryId: String) = "artwork-$entryId"
    fun tape(mixtapeId: String) = "tape-$mixtapeId"
}
