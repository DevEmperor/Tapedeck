package net.devemperor.tapedeck.ui

import androidx.compose.animation.AnimatedVisibilityScope
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

/** Keys of the travelling elements. */
object SharedKeys {
    fun artwork(entryId: String) = "artwork-$entryId"
    fun tape(mixtapeId: String) = "tape-$mixtapeId"
}
