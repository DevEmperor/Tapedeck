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
import java.util.UUID

/**
 * Every download the app knows about, kept in one JSON file. A few hundred entries are a few hundred
 * kilobytes at most, so the whole list lives in memory and is written back shortly after each change.
 */
class Library(private val file: File, scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val loaded = load()
    private val _entries = MutableStateFlow(loaded.entries)
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()
    private val _mixtapes = MutableStateFlow(loaded.mixtapes)
    val mixtapes: StateFlow<List<Mixtape>> = _mixtapes.asStateFlow()

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

    /** Removes the entries from the list and from every mixtape; deleting their files is up to the caller. */
    fun remove(ids: Set<String>): List<Entry> {
        var removed = emptyList<Entry>()
        _entries.update { list ->
            removed = list.filter { it.id in ids }
            list.filterNot { it.id in ids }
        }
        _mixtapes.update { tapes -> tapes.map { tape -> tape.copy(entryIds = tape.entryIds.filterNot { it in ids }) } }
        saveRequests.tryEmit(Unit)
        return removed
    }

    fun mixtape(id: String): Mixtape? = _mixtapes.value.firstOrNull { it.id == id }

    /** Downloads on any mixtape: the clean-up leaves them alone, like kept ones. */
    fun onMixtapes(): Set<String> = _mixtapes.value.flatMapTo(HashSet()) { it.entryIds }

    fun createMixtape(name: String, entryIds: List<String> = emptyList(), subscription: Subscription? = null): Mixtape {
        val tape = Mixtape(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Mixtape" },
            entryIds = entryIds.distinct(),
            // The next colour along, so neighbouring cassettes on the shelf look different.
            color = (_mixtapes.value.maxOfOrNull { it.color + 1 } ?: 0) % MIXTAPE_COLORS,
            createdAt = System.currentTimeMillis(),
            subscription = subscription,
        )
        _mixtapes.update { it + tape }
        saveRequests.tryEmit(Unit)
        return tape
    }

    fun updateMixtape(id: String, transform: (Mixtape) -> Mixtape) {
        _mixtapes.update { tapes -> tapes.map { if (it.id == id) transform(it) else it } }
        saveRequests.tryEmit(Unit)
    }

    /** Changes the subscription of a subscribed mixtape (nothing for one put together by hand). */
    fun updateSubscription(id: String, transform: (Subscription) -> Subscription) =
        updateMixtape(id) { tape -> tape.copy(subscription = tape.subscription?.let(transform)) }

    fun deleteMixtape(id: String) {
        _mixtapes.update { tapes -> tapes.filterNot { it.id == id } }
        saveRequests.tryEmit(Unit)
    }

    /** Appends the entries that are not on the mixtape yet, in the given order. */
    fun addToMixtape(id: String, entryIds: List<String>) =
        updateMixtape(id) { tape -> tape.copy(entryIds = tape.entryIds + entryIds.filterNot { it in tape.entryIds }) }

    fun removeFromMixtape(id: String, entryId: String) =
        updateMixtape(id) { tape -> tape.copy(entryIds = tape.entryIds - entryId) }

    /** A new order after dragging; only taken if it holds exactly the mixtape's songs. */
    fun reorderMixtape(id: String, entryIds: List<String>) = updateMixtape(id) { tape ->
        if (entryIds.size == tape.entryIds.size && entryIds.toSet() == tape.entryIds.toSet()) tape.copy(entryIds = entryIds) else tape
    }

    @Synchronized
    fun saveNow() {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(LibraryFile(entries = _entries.value, mixtapes = _mixtapes.value)))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "saving the library failed", e)
        }
    }

    private fun load(): LibraryFile {
        val candidates = listOf(file, File(file.parentFile, file.name + ".tmp"))
        for (candidate in candidates) {
            if (!candidate.exists()) continue
            try {
                return json.decodeFromString<LibraryFile>(candidate.readText())
            } catch (e: Exception) {
                Log.e(TAG, "reading ${candidate.name} failed", e)
            }
        }
        return LibraryFile()
    }

    companion object {
        private const val TAG = "TapedeckLibrary"

        /** How many cassette colours there are (see MixtapeColors in the UI). */
        const val MIXTAPE_COLORS = 6
    }
}
