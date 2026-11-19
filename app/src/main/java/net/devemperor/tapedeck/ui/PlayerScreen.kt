package net.devemperor.tapedeck.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import net.devemperor.tapedeck.Format
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.data.Chapter
import net.devemperor.tapedeck.data.Entry
import java.io.File

private val SPEEDS = listOf(0.75f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
fun PlayerScreen(vm: MainViewModel, onClose: () -> Unit) {
    val state by vm.player.state.collectAsStateWithLifecycle()
    val controller by vm.player.controller.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val entry = state.entryId?.let { id -> entries.firstOrNull { it.id == id } }
    if (entry == null) {
        // Only close once connected: while the controller reconnects there is simply nothing to show yet.
        if (controller != null) LaunchedEffect(Unit) { onClose() }
        return
    }

    var position by remember { mutableLongStateOf(vm.player.currentPosition()) }
    LaunchedEffect(state.isPlaying, entry.id) {
        while (true) {
            position = vm.player.currentPosition()
            delay(250)
        }
    }
    val duration = state.durationMs.takeIf { it > 0 } ?: entry.durationMs
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var chaptersOpen by remember { mutableStateOf(false) }

    if (fullscreen && entry.isVideo) {
        FullscreenVideo(controller, onExit = { fullscreen = false })
        return
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val cover = entry.thumbnailFile?.let(::File)?.takeIf { it.exists() }
        if (cover != null) {
            AsyncImage(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(70.dp).alpha(0.45f))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.85f), MaterialTheme.colorScheme.background)),
            ),
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.close), Modifier.size(30.dp)) }
                Text(
                    stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.weight(1f))
            if (entry.isVideo) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).shadow(18.dp, MaterialTheme.shapes.large).clip(MaterialTheme.shapes.large)) {
                    VideoSurface(controller, Modifier.fillMaxSize())
                    IconButton(
                        onClick = { fullscreen = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape),
                    ) { Icon(Icons.Rounded.Fullscreen, stringResource(R.string.fullscreen), tint = Color.White) }
                }
            } else {
                Thumbnail(
                    entry,
                    Modifier.fillMaxWidth(0.86f).aspectRatio(1f).shadow(24.dp, MaterialTheme.shapes.extraLarge),
                    showDuration = false,
                    showKind = false,
                    corner = 30.dp,
                )
            }
            Spacer(Modifier.weight(1f))

            Column(Modifier.fillMaxWidth()) {
                Text(entry.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                entry.uploader?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Spacer(Modifier.height(18.dp))

            SeekBar(position, duration, onSeek = { vm.player.seekTo(it) })
            currentChapter(entry.chapters, position)?.let {
                Text(
                    it.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { chaptersOpen = true }.padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.player.previous() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous), Modifier.size(30.dp))
                }
                IconButton(onClick = { vm.player.seekBack() }, modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Rounded.Replay10, stringResource(R.string.action_back_10), Modifier.size(36.dp))
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 10.dp, modifier = Modifier.size(82.dp)) {
                    IconButton(onClick = { vm.player.togglePlay() }) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(46.dp),
                        )
                    }
                }
                IconButton(onClick = { vm.player.seekForward() }, modifier = Modifier.size(60.dp)) {
                    Icon(Icons.Rounded.Forward30, stringResource(R.string.action_forward_30), Modifier.size(36.dp))
                }
                IconButton(onClick = { vm.player.next() }, enabled = state.hasNext, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(18.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                Box {
                    var open by remember { mutableStateOf(false) }
                    PlayerChip(Icons.Rounded.Speed, formatSpeed(settings.playbackSpeed), selected = settings.playbackSpeed != 1f) { open = true }
                    DropdownMenu(open, onDismissRequest = { open = false }) {
                        SPEEDS.forEach { speed ->
                            DropdownMenuItem(text = { Text(formatSpeed(speed)) }, onClick = {
                                vm.updateSettings { it.copy(playbackSpeed = speed) }
                                open = false
                            })
                        }
                    }
                }
                PlayerChip(Icons.Rounded.GraphicEq, stringResource(R.string.skip_silence_short), selected = settings.skipSilence) {
                    vm.updateSettings { it.copy(skipSilence = !it.skipSilence) }
                }
                if (entry.chapters.isNotEmpty()) {
                    PlayerChip(Icons.AutoMirrored.Rounded.List, stringResource(R.string.chapters), selected = false) { chaptersOpen = true }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (chaptersOpen) {
        ChapterSheet(entry, position, onPick = { vm.player.seekTo(it.startMs); chaptersOpen = false }, onDismiss = { chaptersOpen = false })
    }
}

@Composable
private fun SeekBar(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            val shown = dragging?.let { (it * duration).toLong() } ?: position
            Text(Format.clock(shown), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("−" + Format.clock(duration - shown), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlayerChip(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(controller: Player?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { it.player = controller },
        onRelease = { it.player = null },
        modifier = modifier.background(Color.Black),
    )
}

@Composable
private fun FullscreenVideo(controller: Player?, onExit: () -> Unit) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insets?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    BackHandler(onBack = onExit)
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } })) {
        VideoSurface(controller, Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterSheet(entry: Entry, position: Long, onPick: (Chapter) -> Unit, onDismiss: () -> Unit) {
    val current = currentChapter(entry.chapters, position)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.chapters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            itemsIndexed(entry.chapters) { _, chapter ->
                val isCurrent = chapter == current
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(chapter) }.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Format.clock(chapter.startMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(64.dp),
                    )
                    Text(
                        chapter.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun currentChapter(chapters: List<Chapter>, position: Long): Chapter? =
    chapters.lastOrNull { it.startMs <= position }

private fun formatSpeed(speed: Float): String =
    (if (speed % 1f == 0f) "%.0f".format(speed) else "%.2f".format(speed).trimEnd('0')) + "×"
