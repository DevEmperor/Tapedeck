package net.devemperor.tapedeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.devemperor.tapedeck.R
import kotlin.math.abs

/**
 * A wheel like a timer's number dials: the values roll under a highlighted centre row, snap into it, and
 * tick with a light haptic as each one passes. Tapping a value rolls it into the centre.
 */
@Composable
fun <T> WheelPicker(
    items: List<T>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    visibleCount: Int = 5,
    itemHeight: Dp = 52.dp,
    fadeColor: Color = AlertDialogDefaults.containerColor,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, items.lastIndex))
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    // The content padding puts the item at the scroll position into the middle row.
    val centred by remember {
        derivedStateOf {
            val next = if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
            (state.firstVisibleItemIndex + next).coerceIn(0, items.lastIndex)
        }
    }
    LaunchedEffect(centred) {
        if (centred != selectedIndex) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelectedIndexChange(centred)
        }
    }

    Box(modifier.height(itemHeight * visibleCount), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().height(itemHeight).clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items.size) { index ->
                val distance = abs(index - centred)
                Box(
                    Modifier.fillMaxWidth().height(itemHeight).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { scope.launch { state.animateScrollToItem(index) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(items[index]),
                        style = if (distance == 0) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                        color = if (distance == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(
                            when (distance) {
                                0 -> 1f
                                1 -> 0.6f
                                else -> 0.3f
                            },
                        ),
                    )
                }
            }
        }
        // Values fade out towards the edges, like on a real dial.
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(itemHeight * 1.3f)
                .background(Brush.verticalGradient(listOf(fadeColor, fadeColor.copy(alpha = 0f)))),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(itemHeight * 1.3f)
                .background(Brush.verticalGradient(listOf(fadeColor.copy(alpha = 0f), fadeColor))),
        )
    }
}

/** A dialog around [WheelPicker]: roll to a value, then OK. */
@Composable
fun <T> WheelPickerDialog(
    title: String,
    items: List<T>,
    current: T,
    label: @Composable (T) -> String,
    onConfirm: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    var index by remember { mutableIntStateOf(items.indexOf(current).coerceAtLeast(0)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { WheelPicker(items, index, { index = it }, label, Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(items[index]) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
