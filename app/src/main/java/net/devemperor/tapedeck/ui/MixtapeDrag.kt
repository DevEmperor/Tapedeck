package net.devemperor.tapedeck.ui

import android.content.ClipData
import android.view.View
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import net.devemperor.tapedeck.data.Entry
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.devemperor.tapedeck.R

/**
 * A dragged download carries its id under a type of its own, so only Tapedeck's drop spots take it — a
 * text field (the search) would otherwise paste the id.
 */
private const val ENTRY_MIME = "application/vnd.net.devemperor.tapedeck.entry"
private const val DRAG_LABEL = "tapedeck-entry"

private fun DragAndDropEvent.isEntry(): Boolean = ENTRY_MIME in mimeTypes()

private fun DragAndDropEvent.entryId(): String? =
    toAndroidDragEvent().clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

private fun DragAndDropEvent.position(): Offset = toAndroidDragEvent().let { Offset(it.x, it.y) }

/**
 * A download being dragged: whether one is, where the finger is (pixels from the screen's top left), and
 * the spots it can be dropped on. The spots are found by their bounds rather than as drop targets of their
 * own: the shelf may only come into view (and be composed) after the drag began, and Android hands a drag
 * only to targets that were there at its start.
 */
@Stable
class EntryDrag {
    var active by mutableStateOf(false)
    var position by mutableStateOf<Offset?>(null)

    /** The download in hand: its card in the list stays marked while it is dragged. */
    var held by mutableStateOf<String?>(null)

    private class Spot(val bounds: Rect, val onDrop: State<(String) -> Unit>)

    private val spots = mutableStateMapOf<Any, Spot>()

    /** The key of the spot under the finger, if any. */
    val hovered: Any? by derivedStateOf {
        val at = position ?: return@derivedStateOf null
        spots.entries.firstOrNull { it.value.bounds.contains(at) }?.key
    }

    internal fun place(key: Any, bounds: Rect, onDrop: State<(String) -> Unit>) {
        if (spots[key]?.bounds != bounds) spots[key] = Spot(bounds, onDrop)
    }

    internal fun remove(key: Any) {
        spots.remove(key)
    }

    /** Drops [entryId] on the spot at [at]; false if there is none. */
    internal fun drop(at: Offset, entryId: String): Boolean {
        val spot = spots.values.firstOrNull { it.bounds.contains(at) } ?: return false
        spot.onDrop.value(entryId)
        return true
    }
}

/**
 * A long press lifts the card: a compact, opaque copy of it ([EntryDragShadow]) hangs under the finger
 * and can be dropped on a cassette of the mixtape shelf, while the card itself stays marked as the one in
 * hand. The drag is started on the view directly: Compose's own drag source can only show the whole wide
 * card, see-through and wherever it was pressed.
 */
@Composable
fun Modifier.entryDragSource(entry: Entry, drag: EntryDrag): Modifier {
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val colors = ShadowColors(surface = scheme.surfaceContainerHighest.toArgb(), accent = scheme.primary.toArgb(), text = scheme.onSurface.toArgb())
    val lifted by rememberUpdatedState(entry)
    val shadowColors by rememberUpdatedState(colors)
    return pointerInput(drag) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // Null when the finger moved (the list scrolls) or let go before the long press.
            awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            val inHand = lifted
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            drag.held = inHand.id
            drag.active = true
            val clip = ClipData(DRAG_LABEL, arrayOf(ENTRY_MIME), ClipData.Item(inHand.id))
            val shadow = EntryDragShadow(view.context, inHand, shadowColors)
            if (!view.startDragAndDrop(clip, shadow, null, View.DRAG_FLAG_OPAQUE)) {
                drag.active = false
                drag.held = null
            }
        }
    }
}

/**
 * A tap for a card that can also be dragged: unlike clickable, it leaves the finger's first touch to
 * others, so the long press of [entryDragSource] still sees it. A tap shows the usual ripple; a scroll or
 * a drag cancels it.
 */
