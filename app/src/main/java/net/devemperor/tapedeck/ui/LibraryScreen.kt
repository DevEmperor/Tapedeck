package net.devemperor.tapedeck.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import net.devemperor.tapedeck.Format
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.data.AudioFormat
import net.devemperor.tapedeck.data.AudioQuality
import net.devemperor.tapedeck.data.AutoDelete
import net.devemperor.tapedeck.data.DownloadState
import net.devemperor.tapedeck.data.Entry
import net.devemperor.tapedeck.data.LibrarySort
import net.devemperor.tapedeck.data.MediaKind
import net.devemperor.tapedeck.engine.AppInstallState
import net.devemperor.tapedeck.engine.AppUpdates
import net.devemperor.tapedeck.engine.Site
import net.devemperor.tapedeck.engine.Engine
import net.devemperor.tapedeck.engine.ErrorKind
import net.devemperor.tapedeck.engine.LiveProgress
import net.devemperor.tapedeck.player.MediaItems
import java.util.concurrent.TimeUnit

enum class LibraryFilter { ALL, AUDIO, VIDEO, NEW }

@Composable
fun LibraryScreen(
    vm: MainViewModel,
    onOpenPlayer: () -> Unit,
    onOpenFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMixtape: (String) -> Unit,
) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val mixtapes by vm.mixtapes.collectAsStateWithLifecycle()
    val onMixtapes = remember(mixtapes) { mixtapes.flatMapTo(HashSet()) { it.entryIds } }
    val live by vm.live.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val pending by vm.pendingDeletes.collectAsStateWithLifecycle()
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val engine by vm.engineStatus.collectAsStateWithLifecycle()

    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }

    val visible = entries.filter { it.id !in pending }
    val downloads = visible.filter { !it.isDone && it.state != DownloadState.CANCELED }.sortedBy { it.createdAt }
    val done = visible.filter { it.isDone }
    val shown = MediaItems.sorted(
        done.filter { entry ->
            when (filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.AUDIO -> entry.options.kind == MediaKind.AUDIO
                LibraryFilter.VIDEO -> entry.options.kind == MediaKind.VIDEO
                LibraryFilter.NEW -> entry.isNew
            }
        }.filter { query.isBlank() || it.title.contains(query, true) || it.uploader?.contains(query, true) == true },
        settings.librarySort,
    )
    val current = playerState.entryId?.let { id -> entries.firstOrNull { it.id == id } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (current != null) 190.dp else 110.dp),
        ) {
            item(key = "top") {
                TopBar(
                    searching = searching,
                    query = query,
                    onQuery = { query = it },
                    onSearch = { searching = true },
                    onCloseSearch = { searching = false; query = "" },
                    sort = settings.librarySort,
                    onSort = { sort -> vm.updateSettings { it.copy(librarySort = sort) } },
                    onSettings = onOpenSettings,
                )
            }
            item(key = "engine") { EngineBanner(engine) }
            item(key = "app-updated") { AppUpdatedBanner(vm) }
            item(key = "app-update") { AppUpdateBanner(vm) }
            item(key = "clipboard") { ClipboardBanner(vm) }
            if (downloads.isNotEmpty()) {
                item(key = "downloads-header") { SectionHeader(stringResource(R.string.section_downloads), downloads.size) }
                items(downloads, key = { "d-" + it.id }) { entry ->
                    DownloadCard(
                        entry = entry,
                        progress = live[entry.id],
                        onCancel = { vm.cancel(entry.id) },
                        onRetry = { vm.retry(entry.id) },
                        onDelete = { vm.delete(setOf(entry.id)) },
                        modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 5.dp),
                    )
                }
            }
            if (mixtapes.isNotEmpty() && query.isBlank()) {
                item(key = "mixtapes") { MixtapeShelf(vm, visible, playerState, onOpen = onOpenMixtape) }
            }
            if (done.isNotEmpty()) {
                item(key = "filters") {
                    FilterRow(filter, onFilter = { filter = it }, counts = mapOf(
                        LibraryFilter.ALL to done.size,
                        LibraryFilter.AUDIO to done.count { it.options.kind == MediaKind.AUDIO },
                        LibraryFilter.VIDEO to done.count { it.options.kind == MediaKind.VIDEO },
                        LibraryFilter.NEW to done.count { it.isNew },
                    ))
                }
                items(shown, key = { "e-" + it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        isCurrent = entry.id == playerState.entryId,
                        isPlaying = entry.id == playerState.entryId && playerState.isPlaying,
                        deleteAt = AutoDelete.dueAt(entry, settings, onMixtapes),
                        onPlay = {
                            if (entry.id == playerState.entryId) onOpenPlayer() else {
                                vm.play(entry)
                                if (entry.isVideo) onOpenPlayer()
                            }
                        },
                        vm = vm,
                        modifier = Modifier.animateItem(),
                    )
                }
                if (shown.isEmpty()) item(key = "no-match") {
                    Text(
                        stringResource(R.string.no_match),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            } else if (downloads.isEmpty()) {
                item(key = "empty") { EmptyState() }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 12.dp), contentAlignment = Alignment.CenterEnd) {
                ExtendedFloatingActionButton(
                    onClick = { vm.openSheet() },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text(stringResource(R.string.add_link)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
            AnimatedVisibility(current != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                current?.let { MiniPlayer(it, vm, playerState.isPlaying, playerState.durationMs, onOpenPlayer, onOpenFullscreen) }
            }
        }
    }
}

@Composable
private fun TopBar(
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    sort: LibrarySort,
    onSort: (LibrarySort) -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp).height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searching) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            IconButton(onClick = onCloseSearch) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
        } else {
            Icon(CassetteIcon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, stringResource(R.string.search)) }
            Box {
                var open by remember { mutableStateOf(false) }
                IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.sort)) }
                DropdownMenu(open, onDismissRequest = { open = false }) {
                    LibrarySort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(sortLabel(option))) },
                            onClick = { onSort(option); open = false },
                            trailingIcon = { if (option == sort) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                        )
                    }
                }
            }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
        }
    }
}

