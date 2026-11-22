package net.devemperor.tapedeck.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ShareAction { ASK, DOWNLOAD_NOW }

@Serializable
enum class UpdateChannel { STABLE, NIGHTLY }

/** Also the order in which "keep playing" continues, so a lecture series plays 1, 2, 3 with OLDEST. */
@Serializable
enum class LibrarySort { NEWEST, OLDEST, TITLE, LAST_PLAYED }

@Serializable
data class AppSettings(
    val defaults: DownloadOptions = DownloadOptions(),
    val shareAction: ShareAction = ShareAction.ASK,
    // On by default: a long video over mobile data can eat a monthly plan in one go.
    val wifiOnly: Boolean = true,
    /** 1–5, chosen with the wheel. */
    val parallelDownloads: Int = 2,
    val sponsorBlock: Boolean = false,
    val autoDeleteEnabled: Boolean = false,
    /** 1–90, chosen with the spinner; kept while the clean-up is switched off. */
    val autoDeleteDays: Int = 7,
    val autoDeleteOnlyFinished: Boolean = true,
    val autoPlayNext: Boolean = true,
    val resumeRewindSec: Int = 5,
    val playbackSpeed: Float = 1f,
    val skipSilence: Boolean = false,
    /**
     * Treat downloads as speech: a navigation announcement then pauses playback instead of merely
     * lowering it, so no sentence of a podcast or lecture gets lost under "turn left".
     */
    val pauseForAnnouncements: Boolean = true,
    val librarySort: LibrarySort = LibrarySort.NEWEST,
    // Nightly is what yt-dlp itself recommends for regular users: YouTube fixes land there days
    // before a stable release.
    val updateChannel: UpdateChannel = UpdateChannel.NIGHTLY,
    /** Checked whenever the app is opened, never in the background. */
    val autoUpdate: Boolean = true,
    /** Tapedeck is not on Play, so it looks on GitHub for a newer release itself (when opened). */
    val checkAppUpdates: Boolean = true,
    /** QuickJS-NG instead of the bundled QuickJS for YouTube's JavaScript challenge (faster). */
    val fastJsEngine: Boolean = true,
    /** Offer a link found in the clipboard when the app is opened. */
    val watchClipboard: Boolean = true,
    /** The player shows audio as a cassette in a tape deck instead of the cover (a tap switches). */
    val cassetteView: Boolean = false,
    /** Entries on a mixtape are never removed by the clean-up (nor by "Delete everything heard"). */
    val protectMixtapes: Boolean = true,
    /** How often subscribed channels and playlists are looked at for new videos, in hours. */
    val subscriptionHours: Int = 3,
) {
    val autoDeleteAfterDays: Int get() = autoDeleteDays.coerceIn(AUTO_DELETE_MIN_DAYS, AUTO_DELETE_MAX_DAYS)

    companion object {
        const val AUTO_DELETE_MIN_DAYS = 1
        const val AUTO_DELETE_MAX_DAYS = 90
        const val MAX_PARALLEL_DOWNLOADS = 5
        val REWIND_OPTIONS_SEC = listOf(0, 3, 5, 10, 30)
        val SUBSCRIPTION_HOURS = listOf(1, 2, 3, 4, 6, 8, 12, 24)
    }
}

/** Bookkeeping of the yt-dlp updater, kept apart from what the user sets. */
@Serializable
data class EngineMeta(
    val lastCheckAt: Long = 0,
    val lastUpdateAt: Long = 0,
    val lastError: String? = null,
    /** When GitHub was last asked for a newer Tapedeck. */
    val lastAppCheckAt: Long = 0,
    /** A Tapedeck version whose hint the user put away with "Later"; the next one shows again. */
    val dismissedAppVersion: String? = null,
    /** The app version whose QuickJS-NG self-test result is stored in [quickJsNgWorks]. */
    val quickJsNgCheckedFor: Int = 0,
    val quickJsNgWorks: Boolean = false,
    /** When the clip last looked at was copied; only a newer one is read (Android shows a toast per read). */
    val lastClipTimestamp: Long = 0,
)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("tapedeck", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    private val _state = MutableStateFlow(read(KEY_SETTINGS) ?: AppSettings())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    private val _engine = MutableStateFlow(read(KEY_ENGINE) ?: EngineMeta())
    val engine: StateFlow<EngineMeta> = _engine.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = _state.updateAndGet(transform)
        prefs.edit { putString(KEY_SETTINGS, json.encodeToString(next)) }
    }

    fun updateEngine(transform: (EngineMeta) -> EngineMeta) {
        val next = _engine.updateAndGet(transform)
        prefs.edit { putString(KEY_ENGINE, json.encodeToString(next)) }
    }

    private inline fun <reified T> read(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private companion object {
        const val KEY_SETTINGS = "settings"
        const val KEY_ENGINE = "engine"
    }
}
