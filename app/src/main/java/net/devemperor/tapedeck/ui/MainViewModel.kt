package net.devemperor.tapedeck.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.TapedeckApp
import net.devemperor.tapedeck.data.AppSettings
import net.devemperor.tapedeck.data.DownloadOptions
import net.devemperor.tapedeck.data.DownloadState
import net.devemperor.tapedeck.data.Entry
import net.devemperor.tapedeck.data.ShareAction
import net.devemperor.tapedeck.data.Storage
import net.devemperor.tapedeck.download.NewDownload
import net.devemperor.tapedeck.engine.AppInstallState
import net.devemperor.tapedeck.engine.Engine
import net.devemperor.tapedeck.engine.Links
import net.devemperor.tapedeck.engine.Playlist
import net.devemperor.tapedeck.engine.PreviewInfo
import net.devemperor.tapedeck.player.PlayerConnection

data class AddSheetState(
    val url: String = "",
    val options: DownloadOptions = DownloadOptions(),
    val preview: PreviewInfo? = null,
    val loadingPreview: Boolean = false,
    val playlistId: String? = null,
    val wholePlaylist: Boolean = false,
    val playlist: Playlist? = null,
    val loadingPlaylist: Boolean = false,
    val playlistFailed: Boolean = false,
    val alreadyInLibrary: Boolean = false,
) {
    val link: String? get() = Links.extractUrl(url)
    val canStart: Boolean
        get() = link != null && (!wholePlaylist || playlist?.items?.isNotEmpty() == true)
}

/** A YouTube link found in the clipboard, offered at the top of the library. */
data class ClipOffer(val url: String, val preview: PreviewInfo? = null)

sealed interface UiEvent {
    class Message(val render: (Context) -> String) : UiEvent
    class Deleted(val ids: Set<String>) : UiEvent
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TapedeckApp

