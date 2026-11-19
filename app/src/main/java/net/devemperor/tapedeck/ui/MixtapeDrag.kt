package net.devemperor.tapedeck.ui

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ripple
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.data.Mixtape

/** The label of a download being dragged, so only Tapedeck's own drags are taken. */
private const val DRAG_LABEL = "tapedeck-entry"

private fun DragAndDropEvent.isEntry(): Boolean =
    mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) && toAndroidDragEvent().clipDescription?.label == DRAG_LABEL

private fun DragAndDropEvent.entryId(): String? =
    toAndroidDragEvent().clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

/** A long press lifts the card; it can then be dropped on a mixtape in the tray at the top. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.entryDragSource(entryId: String, onStart: () -> Unit): Modifier = dragAndDropSource { _ ->
    onStart()
    DragAndDropTransferData(ClipData(DRAG_LABEL, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item(entryId)))
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

/** Notices when a drag of a download starts and ends anywhere on the screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.entryDragWatcher(onActive: (Boolean) -> Unit): Modifier {
    val target = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) = onActive(true)
            override fun onEnded(event: DragAndDropEvent) = onActive(false)
            override fun onDrop(event: DragAndDropEvent): Boolean = false
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = { it.isEntry() }, target = target)
}

/**
 * The tray that slides in at the top while a download is dragged: every mixtape as a small cassette,
 * and "New mixtape". The one under the finger grows and gets a ring in its colour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MixtapeDropTray(
    visible: Boolean,
    mixtapes: List<Mixtape>,
    onDrop: (mixtapeId: String, entryId: String) -> Unit,
    onDropNew: (entryId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Always composed, so its drop targets exist when the drag starts — and without anything that takes
    // touches (no Surface, no scrolling row), so while hidden it does not block the screen beneath.
    val shown by animateFloatAsState(if (visible) 1f else 0f, tween(220), label = "drop-tray")
    Box(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            .graphicsLayer {
                alpha = shown
                translationY = (1f - shown) * -size.height
            },
    ) {
        Column(
            Modifier.fillMaxWidth().shadow(16.dp, RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(22.dp))
                .padding(vertical = 12.dp),
        ) {
            Text(
                stringResource(R.string.drag_to_mixtape),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                mixtapes.forEach { tape ->
                    key(tape.id) {
                        DropSpot(mixtapeColor(tape.color), onDrop = { entryId -> onDrop(tape.id, entryId) }) {
                            Cassette(tape.name, mixtapeColor(tape.color), Modifier.width(96.dp))
                        }
                    }
                }
                DropSpot(MaterialTheme.colorScheme.primary, onDrop = onDropNew) {
                    Box(
                        Modifier.width(96.dp).aspectRatio(1.58f).border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(22.dp))
                            Text(stringResource(R.string.mixtape_new), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DropSpot(ring: Color, onDrop: (String) -> Unit, content: @Composable () -> Unit) {
    var over by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val grow by animateFloatAsState(if (over) 1.1f else 1f, label = "drop-spot")
    val target = remember(onDrop) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                over = true
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
            override fun onExited(event: DragAndDropEvent) {
                over = false
            }
            override fun onEnded(event: DragAndDropEvent) {
                over = false
            }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val id = event.entryId() ?: return false
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onDrop(id)
                return true
            }
        }
    }
    Box(
        Modifier.scale(grow)
            .border(if (over) 3.dp else 0.dp, if (over) ring else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(3.dp)
            .dragAndDropTarget(shouldStartDragAndDrop = { it.isEntry() }, target = target),
    ) { content() }
}
