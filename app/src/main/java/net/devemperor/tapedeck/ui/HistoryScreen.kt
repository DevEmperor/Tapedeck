package net.devemperor.tapedeck.ui

import android.os.Build
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import net.devemperor.tapedeck.Format
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.data.Entry
import net.devemperor.tapedeck.data.HistoryItem
import net.devemperor.tapedeck.data.MediaKind
import java.io.File
import java.util.Calendar

enum class HistoryFilter { ALL, IN_LIBRARY, DELETED }

/**
 * Every video ever downloaded, newest first and by day — also the ones deleted since. Searchable by title,
 * channel and link; a tap plays what is still in the library and fetches again what is not.
 */
@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit, onPlay: (Entry) -> Unit) {
    val items by vm.history.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val byId = remember(entries) { entries.associateBy { it.id } }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var menu by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }

    val matching = items.filter { item ->
        query.isBlank() || item.title.contains(query, true) || item.uploader?.contains(query, true) == true || item.url.contains(query, true)
    }
    val shown = matching.filter { item ->
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.IN_LIBRARY -> !item.deleted
            HistoryFilter.DELETED -> item.deleted
        }
    }

    Column(Modifier.fillMaxSize().blockTouchesBelow().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }, enabled = items.isNotEmpty()) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    MenuItem(Icons.Rounded.DeleteSweep, R.string.history_clear, danger = true) { menu = false; clearing = true }
                }
            }
        }
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.history_search)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
            },
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        ) {
            items(HistoryFilter.entries) { option ->
                val count = when (option) {
                    HistoryFilter.ALL -> matching.size
                    HistoryFilter.IN_LIBRARY -> matching.count { !it.deleted }
                    HistoryFilter.DELETED -> matching.count { it.deleted }
                }
                val label = when (option) {
                    HistoryFilter.ALL -> R.string.filter_all
                    HistoryFilter.IN_LIBRARY -> R.string.history_in_library
                    HistoryFilter.DELETED -> R.string.history_deleted
                }
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(stringResource(label) + "  " + count) },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
        Box(Modifier.weight(1f)) {
            val listState = rememberLazyListState()
            if (shown.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(if (items.isEmpty()) R.string.history_empty else R.string.no_match),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            ) {
                // By day: "Today", "Yesterday", then the date.
                shown.groupBy { dayOf(it.downloadedAt) }.forEach { (day, dayItems) ->
                    item(key = "d-$day") { DayHeader(dayItems.first().downloadedAt) }
                    items(dayItems, key = { "h-" + it.key }) { item ->
                        val entry = item.entryId?.let { byId[it] }
                        HistoryRow(
                            item, entry,
                            onOpen = { if (entry != null && entry.isDone) onPlay(entry) else vm.redownload(item) },
                            vm = vm,
                        )
                    }
                }
            }
            VerticalScrollbar(listState)
        }
    }
    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text(stringResource(R.string.history_clear)) },
            text = { Text(stringResource(R.string.history_clear_body)) },
            confirmButton = { TextButton(onClick = { clearing = false; vm.clearHistory() }) { Text(stringResource(R.string.history_clear)) } },
            dismissButton = { TextButton(onClick = { clearing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun dayOf(time: Long): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = time }
    return calendar.get(Calendar.YEAR) * 1000L + calendar.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun DayHeader(time: Long) {
    val context = LocalContext.current
    val label = when {
        DateUtils.isToday(time) -> stringResource(R.string.today)
        DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> stringResource(R.string.yesterday)
        else -> {
            val thisYear = Calendar.getInstance().get(Calendar.YEAR) == Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.YEAR)
            DateUtils.formatDateTime(
                context, time,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or (if (thisYear) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR),
            )
        }
    }
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

/**
 * One video of the history: its picture (faded once deleted), title, where it came from, how and when it
 * was downloaded. ⋮ downloads it again, copies or opens its link, or takes it out of the history.
 */
@Composable
private fun HistoryRow(item: HistoryItem, entry: Entry?, onOpen: () -> Unit, vm: MainViewModel) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    @Suppress("DEPRECATION")
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    val gone = entry == null
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            val picture: Any? = entry?.thumbnailFile?.let(::File)?.takeIf { it.exists() } ?: item.thumbnailUrl
            if (picture != null) {
                AsyncImage(picture, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(if (gone) 0.45f else 1f))
            }
            Box(
                Modifier.align(Alignment.TopStart).padding(5.dp).size(20.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (item.kind == MediaKind.VIDEO) Icons.Rounded.Movie else Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
            if (item.durationMs > 0) {
                Text(
                    Format.clock(item.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (gone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            SourceLine(item.url, item.uploader, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant)
            // The link itself, readable at a glance (the search looks in it too).
            Text(
                shortLink(item.url),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (gone) {
                    Text(
                        stringResource(R.string.history_gone),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    listOfNotNull(
                        DateUtils.formatDateTime(context, item.downloadedAt, DateUtils.FORMAT_SHOW_TIME),
                        item.formatLabel,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                MenuItem(Icons.Rounded.Download, R.string.history_redownload) { menu = false; vm.redownload(item) }
                MenuItem(Icons.Rounded.ContentCopy, R.string.history_copy_link) {
                    menu = false
                    clipboard.setText(AnnotatedString(item.url))
                    // Android 13 and later say so themselves.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) vm.say(R.string.history_link_copied)
                }
                MenuItem(Icons.AutoMirrored.Rounded.OpenInNew, R.string.open_source) { menu = false; runCatching { uriHandler.openUri(item.url) } }
                MenuItem(Icons.Rounded.Close, R.string.history_remove) { menu = false; vm.removeFromHistory(item.key) }
            }
        }
    }
}

/** "https://www.youtube.com/watch?v=…" → "youtube.com/watch?v=…": the part that tells links apart. */
internal fun shortLink(url: String): String =
    url.substringAfter("://").removePrefix("www.").removePrefix("m.").trimEnd('/')
