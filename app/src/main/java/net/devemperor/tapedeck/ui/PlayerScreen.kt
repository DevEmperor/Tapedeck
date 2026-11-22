package net.devemperor.tapedeck.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.provider.Settings
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.View
import android.view.accessibility.CaptioningManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import androidx.media3.ui.SubtitleView
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import net.devemperor.tapedeck.Format
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.data.AppSettings
import net.devemperor.tapedeck.data.Chapter
import net.devemperor.tapedeck.data.Entry
import net.devemperor.tapedeck.player.PlayerUiState
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private val SPEEDS = listOf(0.75f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
fun PlayerScreen(vm: MainViewModel, startFullscreen: Boolean = false, visibility: AnimatedVisibilityScope? = null, onClose: () -> Unit) {
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
    var playbackOpen by remember { mutableStateOf(false) }
    val mixtapes by vm.mixtapes.collectAsStateWithLifecycle()

    // Scrubbing the seek bar spools the deck's tape fast forward or back; the reels run down again as
    // soon as the finger rests.
    var scrub by remember { mutableStateOf<Float?>(null) }
    var scrubDirection by remember { mutableFloatStateOf(0f) }
    var scrubMoved by remember { mutableIntStateOf(0) }
    LaunchedEffect(scrubMoved) {
        delay(180)
        scrubDirection = 0f
    }

    // The colour of what plays: its mixtape's, else the cover's — for the background and the deck's cassette.
    val coverColor by rememberCoverColor(entry)
    val playingTape = state.mixtapeId?.let { id -> mixtapes.firstOrNull { it.id == id } }
    val tapeColor = playingTape?.let { mixtapeColor(it.color) } ?: coverColor ?: MaterialTheme.colorScheme.primary

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

    // The background takes the colour of what plays and changes smoothly with the next song.
    val background = MaterialTheme.colorScheme.background
    val tint by animateColorAsState(lerp(background, tapeColor, 0.42f), tween(900), label = "player-tint")
    Box(Modifier.fillMaxSize().blockTouchesBelow().background(background)) {
        val cover = entry.thumbnailFile?.let(::File)?.takeIf { it.exists() }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to tint, 0.55f to lerp(background, tint, 0.35f), 1f to background)))
        if (cover != null) {
            // A breath of the cover's own texture on top of its colour.
            AsyncImage(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(80.dp).alpha(0.18f))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, background.copy(alpha = 0.7f), background)),
            ),
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Close on the left, what is playing (and from which mixtape) in the middle, and on the right the
            // chapters and the playback menu (speed, skip silence, repeat, shuffle) — instead of a row of
            // chips under the controls.
            Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.close), Modifier.size(30.dp))
                }
                Column(Modifier.align(Alignment.Center).padding(horizontal = 104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.now_playing), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val tape = state.mixtapeId?.let { id -> mixtapes.firstOrNull { it.id == id } }
                    if (tape != null) {
                        Text(
                            stringResource(R.string.from_mixtape, tape.name),
                            style = MaterialTheme.typography.labelMedium,
                            color = mixtapeColor(tape.color),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                    if (entry.chapters.isNotEmpty()) {
                        IconButton(onClick = { chaptersOpen = true }) { Icon(Icons.AutoMirrored.Rounded.List, stringResource(R.string.chapters)) }
                    }
                    PlaybackButton(settings, state) { playbackOpen = true }
                }
            }

            if (entry.isVideo) {
                // As large as the space between the top bar and the title allows, in the video's own shape:
                // an upright reel gets the height, a film the width.
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.sharedTransition(SharedKeys.artwork(entry.id), visibility)
                            .aspectRatio(aspect).shadow(18.dp, MaterialTheme.shapes.large).clip(MaterialTheme.shapes.large),
                    ) {
                        // Captions above the CC and full-screen buttons (48 dp + 6 dp margin), not behind them.
                        VideoSurface(controller, Modifier.fillMaxSize(), texture = true, captionsAbove = 60.dp)
                        VideoCover(
                            entry, controller, state.isPlaying,
                            travelling = visibility?.transition?.let { it.currentState != it.targetState } == true,
                        )
                        // A double tap on the picture goes full screen, as the button does.
                        Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures(onDoubleTap = { enterFullscreen() }) })
                        Row(Modifier.align(Alignment.BottomEnd).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // CC only for a video that has subtitles.
                            SubtitleButton(vm, state)
                            IconButton(
                                onClick = ::enterFullscreen,
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape),
                            ) { Icon(Icons.Rounded.Fullscreen, stringResource(R.string.fullscreen), tint = Color.White) }
                        }
                    }
                }
            } else {
                // Audio: the cover, or the cassette in a tape deck — a tap on it switches, and it stays so.
                Spacer(Modifier.weight(1f))
                Box(Modifier.sharedTransition(SharedKeys.artwork(entry.id), visibility).fillMaxWidth(0.86f).aspectRatio(1f)) {
                    Crossfade(settings.cassetteView, animationSpec = tween(450), label = "artwork") { deck ->
                        val artwork = Modifier.fillMaxSize().clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { vm.updateSettings { it.copy(cassetteView = !it.cassetteView) } }
                        if (deck) {
                            val shown = scrub?.let { (it * duration).toLong() } ?: position
                            CassetteDeck(
                                title = entry.title,
                                color = tapeColor,
                                velocity = when {
                                    scrub != null -> ReelSpeed.SCRUB * scrubDirection
                                    state.isPlaying -> ReelSpeed.PLAY * state.speed
                                    else -> 0f
                                },
                                progress = scrub ?: if (duration > 0) position.toFloat() / duration else 0f,
                                counter = Format.clock(shown),
                                playing = state.isPlaying,
                                modifier = artwork.shadow(24.dp, RoundedCornerShape(30.dp)),
                            )
                        } else {
                            Thumbnail(
                                entry,
                                artwork.shadow(24.dp, MaterialTheme.shapes.extraLarge),
                                showDuration = false,
                                showKind = false,
                                corner = 30.dp,
                            )
                        }
                    }
                    // A small switch in the corner, so the deck is easy to find.
                    IconButton(
                        onClick = { vm.updateSettings { it.copy(cassetteView = !it.cassetteView) } },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape),
                    ) {
                        Icon(
                            if (settings.cassetteView) Icons.Rounded.Image else CassetteIcon,
                            stringResource(if (settings.cassetteView) R.string.show_cover else R.string.show_cassette),
                            tint = Color.White,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            }

            Column(Modifier.fillMaxWidth()) {
                Text(entry.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                SourceLine(entry.url, entry.uploader, MaterialTheme.typography.bodyMedium, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(18.dp))

            SeekBar(
                position, duration,
                onSeek = { vm.player.seekTo(it) },
                chapters = entry.chapters,
                onScrub = { fraction ->
                    if (fraction != null) {
                        val before = scrub ?: if (duration > 0) position.toFloat() / duration else 0f
                        if (fraction != before) scrubDirection = if (fraction > before) 1f else -1f
                        scrubMoved++
                    }
                    scrub = fraction
                },
            )
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
            Spacer(Modifier.height(36.dp))
        }
    }
    if (playbackOpen) {
        PlaybackSheet(vm, settings, state, onDismiss = { playbackOpen = false })
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
    chapters: List<Chapter> = emptyList(),
    onScrub: (Float?) -> Unit = {},
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    // A short tick each time the thumb crosses into another chapter, so they can be felt while scrubbing.
    val haptic = LocalHapticFeedback.current
    var chapterIndex by remember { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { value ->
                val index = chapters.indexOfLast { it.startMs <= (value * duration).toLong() }
                if (dragging != null && index != chapterIndex) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                chapterIndex = index
                dragging = value
                onDrag()
                onScrub(value)
            },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
                onScrub(null)
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

/**
 * The playback menu's button in the top bar: a plain icon while everything is as usual, a tinted pill
 * with the speed ("1.5×") as soon as something differs — so the state stays visible without a chip row.
 */
@Composable
private fun PlaybackButton(settings: AppSettings, state: PlayerUiState, onClick: () -> Unit) {
    val changed = settings.playbackSpeed != 1f || settings.skipSilence || state.shuffle || state.repeatMode != Player.REPEAT_MODE_OFF
    val container = if (changed) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val content = if (changed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        modifier = Modifier.padding(end = 4.dp).height(40.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tune, stringResource(R.string.playback_title), tint = content, modifier = Modifier.size(22.dp))
            if (settings.playbackSpeed != 1f) {
                Spacer(Modifier.width(6.dp))
                Text(formatSpeed(settings.playbackSpeed), style = MaterialTheme.typography.labelLarge, color = content)
            }
        }
    }
}

/** Speed, skip silence, repeat and shuffle in one sheet. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlaybackSheet(vm: MainViewModel, settings: AppSettings, state: PlayerUiState, onDismiss: () -> Unit) {
    // Fully open at once: half-open, its last rows sat under the navigation bar.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
            Text(stringResource(R.string.playback_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(18.dp))

            SheetLabel(Icons.Rounded.Speed, R.string.playback_speed)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SPEEDS.forEach { speed ->
                    FilterChip(
                        selected = settings.playbackSpeed == speed,
                        onClick = { vm.updateSettings { it.copy(playbackSpeed = speed) } },
                        label = { Text(formatSpeed(speed)) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            SheetLabel(Icons.Rounded.Repeat, R.string.playback_repeat)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val modes = listOf(
                    Player.REPEAT_MODE_OFF to R.string.repeat_off,
                    Player.REPEAT_MODE_ALL to R.string.repeat_all,
                    Player.REPEAT_MODE_ONE to R.string.repeat_one,
                )
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = state.repeatMode == mode,
                        onClick = { vm.player.setRepeatMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        icon = {},
                    ) { Text(stringResource(label)) }
                }
            }
            Spacer(Modifier.height(8.dp))

            SheetSwitch(Icons.Rounded.Shuffle, R.string.playback_shuffle, R.string.playback_shuffle_sub, state.shuffle) { vm.player.setShuffle(it) }
            SheetSwitch(Icons.Rounded.GraphicEq, R.string.skip_silence, R.string.skip_silence_sub, settings.skipSilence) { on ->
                vm.updateSettings { it.copy(skipSilence = on) }
            }
        }
    }
}

@Composable
private fun SheetLabel(icon: ImageVector, text: Int) {
    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(text), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SheetSwitch(icon: ImageVector, title: Int, sub: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * The download's picture lying on the video until its first frame is there, and again while the picture
 * travels into the player or back into its card: the flight shows the picture the card showed, never an
 * empty black box, and the video fades in once it has a frame.
 */
@Composable
private fun BoxScope.VideoCover(entry: Entry, controller: Player?, playing: Boolean, travelling: Boolean) {
    var framed by remember(entry.id, controller) { mutableStateOf(false) }
    DisposableEffect(controller, entry.id) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                framed = true
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }
    // Should the report of the first frame not come, a moment of playing uncovers the video anyway.
    LaunchedEffect(playing, entry.id) {
        if (playing) {
            delay(1_500)
            framed = true
        }
    }
    val alpha by animateFloatAsState(if (framed && !travelling) 0f else 1f, tween(if (travelling) 120 else 280), label = "video-cover")
    if (alpha > 0f) {
        Thumbnail(entry, Modifier.matchParentSize().alpha(alpha), showDuration = false, showKind = false, corner = 0.dp)
    }
}

/**
 * The video. In the player it is drawn on a TextureView ([texture]), so it moves and scales with the
 * picture travelling between list and player; full screen keeps the lighter SurfaceView.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(controller: Player?, modifier: Modifier = Modifier, texture: Boolean = false, captionsAbove: Dp = 0.dp) {
    val reserve = rememberUpdatedState(with(LocalDensity.current) { captionsAbove.toPx() })
    AndroidView(
        factory = { context ->
            val view = if (texture) {
                LayoutInflater.from(context).inflate(R.layout.video_texture, null, false) as PlayerView
            } else {
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            }
            // On every change of size (the shared transition), the captions follow.
            view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> (v as PlayerView).liftCaptions(reserve.value) }
            view
        },
        update = {
            it.player = controller
            it.liftCaptions(reserve.value)
        },
        onRelease = { it.player = null },
        modifier = modifier.background(Color.Black),
    )
}

/**
 * Keeps [reserve] px free below the player's own captions (for the buttons or controls on the picture).
 * Padding moves every caption up, also those the file places itself (MP4 subtitles sit at 85 % of the
 * height, where the bottom padding fraction does not reach). The text keeps the size it has on the whole
 * picture (a fraction of its height, as the player's default), so it does not shrink with the padding.
 */
private fun PlayerView.liftCaptions(reserve: Float) {
    val captions = subtitleView ?: return
    if (height <= 0) return
    val padding = reserve.roundToInt()
    if (captions.paddingBottom != padding) {
        captions.setPadding(0, 0, 0, padding)
        // Its request for a new layout never gets through the AndroidView (22.11.2026: it stayed pending
        // and the captions stayed where they were), so they lay themselves out again in their frame.
        if (captions.width > 0 && captions.height > 0) {
            captions.measure(
                View.MeasureSpec.makeMeasureSpec(captions.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(captions.height, View.MeasureSpec.EXACTLY),
            )
            captions.layout(captions.left, captions.top, captions.right, captions.bottom)
        }
    }
    val captioning = context.getSystemService(CaptioningManager::class.java)
    val scale = if (captioning?.isEnabled == true) captioning.fontScale else 1f
    captions.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX, SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * height * scale)
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
    val haptic = LocalHapticFeedback.current
    var scrubChapter by remember { mutableIntStateOf(-1) }
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
        // While the controls show, the captions move up above the seek bar (as YouTube's do).
        val captionsAbove by animateDpAsState(if (controls) 92.dp else 0.dp, tween(200), label = "captions")
        VideoSurface(controller, Modifier.fillMaxSize(), captionsAbove = captionsAbove)
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
                            scrubChapter = entry.chapters.indexOfLast { it.startMs <= start }
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
                            // A tick when the scrub crosses into another chapter.
                            val chapter = entry.chapters.indexOfLast { it.startMs <= target }
                            if (chapter != scrubChapter) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            scrubChapter = chapter
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
                        // Over the video it is always dark, whatever the app's theme.
                        SourceLine(entry.url, entry.uploader, MaterialTheme.typography.bodySmall, Color.White.copy(alpha = 0.7f), onDark = true)
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
                        val tracks by vm.player.state.collectAsStateWithLifecycle()
                        SubtitleButton(vm, tracks)
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
        val chapterList = rememberLazyListState()
        Box(Modifier.fillMaxWidth().navigationBarsPadding()) {
        LazyColumn(Modifier.fillMaxWidth(), state = chapterList) {
            itemsIndexed(entry.chapters.filterNot { it.isPlaceholder }) { _, chapter ->
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
        VerticalScrollbar(chapterList)
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

/** The chapter playing at [position] — nothing while a gap filler of yt-dlp plays. */
private fun currentChapter(chapters: List<Chapter>, position: Long): Chapter? =
    chapters.lastOrNull { it.startMs <= position }?.takeUnless { it.isPlaceholder }

private fun formatSpeed(speed: Float): String =
    (if (speed % 1f == 0f) "%.0f".format(speed) else "%.2f".format(speed).trimEnd('0')) + "×"
