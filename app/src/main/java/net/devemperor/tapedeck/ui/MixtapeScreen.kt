package net.devemperor.tapedeck.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.devemperor.tapedeck.Format
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.data.Entry
import net.devemperor.tapedeck.data.Mixtape
import net.devemperor.tapedeck.player.PlayerUiState

/** "12 items · 48:10": how much is on a mixtape. */
@Composable
private fun mixtapeSummary(tape: Mixtape, byId: Map<String, Entry>): String {
    val count = tape.entryIds.size
    val total = tape.entryIds.sumOf { byId[it]?.durationMs ?: 0 }
    return listOfNotNull(
        pluralStringResource(R.plurals.mixtape_titles, count, count),
        total.takeIf { it > 0 }?.let(Format::clock),
    ).joinToString(" · ")
}

/** How far a playing mixtape is: the item's place on it, for the tape winding from reel to reel. */
private fun mixtapeProgress(tape: Mixtape, state: PlayerUiState): Float {
    if (state.mixtapeId != tape.id || tape.entryIds.isEmpty()) return 0f
    val index = tape.entryIds.indexOf(state.entryId).coerceAtLeast(0)
    return (index + 0.5f) / tape.entryIds.size
}

/** A mixtape's reels turn while it plays, as fast as it plays. */
private fun mixtapeVelocity(tape: Mixtape, state: PlayerUiState): Float =
    if (state.isPlaying && state.mixtapeId == tape.id) ReelSpeed.PLAY * state.speed else 0f

/** A cassette on the shelf. While its mixtape is open it has travelled to the top of that page, and comes back on return. */
@Composable
private fun ShelfCassette(tape: Mixtape, state: PlayerUiState, travelling: Boolean) {
    Box(Modifier.fillMaxWidth().aspectRatio(1.58f)) {
        AnimatedVisibility(!travelling, enter = fadeIn(travelSpec()), exit = fadeOut(travelSpec())) {
            Cassette(
                tape.name,
                mixtapeColor(tape.color),
                Modifier.sharedTransition(SharedKeys.tape(tape.id), this).fillMaxWidth().shadow(6.dp, MaterialTheme.shapes.small),
                velocity = mixtapeVelocity(tape, state),
                progress = mixtapeProgress(tape, state),
            )
        }
    }
}

