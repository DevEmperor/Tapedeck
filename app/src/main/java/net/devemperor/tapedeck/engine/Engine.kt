package net.devemperor.tapedeck.engine

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.devemperor.tapedeck.BuildConfig
import net.devemperor.tapedeck.data.Entry
import net.devemperor.tapedeck.data.Settings
import net.devemperor.tapedeck.data.Storage
import net.devemperor.tapedeck.data.UpdateChannel
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * yt-dlp with its own Python, FFmpeg and QuickJS (youtubedl-android). YouTube changes its player every
 * few weeks, so the yt-dlp script is replaced from GitHub whenever the app is opened and a check is due;
 * Python and FFmpeg stay as shipped in the APK.
 */
object Engine {
    sealed interface Status {
        data object Starting : Status
        data object Ready : Status
        data object Updating : Status
        data class Failed(val message: String) : Status
    }

    sealed interface UpdateResult {
        data class Updated(val version: String?) : UpdateResult
        data object UpToDate : UpdateResult
        data class Failed(val message: String) : UpdateResult
    }

    /** The yt-dlp that ships inside youtubedl-android 0.18.1. */
    const val BUNDLED_VERSION = "2025.11.12"

    private const val TAG = "TapedeckEngine"
    private val CHECK_INTERVAL = 1.hours
    private val RETRY_CHECK_INTERVAL = 30.minutes

    private val _status = MutableStateFlow<Status>(Status.Starting)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version.asStateFlow()

    private val initMutex = Mutex()
    private val updateMutex = Mutex()

    @Volatile
    private var initialized = false

    // Downloads and an update must never overlap: the updater deletes the yt-dlp script that running
    // processes still read modules from.
    private val gate = Any()
    private val activeRuns = MutableStateFlow(0)
    private val updating = MutableStateFlow(false)

