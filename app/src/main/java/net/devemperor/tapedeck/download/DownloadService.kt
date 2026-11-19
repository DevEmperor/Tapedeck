package net.devemperor.tapedeck.download

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.devemperor.tapedeck.AppVisibility
import net.devemperor.tapedeck.BuildConfig
import net.devemperor.tapedeck.Format
import net.devemperor.tapedeck.MainActivity
import net.devemperor.tapedeck.Notifications
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.TapedeckApp
import net.devemperor.tapedeck.data.AppSettings
import net.devemperor.tapedeck.data.DownloadState
import net.devemperor.tapedeck.data.Entry
import net.devemperor.tapedeck.data.MediaKind
import net.devemperor.tapedeck.data.Storage
import net.devemperor.tapedeck.engine.DownloadError
import net.devemperor.tapedeck.engine.Engine
import net.devemperor.tapedeck.engine.ErrorKind
import net.devemperor.tapedeck.engine.Errors
import net.devemperor.tapedeck.engine.InfoJson
import net.devemperor.tapedeck.engine.MediaInfo
import net.devemperor.tapedeck.engine.ProgressTracker
import net.devemperor.tapedeck.engine.YtDlpArgs
import net.devemperor.tapedeck.engine.YtDlpOutput
import java.io.File
import kotlin.math.roundToInt

/**
 * Runs the download queue in the foreground, so it keeps going with the screen off. A wake lock and a
 * Wi-Fi lock stop the phone from dozing off mid-download; the service ends itself once the queue is
 * empty.
 */
class DownloadService : LifecycleService() {
    private val app get() = application as TapedeckApp
    private val jobs = mutableMapOf<String, Job>()
    private val kick = MutableStateFlow(0)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var stopping = false
    private var closing = false