fun sortLabel(sort: LibrarySort) = when (sort) {
    LibrarySort.NEWEST -> R.string.sort_newest
    LibrarySort.OLDEST -> R.string.sort_oldest
    LibrarySort.TITLE -> R.string.sort_title
    LibrarySort.LAST_PLAYED -> R.string.sort_last_played
}

@Composable
private fun EngineBanner(status: Engine.Status) {
    AnimatedVisibility(status is Engine.Status.Updating || status is Engine.Status.Failed, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val failed = status is Engine.Status.Failed
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (failed) Icons.Rounded.ErrorOutline else Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(if (failed) R.string.engine_failed else R.string.engine_updating),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (!failed) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(CircleShape))
                }
            }
        }
    }
}

/**
 * "Tapedeck 0.3.0 is out": Install downloads the APK for this phone with a progress bar and hands it to
 * Android's installer. The steps in between (allowing installs once, the download, the installer) show
 * right here.
 */
@Composable
private fun AppUpdateBanner(vm: MainViewModel) {
    val available by vm.appUpdate.collectAsStateWithLifecycle()
    val install by vm.appInstall.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    // Keeps the content while the banner animates away.
    var shown by remember { mutableStateOf(available) }
    if (available != null) shown = available
    AnimatedVisibility(available != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val release = shown ?: return@AnimatedVisibility
        val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Column(Modifier.padding(16.dp).animateContentSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NewReleases, null, tint = onContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_update_title, release.version), style = MaterialTheme.typography.titleSmall, color = onContainer)
                        val body = when (val state = install) {
                            AppInstallState.NeedsPermission -> stringResource(R.string.app_update_permission)
                            is AppInstallState.Downloading -> stringResource(
                                R.string.app_update_downloading,
                                Format.bytes(context, state.bytes),
                                Format.bytes(context, state.total.coerceAtLeast(state.bytes)),
                            )
                            AppInstallState.Installing -> stringResource(R.string.app_update_installing)
                            is AppInstallState.Failed -> stringResource(R.string.app_update_failed)
                            AppInstallState.Idle -> stringResource(R.string.app_update_body)
                        }
                        Text(
                            body,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (install is AppInstallState.Failed) MaterialTheme.colorScheme.error else onContainer.copy(alpha = 0.8f),
                        )
                    }
                }
                when (val state = install) {
                    is AppInstallState.Downloading -> {
                        Spacer(Modifier.height(12.dp))
                        TapeProgressBar(fraction = if (state.total > 0) state.bytes.toFloat() / state.total else null)
                    }
                    AppInstallState.Installing -> {
                        Spacer(Modifier.height(12.dp))
                        TapeProgressBar(fraction = null)
                    }
                    else -> Unit
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    when (install) {
                        is AppInstallState.Downloading -> {
                            TextButton(onClick = vm::cancelAppUpdate) { Text(stringResource(R.string.cancel), color = onContainer) }
                        }
                        // Android says nothing when its dialog is tapped away, so offer to bring it back.
                        AppInstallState.Installing -> {
                            TextButton(onClick = vm::cancelAppUpdate) { Text(stringResource(R.string.cancel), color = onContainer) }
                            TextButton(onClick = vm::installAppUpdate) { Text(stringResource(R.string.app_update_reopen), color = onContainer) }
                        }
                        AppInstallState.NeedsPermission -> {
                            TextButton(onClick = vm::dismissAppUpdate) { Text(stringResource(R.string.later), color = onContainer) }
                            Button(onClick = { context.startActivity(vm.installPermissionIntent()) }) { Text(stringResource(R.string.app_update_allow)) }
                        }
                        else -> {
                            TextButton(onClick = vm::dismissAppUpdate) { Text(stringResource(R.string.later), color = onContainer) }
                            TextButton(onClick = { runCatching { uriHandler.openUri(release.pageUrl) } }) {
                                Text(stringResource(R.string.whats_new), color = onContainer)
                            }
                            Button(onClick = vm::installAppUpdate) {
                                Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(if (install is AppInstallState.Failed) R.string.retry else R.string.app_update_install))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Tapedeck 0.4.0 is installed": feedback in the place of the install banner once Android finished. */
@Composable
private fun AppUpdatedBanner(vm: MainViewModel) {
    val version by vm.justUpdated.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var shown by remember { mutableStateOf(version) }
    if (version != null) shown = version
    AnimatedVisibility(version != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val current = shown ?: return@AnimatedVisibility
        val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = onContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_updated_banner_title, current), style = MaterialTheme.typography.titleSmall, color = onContainer)
                        Text(stringResource(R.string.app_updated_banner_body), style = MaterialTheme.typography.bodySmall, color = onContainer.copy(alpha = 0.8f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { runCatching { uriHandler.openUri("${AppUpdates.RELEASES_PAGE}/tag/v$current") } }) {
                        Text(stringResource(R.string.whats_new), color = onContainer)
                    }
                    Button(onClick = vm::dismissJustUpdated) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }
}

/** A YouTube link that was copied before Tapedeck was opened: one tap to the download sheet. */
@Composable
private fun ClipboardBanner(vm: MainViewModel) {
    val offer by vm.clipOffer.collectAsStateWithLifecycle()
    var shown by remember { mutableStateOf(offer) }
    if (offer != null) shown = offer
    AnimatedVisibility(offer != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val current = shown ?: return@AnimatedVisibility
        // The whole card opens the download sheet, not only its small button; × still dismisses it.
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(MaterialTheme.shapes.large).clickable(onClick = vm::acceptClipOffer),
        ) {
            Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.width(88.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    val thumbnail = current.preview?.thumbnailUrl
                    if (thumbnail != null) {
                        AsyncImage(thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Rounded.ContentPaste, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.clip_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        current.preview?.title ?: current.url,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = vm::dismissClipOffer) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
                    FilledIconButton(onClick = vm::acceptClipOffer) { Icon(Icons.Rounded.Download, stringResource(R.string.download)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun FilterRow(filter: LibraryFilter, onFilter: (LibraryFilter) -> Unit, counts: Map<LibraryFilter, Int>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
    ) {
        items(LibraryFilter.entries) { option ->
            val label = when (option) {
                LibraryFilter.ALL -> R.string.filter_all
                LibraryFilter.AUDIO -> R.string.filter_audio
                LibraryFilter.VIDEO -> R.string.filter_video
                LibraryFilter.NEW -> R.string.filter_new
            }
            FilterChip(
                selected = filter == option,
                onClick = { onFilter(option) },
                label = { Text(stringResource(label) + "  " + (counts[option] ?: 0)) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun DownloadCard(
    entry: Entry,
    progress: LiveProgress?,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val failed = entry.state == DownloadState.FAILED
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(entry, Modifier.width(104.dp).aspectRatio(16f / 9f), corner = 10.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    val (status, color) = when (entry.state) {
                        DownloadState.QUEUED -> stringResource(R.string.state_queued) to MaterialTheme.colorScheme.onSurfaceVariant
                        DownloadState.WAITING_NETWORK -> stringResource(R.string.state_waiting_network) to MaterialTheme.colorScheme.secondary
                        DownloadState.RUNNING -> Format.progress(context, progress) to MaterialTheme.colorScheme.onSurfaceVariant
                        DownloadState.FAILED -> stringResource(errorText(entry.errorKind)) to MaterialTheme.colorScheme.error
                        else -> "" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (entry.state == DownloadState.WAITING_NETWORK) {
                            Icon(Icons.Rounded.WifiOff, null, tint = color, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(status, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Site.of(entry.url)?.let { site ->
                            SiteLogo(site, size = 12.dp)
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            optionsLabel(entry),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                }
                if (failed) {
                    IconButton(onClick = onRetry) { Icon(Icons.Rounded.Replay, stringResource(R.string.retry)) }
                    IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
                } else {
                    IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
                }
            }
            if (entry.state == DownloadState.RUNNING) {
                Spacer(Modifier.height(12.dp))
                TapeProgressBar(fraction = progress?.fraction)
            }
            if (failed && !entry.errorDetail.isNullOrBlank() && entry.errorKind == ErrorKind.UNKNOWN.name) {
                Text(
                    entry.errorDetail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun optionsLabel(entry: Entry): String {
    val o = entry.options
    return when (o.kind) {
        MediaKind.AUDIO -> stringResource(R.string.kind_audio) + " · " + (
            o.customLabel ?: (
                stringResource(audioFormatLabel(o.audioFormat)) +
                    if (o.audioQuality == AudioQuality.SAVER) " · " + stringResource(R.string.quality_saver) else ""
                )
            )
        MediaKind.VIDEO -> stringResource(R.string.kind_video) + " · " + stringResource(videoFormatLabel(o.videoFormat)) + " · " +
            (o.customLabel ?: "${o.videoHeight}p")
    }
}

fun errorText(kind: String?): Int = when (kind?.let { runCatching { ErrorKind.valueOf(it) }.getOrNull() }) {
    ErrorKind.NETWORK -> R.string.error_network
    ErrorKind.UNAVAILABLE -> R.string.error_unavailable
    ErrorKind.PRIVATE -> R.string.error_private
    ErrorKind.AGE_RESTRICTED -> R.string.error_age
    ErrorKind.MEMBERS_ONLY -> R.string.error_members
    ErrorKind.NOT_YET_LIVE -> R.string.error_not_yet
    ErrorKind.BOT_CHECK -> R.string.error_bot
    ErrorKind.LOGIN_REQUIRED -> R.string.error_login
    ErrorKind.BLOCKED -> R.string.error_blocked
    ErrorKind.UNSUPPORTED_URL -> R.string.error_unsupported
    ErrorKind.FORMAT -> R.string.error_format
    ErrorKind.STORAGE -> R.string.error_storage
    ErrorKind.EXTRACTOR -> R.string.error_extractor
    ErrorKind.ENGINE -> R.string.error_engine
    ErrorKind.UNKNOWN, null -> R.string.error_unknown
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCard(
    entry: Entry,
    isCurrent: Boolean,
    isPlaying: Boolean,
    deleteAt: Long?,
    onPlay: () -> Unit,
    vm: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var menu by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val background = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(background)
                .combinedClickable(onClick = onPlay, onLongClick = { menu = true })
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(128.dp)) {
                Box {
                    Thumbnail(entry, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    if (isCurrent) {
                        Box(
                            Modifier.align(Alignment.Center).size(34.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                if (entry.listenedFraction > 0f && !entry.finished) {
                    Spacer(Modifier.height(5.dp))
                    TapeProgressBar(entry.listenedFraction, active = false, height = 3.dp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // The site's logo before the channel shows at a glance whether it is from YouTube, Instagram …
                SourceLine(entry.url, entry.uploader, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        entry.isNew -> Badge(stringResource(R.string.badge_new), MaterialTheme.colorScheme.primary)
                        entry.finished -> Icon(Icons.Rounded.CheckCircle, stringResource(R.string.badge_heard), tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(15.dp))
                    }
                    if (entry.keep) Icon(Icons.Rounded.PushPin, stringResource(R.string.keep), tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                    Text(
                        listOfNotNull(entry.formatLabel, Format.bytes(context, entry.sizeBytes)).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (deleteAt != null) {
                    // Rounded up: three days after the download reads "in 3 days", not "in 2".
                    val dayMs = TimeUnit.DAYS.toMillis(1)
                    val days = ((deleteAt - System.currentTimeMillis()).coerceAtLeast(0) + dayMs - 1).div(dayMs).toInt()
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                        Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            if (days < 1) stringResource(R.string.deleted_today) else pluralStringResource(R.plurals.deleted_in_days, days, days),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    MenuItem(Icons.Rounded.PlayArrow, R.string.play_from_start) { menu = false; vm.playFromStart(entry) }
                    MenuItem(Icons.AutoMirrored.Rounded.PlaylistAdd, R.string.mixtape_add) { menu = false; picking = true }
                    MenuItem(Icons.Rounded.DoneAll, if (entry.finished) R.string.mark_unheard else R.string.mark_heard) { menu = false; vm.toggleFinished(entry) }
                    MenuItem(Icons.Rounded.PushPin, if (entry.keep) R.string.unkeep else R.string.keep) { menu = false; vm.toggleKeep(entry) }
                    MenuItem(Icons.Rounded.Share, R.string.share_file) {
                        menu = false
                        vm.shareIntent(entry)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    MenuItem(Icons.Rounded.SaveAlt, R.string.export) { menu = false; vm.export(entry) }
                    MenuItem(Icons.AutoMirrored.Rounded.OpenInNew, R.string.open_source) { menu = false; runCatching { uriHandler.openUri(entry.url) } }
                    HorizontalDivider()
                    MenuItem(Icons.Rounded.Delete, R.string.delete, danger = true) { menu = false; vm.delete(setOf(entry.id)) }
                }
            }
        }
    }
    if (picking) MixtapePickerDialog(vm, entry, onDismiss = { picking = false })
}

@Composable
private fun MenuItem(icon: ImageVector, label: Int, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(stringResource(label), color = color) },
        leadingIcon = { Icon(icon, null, tint = color) },
        onClick = onClick,
    )
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
fun MiniPlayer(entry: Entry, vm: MainViewModel, isPlaying: Boolean, durationMs: Long, onOpen: () -> Unit, onOpenFullscreen: () -> Unit) {
    var position by remember { mutableLongStateOf(vm.player.currentPosition()) }
    LaunchedEffect(isPlaying, entry.id) {
        while (true) {
            position = vm.player.currentPosition()
            delay(500)
        }
    }
    val duration = durationMs.takeIf { it > 0 } ?: entry.durationMs
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // A video opens straight in full screen on a double tap (a single tap then waits a moment for the second).
        Column(Modifier.combinedClickable(onClick = onOpen, onDoubleClick = if (entry.isVideo) onOpenFullscreen else null)) {
            Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(entry, Modifier.width(72.dp).aspectRatio(16f / 9f), showDuration = false, corner = 9.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        Format.clock(position) + " / " + Format.clock(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.player.seekBack() }) { Icon(Icons.Rounded.Replay10, stringResource(R.string.action_back_10)) }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(46.dp)) {
                    IconButton(onClick = { vm.player.togglePlay() }) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            stringResource(if (isPlaying) R.string.pause else R.string.play),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
            }
            TapeProgressBar(
                fraction = if (duration > 0) position.toFloat() / duration else 0f,
                active = false,
                height = 3.dp,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(112.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CassetteIcon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudDownload, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.empty_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun audioFormatLabel(format: AudioFormat) = when (format) {
    AudioFormat.M4A -> R.string.format_m4a
    AudioFormat.MP3 -> R.string.format_mp3
    AudioFormat.ORIGINAL -> R.string.format_original
}
