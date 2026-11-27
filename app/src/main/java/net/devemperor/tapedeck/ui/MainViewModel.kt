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
import kotlinx.coroutines.withContext
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.TapedeckApp
import net.devemperor.tapedeck.data.AppSettings
import net.devemperor.tapedeck.data.DownloadOptions
import net.devemperor.tapedeck.data.DownloadState
import net.devemperor.tapedeck.data.Entry
import net.devemperor.tapedeck.data.HistoryItem
import net.devemperor.tapedeck.data.MediaKind
import net.devemperor.tapedeck.data.Mixtape
import net.devemperor.tapedeck.data.ShareAction
import net.devemperor.tapedeck.data.Storage
import net.devemperor.tapedeck.data.StorageLocation
import net.devemperor.tapedeck.data.Subscription
import net.devemperor.tapedeck.data.SubscriptionPlan
import net.devemperor.tapedeck.download.NewDownload
import net.devemperor.tapedeck.engine.AppInstallState
import net.devemperor.tapedeck.engine.Engine
import net.devemperor.tapedeck.engine.ErrorKind
import net.devemperor.tapedeck.engine.Errors
import net.devemperor.tapedeck.engine.Formats
import net.devemperor.tapedeck.engine.Links
import net.devemperor.tapedeck.engine.MediaDetails
import net.devemperor.tapedeck.engine.MediaFormat
import net.devemperor.tapedeck.engine.Playlist
import net.devemperor.tapedeck.engine.PreviewInfo
import net.devemperor.tapedeck.player.PlayerConnection
import net.devemperor.tapedeck.work.Subscriptions

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
    /** Everything yt-dlp told about the link (`-J`): the format list, and the preview of non-YouTube sites. */
    val details: MediaDetails? = null,
    val loadingDetails: Boolean = false,
    /** [net.devemperor.tapedeck.engine.ErrorKind] name when yt-dlp could not read the link. */
    val detailsError: String? = null,
    /** The sheet shows the list of formats instead of the options. */
    val showFormats: Boolean = false,
    /** Put the new download(s) on this mixtape … */
    val mixtapeId: String? = null,
    /** … or on a new one of this name (a playlist's own title, or one typed in). */
    val newMixtapeName: String? = null,
    /** Load over mobile data although "Wi-Fi only" is on (offered while on mobile data). */
    val mobileData: Boolean = false,
    /** The link is a YouTube channel: the sheet subscribes to its videos (this tab's link) instead. */
    val channelUrl: String? = null,
    /** The channel's newest videos, for its name and the first ones to load. */
    val channel: Playlist? = null,
    val loadingChannel: Boolean = false,
    val channelFailed: Boolean = false,
    /** A whole playlist is subscribed to: its new videos come by themselves later. */
    val subscribe: Boolean = false,
    /** How many downloads the subscription keeps (0: all). */
    val keep: Int = Subscription.DEFAULT_KEEP_CHANNEL,
    /** How many of a channel's newest videos load right away. */
    val loadNow: Int = 1,
) {
    val link: String? get() = Links.extractUrl(url)
    val isChannel: Boolean get() = channelUrl != null
    val canStart: Boolean
        get() = link != null && (!wholePlaylist || playlist?.items?.isNotEmpty() == true) &&
            (!isChannel || channel?.items?.isNotEmpty() == true)

    /** Formats are per video, so a whole playlist or a channel always goes by the presets. */
    val canPickFormat: Boolean get() = link != null && !wholePlaylist && !isChannel
}

/** A video link (YouTube, Instagram, …) found in the clipboard, offered at the top of the library. */
data class ClipOffer(val url: String, val preview: PreviewInfo? = null)

sealed interface UiEvent {
    class Message(val render: (Context) -> String) : UiEvent
    class Deleted(val ids: Set<String>) : UiEvent
    /** An activity to start, e.g. the share sheet once the file is ready. */
    class Launch(val intent: Intent) : UiEvent
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TapedeckApp

