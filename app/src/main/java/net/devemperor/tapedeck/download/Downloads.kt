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
)

/** What the UI calls to start, stop and remove downloads; [DownloadService] does the work. */
class Downloads(private val app: TapedeckApp, private val scope: CoroutineScope) {
    private val _live = MutableStateFlow<Map<String, LiveProgress>>(emptyMap())
    val live: StateFlow<Map<String, LiveProgress>> = _live.asStateFlow()

    fun enqueue(requests: List<NewDownload>): List<Entry> {
        val now = System.currentTimeMillis()
        val entries = requests.mapIndexed { index, request ->
            val url = Links.clean(request.url)
            Entry(
                id = UUID.randomUUID().toString(),
                url = url,
                title = request.title ?: url,
                uploader = request.uploader,
                durationMs = request.durationMs,
                options = request.options,
                thumbnailUrl = request.thumbnailUrl ?: Links.thumbnailFor(url),
                // Keeps a playlist in its own order in the queue.
                createdAt = now + index,
            )
        }
        app.library.add(entries.reversed())
        start()
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

    fun delete(ids: Set<String>) {
        ids.forEach(Engine::cancel)
        val removed = app.library.remove(ids)
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
