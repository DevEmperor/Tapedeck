package net.devemperor.tapedeck.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.EventLogger
import net.devemperor.tapedeck.BuildConfig
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.devemperor.tapedeck.MainActivity
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.TapedeckApp
import net.devemperor.tapedeck.data.Entry
import java.io.File
import kotlin.math.abs

/**
 * Plays downloads through one ExoPlayer that lives in a media session: lock screen, headset, car
 * stereo and Android Auto all control it, and it keeps playing with the screen off (local wake lock).
 * Every few seconds the position goes into the library, so a drive can resume where it stopped.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private val app get() = application as TapedeckApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        val settings = app.settings.current
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes(settings.pauseForAnnouncements), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .build()
        player.addListener(listener)
        // Debug builds log every change of the player with its reason (who paused, why it stalled).
        if (BuildConfig.DEBUG) player.addAnalyticsListener(EventLogger("TapedeckPlayer"))

        val back = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setDisplayName(getString(R.string.action_back_10))
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setSlots(CommandButton.SLOT_BACK)
            .build()
        val forward = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
            .setDisplayName(getString(R.string.action_forward_10))
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setSlots(CommandButton.SLOT_FORWARD)
            .build()

        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setMediaButtonPreferences(ImmutableList.of(back, forward))
            .build()

        scope.launch {
            app.settings.state.map { Triple(it.playbackSpeed, it.skipSilence, it.pauseForAnnouncements) }.distinctUntilChanged()
                .collect { (speed, skipSilence, speech) ->
                    player.playbackParameters = PlaybackParameters(speed)
                    player.skipSilenceEnabled = skipSilence
                    player.setAudioAttributes(audioAttributes(speech), true)
                }
        }
        scope.launch {
            app.settings.state.map { Triple(it.showSubtitles, it.subtitlePick, it.subtitleLangs) }.distinctUntilChanged()
                .collect { applySubtitles() }
        }
        scope.launch {
            app.library.entries.map { entries -> entries.associate { it.id to (it.mediaFile to it.thumbnailFile) } }
                .distinctUntilChanged()
                .collect { followMovedFiles(it) }
        }
        scope.launch {
            while (isActive) {
                delay(SAVE_INTERVAL_MS)
                if (player.isPlaying) saveCurrent()
            }
        }
    }

    /**
     * Subtitles of the video playing: shown or not as the CC button says, in the language picked there,
     * else in one of the languages chosen for downloads, else in the one the video has.
     */
    private fun applySubtitles() {
        val settings = app.settings.current
        val inVideo = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            .flatMap { group -> (0 until group.length).mapNotNull { group.getTrackFormat(it).language } }
        val wanted = (listOfNotNull(settings.subtitlePick) + settings.subtitleLangs + inVideo).distinct()
        val params = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !settings.showSubtitles)
            .setPreferredTextLanguages(*wanted.toTypedArray())
            .setSelectUndeterminedTextLanguage(true)
            .build()
        // Setting equal parameters again would only make the player choose its tracks anew.
        if (params != player.trackSelectionParameters) player.trackSelectionParameters = params
    }

    /**
     * Downloads moved to another place (Settings → Download location) get their new paths in the queue, so
     * the next ones still play. The one playing goes on where it was: a moved file stays readable through
     * what the player has open, and the new path takes over at once (a short stall at most).
     */
    private fun followMovedFiles(files: Map<String, Pair<String?, String?>>) {
        for (index in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            val (media, thumbnail) = files[MediaItems.entryId(item.mediaId)] ?: continue
            val uri = media?.let { Uri.fromFile(File(it)) } ?: continue
            if (item.localConfiguration?.uri == uri) continue
            val artwork = thumbnail?.let { Uri.fromFile(File(it)) } ?: item.mediaMetadata.artworkUri
            val moved = item.buildUpon()
                .setUri(uri)
                .setMediaMetadata(item.mediaMetadata.buildUpon().setArtworkUri(artwork).build())
                .build()
            if (index == player.currentMediaItemIndex) {
                val position = player.currentPosition
                player.replaceMediaItem(index, moved)
                player.seekTo(index, position)
            } else {
                player.replaceMediaItem(index, moved)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    // Swiping Tapedeck away in the recent apps closes it completely, playback included. The position is
    // saved, and the car's play button can still pick it up later (playback resumption).
    override fun onTaskRemoved(rootIntent: Intent?) {
        saveCurrent()
        app.library.saveNow()
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        saveCurrent()
        app.library.saveNow()
        NowPlaying.entryId = null
        scope.cancel()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    private fun audioAttributes(speech: Boolean) = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(if (speech) C.AUDIO_CONTENT_TYPE_SPEECH else C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            NowPlaying.entryId = mediaItem?.mediaId
            val entry = mediaItem?.mediaId?.let { app.library[it] } ?: return
            // The next item of a queue starts at 0; continue it where it was left instead.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && player.currentPosition < 1_000) {
                val resume = MediaItems.resumePosition(entry, app.settings.current.resumeRewindSec)
                if (resume > 0) player.seekTo(resume)
            }
            app.library.update(entry.id) { it.copy(lastPlayedAt = System.currentTimeMillis()) }
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            val oldId = oldPosition.mediaItem?.mediaId ?: return
            if (oldId == newPosition.mediaItem?.mediaId) return
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                markFinished(oldId)
            } else {
                save(oldId, oldPosition.positionMs, 0)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveCurrent()
        }

        // A new video brings its own subtitle languages.
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) = applySubtitles()

        override fun onPlaybackStateChanged(playbackState: Int) {
            val id = player.currentMediaItem?.mediaId ?: return
            when (playbackState) {
                Player.STATE_ENDED -> markFinished(id)
                Player.STATE_READY -> {
                    // SponsorBlock cuts and the info JSON's rounding make the stored length a guess.
                    val duration = player.duration
                    if (duration != C.TIME_UNSET && duration > 0) {
                        app.library.update(id) { if (abs(it.durationMs - duration) > 1_500) it.copy(durationMs = duration) else it }
                    }
                }
            }
        }
    }

    private fun saveCurrent() {
        val id = player.currentMediaItem?.mediaId ?: return
        if (player.playbackState == Player.STATE_ENDED) return
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0
        save(id, player.currentPosition, duration)
    }

    private fun save(id: String, positionMs: Long, durationMs: Long) {
        val now = System.currentTimeMillis()
        app.library.update(id) { entry ->
            val duration = if (durationMs > 0) durationMs else entry.durationMs
            // The last few seconds are usually an outro; count the entry as heard from there on.
            val threshold = (duration * 0.03).toLong().coerceIn(3_000, 20_000)
            if (duration > 0 && positionMs >= duration - threshold) {
                entry.copy(positionMs = 0, finished = true, finishedAt = entry.finishedAt ?: now, lastPlayedAt = now)
            } else {
                entry.copy(positionMs = positionMs, lastPlayedAt = now)
            }
        }
    }

    private fun markFinished(id: String) {
        val now = System.currentTimeMillis()
        app.library.update(id) { it.copy(positionMs = 0, finished = true, finishedAt = it.finishedAt ?: now, lastPlayedAt = now) }
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(MediaItems.root(this@PlaybackService), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children = MediaItems.children(this@PlaybackService, parentId, app.library.entries.value, app.library.mixtapes.value, app.settings.current)
                ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            val paged = if (pageSize > 0) children.drop(page * pageSize).take(pageSize) else children
            return Futures.immediateFuture(LibraryResult.ofItemList(paged, params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val entry = app.library[MediaItems.entryId(mediaId)]?.takeIf { it.isDone }
                ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            return Futures.immediateFuture(LibraryResult.ofItem(MediaItems.from(entry), null))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val mixtape = mediaItems.firstNotNullOfOrNull(MediaItems::mixtapeOf)
            return Futures.immediateFuture(resolve(mediaItems).map { MediaItems.from(it, mixtapeId = mixtape) }.toMutableList())
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // Played from a mixtape (in the app, or from its folder in the car): the queue keeps saying so.
            val mixtape = mediaItems.firstNotNullOfOrNull(MediaItems::mixtapeOf)?.let(app.library::mixtape)
            var entries = resolve(mediaItems)
            var index = startIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
            // Android Auto hands over a single item: queue what follows it — in the mixtape it was picked
            // from, else in the library — like the app does.
            if (entries.size == 1 && controller.packageName != packageName) {
                val picked = entries.first()
                if (mixtape != null) {
                    entries = MediaItems.mixtapeQueue(mixtape, app.library.entries.value)
                    index = entries.indexOfFirst { it.id == picked.id }.coerceAtLeast(0)
                } else {
                    entries = MediaItems.queueFrom(picked, app.library.entries.value, app.settings.current)
                    index = 0
                }
            }
            val start = entries.getOrNull(index)
            val position = if (startPositionMs == C.TIME_UNSET && start != null) {
                MediaItems.resumePosition(start, app.settings.current.resumeRewindSec)
            } else {
                startPositionMs
            }
            val items = entries.map { MediaItems.from(it, mixtapeId = mixtape?.id) }
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, index, position))
        }

        // The car's play button after the app was closed: carry on with the last download.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val entries = app.library.entries.value
            val last = entries.filter { it.isDone && it.lastPlayedAt != null }.maxByOrNull { it.lastPlayedAt ?: 0 }
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("nothing played yet"))
            val queue = MediaItems.queueFrom(last, entries, app.settings.current)
            val position = MediaItems.resumePosition(last, app.settings.current.resumeRewindSec)
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(queue.map { MediaItems.from(it) }, 0, position))
        }

        private fun resolve(items: List<MediaItem>): List<Entry> =
            items.mapNotNull { item -> app.library[MediaItems.entryId(item.mediaId)]?.takeIf { it.isDone } }
    }

    private companion object {
        const val SEEK_BACK_MS = 10_000L
        // Both ways the same since 0.4.1 (Jannis): one press is always ten seconds.
        const val SEEK_FORWARD_MS = 10_000L
        const val SAVE_INTERVAL_MS = 5_000L
    }
}