@Composable
fun Modifier.tapLeavingLongPress(onClick: () -> Unit): Modifier {
    val interactions = remember { MutableInteractionSource() }
    val click by rememberUpdatedState(onClick)
    val scope = rememberCoroutineScope()
    return indication(interactions, ripple()).pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val press = PressInteraction.Press(down.position)
            scope.launch { interactions.emit(press) }
            val up = waitForUpOrCancellation()
            if (up != null) {
                up.consume()
                scope.launch { interactions.emit(PressInteraction.Release(press)) }
                click()
            } else {
                scope.launch { interactions.emit(PressInteraction.Cancel(press)) }
            }
        }
    }
}

/**
 * The one drop target, on the whole screen: follows a drag of a download (when it starts and ends, where
 * the finger is) and hands a drop to the spot under the finger.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.entryDragWatcher(drag: EntryDrag): Modifier {
    val haptic = LocalHapticFeedback.current
    val target = remember(drag, haptic) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                drag.active = true
            }
            override fun onEntered(event: DragAndDropEvent) {
                drag.position = event.position()
            }
            override fun onMoved(event: DragAndDropEvent) {
                drag.position = event.position()
            }
            override fun onEnded(event: DragAndDropEvent) {
                drag.active = false
                drag.position = null
                drag.held = null
            }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val id = event.entryId() ?: return false
                if (!drag.drop(event.position(), id)) return false
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                return true
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = { it.isEntry() }, target = target)
}

/**
 * Makes this a spot a dragged download can be dropped on, under [key]; [onDrop] gets its id. Whether the
 * download is over it reads as `drag.hovered == key` — with a tick to feel when it gets there.
 */
@Composable
fun Modifier.entryDropSpot(drag: EntryDrag, key: Any, onDrop: (String) -> Unit): Modifier {
    val drop = rememberUpdatedState(onDrop)
    val haptic = LocalHapticFeedback.current
    val over = drag.hovered == key
    LaunchedEffect(over) {
        if (over) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
    DisposableEffect(drag, key) {
        onDispose { drag.remove(key) }
    }
    return onGloballyPositioned { drag.place(key, it.boundsInRoot(), drop) }
}

/** How a drop spot looks during a drag: waiting for the download, or with it right over the spot. */
enum class DropLook { NONE, READY, HOVER }

/**
 * A ring around a drop spot, [inset] outside its edge: dashed and breathing while a download is dragged,
 * solid and bold once it is over the spot.
 */
@Composable
fun Modifier.dropRing(look: DropLook, color: Color, corner: Dp, inset: Dp = 3.dp, breathing: Boolean = true): Modifier {
    // Only runs while a drag is on, so the shelf does not redraw for nothing the rest of the time.
    val breath = if (look == DropLook.READY && breathing) {
        rememberInfiniteTransition(label = "drop-ring")
            .animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "drop-ring-alpha")
    } else null
    return drawWithContent {
        drawContent()
        if (look == DropLook.NONE) return@drawWithContent
        val out = inset.toPx()
        val hovered = look == DropLook.HOVER
        drawRoundRect(
            color = if (hovered || breath == null) color else color.copy(alpha = color.alpha * breath.value),
            topLeft = Offset(-out, -out),
            size = Size(size.width + 2 * out, size.height + 2 * out),
            cornerRadius = CornerRadius((corner + inset).toPx()),
            style = Stroke(
                width = (if (hovered) 3.dp else 2.dp).toPx(),
                pathEffect = if (hovered) null else PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())),
            ),
        )
    }
}

private const val NEW_MIXTAPE_SPOT = "new-mixtape"

/** "+ New mixtape" in the shelf's head while a download is dragged: dropped there, it starts a mixtape of its own. */
@Composable
fun NewMixtapeDropSpot(drag: EntryDrag, onDrop: (String) -> Unit) {
    val over = drag.hovered == NEW_MIXTAPE_SPOT
    val grow by animateFloatAsState(if (over) 1.08f else 1f, label = "new-mixtape-drop")
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.padding(end = 8.dp)
            .entryDropSpot(drag, NEW_MIXTAPE_SPOT, onDrop)
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .dropRing(if (over) DropLook.HOVER else DropLook.READY, primary, corner = 20.dp, inset = 0.dp)
            .background(if (over) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape)
            .height(40.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.mixtape_new), style = MaterialTheme.typography.labelLarge, color = primary)
    }
}