    suspend fun ensureInitialized(context: Context) {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            withContext(Dispatchers.IO) {
                try {
                    YoutubeDL.init(context)
                    FFmpeg.init(context)
                    initialized = true
                    refreshVersion(context)
                    if (_status.value !is Status.Updating) _status.value = Status.Ready
                } catch (e: Exception) {
                    Log.e(TAG, "init failed", e)
                    _status.value = Status.Failed(e.message ?: e.javaClass.simpleName)
                    throw e
                }
            }
        }
    }

    /**
     * Called when Tapedeck comes to the foreground. yt-dlp is only ever updated while the app is open,
     * never from a background job: the very first open replaces the year-old bundled version, after
     * that it checks at most once an hour.
     */
    suspend fun updateIfDue(context: Context, settings: Settings) {
        try {
            ensureInitialized(context)
        } catch (e: Exception) {
            return
        }
        if (neverUpdated(context) || (settings.current.autoUpdate && isCheckDue(settings))) {
            update(context, settings)
        }
    }

    fun neverUpdated(context: Context) = YoutubeDL.version(context) == null

    fun isCheckDue(settings: Settings): Boolean =
        System.currentTimeMillis() - settings.engine.value.lastCheckAt > CHECK_INTERVAL.inWholeMilliseconds

    /** A failed download only triggers a new check if the last one is a while ago. */
    fun isRetryUpdateWorthwhile(settings: Settings): Boolean =
        System.currentTimeMillis() - settings.engine.value.lastCheckAt > RETRY_CHECK_INTERVAL.inWholeMilliseconds

    suspend fun update(context: Context, settings: Settings, channel: UpdateChannel = settings.current.updateChannel): UpdateResult =
        updateMutex.withLock {
            try {
                ensureInitialized(context)
            } catch (e: Exception) {
                return@withLock UpdateResult.Failed(e.message ?: "init")
            }
            synchronized(gate) { updating.value = true }
            _status.value = Status.Updating
            try {
                // Let running downloads finish rather than pulling the script out from under them.
                withTimeoutOrNull(15.minutes) { activeRuns.first { it == 0 } }
                    ?: return@withLock UpdateResult.Failed("downloads still running")
                val outcome = runInterruptible(Dispatchers.IO) { YoutubeDL.updateYoutubeDL(context, channel.toLibrary()) }
                val now = System.currentTimeMillis()
                val updated = outcome == YoutubeDL.UpdateStatus.DONE
                settings.updateEngine { it.copy(lastCheckAt = now, lastUpdateAt = if (updated) now else it.lastUpdateAt, lastError = null) }
                refreshVersion(context)
                Log.i(TAG, "update ($channel): $outcome → ${_version.value}")
                if (updated) UpdateResult.Updated(_version.value) else UpdateResult.UpToDate
            } catch (e: Exception) {
                Log.w(TAG, "update failed", e)
                val message = e.cause?.message ?: e.message ?: e.javaClass.simpleName
                settings.updateEngine { it.copy(lastCheckAt = System.currentTimeMillis(), lastError = message) }
                UpdateResult.Failed(message)
            } finally {
                synchronized(gate) { updating.value = false }
                _status.value = Status.Ready
            }
        }

    /** Runs one yt-dlp process; waits while an update is replacing the script. */
    private suspend fun <T> withYtDlp(block: () -> T): T {
        while (true) {
            updating.first { !it }
            val entered = synchronized(gate) {
                if (updating.value) false else {
                    activeRuns.value += 1
                    true
                }
            }
            if (entered) break
        }
        try {
            return runInterruptible(Dispatchers.IO) { block() }
        } finally {
            synchronized(gate) { activeRuns.value -= 1 }
        }
    }

    suspend fun download(context: Context, entry: Entry, settings: Settings, onLine: (String) -> Unit): YoutubeDLResponse {
        ensureInitialized(context)
        val dir = Storage.entryDir(context, entry.id).apply { mkdirs() }
        val request = YoutubeDLRequest(entry.url)
            .addOption("--cache-dir", cacheDir(context).absolutePath)
            .addCommands(YtDlpArgs.download(entry.options, dir.absolutePath, settings.current.sponsorBlock))
        // The library always passes its own QuickJS as an option; a custom command comes later on the command
        // line, and yt-dlp keeps the last path given for a runtime, so this one wins.
        val engine = jsEngine(context, settings)
        if (engine != null) request.addCommands(listOf("--js-runtimes", "quickjs:${engine.absolutePath}"))
        Log.d(TAG, "JavaScript: ${engine?.name ?: "bundled QuickJS"}")
        // Every attempt gets its own process id: youtubedl-android 0.18.1 leaves the id of a failed run in
        // its process map, so a retry under the entry's id would stop at once with "Process ID already exists".
        val processId = "${entry.id}#${System.nanoTime()}"
        processIds[entry.id] = processId
        try {
            return withYtDlp {
                YoutubeDL.execute(request, processId) { _, _, line -> onLine(line) }
            }
        } finally {
            processIds.remove(entry.id, processId)
        }
    }

    suspend fun fetchPlaylist(context: Context, url: String): Playlist {
        ensureInitialized(context)
        val request = YoutubeDLRequest(url)
            .addOption("--cache-dir", cacheDir(context).absolutePath)
            .addCommands(YtDlpArgs.playlist())
        val response = withYtDlp { YoutubeDL.execute(request) }
        return InfoJson.parsePlaylist(response.out)
    }

    /**
     * QuickJS-NG (libqjsng.so, see tools/fetch-quickjs-ng.sh) if it is switched on and runs on this phone,
     * else null for the library's own QuickJS. The release binaries are static Linux builds; whether
     * Android lets them run is checked once per app version with a tiny script.
     */
    private suspend fun jsEngine(context: Context, settings: Settings): File? {
        if (!settings.current.fastJsEngine) return null
        val binary = File(context.applicationInfo.nativeLibraryDir, "libqjsng.so").takeIf { it.exists() } ?: return null
        val versionCode = BuildConfig.VERSION_CODE
        val meta = settings.engine.value
        if (meta.quickJsNgCheckedFor == versionCode) return binary.takeIf { meta.quickJsNgWorks }
        val works = runInterruptible(Dispatchers.IO) { selfTest(binary) }
        Log.i(TAG, "QuickJS-NG self-test: ${if (works) "ok" else "failed, using the bundled QuickJS"}")
        settings.updateEngine { it.copy(quickJsNgCheckedFor = versionCode, quickJsNgWorks = works) }
        return binary.takeIf { works }
    }

    private fun selfTest(binary: File): Boolean = try {
        val process = ProcessBuilder(binary.absolutePath, "-e", "print(6 * 7)").redirectErrorStream(true).start()
        val finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText().trim()
        if (!finished) process.destroy()
        finished && process.exitValue() == 0 && output == "42"
    } catch (e: Exception) {
        Log.w(TAG, "QuickJS-NG does not start", e)
        false
    }

    /** Entry id → process id of its running yt-dlp. */
    private val processIds = ConcurrentHashMap<String, String>()

    fun cancel(id: String) {
        processIds[id]?.let(YoutubeDL::destroyProcessById)
    }

    private fun cacheDir(context: Context) = File(context.noBackupFilesDir, "ytdlp-cache").apply { mkdirs() }

    private fun refreshVersion(context: Context) {
        _version.value = YoutubeDL.version(context) ?: BUNDLED_VERSION
    }

    private fun UpdateChannel.toLibrary(): YoutubeDL.UpdateChannel = when (this) {
        UpdateChannel.STABLE -> YoutubeDL.UpdateChannel.STABLE
        UpdateChannel.NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
    }
}