    val player = PlayerConnection(app)
    val entries: StateFlow<List<Entry>> = app.library.entries
    val live = app.downloads.live
    val settings: StateFlow<AppSettings> = app.settings.state
    val engineStatus = Engine.status
    val engineVersion = Engine.version
    val engineMeta = app.settings.engine
    val usedBytes: StateFlow<Long> = entries.map { list -> list.sumOf { it.sizeBytes } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _sheet = MutableStateFlow<AddSheetState?>(null)
    val sheet: StateFlow<AddSheetState?> = _sheet.asStateFlow()

    private val _pendingDeletes = MutableStateFlow<Set<String>>(emptySet())
    val pendingDeletes: StateFlow<Set<String>> = _pendingDeletes.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var previewJob: Job? = null
    private var playlistJob: Job? = null

    /** Text handed over by "Share"; usually "Title https://youtu.be/…". */
    fun onShared(text: String) {
        val url = Links.extractUrl(text)
        if (url == null) {
            message { it.getString(R.string.share_no_link) }
            return
        }
        if (settings.value.shareAction == ShareAction.DOWNLOAD_NOW && !Links.isPlaylistOnly(url)) {
            downloadNow(url)
        } else {
            openSheet(url)
        }
    }

    /** "Add link" without a link takes the one last seen in the clipboard, if it is not downloaded already. */
    fun openSheet(url: String? = null) {
        _clipOffer.value = null
        _sheet.value = AddSheetState(options = settings.value.defaults)
        val prefill = url ?: clipUrl?.takeIf { !inLibrary(it) }
        if (prefill != null) onUrlChanged(prefill)
    }

    private val _clipOffer = MutableStateFlow<ClipOffer?>(null)
    val clipOffer: StateFlow<ClipOffer?> = _clipOffer.asStateFlow()

    /** The latest link (of any site) seen in the clipboard. */
    private var clipUrl: String? = null

    /**
     * Called when the window gains focus — Android only lets the focused app read the clipboard. A clip is
     * read once, when it is newer than the last one looked at (each read shows Android's "pasted" toast);
     * nothing of it is stored but its timestamp. A YouTube link not in the library yet is offered.
     */
    fun checkClipboard(context: Context) {
        if (!settings.value.watchClipboard || _sheet.value != null) return
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val description = clipboard.primaryClipDescription ?: return
        val stamp = description.timestamp
        if (stamp != 0L && stamp == app.settings.engine.value.lastClipTimestamp) return
        app.settings.updateEngine { it.copy(lastClipTimestamp = stamp) }
        if (!description.hasMimeType("text/*")) return
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() ?: return
        considerClipText(text)
    }

    /** What happens with the clipboard's text; also the entry point of the debug-only test hook. */
    fun considerClipText(text: String) {
        val url = Links.extractUrl(text) ?: return
        clipUrl = url
        if (!Links.isYoutube(url) || inLibrary(url)) return
        _clipOffer.value = ClipOffer(url)
        viewModelScope.launch {
            val preview = app.preview.fetch(Links.clean(url))
            _clipOffer.update { if (it?.url == url) it.copy(preview = preview) else it }
        }
    }

    fun acceptClipOffer() {
        val offer = _clipOffer.value ?: return
        openSheet(offer.url)
    }

    fun dismissClipOffer() {
        _clipOffer.value = null
    }

    /** Whether the video behind [url] is already in the library (same YouTube id, or the same cleaned link). */
    private fun inLibrary(url: String): Boolean {
        val id = Links.videoId(url)
        val clean = Links.clean(url)
        return entries.value.any { entry ->
            entry.id !in _pendingDeletes.value &&
                ((id != null && Links.videoId(entry.url) == id) || entry.url == clean)
        }
    }

    fun closeSheet() {
        previewJob?.cancel()
        playlistJob?.cancel()
        _sheet.value = null
    }

    fun onUrlChanged(text: String) {
        val url = Links.extractUrl(text)
        val playlistOnly = url != null && Links.isPlaylistOnly(url)
        _sheet.update {
            it?.copy(
                url = text, preview = null, loadingPreview = url != null, playlist = null, playlistFailed = false,
                playlistId = url?.let(Links::playlistId), wholePlaylist = playlistOnly,
                alreadyInLibrary = url != null && !playlistOnly && inLibrary(url),
            )
        }
        previewJob?.cancel()
        playlistJob?.cancel()
        if (url == null) return
        previewJob = viewModelScope.launch {
            delay(250)
            val info = app.preview.fetch(Links.clean(url))
            _sheet.update { if (it?.url == text) it.copy(preview = info, loadingPreview = false) else it }
        }
        if (playlistOnly) loadPlaylist()
    }

    fun setOptions(options: DownloadOptions) = _sheet.update { it?.copy(options = options) }

    fun setWholePlaylist(whole: Boolean) {
        _sheet.update { it?.copy(wholePlaylist = whole) }
        if (whole && _sheet.value?.playlist == null) loadPlaylist()
    }

    private fun loadPlaylist() {
        val sheet = _sheet.value ?: return
        val link = sheet.link ?: return
        val listId = Links.playlistId(link) ?: return
        playlistJob?.cancel()
        _sheet.update { it?.copy(loadingPlaylist = true, playlistFailed = false) }
        playlistJob = viewModelScope.launch {
            val result = runCatching { Engine.fetchPlaylist(app, "https://www.youtube.com/playlist?list=$listId") }
            result.exceptionOrNull()?.let { Log.w("TapedeckPlaylist", "reading playlist $listId failed", it) }
            _sheet.update {
                if (it?.url != sheet.url) it
                else it.copy(playlist = result.getOrNull(), loadingPlaylist = false, playlistFailed = result.isFailure || result.getOrNull()?.items.isNullOrEmpty())
            }
        }
    }

    fun confirmSheet() {
        val sheet = _sheet.value ?: return
        val link = sheet.link ?: return
        app.settings.update { it.copy(defaults = sheet.options) }
        val playlist = sheet.playlist
        val requests = if (sheet.wholePlaylist && playlist != null) {
            playlist.items.map { NewDownload(it.url, sheet.options, it.title, it.uploader, it.thumbnailUrl, it.durationMs) }
        } else {
            listOf(NewDownload(link, sheet.options, sheet.preview?.title, sheet.preview?.author, sheet.preview?.thumbnailUrl))
        }
        app.downloads.enqueue(requests)
        closeSheet()
        val count = requests.size
        message { it.resources.getQuantityString(R.plurals.started, count, count) }
    }

    private fun downloadNow(url: String) {
        val entry = app.downloads.enqueue(listOf(NewDownload(url, settings.value.defaults))).first()
        message { it.resources.getQuantityString(R.plurals.started, 1, 1) }
        // The title shows up as soon as oEmbed answers; yt-dlp fills in the rest later.
        viewModelScope.launch {
            val info = app.preview.fetch(entry.url) ?: return@launch
            app.library.update(entry.id) { if (it.title == it.url) it.copy(title = info.title, uploader = it.uploader ?: info.author) else it }
        }
    }

    fun cancel(id: String) = app.downloads.delete(setOf(id))

    fun retry(id: String) = app.downloads.retry(id)

    /** Hides the entries at once; the files go only when the snackbar's "Undo" was not tapped. */
    fun delete(ids: Set<String>) {
        if (ids.isEmpty()) return
        _pendingDeletes.update { it + ids }
        _events.tryEmit(UiEvent.Deleted(ids))
    }

    fun undoDelete(ids: Set<String>) = _pendingDeletes.update { it - ids }

    fun commitDelete(ids: Set<String>) {
        val still = ids.intersect(_pendingDeletes.value)
        if (still.isEmpty()) return
        player.forget(still)
        app.downloads.delete(still)
        _pendingDeletes.update { it - still }
    }

    fun deleteFinished() {
        val ids = entries.value.filter { it.isDone && it.finished && !it.keep }.map { it.id }.toSet()
        if (ids.isEmpty()) message { it.getString(R.string.nothing_finished) } else delete(ids)
    }

    fun toggleFinished(entry: Entry) = app.library.update(entry.id) {
        if (it.finished) it.copy(finished = false, finishedAt = null) else it.copy(finished = true, finishedAt = System.currentTimeMillis(), positionMs = 0)
    }

    fun toggleKeep(entry: Entry) = app.library.update(entry.id) { it.copy(keep = !it.keep) }

    fun play(entry: Entry) {
        if (entry.state != DownloadState.DONE) return
        player.play(entry, entries.value.filter { it.id !in _pendingDeletes.value }, settings.value)
    }

    fun playFromStart(entry: Entry) = player.playFromStart(entry, entries.value.filter { it.id !in _pendingDeletes.value }, settings.value)

    fun shareIntent(entry: Entry): Intent? = Storage.shareIntent(app, entry)

    fun export(entry: Entry) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = Storage.exportToDownloads(app, entry)
            message { it.getString(if (ok) R.string.exported else R.string.export_failed) }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = app.settings.update(transform)

    fun updateYtDlp() {
        if (_updating.value) return
        viewModelScope.launch {
            _updating.value = true
            val result = Engine.update(app, app.settings)
            _updating.value = false
            message {
                when (result) {
                    is Engine.UpdateResult.Updated -> it.getString(R.string.ytdlp_updated, result.version ?: "")
                    Engine.UpdateResult.UpToDate -> it.getString(R.string.ytdlp_up_to_date)
                    is Engine.UpdateResult.Failed -> it.getString(R.string.ytdlp_update_failed)
                }
            }
        }
    }

    fun resumePendingDownloads() = app.downloads.resumePending()

    /** The only place a regular yt-dlp update starts. In the app scope, so leaving the app mid-update does not cut it off. */
    fun onAppOpened() {
        app.scope.launch { Engine.updateIfDue(app, app.settings) }
        app.scope.launch { app.appUpdates.checkIfDue() }
        // Back from the "Install unknown apps" switch: carry on with the update by itself.
        if (appInstall.value == AppInstallState.NeedsPermission && app.appUpdates.canInstall(app)) installAppUpdate()
    }

    /** A newer Tapedeck on GitHub, until "Later" puts it away. */
    val appUpdate = app.appUpdates.available
    val appInstall: StateFlow<AppInstallState> = app.appUpdates.install
    private var installJob: Job? = null

    fun dismissAppUpdate() {
        installJob?.cancel()
        app.appUpdates.dismiss()
    }

    /** Downloads the new APK and hands it to Android's installer; Tapedeck is ended once it is in place. */
    fun installAppUpdate() {
        val release = appUpdate.value ?: return
        if (installJob?.isActive == true) return
        installJob = app.scope.launch { app.appUpdates.downloadAndInstall(app, release) }
    }

    fun cancelAppUpdate() {
        installJob?.cancel()
        app.appUpdates.reset(app)
    }

    fun installPermissionIntent(): Intent = app.appUpdates.permissionIntent(app)

    fun takePendingInstallConfirmation(): Intent? = app.appUpdates.takePendingConfirmation()

    private val _checkingApp = MutableStateFlow(false)
    val checkingApp: StateFlow<Boolean> = _checkingApp.asStateFlow()

    /** "Check now" in the settings: asks GitHub right away and says what it found. */
    fun checkAppUpdateNow() {
        if (_checkingApp.value) return
        viewModelScope.launch {
            _checkingApp.value = true
            // Asked for explicitly, so a version put away with "Later" counts again.
            app.settings.updateEngine { it.copy(dismissedAppVersion = null) }
            val found = app.appUpdates.check()
            _checkingApp.value = false
            if (found == null) message { it.getString(R.string.app_up_to_date) }
        }
    }

    private fun message(render: (Context) -> String) {
        _events.tryEmit(UiEvent.Message(render))
    }

    override fun onCleared() {
        commitDelete(_pendingDeletes.value)
        player.disconnect()
    }
}
