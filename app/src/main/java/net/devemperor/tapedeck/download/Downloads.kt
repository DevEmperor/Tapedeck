package net.devemperor.tapedeck.download

import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.devemperor.tapedeck.TapedeckApp
import net.devemperor.tapedeck.data.DownloadOptions
import net.devemperor.tapedeck.data.DownloadState
import net.devemperor.tapedeck.data.Entry
import net.devemperor.tapedeck.data.Storage
import net.devemperor.tapedeck.data.StorageLocation
import android.util.Log
import java.io.File
import net.devemperor.tapedeck.engine.Engine
import net.devemperor.tapedeck.engine.Links
import net.devemperor.tapedeck.engine.LiveProgress
import java.util.UUID

data class NewDownload(
    val url: String,
    val options: DownloadOptions,
    val title: String? = null,
    val uploader: String? = null,
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0,
    /** Load over mobile data even with "Wi-Fi only" on. */
    val mobileData: Boolean = false,
    /** The subscribed mixtape it is fetched for by itself. */
    val subscription: String? = null,
)

/** What the UI calls to start, stop and remove downloads; [DownloadService] does the work. */
class Downloads(private val app: TapedeckApp, private val scope: CoroutineScope) {
    private val _live = MutableStateFlow<Map<String, LiveProgress>>(emptyMap())
    val live: StateFlow<Map<String, LiveProgress>> = _live.asStateFlow()

    /** Puts the downloads into the queue; [start] the download service right away (subscriptions start it themselves). */
    fun enqueue(requests: List<NewDownload>, start: Boolean = true): List<Entry> {
        val now = System.currentTimeMillis()
        // Each download works in its own folder there (the SD card, or the app folder), kept with it so a
        // change of the setting meanwhile never loses its half-done files.
        val root = Storage.workRoot(app, app.settings.current.storage)
        val entries = requests.mapIndexed { index, request ->
            val url = Links.clean(request.url)
            val id = UUID.randomUUID().toString()
            Entry(
                id = id,
                folder = File(root, id).path,
                url = url,
                title = request.title ?: url,
                uploader = request.uploader,
                durationMs = request.durationMs,
                options = request.options,
                thumbnailUrl = request.thumbnailUrl ?: Links.thumbnailFor(url),
                // Keeps a playlist in its own order in the queue.
                createdAt = now + index,
                mobileData = request.mobileData,
                subscription = request.subscription,
            )
        }
        app.library.add(entries.reversed())
        if (start) start()
        return entries
    }

    fun cancel(id: String) {
        Engine.cancel(id)
        app.library.update(id) { if (it.isActive) it.copy(state = DownloadState.CANCELED) else it }
        clear(id)
        deleteFilesLater(listOfNotNull(app.library[id]))
    }

    fun retry(id: String) {
        app.library.update(id) { it.copy(state = DownloadState.QUEUED, errorKind = null, errorDetail = null, autoRetries = 0) }
        start()
    }

    /**
     * Downloads waiting for Wi-Fi go ahead over mobile data (all waiting ones when [ids] is null), and so
     * do later attempts of them; "Wi-Fi only" keeps holding back everything else.
     */
    fun allowMobileData(ids: Set<String>? = null) {
        app.library.updateAll { entry ->
            if (entry.isActive && !entry.mobileData && (ids == null || entry.id in ids)) entry.copy(mobileData = true) else entry
        }
        start()
    }

    private val _moving = MutableStateFlow<Pair<Int, Int>?>(null)

    /** While finished downloads move to another place: how many are done and how many there are. */
    val moving: StateFlow<Pair<Int, Int>?> = _moving.asStateFlow()

    /** The finished downloads that are not where [location] puts them (the one playing aside). */
    fun elsewhere(location: StorageLocation): List<Entry> =
        // The one playing moves too: the player follows the new path (PlaybackService.followMovedFiles).
        app.library.entries.value.filter { it.isDone && !Storage.isIn(app, it, location) }

    /** Moves the finished downloads to [location], one after the other, keeping the library in step. */
    fun relocate(location: StorageLocation) {
        if (_moving.value != null) return
        val list = elsewhere(location)
        if (list.isEmpty()) return
        _moving.value = 0 to list.size
        scope.launch(Dispatchers.IO) {
            list.forEachIndexed { index, entry ->
                runCatching { Storage.relocate(app, entry, location) }
                    .onFailure { Log.w("TapedeckStorage", "moving ${entry.id} failed", it) }
                    .getOrNull()?.let { moved ->
                        app.library.update(entry.id) { it.copy(mediaFile = moved.mediaFile, thumbnailFile = moved.thumbnailFile, folder = moved.folder) }
                    }
                _moving.value = index + 1 to list.size
            }
            // All out of Download/Tapedeck again: no empty folder left behind there.
            Storage.downloadsRoot().takeIf { it.list()?.isEmpty() == true }?.delete()
            app.library.saveNow()
            _moving.value = null
        }
    }

    fun delete(ids: Set<String>) {
        ids.forEach(Engine::cancel)
        val removed = app.library.remove(ids)
        // Gone from the library, still in the history — to be found and fetched again.
        app.history.markDeleted(ids)
        ids.forEach(::clear)
        deleteFilesLater(removed)
    }

    /** Entries that were cut off when the app was last closed go back into the queue. */
    fun resumePending() {
        val entries = app.library.entries.value
        if (entries.any { it.state == DownloadState.RUNNING }) {
            app.library.updateAll { if (it.state == DownloadState.RUNNING) it.copy(state = DownloadState.QUEUED) else it }
        }
        if (entries.any { it.isActive }) start()
    }

    fun start() {
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
    }

    internal fun publish(id: String, progress: LiveProgress) = _live.update { it + (id to progress) }

    internal fun clear(id: String) = _live.update { it - id }

    // The yt-dlp process may still hold its files for a moment after it was killed.
    private fun deleteFilesLater(entries: List<Entry>) {
        if (entries.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            delay(1500)
            entries.forEach { entry ->
                val current = app.library[entry.id]
                // Gone from the list, or still cancelled (not retried in the meantime).
                if (current == null || current.state == DownloadState.CANCELED) Storage.deleteFiles(app, entry)
            }
        }
    }
}