    val player = PlayerConnection(app)
    val entries: StateFlow<List<Entry>> = app.library.entries
    val live = app.downloads.live
    val network = app.network.state
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
        // A playlist or a channel always opens the sheet: there is something to choose (whole list, subscription).
        if (settings.value.shareAction == ShareAction.DOWNLOAD_NOW && !Links.isPlaylistOnly(url) && Links.channelVideosUrl(url) == null) {
            downloadNow(url)
        } else {
            openSheet(url)
        }
    }

    /** "Add link" without a link takes the one last seen in the clipboard, if it is not downloaded already. */
    fun openSheet(url: String? = null) {
        _clipOffer.value = null
        _sheet.value = AddSheetState(options = settings.value.defaults.asDefaults())
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
     * nothing of it is stored but its timestamp. A link to a known video site not in the library yet is offered.
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
        if (!Links.isKnownSite(url) || inLibrary(url)) return
        _clipOffer.value = ClipOffer(url)
        // Only YouTube answers quickly (oEmbed); other sites would need a whole yt-dlp run just for the card.
        if (Links.isYoutube(url)) viewModelScope.launch {
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
        detailsJob?.cancel()
        channelJob?.cancel()
        _sheet.value = null
    }

    fun onUrlChanged(text: String) {
        val url = Links.extractUrl(text)
        val playlistOnly = url != null && Links.isPlaylistOnly(url)
        val channelUrl = url?.let(Links::channelVideosUrl)
        _sheet.update {
            it?.copy(
                url = text, preview = null, loadingPreview = url != null, playlist = null, playlistFailed = false,
                playlistId = url?.let(Links::playlistId), wholePlaylist = playlistOnly,
                alreadyInLibrary = url != null && !playlistOnly && channelUrl == null && inLibrary(url),
                details = null, loadingDetails = false, detailsError = null, showFormats = false,
                options = it.options.asDefaults(),
                channelUrl = channelUrl, channel = null, loadingChannel = false, channelFailed = false,
                subscribe = false, keep = Subscription.DEFAULT_KEEP_CHANNEL, loadNow = 1,
                mixtapeId = null, newMixtapeName = null,
            )
        }
        previewJob?.cancel()
        playlistJob?.cancel()
        detailsJob?.cancel()
        if (url == null) return
        // A channel has no oEmbed and no single video to read: its newest videos are the preview.
        if (channelUrl != null) {
            loadChannel()
            return
        }
        previewJob = viewModelScope.launch {
            delay(250)
            val info = app.preview.fetch(Links.clean(url))
            _sheet.update { if (it?.url == text) it.copy(preview = info, loadingPreview = info == null && !Links.isYoutube(url)) else it }
            // Instagram, Vimeo & Co. have no quick oEmbed: yt-dlp reads title and picture (and the formats with them).
            if (info == null && !Links.isYoutube(url)) loadDetails()
        }
        if (playlistOnly) loadPlaylist()
    }

    fun setOptions(options: DownloadOptions) = _sheet.update { sheet ->
        // A picked stream belongs to Audio or Video; switching drops it.
        sheet?.copy(options = if (options.kind != sheet.options.kind) options.asDefaults() else options)
    }

    fun setWholePlaylist(whole: Boolean) {
        _sheet.update { sheet ->
            sheet?.copy(wholePlaylist = whole, options = if (whole) sheet.options.asDefaults() else sheet.options, subscribe = whole && sheet.subscribe)
                ?.let { if (whole) it.withPlaylistMixtape() else it.copy(newMixtapeName = null) }
        }
        if (whole && _sheet.value?.playlist == null) loadPlaylist()
    }

    /** A whole playlist becomes a mixtape of its own name, unless another one was chosen. */
    private fun AddSheetState.withPlaylistMixtape(): AddSheetState {
        val title = playlist?.title ?: return this
        return if (wholePlaylist && mixtapeId == null && newMixtapeName == null) copy(newMixtapeName = title) else this
    }

    /** "Keep it up to date" for a whole playlist: a subscription, which by default keeps all of it. */
    fun setSheetSubscribe(on: Boolean) = _sheet.update {
        it?.copy(subscribe = on, keep = if (on) Subscription.DEFAULT_KEEP_PLAYLIST else it.keep)
    }

    fun setSheetKeep(keep: Int) = _sheet.update { it?.copy(keep = keep, loadNow = if (keep > 0) it.loadNow.coerceAtMost(keep) else it.loadNow) }

    fun setSheetLoadNow(count: Int) = _sheet.update { it?.copy(loadNow = count) }

    /** Subtitles of this one video: null as in the settings, empty none. */
    fun setSheetSubtitles(languages: List<String>?) = _sheet.update { it?.copy(options = it.options.copy(subtitles = languages)) }

    private var channelJob: Job? = null

    private fun loadChannel() {
        val sheet = _sheet.value ?: return
        val channelUrl = sheet.channelUrl ?: return
        channelJob?.cancel()
        _sheet.update { it?.copy(loadingChannel = true, channelFailed = false, loadingPreview = true) }
        channelJob = viewModelScope.launch {
            val result = runCatching { Engine.fetchPlaylist(app, channelUrl, limit = SubscriptionPlan.CHANNEL_LOOK) }
            if (result.exceptionOrNull() is kotlinx.coroutines.CancellationException) return@launch
            result.exceptionOrNull()?.let { Log.w("TapedeckSubscribe", "reading channel $channelUrl failed", it) }
            val channel = result.getOrNull()?.takeIf { it.items.isNotEmpty() }
            _sheet.update {
                if (it?.url != sheet.url) it
                else it.copy(
                    channel = channel, loadingChannel = false, channelFailed = channel == null, loadingPreview = false,
                    preview = channel?.let { c ->
                        PreviewInfo(c.channel ?: c.title ?: channelUrl, c.items.firstOrNull()?.title, c.items.firstOrNull()?.thumbnailUrl)
                    },
                )
            }
        }
    }

    private var detailsJob: Job? = null

    /** "Custom…": the sheet turns into the format list, read from yt-dlp the first time. */
    fun openFormats() {
        val sheet = _sheet.value ?: return
        if (!sheet.canPickFormat) return
        _sheet.update { it?.copy(showFormats = true) }
        if (sheet.details == null && !sheet.loadingDetails) loadDetails()
    }

    fun closeFormats() = _sheet.update { it?.copy(showFormats = false) }

    fun retryDetails() = loadDetails()

    /** Picture (maybe with its own sound) and/or sound from the list; null for both goes back to the presets. */
    fun pickFormat(video: MediaFormat?, audio: MediaFormat?) {
        val selector = Formats.selector(video, audio)
        val label = Formats.label(video, audio) { app.getString(R.string.format_kbit, it) }
        _sheet.update { it?.copy(options = it.options.copy(customFormat = selector, customLabel = label), showFormats = false) }
    }

    fun clearCustomFormat() = _sheet.update { it?.copy(options = it.options.asDefaults()) }

    /** The sheet's "On mixtape" choice: none, an existing one, or a new one by name. */
    fun setSheetMixtape(mixtapeId: String?, newName: String?) = _sheet.update { it?.copy(mixtapeId = mixtapeId, newMixtapeName = newName) }

    fun setSheetMobileData(on: Boolean) = _sheet.update { it?.copy(mobileData = on) }

    // ---- Mixtapes ----

    val mixtapes: StateFlow<List<Mixtape>> = app.library.mixtapes

    fun createMixtape(name: String, entryIds: List<String> = emptyList()): Mixtape = app.library.createMixtape(name, entryIds)

    fun renameMixtape(id: String, name: String) {
        if (name.isBlank()) return
        app.library.updateMixtape(id) { it.copy(name = name.trim()) }
    }

    /** The mixtape goes; its downloads stay in the library. */
    fun deleteMixtape(id: String) = app.library.deleteMixtape(id)

    fun toggleOnMixtape(mixtapeId: String, entryId: String) {
        val tape = app.library.mixtape(mixtapeId) ?: return
        if (entryId in tape.entryIds) app.library.removeFromMixtape(mixtapeId, entryId) else app.library.addToMixtape(mixtapeId, listOf(entryId))
    }

    fun removeFromMixtape(mixtapeId: String, entryId: String) = app.library.removeFromMixtape(mixtapeId, entryId)

    /** A download dropped on a mixtape in the drag tray: onto it, with a word on what happened. */
    fun dropOnMixtape(mixtapeId: String, entryId: String) {
        val tape = app.library.mixtape(mixtapeId) ?: return
        if (entryId in tape.entryIds) {
            message { it.getString(R.string.already_on_mixtape, tape.name) }
        } else {
            app.library.addToMixtape(mixtapeId, listOf(entryId))
            message { it.getString(R.string.added_to_mixtape, tape.name) }
        }
    }

    fun reorderMixtape(mixtapeId: String, entryIds: List<String>) = app.library.reorderMixtape(mixtapeId, entryIds)

    // ---- Where downloads go ----

    val moving = app.downloads.moving

    fun downloadsElsewhere(location: StorageLocation): List<Entry> = app.downloads.elsewhere(location)

    fun relocate(location: StorageLocation) = app.downloads.relocate(location)

    // ---- History ----

    val history: StateFlow<List<HistoryItem>> = app.history.items

    /** A video from the history once more: the download sheet with its link, as audio or video like last time. */
    fun redownload(item: HistoryItem) {
        openSheet(item.url)
        _sheet.update { it?.copy(options = it.options.copy(kind = item.kind)) }
    }

    fun removeFromHistory(key: String) = app.history.remove(key)

    fun clearHistory() = app.history.clear()

    /** A short word at the bottom of the screen. */
    fun say(text: Int) = message { it.getString(text) }

    // ---- Subscriptions ----

    private val _checking = MutableStateFlow<Set<String>>(emptySet())

    /** Subscribed mixtapes being looked at right now ("Look now"). */
    val checking: StateFlow<Set<String>> = _checking.asStateFlow()

    fun checkSubscription(mixtapeId: String) {
        if (mixtapeId in _checking.value) return
        _checking.update { it + mixtapeId }
        viewModelScope.launch {
            val found = runCatching { Subscriptions.check(app, mixtapeId) }.getOrDefault(0)
            _checking.update { it - mixtapeId }
            message { if (found > 0) it.resources.getQuantityString(R.plurals.subscription_found, found, found) else it.getString(R.string.subscription_nothing) }
        }
    }

    /** A new limit makes room at once if there is more than it allows now. */
    fun setSubscriptionKeep(mixtapeId: String, keep: Int) {
        app.library.updateSubscription(mixtapeId) { it.copy(keep = keep) }
        Subscriptions.rotate(app, mixtapeId)
    }

    /** Audio or video from the next download of the subscription on. */
    fun setSubscriptionKind(mixtapeId: String, kind: MediaKind) =
        app.library.updateSubscription(mixtapeId) { it.copy(options = it.options.copy(kind = kind)) }

    /** The mixtape and its downloads stay; nothing new comes in any more. */
    fun unsubscribe(mixtapeId: String) = app.library.updateMixtape(mixtapeId) { it.copy(subscription = null) }

    fun backgroundAllowed(): Boolean = Subscriptions.unrestricted(app)

    /** Android's question whether Tapedeck may run unrestricted (so subscriptions download in the background). */
    @android.annotation.SuppressLint("BatteryLife")
    fun backgroundPermissionIntent(): Intent =
        Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${app.packageName}"))

    fun playMixtape(tape: Mixtape, start: Entry? = null, shuffle: Boolean = false, fromStart: Boolean = false) =
        player.playMixtape(tape, entries.value.filter { it.id !in _pendingDeletes.value }, settings.value, start, shuffle, fromStart)

    private fun loadDetails() {
        val sheet = _sheet.value ?: return
        val link = sheet.link ?: return
        detailsJob?.cancel()
        _sheet.update { it?.copy(loadingDetails = true, detailsError = null) }
        detailsJob = viewModelScope.launch {
            val result = runCatching { Engine.inspect(app, Links.clean(link), app.settings) }
            if (result.exceptionOrNull() is kotlinx.coroutines.CancellationException) return@launch
            result.exceptionOrNull()?.let { Log.w("TapedeckFormats", "reading formats failed", it) }
            val details = result.getOrNull()
            val error = result.exceptionOrNull()?.let { e ->
                (if (e is com.yausername.youtubedl_android.YoutubeDLException) Errors.classify(e.message) else null)?.kind?.name ?: ErrorKind.UNKNOWN.name
            }
            _sheet.update {
                if (it?.url != sheet.url) it
                else it.copy(
                    details = details,
                    loadingDetails = false,
                    detailsError = error,
                    // Sites without oEmbed get their card from here.
                    preview = it.preview ?: details?.takeIf { d -> d.title != null }?.let { d -> PreviewInfo(d.title!!, d.uploader, d.thumbnailUrl) },
                    loadingPreview = false,
                )
            }
        }
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
                    .withPlaylistMixtape()
            }
        }
    }

    fun confirmSheet() {
        val sheet = _sheet.value ?: return
        val link = sheet.link ?: return
        // The sheet's choice (Audio or Video included) is where the next download starts; a picked stream is not.
        app.settings.update { it.copy(defaults = sheet.options.asDefaults()) }
        val playlist = sheet.playlist
        // Subscribing: a channel, or a whole playlist kept up to date — a mixtape that fills itself.
        val channel = sheet.channel
        val subscribed = when {
            sheet.channelUrl != null && channel != null -> Subscriptions.subscribe(
                app, sheet.channelUrl, playlist = false, title = channel.channel ?: channel.title ?: sheet.channelUrl,
                options = sheet.options, keep = sheet.keep, items = channel.items, now = sheet.loadNow,
            )
            sheet.wholePlaylist && sheet.subscribe && playlist != null && sheet.playlistId != null -> Subscriptions.subscribe(
                app, Links.playlistUrl(sheet.playlistId), playlist = true,
                title = sheet.newMixtapeName ?: playlist.title ?: link,
                options = sheet.options, keep = sheet.keep, items = playlist.items,
                now = sheet.keep.takeIf { it > 0 } ?: Int.MAX_VALUE,
            )
            else -> null
        }
        if (subscribed != null) {
            closeSheet()
            message { it.getString(R.string.subscribed_message, subscribed.name) }
            return
        }
        // The switch only shows on mobile data; switched on and then back on Wi-Fi, it no longer matters.
        val mobileData = sheet.mobileData && settings.value.wifiOnly
        val requests = if (sheet.wholePlaylist && playlist != null) {
            playlist.items.map {
                NewDownload(it.url, sheet.options.asDefaults(), it.title, it.uploader, it.thumbnailUrl, it.durationMs, mobileData)
            }
        } else {
            listOf(
                NewDownload(
                    link, sheet.options, sheet.preview?.title, sheet.preview?.author, sheet.preview?.thumbnailUrl,
                    sheet.details?.durationMs ?: 0, mobileData,
                ),
            )
        }
        val added = app.downloads.enqueue(requests).map { it.id }
        // Straight onto a mixtape: the songs of a playlist in their order, while they are still downloading.
        when {
            sheet.newMixtapeName != null -> app.library.createMixtape(sheet.newMixtapeName, added)
            sheet.mixtapeId != null -> app.library.addToMixtape(sheet.mixtapeId, added)
        }
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

    /** Waiting downloads go ahead over mobile data: one, or all of them (null). */
    fun useMobileData(id: String? = null) = app.downloads.allowMobileData(id?.let(::setOf))

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
        val onMixtapes = if (settings.value.protectMixtapes) app.library.onMixtapes() else emptySet()
        val ids = entries.value.filter { it.isDone && it.finished && !it.keep && it.id !in onMixtapes }.map { it.id }.toSet()
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

    private val sharing = mutableSetOf<String>()

    /**
     * Opens the share sheet for a download's file once it is ready — a file from the SD card or the Download
     * folder is copied into the app folder first (Storage.prepareShare) — or says why it cannot be shared.
     */
    fun share(entry: Entry) {
        if (!sharing.add(entry.id)) return
        viewModelScope.launch {
            val intent = withContext(Dispatchers.IO) {
                Storage.prepareShare(app, entry, onCopy = { say(R.string.share_preparing) })
                    ?.let { Storage.shareIntent(app, entry, it) }
            }
            sharing.remove(entry.id)
            if (intent == null) say(R.string.share_failed) else _events.tryEmit(UiEvent.Launch(intent))
        }
    }

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

    /** The version Android just installed through Tapedeck's own update, for the success banner. */
    val justUpdated = app.appUpdates.justUpdated

    fun refreshJustUpdated() = app.appUpdates.refreshJustUpdated(app)

    fun dismissJustUpdated() = app.appUpdates.dismissJustUpdated()
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
