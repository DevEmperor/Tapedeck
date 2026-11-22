package net.devemperor.tapedeck.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One video ever downloaded: what it was, where it came from, and whether it is still in the library. It
 * stays when the download is deleted, so it can be found and fetched again later.
 */
@Serializable
data class HistoryItem(
    /** The video's mark: its YouTube id, else its clean link (see [SubscriptionPlan.key]). */
    val key: String,
    val url: String,
    val title: String,
    val uploader: String? = null,
    /** The picture on the web; the file of the download goes with it. */
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0,
    val kind: MediaKind = MediaKind.AUDIO,
    val formatLabel: String? = null,
    val downloadedAt: Long,
    /** The download in the library, while it is there. */
    val entryId: String? = null,
    val deletedAt: Long? = null,
) {
    val deleted: Boolean get() = entryId == null
}

@Serializable
private data class HistoryFile(val version: Int = 1, val items: List<HistoryItem> = emptyList())

/**
 * The download history, newest first, in a file of its own (it outlives the downloads). A video downloaded
 * again is one item, moved to the top. The oldest go once there are more than [LIMIT].
 */
class History(private val file: File, scope: CoroutineScope, seed: () -> List<Entry>) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val _items = MutableStateFlow(load() ?: fromLibrary(seed()))
    val items: StateFlow<List<HistoryItem>> = _items.asStateFlow()

    private val saveRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        @OptIn(FlowPreview::class)
        scope.launch(Dispatchers.IO) { saveRequests.debounce(500).collect { saveNow() } }
        // A new history (first start of this version) begins with what is in the library already.
        if (!file.exists()) saveRequests.tryEmit(Unit)
    }

    /** A finished download: a new item on top, or the earlier one of the same video brought up to date. */
    fun record(entry: Entry) {
        val item = itemFor(entry, System.currentTimeMillis())
        _items.update { list -> (listOf(item) + list.filterNot { it.key == item.key }).take(LIMIT) }
        saveRequests.tryEmit(Unit)
    }

    /** The downloads [entryIds] were deleted: their items stay, marked as no longer in the library. */
    fun markDeleted(entryIds: Set<String>) {
        if (_items.value.none { it.entryId in entryIds }) return
        val now = System.currentTimeMillis()
        _items.update { list -> list.map { if (it.entryId != null && it.entryId in entryIds) it.copy(entryId = null, deletedAt = now) else it } }
        saveRequests.tryEmit(Unit)
    }

    fun remove(key: String) {
        _items.update { list -> list.filterNot { it.key == key } }
        saveRequests.tryEmit(Unit)
    }

    fun clear() {
        _items.value = emptyList()
        saveRequests.tryEmit(Unit)
    }

    @Synchronized
    fun saveNow() {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(HistoryFile(items = _items.value)))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "saving the history failed", e)
        }
    }

    private fun load(): List<HistoryItem>? {
        for (candidate in listOf(file, File(file.parentFile, file.name + ".tmp"))) {
            if (!candidate.exists()) continue
            try {
                return json.decodeFromString<HistoryFile>(candidate.readText()).items
            } catch (e: Exception) {
                Log.e(TAG, "reading ${candidate.name} failed", e)
            }
        }
        return null
    }

    private fun fromLibrary(entries: List<Entry>): List<HistoryItem> =
        entries.filter { it.isDone }
            .sortedByDescending { it.completedAt ?: it.createdAt }
            .map { itemFor(it, it.completedAt ?: it.createdAt) }
            .distinctBy { it.key }
            .take(LIMIT)

    private fun itemFor(entry: Entry, at: Long) = HistoryItem(
        key = SubscriptionPlan.key(entry.url),
        url = entry.url,
        title = entry.title,
        uploader = entry.uploader,
        thumbnailUrl = entry.thumbnailUrl ?: net.devemperor.tapedeck.engine.Links.thumbnailFor(entry.url),
        durationMs = entry.durationMs,
        kind = entry.options.kind,
        formatLabel = entry.formatLabel,
        downloadedAt = at,
        entryId = entry.id,
    )

    companion object {
        private const val TAG = "TapedeckHistory"
        const val LIMIT = 3000
    }
}
