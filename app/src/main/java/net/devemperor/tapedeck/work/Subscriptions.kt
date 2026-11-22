package net.devemperor.tapedeck.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.devemperor.tapedeck.MainActivity
import net.devemperor.tapedeck.Notifications
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.TapedeckApp
import net.devemperor.tapedeck.data.DownloadOptions
import net.devemperor.tapedeck.data.Mixtape
import net.devemperor.tapedeck.data.Subscription
import net.devemperor.tapedeck.data.SubscriptionPlan
import net.devemperor.tapedeck.download.NewDownload
import net.devemperor.tapedeck.engine.Engine
import net.devemperor.tapedeck.engine.ErrorKind
import net.devemperor.tapedeck.engine.Errors
import net.devemperor.tapedeck.engine.PlaylistItem
import net.devemperor.tapedeck.player.NowPlaying
import java.util.concurrent.TimeUnit

/** The regular look at the subscribed channels and playlists (see [Subscriptions.schedule]). */
class SubscriptionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Subscriptions.check(applicationContext as TapedeckApp)
        return Result.success()
    }
}

/**
 * Subscriptions: a channel or playlist that fills its mixtape by itself. Every few hours (WorkManager, on
 * Wi-Fi with "Wi-Fi only") yt-dlp lists its newest videos; the ones not seen before join the download
 * queue and go onto the mixtape, and once a new one is finished the oldest beyond the limit make room.
 * yt-dlp itself is still only ever updated while the app is open.
 */
object Subscriptions {
    private const val WORK_NAME = "subscriptions"
    private const val TAG = "TapedeckSubscriptions"
    private const val ID_WAITING = 3

    // One look at a time: the regular one and "Look now" must not download the same video twice.
    private val lock = Mutex()

    /**
     * Keeps the regular look scheduled while there are subscriptions, every [hours] hours — only on Wi-Fi
     * with [wifiOnly] — and cancels it once the last subscription is gone.
     */
    fun schedule(context: Context, hours: Int, wifiOnly: Boolean, any: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!any) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<SubscriptionWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        // UPDATE keeps the running schedule and only takes the new interval and network rule.
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * Emulator tests (debug builds): one look in [delaySeconds], so the app is truly in the background by
     * then — WorkManager skips a periodic look that is forced before it is due.
     */
    fun checkSoon(context: Context, delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<SubscriptionWorker>().setInitialDelay(delaySeconds, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueue(request)
    }

    /**
     * Subscribes to [url] (a channel's videos or a playlist): a new mixtape named [title] that fills itself.
     * Of the videos listed now ([items]), the newest [now] download right away, the rest count as seen.
     */
    fun subscribe(
        app: TapedeckApp,
        url: String,
        playlist: Boolean,
        title: String,
        options: DownloadOptions,
        keep: Int,
        items: List<PlaylistItem>,
        now: Int,
    ): Mixtape {
        val subscription = Subscription(url = url, playlist = playlist, options = options.asDefaults(), keep = keep)
        val look = SubscriptionPlan.look(subscription, items, take = now)
        val tape = app.library.createMixtape(
            title, subscription = subscription.copy(known = look.known, lastCheckAt = System.currentTimeMillis()),
        )
        add(app, tape.id, subscription, look.download)
        if (look.download.isNotEmpty()) app.downloads.start()
        return tape
    }

    /** Looks at one subscribed mixtape ([tapeId]), or at all, for new videos; returns how many it started. */
    suspend fun check(app: TapedeckApp, tapeId: String? = null): Int = lock.withLock {
        var started = 0
        val names = mutableListOf<String>()
        val tapes = app.library.mixtapes.value.filter { it.subscription != null && (tapeId == null || it.id == tapeId) }
        for (tape in tapes) {
            val subscription = tape.subscription ?: continue
            val now = System.currentTimeMillis()
            val result = runCatching {
                Engine.fetchPlaylist(app, subscription.url, limit = if (subscription.playlist) null else SubscriptionPlan.CHANNEL_LOOK)
            }
            val list = result.getOrNull()
            if (list == null) {
                val error = result.exceptionOrNull()
                if (error is CancellationException) throw error
                Log.w(TAG, "looking at ${subscription.url} failed", error)
                val kind = (error as? YoutubeDLException)?.let { Errors.classify(it.message).kind } ?: ErrorKind.UNKNOWN
                app.library.updateSubscription(tape.id) { it.copy(lastCheckAt = now, lastError = kind.name) }
                continue
            }
            // The look takes a few seconds; go on from the subscription as it is now.
            val current = app.library.mixtape(tape.id)?.subscription ?: continue
            val look = SubscriptionPlan.look(current, list.items)
            add(app, tape.id, current, look.download)
            app.library.updateSubscription(tape.id) { it.copy(known = look.known, lastCheckAt = now, lastError = null) }
            if (look.download.isNotEmpty()) {
                started += look.download.size
                names += tape.name
            }
            rotate(app, tape.id)
        }
        if (started > 0) startDownloads(app, started, names)
        started
    }

    /** New videos into the queue (marked as the subscription's) and onto its mixtape. */
    private fun add(app: TapedeckApp, tapeId: String, subscription: Subscription, items: List<PlaylistItem>) {
        if (items.isEmpty()) return
        // The newest goes into the queue last, so it has the latest date: the limit goes by that.
        val ordered = if (subscription.playlist) items else items.reversed()
        val added = app.downloads.enqueue(
            ordered.map { NewDownload(it.url, subscription.options, it.title, it.uploader, it.thumbnailUrl, it.durationMs, subscription = tapeId) },
            start = false,
        ).map { it.id }
        app.library.updateMixtape(tapeId) { tape ->
            // A channel's newest on top; a playlist's new videos at its end, as on YouTube.
            if (subscription.playlist) tape.copy(entryIds = tape.entryIds + added) else tape.copy(entryIds = added.reversed() + tape.entryIds)
        }
    }

    /** The oldest finished downloads beyond the subscription's limit make room (see [SubscriptionPlan.surplus]). */
    fun rotate(app: TapedeckApp, tapeId: String) {
        val tape = app.library.mixtape(tapeId) ?: return
        val surplus = SubscriptionPlan.surplus(tape, app.library.entries.value, NowPlaying.entryId)
        if (surplus.isEmpty()) return
        Log.i(TAG, "${tape.name}: ${surplus.size} old downloads make room")
        app.downloads.delete(surplus.map { it.id }.toSet())
    }

    /** Whether Android lets Tapedeck start its downloads from the background (battery optimisation off). */
    fun unrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /**
     * Starts the downloads. From the background, Android 12 and later allow that only when Tapedeck may
     * run unrestricted; otherwise a notification says what is waiting, and the downloads start as soon as
     * Tapedeck is opened.
     */
    private fun startDownloads(app: TapedeckApp, count: Int, names: List<String>) {
        try {
            app.downloads.start()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "starting the downloads from the background was not allowed", e)
            notifyWaiting(app, count, names)
        }
    }

    private fun notifyWaiting(app: TapedeckApp, count: Int, names: List<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(app, Notifications.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(app.resources.getQuantityString(R.plurals.subscription_waiting_title, count, count))
            .setContentText(app.getString(R.string.subscription_waiting_body, names.distinct().joinToString(", ")))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(app).notify(ID_WAITING, notification)
    }
}