/** The mixtapes as cassettes on a shelf, above the library; their reels turn while one of them plays. */
@Composable
fun MixtapeShelf(vm: MainViewModel, entries: List<Entry>, state: PlayerUiState, openMixtape: String?, onOpen: (String) -> Unit) {
    val mixtapes by vm.mixtapes.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val byId = remember(entries) { entries.associateBy { it.id } }
    Column(Modifier.padding(bottom = 6.dp)) {
        SectionHeader(stringResource(R.string.mixtapes), mixtapes.size) {
            IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.mixtape_new)) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(mixtapes, key = { it.id }) { tape ->
                Column(
                    Modifier.width(156.dp).clip(MaterialTheme.shapes.medium).clickable { onOpen(tape.id) }.padding(4.dp),
                ) {
                    ShelfCassette(tape, state, travelling = openMixtape == tape.id)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        mixtapeSummary(tape, byId),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }
    if (creating) {
        NameDialog(R.string.mixtape_new, "", R.string.mixtape_create, onDismiss = { creating = false }) { name ->
            creating = false
            onOpen(vm.createMixtape(name).id)
        }
    }
}

/**
 * One mixtape: the cassette, Play and Shuffle, and its items in order — dragged by the handle to
 * reorder, × to take one off (the download stays in the library).
 */
@Composable
fun MixtapeScreen(
    vm: MainViewModel,
    mixtapeId: String,
    visibility: AnimatedVisibilityScope?,
    playerOpen: Boolean,
    playerOrigin: String?,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenPlayerFrom: (String) -> Unit,
    onOpenFullscreen: () -> Unit,
) {
    val mixtapes by vm.mixtapes.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val pending by vm.pendingDeletes.collectAsStateWithLifecycle()
    val state by vm.player.state.collectAsStateWithLifecycle()
    val tape = mixtapes.firstOrNull { it.id == mixtapeId }
    if (tape == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val byId = remember(entries, pending) { entries.filter { it.id !in pending }.associateBy { it.id } }
    val color = mixtapeColor(tape.color)
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    // Reordering happens on a local copy and is saved when the finger lifts.
    val order = remember(tape.entryIds) { tape.entryIds.filter { it in byId }.toMutableStateList() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    fun finishDrag() {
        if (dragging == null) return
        dragging = null
        dragOffset = 0f
        // Songs hidden just now (deleted, undo still possible) keep their place at the end.
        val newOrder = order.toList() + tape.entryIds.filterNot { it in order }
        if (newOrder != tape.entryIds) vm.reorderMixtape(tape.id, newOrder)
    }

    Box(Modifier.fillMaxSize().blockTouchesBelow().background(MaterialTheme.colorScheme.background)) {
        // The page glows in its cassette's colour from the top.
        Box(Modifier.fillMaxWidth().height(520.dp).background(Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), Color.Transparent))))
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                Text(
                    tape.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mixtape_rename)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { menu = false; renaming = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mixtape_delete)) },
                            leadingIcon = { Icon(Icons.Rounded.Close, null) },
                            onClick = { menu = false; deleting = true },
                        )
                    }
                }
            }

            Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "header") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Cassette(
                            tape.name,
                            color,
                            Modifier.sharedTransition(SharedKeys.tape(tape.id), visibility).fillMaxWidth(0.78f).shadow(16.dp, MaterialTheme.shapes.medium),
                            velocity = mixtapeVelocity(tape, state),
                            progress = mixtapeProgress(tape, state),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(mixtapeSummary(tape, byId), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        val playable = order.any { byId[it]?.isDone == true }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // While this mixtape is the one in the player, the button pauses and resumes it.
                            val loaded = state.mixtapeId == tape.id && state.entryId != null
                            Button(
                                onClick = { if (loaded) vm.player.togglePlay() else vm.playMixtape(tape) },
                                enabled = playable,
                                modifier = Modifier.animateContentSize(),
                            ) {
                                AnimatedContent(
                                    targetState = when {
                                        !loaded -> 0
                                        state.isPlaying -> 1
                                        else -> 2
                                    },
                                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.8f)) },
                                    label = "mixtape-play",
                                ) { mode ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (mode == 1) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(when (mode) { 1 -> R.string.pause; 2 -> R.string.resume; else -> R.string.play }))
                                    }
                                }
                            }
                            FilledTonalButton(onClick = { vm.playMixtape(tape, shuffle = true) }, enabled = playable) {
                                Icon(Icons.Rounded.Shuffle, null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.mixtape_shuffle))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                if (order.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.mixtape_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                        )
                    }
                }
                items(order, key = { "m-$it" }) { id ->
                    val entry = byId[id] ?: return@items
                    val isDragged = dragging == id
                    val isCurrent = state.entryId == id && state.mixtapeId == tape.id
                    Row(
                        Modifier.fillMaxWidth()
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                            .animateItem(placementSpec = if (isDragged) null else spring())
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                when {
                                    isDragged -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    isCurrent -> color.copy(alpha = 0.16f)
                                    else -> MaterialTheme.colorScheme.background
                                },
                            )
                            .clickable(enabled = entry.isDone) {
                                vm.playMixtape(tape, start = entry)
                                if (entry.isVideo) onOpenPlayerFrom(entry.id)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The handle: drag up or down to move the song.
                        Icon(
                            Icons.Rounded.DragHandle,
                            stringResource(R.string.mixtape_move),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp).padding(10.dp).pointerInput(id) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = id
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val visible = listState.layoutInfo.visibleItemsInfo
                                        val current = visible.firstOrNull { it.key == "m-$id" } ?: return@detectDragGestures
                                        // Right after a swap the layout still shows the old order for a frame; swapping
                                        // again then would flip the song straight back. Wait until it caught up
                                        // (the header is item 0).
                                        if (current.index != order.indexOf(id) + 1) return@detectDragGestures
                                        val middle = current.offset + current.size / 2f + dragOffset
                                        val target = visible.firstOrNull { info ->
                                            info.key != current.key && (info.key as? String)?.startsWith("m-") == true &&
                                                middle > info.offset && middle < info.offset + info.size
                                        } ?: return@detectDragGestures
                                        val from = order.indexOf(id)
                                        val to = order.indexOf((target.key as String).removePrefix("m-"))
                                        if (from < 0 || to < 0) return@detectDragGestures
                                        order.add(to, order.removeAt(from))
                                        dragOffset += current.offset - target.offset
                                    },
                                    onDragEnd = ::finishDrag,
                                    onDragCancel = ::finishDrag,
                                )
                            },
                        )
                        val travels = playerOrigin == id
                        TravellingThumbnail(
                            entry, travels, away = travels && playerOpen,
                            modifier = Modifier.width(88.dp).aspectRatio(16f / 9f).alpha(if (entry.isDone) 1f else 0.5f),
                            corner = 8.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isCurrent) color else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (entry.isDone) {
                                SourceLine(entry.url, entry.uploader, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text(stringResource(R.string.mixtape_not_ready), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { vm.removeFromMixtape(tape.id, id) }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.mixtape_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            VerticalScrollbar(listState)
            }

            // The mini player as in the library, above the navigation bar.
            val current = state.entryId?.let { byId[it] }
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                AnimatedVisibility(
                    current != null && !playerOpen,
                    enter = expandVertically(travelSpec()) + fadeIn(travelSpec()),
                    exit = shrinkVertically(travelSpec()) + fadeOut(travelSpec()),
                ) {
                    current?.let {
                        MiniPlayer(
                            it, vm, state.isPlaying, state.durationMs, onOpenPlayer, onOpenFullscreen,
                            thumbnailModifier = if (playerOrigin == null) Modifier.sharedTransition(SharedKeys.artwork(it.id), this) else Modifier,
                        )
                    }
                }
            }
        }
    }

    if (renaming) {
        NameDialog(R.string.mixtape_rename, tape.name, R.string.ok, onDismiss = { renaming = false }) { name ->
            renaming = false
            vm.renameMixtape(tape.id, name)
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.mixtape_delete_title, tape.name)) },
            text = { Text(stringResource(R.string.mixtape_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    vm.deleteMixtape(tape.id)
                    onBack()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** "On mixtape": tick the mixtapes a download belongs on, or start a new one with it. */
@Composable
fun MixtapePickerDialog(vm: MainViewModel, entry: Entry, onDismiss: () -> Unit) {
    val mixtapes by vm.mixtapes.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mixtape_pick_title)) },
        text = {
            Column {
                mixtapes.forEach { tape ->
                    val on = entry.id in tape.entryIds
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { vm.toggleOnMixtape(tape.id, entry.id) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = { vm.toggleOnMixtape(tape.id, entry.id) })
                        Box(Modifier.size(12.dp).clip(CircleShape).background(mixtapeColor(tape.color)))
                        Spacer(Modifier.width(10.dp))
                        Text(tape.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (mixtapes.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.mixtape_new)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        vm.createMixtape(newName, listOf(entry.id))
                        newName = ""
                    }, enabled = newName.isNotBlank()) { Icon(Icons.Rounded.Add, stringResource(R.string.mixtape_create)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
fun NameDialog(title: Int, initial: String, confirm: Int, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.mixtape_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
