package net.devemperor.tapedeck.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.provider.Settings
import android.view.OrientationEventListener
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
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
import androidx.compose.runtime.State
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
import androidx.compose.ui.input.pointer.pointerInput
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
import net.devemperor.tapedeck.player.PlayerUiState
import java.io.File
import kotlin.math.abs

private val SPEEDS = listOf(0.75f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
fun PlayerScreen(vm: MainViewModel, startFullscreen: Boolean = false, onClose: () -> Unit) {
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
    var fullscreen by rememberSaveable { mutableStateOf(startFullscreen) }
    var chaptersOpen by remember { mutableStateOf(false) }

    // Upright videos (Instagram, Shorts) are shown upright: big in the player, and full screen stays upright.
    val aspect = videoAspect(entry, state)
    val upright = aspect < 1f

    // Turning the phone: a landscape video goes full screen when the phone is turned sideways (with
    // auto-rotate on) and back when it is held upright again — the way video apps do it. An upright video
    // already fills the upright screen, so turning does nothing there. After leaving full screen by hand
    // while the phone is still sideways, it waits for the phone to be upright once before turning counts again.
    val context = LocalContext.current
    val held by rememberHeldOrientation(enabled = entry.isVideo)
    var byTurning by rememberSaveable { mutableStateOf(false) }
    var waitUpright by rememberSaveable { mutableStateOf(false) }
    var sawSideways by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(held, upright, entry.isVideo) {
        if (!entry.isVideo) return@LaunchedEffect
        when (held) {
            Held.UPRIGHT -> {
                waitUpright = false
                if (fullscreen && !upright && (byTurning || sawSideways) && autoRotateOn(context)) {
                    fullscreen = false
                    byTurning = false
                    sawSideways = false
                }
            }
            Held.SIDEWAYS -> when {
                fullscreen -> sawSideways = true
                !upright && !waitUpright && autoRotateOn(context) -> {
                    fullscreen = true
                    byTurning = true
                    sawSideways = true
                }
            }
            Held.UNKNOWN -> Unit
        }
    }
    fun enterFullscreen() {
        fullscreen = true
        byTurning = false
        sawSideways = held == Held.SIDEWAYS
    }
    fun exitFullscreen() {
        fullscreen = false
        byTurning = false
        sawSideways = false
        if (held == Held.SIDEWAYS) waitUpright = true
    }

    // The screen's orientation follows the video, not the sensor: the player with a video stays upright
    // (turning the phone opens full screen instead of a squeezed sideways player), full screen takes the
    // video's own shape. Audio leaves it to the phone's setting.
    val activity = context as? Activity
    val wanted = when {
        !entry.isVideo -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        fullscreen && !upright -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    LaunchedEffect(wanted) { activity?.requestedOrientation = wanted }
    DisposableEffect(Unit) { onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }

    if (fullscreen && entry.isVideo) {
        FullscreenVideo(vm, controller, entry, onExit = ::exitFullscreen)
        return
    }

    Box(Modifier.fillMaxSize().blockTouchesBelow().background(MaterialTheme.colorScheme.background)) {
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

            if (entry.isVideo) {
                // As large as the space between the top bar and the title allows, in the video's own shape:
                // an upright reel gets the height, a film the width.
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.aspectRatio(aspect).shadow(18.dp, MaterialTheme.shapes.large).clip(MaterialTheme.shapes.large)) {
                        VideoSurface(controller, Modifier.fillMaxSize())
                        // A double tap on the picture goes full screen, as the button does.
                        Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures(onDoubleTap = { enterFullscreen() }) })
                        IconButton(
                            onClick = ::enterFullscreen,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape),
                        ) { Icon(Icons.Rounded.Fullscreen, stringResource(R.string.fullscreen), tint = Color.White) }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
                Thumbnail(
                    entry,
                    Modifier.fillMaxWidth(0.86f).aspectRatio(1f).shadow(24.dp, MaterialTheme.shapes.extraLarge),
                    showDuration = false,
                    showKind = false,
                    corner = 30.dp,
                )
                Spacer(Modifier.weight(1f))
            }

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
                    Icon(Icons.Rounded.Forward10, stringResource(R.string.action_forward_10), Modifier.size(36.dp))
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
private fun SeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    onDrag: () -> Unit = {},
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = {
                dragging = it
                onDrag()
            },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = trackColor,
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            val shown = dragging?.let { (it * duration).toLong() } ?: position
            Text(Format.clock(shown), style = MaterialTheme.typography.labelMedium, color = textColor)
            Spacer(Modifier.weight(1f))
            Text("−" + Format.clock(duration - shown), style = MaterialTheme.typography.labelMedium, color = textColor)
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

/**
 * Full-screen video with its own controls: a tap shows or hides them, and while the video plays they fade
 * out by themselves after a few seconds. Title and the way back at the top, ⟲10 · play/pause · 10⟳ in the
 * middle, the seek bar with the chapter and "leave full screen" at the bottom.
 */
@Composable
private fun FullscreenVideo(vm: MainViewModel, controller: Player?, entry: Entry, onExit: () -> Unit) {
    // The orientation is set by PlayerScreen (upright videos stay upright); here only the system bars go.
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { insets?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    BackHandler(onBack = onExit)

    val state by vm.player.state.collectAsStateWithLifecycle()
    var position by remember { mutableLongStateOf(vm.player.currentPosition()) }
    LaunchedEffect(entry.id) {
        while (true) {
            position = vm.player.currentPosition()
            delay(250)
        }
    }
    val duration = state.durationMs.takeIf { it > 0 } ?: entry.durationMs
    var controls by remember { mutableStateOf(true) }
    var touchedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    fun touched() {
        touchedAt = System.currentTimeMillis()
        controls = true
    }
    LaunchedEffect(controls, state.isPlaying, touchedAt) {
        if (controls && state.isPlaying) {
            delay(CONTROLS_TIMEOUT_MS)
            controls = false
        }
    }

    // Double tap left or right: 10 s back or ahead, and every further tap on that side within a moment adds
    // 10 s more (−10, −20, −30 …). Press and slide sideways: scrub, with the jump and the target shown.
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    var scrub by remember { mutableStateOf<Scrub?>(null) }
    fun clamp(ms: Long) = if (duration > 0) ms.coerceIn(0, duration) else ms.coerceAtLeast(0)
    fun seekStep(forward: Boolean) {
        val now = System.currentTimeMillis()
        val chained = seekFeedback?.takeIf { it.forward == forward && now - it.at < SEEK_CHAIN_MS }
        val target = clamp(vm.player.currentPosition() + if (forward) DOUBLE_TAP_SEEK_MS else -DOUBLE_TAP_SEEK_MS)
        vm.player.seekTo(target)
        position = target
        seekFeedback = SeekFeedback(forward, (chained?.totalMs ?: 0) + DOUBLE_TAP_SEEK_MS, now)
    }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(SEEK_CHAIN_MS)
            seekFeedback = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(controller, Modifier.fillMaxSize())
        // Above the picture and below the controls: their buttons and seek bar keep their own touches.
        Box(
            Modifier.fillMaxSize()
                .pointerInput(duration) {
                    // While the jump is still shown, every further tap on that side counts at once (on the
                    // press itself, not after the wait for a possible second tap) and is no tap or double tap.
                    var chained = false
                    detectTapGestures(
                        onPress = { offset ->
                            val forward = offset.x > size.width / 2
                            chained = seekFeedback?.let { it.forward == forward && System.currentTimeMillis() - it.at < SEEK_CHAIN_MS } == true
                            if (chained) seekStep(forward)
                        },
                        onTap = {
                            when {
                                chained -> Unit
                                controls -> controls = false
                                else -> touched()
                            }
                        },
                        onDoubleTap = { offset -> if (!chained) seekStep(offset.x > size.width / 2) },
                    )
                }
                .pointerInput(duration) {
                    var start = 0L
                    var travelled = 0f
                    var lastSent = 0L
                    detectHorizontalDragGestures(
                        onDragStart = {
                            start = vm.player.currentPosition()
                            travelled = 0f
                            lastSent = start
                            seekFeedback = null
                            controls = false
                            scrub = Scrub(start, start)
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            travelled += amount
                            // One swipe across the screen covers a quarter of the video, between 1.5 and 10 minutes.
                            val span = (duration / 4).coerceIn(SCRUB_SPAN_MIN_MS, SCRUB_SPAN_MAX_MS)
                            val target = clamp(start + (travelled / size.width * span).toLong())
                            scrub = Scrub(start, target)
                            // The picture follows the finger, without sending the player a seek for every pixel.
                            if (abs(target - lastSent) >= SCRUB_SEEK_STEP_MS) {
                                vm.player.seekTo(target)
                                lastSent = target
                            }
                        },
                        onDragEnd = {
                            scrub?.let {
                                vm.player.seekTo(it.target)
                                position = it.target
                            }
                            scrub = null
                        },
                        onDragCancel = { scrub = null },
                    )
                },
        )
        seekFeedback?.let { SeekSide(it, Modifier.align(if (it.forward) Alignment.CenterEnd else Alignment.CenterStart)) }
        scrub?.let { ScrubBadge(it, duration, Modifier.align(Alignment.Center)) }
        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onExit) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.close), tint = Color.White) }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        entry.uploader?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1) }
                    }
                }

                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.player.seekBack(); touched() }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Rounded.Replay10, stringResource(R.string.action_back_10), tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp)) {
                        IconButton(onClick = { vm.player.togglePlay(); touched() }) {
                            Icon(
                                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(46.dp),
                            )
                        }
                    }
                    IconButton(onClick = { vm.player.seekForward(); touched() }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Rounded.Forward10, stringResource(R.string.action_forward_10), tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                }

                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                        .padding(start = 24.dp, end = 12.dp, bottom = 8.dp, top = 24.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SeekBar(
                                position, duration,
                                onSeek = { vm.player.seekTo(it); touched() },
                                textColor = Color.White.copy(alpha = 0.85f),
                                trackColor = Color.White.copy(alpha = 0.25f),
                                onDrag = ::touched,
                            )
                        }
                        IconButton(onClick = onExit) {
                            Icon(Icons.Rounded.FullscreenExit, stringResource(R.string.fullscreen_exit), tint = Color.White)
                        }
                    }
                    currentChapter(entry.chapters, position)?.let {
                        Text(it.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private const val CONTROLS_TIMEOUT_MS = 3_500L
private const val DOUBLE_TAP_SEEK_MS = 10_000L
private const val SEEK_CHAIN_MS = 1_000L
private const val SCRUB_SPAN_MIN_MS = 90_000L
private const val SCRUB_SPAN_MAX_MS = 600_000L
private const val SCRUB_SEEK_STEP_MS = 400L

/** The jump of the latest double taps, shown on its side of the screen. */
private data class SeekFeedback(val forward: Boolean, val totalMs: Long, val at: Long)

/** A scrub in progress: where it started and where the finger is now. */
private data class Scrub(val start: Long, val target: Long)

/** A half-round glow on the tapped side with ⏪ "−30 s", like other video players. */
@Composable
private fun SeekSide(feedback: SeekFeedback, modifier: Modifier) {
    val shape = if (feedback.forward) {
        RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
    } else {
        RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
    }
    Box(
        modifier.fillMaxHeight().fillMaxWidth(0.32f).clip(shape).background(Color.White.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (feedback.forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                null, tint = Color.White, modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                (if (feedback.forward) "+" else "−") + stringResource(R.string.seconds_short, (feedback.totalMs / 1000).toInt()),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
    }
}

/** "+1:25" big, "12:34 / 45:00" below it, while sliding. */
@Composable
private fun ScrubBadge(scrub: Scrub, duration: Long, modifier: Modifier) {
    val delta = scrub.target - scrub.start
    Surface(shape = MaterialTheme.shapes.large, color = Color.Black.copy(alpha = 0.6f), modifier = modifier) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                (if (delta < 0) "−" else "+") + Format.clock(abs(delta)),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Text(
                Format.clock(scrub.target) + if (duration > 0) " / " + Format.clock(duration) else "",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
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

/** Width ÷ height of the picture: what the player reports, else what yt-dlp said at download, else 16:9. */
private fun videoAspect(entry: Entry, state: PlayerUiState): Float {
    val live = state.videoAspect?.takeIf { state.entryId == entry.id }
    val w = entry.videoWidth ?: 0
    val h = entry.videoHeight ?: 0
    val stored = if (w > 0 && h > 0) w.toFloat() / h else null
    return (live ?: stored ?: 16f / 9f).coerceIn(0.4f, 2.6f)
}

/** How the phone is held, from the motion sensor — also while the screen itself is locked to one orientation. */
private enum class Held { UPRIGHT, SIDEWAYS, UNKNOWN }

@Composable
private fun rememberHeldOrientation(enabled: Boolean): State<Held> {
    val context = LocalContext.current
    val held = remember { mutableStateOf(Held.UNKNOWN) }
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return // lying flat
                // Gaps between the ranges, so a phone held at an angle does not flip back and forth.
                val next = when {
                    degrees <= 25 || degrees >= 335 -> Held.UPRIGHT
                    degrees in 65..115 || degrees in 245..295 -> Held.SIDEWAYS
                    else -> return
                }
                if (held.value != next) held.value = next
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    return held
}

/** The quick-settings "Auto-rotate" switch: with it off, turning the phone changes nothing. */
private fun autoRotateOn(context: Context): Boolean =
    Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

private fun currentChapter(chapters: List<Chapter>, position: Long): Chapter? =
    chapters.lastOrNull { it.startMs <= position }

private fun formatSpeed(speed: Float): String =
    (if (speed % 1f == 0f) "%.0f".format(speed) else "%.2f".format(speed).trimEnd('0')) + "×"
