package net.devemperor.tapedeck.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Where the visible part of a list is: [start] and [size] as parts of the whole (0..1). */
private data class Window(val start: Float, val size: Float)

/**
 * A slim scrollbar at the right edge of a long list. It shows while the list moves and fades out a
 * moment later; its thumb can be dragged to jump through the list. While faded out it takes no touches,
 * so the buttons at the edge of the cards stay reachable.
 */
@Composable
fun BoxScope.VerticalScrollbar(state: LazyListState, top: Dp = 0.dp, bottom: Dp = 0.dp) {
    val window by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val items = info.visibleItemsInfo
            if (items.isEmpty() || info.totalItemsCount == 0 || !(state.canScrollForward || state.canScrollBackward)) return@derivedStateOf null
            // Items differ in height; their average is close enough for a position.
            val average = items.sumOf { it.size }.toFloat() / items.size
            val content = average * info.totalItemsCount
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            val offset = state.firstVisibleItemIndex * average + state.firstVisibleItemScrollOffset
            Window(start = (offset / content).coerceIn(0f, 1f), size = (viewport / content).coerceIn(0.05f, 1f))
        }
    }
    val scope = rememberCoroutineScope()
    Scrollbar(window, state.isScrollInProgress, top, bottom) { fraction ->
        val total = state.layoutInfo.totalItemsCount
        if (total > 0) scope.launch { state.scrollToItem((fraction * (total - 1)).roundToInt().coerceIn(0, total - 1)) }
    }
}

/** The same for a plain scrolling column. */
@Composable
fun BoxScope.VerticalScrollbar(state: ScrollState, top: Dp = 0.dp, bottom: Dp = 0.dp) {
    val window by remember(state) {
        derivedStateOf {
            if (state.maxValue <= 0 || state.maxValue == Int.MAX_VALUE) return@derivedStateOf null
            val viewport = state.viewportSize.toFloat()
            val content = viewport + state.maxValue
            Window(start = state.value / content, size = (viewport / content).coerceIn(0.05f, 1f))
        }
    }
    val scope = rememberCoroutineScope()
    Scrollbar(window, state.isScrollInProgress, top, bottom) { fraction ->
        scope.launch { state.scrollTo((fraction * state.maxValue).roundToInt()) }
    }
}

@Composable
private fun BoxScope.Scrollbar(window: Window?, scrolling: Boolean, top: Dp, bottom: Dp, onJump: (Float) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    val active = scrolling || dragging
    LaunchedEffect(active) {
        if (active) shown = true else {
            delay(1_200)
            shown = false
        }
    }
    val alpha by animateFloatAsState(if (shown && window != null) 1f else 0f, tween(if (shown) 150 else 400), label = "scrollbar")
    if (window == null || alpha == 0f) return
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.align(Alignment.TopEnd).fillMaxHeight().padding(top = top, bottom = bottom).width(24.dp).alpha(alpha)) {
        val track = maxHeight
        val thumb = track * window.size
        val thumbTop = (track - thumb) * (window.start / (1f - window.size).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
        var dragTop by remember { mutableStateOf(0f) }
        // Read while dragging: the thumb's size shifts a little as items of other heights scroll by.
        val trackPx by rememberUpdatedState(with(density) { track.toPx() })
        val thumbPx by rememberUpdatedState(with(density) { thumb.toPx() })
        val thumbTopPx by rememberUpdatedState(with(density) { thumbTop.toPx() })
        val jump by rememberUpdatedState(onJump)
        Box(
            Modifier.offset(y = thumbTop).height(thumb).width(24.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragging = true
                            dragTop = thumbTopPx
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, amount ->
                        change.consume()
                        val room = trackPx - thumbPx
                        dragTop = (dragTop + amount).coerceIn(0f, room.coerceAtLeast(0f))
                        jump(if (room > 0f) dragTop / room else 0f)
                    }
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier.padding(end = 3.dp).width(if (dragging) 8.dp else 5.dp).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (dragging) 0.75f else 0.5f), RoundedCornerShape(4.dp)),
            )
        }
    }
}