    override fun onCreate() {
        super.onCreate()
        goForeground()
        acquireLocks()
        lifecycleScope.launch {
            combine(app.library.entries, app.settings.state, app.network.state, kick) { entries, settings, net, _ ->
                Triple(entries, settings, net)
            }.collect { (entries, settings, net) -> schedule(entries, settings, net) }
        }
        lifecycleScope.launch {
            while (true) {
                if (!stopping) postProgress()
                delay(1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        goForeground()
        stopping = false
        kick.value++
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15 limits data-sync services to six hours a day; what is left waits for the next start.
        jobs.keys.toList().forEach { id ->
            Engine.cancel(id)
            app.library.update(id) { it.copy(state = DownloadState.QUEUED) }
        }
        stopSelf()
    }

    /**
     * Swiping Tapedeck away in the recent apps stops the downloads as well. They go back into the queue and
     * continue from their partial files the next time the app is opened.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        closing = true
        jobs.keys.toList().forEach { id ->
            Engine.cancel(id)
            app.library.update(id) { if (it.state == DownloadState.RUNNING) it.copy(state = DownloadState.QUEUED) else it }
        }
        stopping = true
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        jobs.values.forEach { it.cancel() }
        jobs.keys.forEach(Engine::cancel)
        app.library.saveNow()
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun schedule(entries: List<Entry>, settings: AppSettings, net: NetState) {
        // The app was swiped away: start nothing new on the way out.
        if (closing) return
        val networkOk = net.connected && (!settings.wifiOnly || net.unmetered)
        val byId = entries.associateBy { it.id }

        // RUNNING without a job here means the download was cut off (service restarted): queue it again.
        entries.filter { it.state == DownloadState.RUNNING && it.id !in jobs }.forEach { orphan ->
            app.library.update(orphan.id) { if (it.state == DownloadState.RUNNING) it.copy(state = DownloadState.QUEUED) else it }
        }

        // Jobs whose entry was cancelled, deleted or sent back to wait for the network.
        jobs.entries.toList().forEach { (id, job) ->
            val entry = byId[id]
            if (entry == null || entry.state != DownloadState.RUNNING || !networkOk) {
                Engine.cancel(id)
                job.cancel()
                jobs.remove(id)
                if (entry != null && entry.state == DownloadState.RUNNING) {
                    app.library.update(id) { it.copy(state = DownloadState.WAITING_NETWORK) }
                }
                app.downloads.clear(id)
            }
        }

        val pending = entries.filter { it.state == DownloadState.QUEUED || it.state == DownloadState.WAITING_NETWORK }
            .sortedBy { it.createdAt }
        for (entry in pending) {
            if (!networkOk) {
                if (entry.state != DownloadState.WAITING_NETWORK) app.library.update(entry.id) { it.copy(state = DownloadState.WAITING_NETWORK) }
                continue
            }
            if (jobs.size >= settings.parallelDownloads.coerceIn(1, AppSettings.MAX_PARALLEL_DOWNLOADS)) break
            app.library.update(entry.id) { it.copy(state = DownloadState.RUNNING, errorKind = null, errorDetail = null) }
            jobs[entry.id] = lifecycleScope.launch { runJob(entry.id) }
        }

        if (jobs.isEmpty() && pending.isEmpty()) {
            stopping = true
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun runJob(id: String) {
        val entry = app.library[id] ?: return
        val tracker = ProgressTracker(entry.options.kind)
        app.downloads.publish(id, tracker.current)
        try {
            // The bundled yt-dlp is a year old by now; fetch a current one before the very first download
            // (if opening the app could not, e.g. offline) — but only while the app is on screen.
            if (Engine.neverUpdated(app) && AppVisibility.foreground) Engine.update(app, app.settings)
            if (Storage.freeBytes(app) < MIN_FREE_BYTES) throw StorageFullException()
            Engine.download(app, entry, app.settings) { line ->
                if (BuildConfig.DEBUG) Log.d(TAG, "${id.take(8)} $line")
                if (tracker.onLine(line)) app.downloads.publish(id, tracker.current)
                if (YtDlpOutput.isInfoJsonWritten(line)) lifecycleScope.launch(Dispatchers.IO) { applyEarlyInfo(id) }
            }
            finish(id)
        } catch (e: CancellationException) {
            Engine.cancel(id)
            throw e
        } catch (e: YoutubeDL.CanceledException) {
            // Cancelled by the user; the state is already set.
        } catch (e: Exception) {
            Log.w(TAG, "download $id failed", e)
            fail(id, e)
        } finally {
            app.downloads.clear(id)
            // A retry may already run under the same id; only drop the map entry if it is still this job.
            if (jobs[id] === currentCoroutineContext()[Job]) jobs.remove(id)
            kick.value++
        }
    }

    /** As soon as yt-dlp has written its info file, the card shows the real title, channel and length. */
    private fun applyEarlyInfo(id: String) {
        val info = readInfo(id) ?: return
        app.library.update(id) { entry ->
            entry.copy(
                title = info.title ?: entry.title,
                uploader = info.uploader ?: entry.uploader,
                durationMs = info.durationMs.takeIf { it > 0 } ?: entry.durationMs,
            )
        }
    }

    private suspend fun finish(id: String) = withContext(Dispatchers.IO) {
        val entry = app.library[id] ?: return@withContext
        val dir = Storage.entryDir(app, id)
        val files = dir.listFiles().orEmpty().filter { it.isFile }
        val media = files.filter { isFinalMedia(it) }.maxByOrNull { it.length() }
            ?: throw IllegalStateException("yt-dlp finished without a media file")
        val cover = files.firstOrNull { it.name.startsWith(YtDlpArgs.COVER_BASENAME + ".") }
        cover?.let { runCatching { Covers.trimLetterbox(it) } }
        val info = readInfo(id)
        // Intermediate formats, the info JSON and anything else yt-dlp left behind.
        files.filter { it != media && it != cover }.forEach { it.delete() }
        val sponsorBlock = app.settings.current.sponsorBlock
        app.library.update(id) {
            it.copy(
                state = DownloadState.DONE,
                mediaFile = media.absolutePath,
                thumbnailFile = cover?.absolutePath,
                sizeBytes = media.length(),
                title = info?.title ?: it.title,
                uploader = info?.uploader ?: it.uploader,
                durationMs = info?.durationMs?.takeIf { ms -> ms > 0 } ?: it.durationMs,
                // Cut sponsor segments shift every chapter, so only uncut downloads keep them.
                chapters = if (sponsorBlock) emptyList() else info?.chapters.orEmpty(),
                formatLabel = formatLabel(entry, media, info),
                completedAt = System.currentTimeMillis(),
                errorKind = null,
                errorDetail = null,
            )
        }
        app.library.saveNow()
        app.library[id]?.let(::notifyDone)
    }

    private fun fail(id: String, e: Exception) {
        val entry = app.library[id] ?: return
        if (entry.state != DownloadState.RUNNING) return
        val error = when (e) {
            is StorageFullException -> DownloadError(ErrorKind.STORAGE, null)
            is YoutubeDLException -> Errors.classify(e.message)
            else -> DownloadError(ErrorKind.ENGINE, e.message)
        }
        val settings = app.settings.current
        // QuickJS-NG passed its self-test but failed in earnest: back to the bundled QuickJS for this version.
        if (e is YoutubeDLException && e.message?.contains("Error running QuickJS") == true && settings.fastJsEngine) {
            app.settings.updateEngine { it.copy(quickJsNgWorks = false) }
        }
        val net = app.network.state.value
        val networkOk = net.connected && (!settings.wifiOnly || net.unmetered)
        when {
            // The connection dropped: wait for it and continue from the partial file.
            error.kind == ErrorKind.NETWORK && !networkOk ->
                app.library.update(id) { it.copy(state = DownloadState.WAITING_NETWORK) }
            // A passing 403, or YouTube changed something: back into the queue for another try, and
            // fetch the newest yt-dlp meanwhile if the app is open (never in the background). The retry
            // waits for that update by itself (Engine gate). A 403 gets two tries: on 19.11.2026 three of
            // eight downloads in a row hit one, and each went through on the next attempt.
            error.mayBeFixedByUpdate && entry.autoRetries < (if (error.kind == ErrorKind.BLOCKED) 2 else 1) -> {
                app.library.update(id) { it.copy(state = DownloadState.QUEUED, autoRetries = it.autoRetries + 1) }
                if (settings.autoUpdate && AppVisibility.foreground && Engine.isRetryUpdateWorthwhile(app.settings)) {
                    app.scope.launch { Engine.update(app, app.settings) }
                }
            }
            else -> app.library.update(id) {
                it.copy(state = DownloadState.FAILED, errorKind = error.kind.name, errorDetail = error.detail)
            }
        }
    }

    private fun readInfo(id: String): MediaInfo? {
        val file = File(Storage.entryDir(app, id), YtDlpArgs.INFO_BASENAME + ".info.json")
        if (!file.exists()) return null
        return runCatching { InfoJson.parse(file.readText()) }.getOrNull()
    }

    private fun isFinalMedia(file: File): Boolean {
        val name = file.name
        if (!name.startsWith(YtDlpArgs.MEDIA_BASENAME + ".")) return false
        if (name.endsWith(".part") || name.endsWith(".ytdl") || name.contains(".part-Frag") || name.contains(".temp.")) return false
        // Picture and sound of a video arrive as media.f137.mp4 + media.f140.m4a before the merge.
        if (Regex("""^media\.f\d+[\w-]*\.""").containsMatchIn(name)) return false
        return file.extension.lowercase() !in setOf("json", "jpg", "jpeg", "png", "webp")
    }

    private fun formatLabel(entry: Entry, media: File, info: MediaInfo?): String {
        val ext = media.extension.uppercase()
        return when (entry.options.kind) {
            MediaKind.VIDEO -> listOfNotNull(info?.resolutionClass?.let { "${it}p" }, ext).joinToString(" · ")
            MediaKind.AUDIO -> {
                val durationSec = (info?.durationMs ?: entry.durationMs) / 1000.0
                val kbit = if (durationSec > 0) (media.length() * 8 / durationSec / 1000).roundToInt() else null
                listOfNotNull(ext, kbit?.let { getString(R.string.format_kbit, it) }).joinToString(" · ")
            }
        }
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, Notifications.ID_DOWNLOADS, buildNotification(), type)
    }

    private fun postProgress() {
        if (!canNotify()) return
        NotificationManagerCompat.from(this).notify(Notifications.ID_DOWNLOADS, buildNotification())
    }

    private fun buildNotification(): Notification {
        val entries = app.library.entries.value
        val running = entries.filter { it.state == DownloadState.RUNNING }
        val queued = entries.count { it.state == DownloadState.QUEUED || it.state == DownloadState.WAITING_NETWORK }
        val waitingForNetwork = running.isEmpty() && entries.any { it.state == DownloadState.WAITING_NETWORK }
        val first = running.firstOrNull()
        val progress = first?.let { app.downloads.live.value[it.id] }
        val more = running.size - 1 + queued
        val builder = NotificationCompat.Builder(this, Notifications.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp())
        when {
            first != null -> {
                builder.setContentTitle(first.title)
                val line = Format.progress(this, progress)
                builder.setContentText(if (more > 0) getString(R.string.notif_more, line, more) else line)
                val fraction = progress?.fraction
                builder.setProgress(1000, ((fraction ?: 0f) * 1000).roundToInt(), fraction == null)
            }
            waitingForNetwork -> builder.setContentTitle(getString(R.string.notif_waiting_network_title))
                .setContentText(resources.getQuantityString(R.plurals.notif_waiting_count, queued, queued))
            else -> builder.setContentTitle(getString(R.string.notif_preparing))
        }
        return builder.build()
    }

    private fun notifyDone(entry: Entry) {
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(this, Notifications.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(entry.title)
            .setContentText(getString(if (entry.isVideo) R.string.notif_done_video else R.string.notif_done_audio))
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .setGroup(Notifications.GROUP_DONE)
            .build()
        NotificationManagerCompat.from(this).notify(entry.id.hashCode(), notification)
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun canNotify() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Tapedeck:download")
            .apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
        wifiLock = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Tapedeck:download")
            .apply { setReferenceCounted(false); acquire() }
    }

    private class StorageFullException : Exception()

    private companion object {
        const val TAG = "TapedeckDownload"
        const val MIN_FREE_BYTES = 150L * 1024 * 1024
    }
}
