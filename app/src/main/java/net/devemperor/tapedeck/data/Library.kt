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
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Every download the app knows about, kept in one JSON file. A few hundred entries are a few hundred
 * kilobytes at most, so the whole list lives in memory and is written back shortly after each change.
 */
class Library(private val file: File, scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val saveRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        @OptIn(FlowPreview::class)
        scope.launch(Dispatchers.IO) { saveRequests.debounce(400).collect { saveNow() } }
    }

    operator fun get(id: String): Entry? = _entries.value.firstOrNull { it.id == id }

    fun add(entries: List<Entry>) {
        _entries.update { entries + it }
        saveRequests.tryEmit(Unit)
    }

    fun update(id: String, transform: (Entry) -> Entry) {
        _entries.update { list -> list.map { if (it.id == id) transform(it) else it } }
        saveRequests.tryEmit(Unit)
    }

    fun updateAll(transform: (Entry) -> Entry) {
        _entries.update { list -> list.map(transform) }
        saveRequests.tryEmit(Unit)
    }

    /** Removes the entries from the list; deleting their files is up to the caller. */
    fun remove(ids: Set<String>): List<Entry> {
        var removed = emptyList<Entry>()
        _entries.update { list ->
            removed = list.filter { it.id in ids }
            list.filterNot { it.id in ids }
        }
        saveRequests.tryEmit(Unit)
        return removed
    }

    @Synchronized
    fun saveNow() {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(LibraryFile(entries = _entries.value)))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "saving the library failed", e)
        }
    }

    private fun load(): List<Entry> {
        val candidates = listOf(file, File(file.parentFile, file.name + ".tmp"))
        for (candidate in candidates) {
            if (!candidate.exists()) continue
            try {
                return json.decodeFromString<LibraryFile>(candidate.readText()).entries
            } catch (e: Exception) {
                Log.e(TAG, "reading ${candidate.name} failed", e)
            }
        }
        return emptyList()
    }

    private companion object {
        const val TAG = "TapedeckLibrary"
    }
}
