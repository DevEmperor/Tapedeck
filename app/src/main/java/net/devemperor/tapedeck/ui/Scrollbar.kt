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
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Where the visible part of a list is: [position] on the way from the top (0) to the end (1), [size] as a part of the whole. */
private data class Window(val position: Float, val size: Float)

/**
 * The heights of a list's items as they were last laid out. A lazy list only measures what is on screen,
 * so these are kept for every item seen once, and an item not seen yet counts as tall as most of the
 * seen ones are — in the library, a card. The whole list's height, and with it the thumb, then stays put
 * while scrolling, instead of following whichever items happen to be on screen.
 */
private class ItemHeights {
    private var count = -1
    private val heights = HashMap<Int, Int>()
    private val keys = HashMap<Int, Any>()

    /** Takes the heights on screen; a list that changed (items added, removed, sorted) starts over. */
    fun learn(info: LazyListLayoutInfo) {
        val reordered = info.visibleItemsInfo.any { item -> keys[item.index]?.let { it != item.key } == true }
        if (info.totalItemsCount != count || reordered) {
            heights.clear()
            keys.clear()
            count = info.totalItemsCount
        }
        info.visibleItemsInfo.forEach {
            heights[it.index] = it.size
            keys[it.index] = it.key
        }
    }

    /** The height of an item not seen yet: the one most seen items share (in 4 px steps; empty banners aside). */
    private fun typical(): Float {
        val groups = heights.entries.filter { it.value > 0 }.groupBy { it.value / 4 }.values
        // On a tie, the group reaching furthest down the list: what comes next is more of the same.
        val most = groups.maxWithOrNull(compareBy<List<Map.Entry<Int, Int>>> { it.size }.thenBy { group -> group.maxOf { it.key } })
        return most?.map { it.value }?.average()?.toFloat() ?: 0f
    }

    private fun height(index: Int, typical: Float): Float = heights[index]?.toFloat() ?: typical

    private fun content(info: LazyListLayoutInfo, typical: Float): Float {
        val n = info.totalItemsCount
        var total = (info.beforeContentPadding + info.afterContentPadding).toFloat() + info.mainAxisItemSpacing * (n - 1).coerceAtLeast(0)
        for (i in 0 until n) total += height(i, typical)
        return total
    }

    fun window(info: LazyListLayoutInfo): Window? {
        val first = info.visibleItemsInfo.firstOrNull() ?: return null
        learn(info)
        val typical = typical()
        val content = content(info, typical)
        val viewport = info.viewportSize.height.toFloat()
        val range = content - viewport
        if (range < 1f) return null
        var above = 0f
        for (i in 0 until first.index) above += height(i, typical) + info.mainAxisItemSpacing
        val scrolled = above - first.offset
        return Window(position = (scrolled / range).coerceIn(0f, 1f), size = (viewport / content).coerceIn(0.05f, 1f))
    }

    /** The item a thumb dragged to [position] (0..1) points at, and how far into it. */
    fun target(info: LazyListLayoutInfo, position: Float): Pair<Int, Int> {
        val n = info.totalItemsCount
        if (n == 0) return 0 to 0
        val typical = typical()
        var goal = position * (content(info, typical) - info.viewportSize.height).coerceAtLeast(0f)
        for (i in 0 until n) {
            val step = height(i, typical) + info.mainAxisItemSpacing
            if (goal < step || i == n - 1) return i to goal.roundToInt().coerceAtLeast(0)
            goal -= step
        }
        return n - 1 to 0
    }
}

/**
 * A slim scrollbar at the right edge of a long list. It shows while the list moves and fades out a
 * moment later; its thumb can be dragged to jump through the list. While faded out it takes no touches,
 * so the buttons at the edge of the cards stay reachable.
 */
@Composable
fun BoxScope.VerticalScrollbar(state: LazyListState, top: Dp = 0.dp, bottom: Dp = 0.dp) {
    val heights = remember(state) { ItemHeights() }
    val window by remember(state) { derivedStateOf { heights.window(state.layoutInfo) } }
    val scope = rememberCoroutineScope()
    Scrollbar(window, state.isScrollInProgress, top, bottom) { fraction ->
        scope.launch {
            val (index, offset) = heights.target(state.layoutInfo, fraction)
            state.scrollToItem(index, offset)
        }
    }
}

/** The same for a plain scrolling column, whose whole height is known. */
@Composable
fun BoxScope.VerticalScrollbar(state: ScrollState, top: Dp = 0.dp, bottom: Dp = 0.dp) {
    val window by remember(state) {
        derivedStateOf {
            if (state.maxValue <= 0 || state.maxValue == Int.MAX_VALUE) return@derivedStateOf null
            val viewport = state.viewportSize.toFloat()
            Window(position = state.value.toFloat() / state.maxValue, size = (viewport / (viewport + state.maxValue)).coerceIn(0.05f, 1f))
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
        val trackPx = constraints.maxHeight.toFloat()
        // A late correction of the list's height eases in instead of jumping; while dragged, the thumb
        // keeps its size and stays under the finger.
        var held by remember { mutableStateOf<Float?>(null) }
        val eased by animateFloatAsState(window.size, tween(300), label = "scrollbar-size")
        val thumbPx = trackPx * (held ?: eased)
        var dragTop by remember { mutableFloatStateOf(0f) }
        val thumbTop = if (dragging) dragTop else (trackPx - thumbPx) * window.position
        val trackNow by rememberUpdatedState(trackPx)
        val thumbNow by rememberUpdatedState(thumbPx)
        val topNow by rememberUpdatedState(thumbTop)
        val sizeNow by rememberUpdatedState(eased)
        val jump by rememberUpdatedState(onJump)
        Box(
            Modifier.offset { IntOffset(0, thumbTop.roundToInt()) }
                .height(with(density) { thumbPx.toDp() })
                .width(24.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            held = sizeNow
                            dragTop = topNow
                            dragging = true
                        },
                        onDragEnd = {
                            dragging = false
                            held = null
                        },
                        onDragCancel = {
                            dragging = false
                            held = null
                        },
                    ) { change, amount ->
                        change.consume()
                        val room = (trackNow - thumbNow).coerceAtLeast(0f)
                        dragTop = (dragTop + amount).coerceIn(0f, room)
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
