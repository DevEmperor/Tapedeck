package net.devemperor.tapedeck.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.devemperor.tapedeck.data.AppSettings
import net.devemperor.tapedeck.data.Entry

data class PlayerUiState(
    val entryId: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val speed: Float = 1f,
    /** Width ÷ height of the picture once the player knows it; below 1 for an upright video (Instagram, Shorts). */
    val videoAspect: Float? = null,
)

/** The app's connection to [PlaybackService]; lives while the activity is started. */
class PlayerConnection(private val context: Context) {
    private var future: ListenableFuture<MediaController>? = null
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val pending = MediaController.Builder(context, token).buildAsync()
        future = pending
        pending.addListener({
            val controller = runCatching { pending.get() }.getOrNull() ?: return@addListener
            if (future !== pending) {
                controller.release()
                return@addListener
            }
            controller.addListener(listener)
            _controller.value = controller
            refresh()
        }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        _controller.value?.removeListener(listener)
        future?.let(MediaController::releaseFuture)
        future = null
        _controller.value = null
        _state.value = PlayerUiState()
    }

    fun currentPosition(): Long = _controller.value?.currentPosition ?: 0

    fun play(entry: Entry, library: List<Entry>, settings: AppSettings) {
        val controller = _controller.value ?: return
        if (controller.currentMediaItem?.mediaId == entry.id) {
            if (!controller.isPlaying) controller.play()
            return
        }
        val queue = MediaItems.queueFrom(entry, library, settings)
        val start = MediaItems.resumePosition(entry, settings.resumeRewindSec)
        controller.setMediaItems(queue.map { MediaItems.from(it) }, 0, start)
        controller.prepare()
        controller.play()
    }

    fun playFromStart(entry: Entry, library: List<Entry>, settings: AppSettings) {
        val controller = _controller.value ?: return
        val queue = MediaItems.queueFrom(entry, library, settings)
        controller.setMediaItems(queue.map { MediaItems.from(it) }, 0, 0)
        controller.prepare()
        controller.play()
    }

    fun togglePlay() {
        val controller = _controller.value ?: return
        if (controller.isPlaying) controller.pause() else {
            if (controller.playbackState == Player.STATE_ENDED) controller.seekToDefaultPosition()
            if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) = _controller.value?.seekTo(positionMs)
    fun seekBack() = _controller.value?.seekBack()
    fun seekForward() = _controller.value?.seekForward()
    fun next() = _controller.value?.seekToNextMediaItem()

    /** Back to the start of the item, or to the previous one when already near the start. */
    fun previous() {
        val controller = _controller.value ?: return
        if (controller.currentPosition > 5_000 || !controller.hasPreviousMediaItem()) controller.seekTo(0) else controller.seekToPreviousMediaItem()
    }

    fun stop() {
        val controller = _controller.value ?: return
        controller.pause()
        controller.clearMediaItems()
    }

    /** Takes an entry out of the queue before it is deleted. */
    fun forget(ids: Set<String>) {
        val controller = _controller.value ?: return
        for (index in controller.mediaItemCount - 1 downTo 0) {
            if (controller.getMediaItemAt(index).mediaId in ids) controller.removeMediaItem(index)
        }
    }

    private fun refresh() {
        val controller = _controller.value ?: return
        _state.value = PlayerUiState(
            entryId = controller.currentMediaItem?.mediaId,
            isPlaying = controller.isPlaying,
            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
            durationMs = controller.duration.takeIf { it != C.TIME_UNSET } ?: 0,
            hasNext = controller.hasNextMediaItem(),
            hasPrevious = controller.hasPreviousMediaItem(),
            speed = controller.playbackParameters.speed,
            videoAspect = controller.videoSize.takeIf { it.width > 0 && it.height > 0 }
                ?.let { it.width * it.pixelWidthHeightRatio / it.height },
        )
    }
}
