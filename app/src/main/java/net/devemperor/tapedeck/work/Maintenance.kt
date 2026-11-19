package net.devemperor.tapedeck.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.devemperor.tapedeck.TapedeckApp
import net.devemperor.tapedeck.data.AutoDelete
import net.devemperor.tapedeck.player.NowPlaying
import java.util.concurrent.TimeUnit

/**
 * Twice a day: delete what has expired. Deliberately nothing else — yt-dlp is only updated while the app
 * is open (Engine.updateIfDue), never from here.
 */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Maintenance.cleanUp(applicationContext as TapedeckApp)
        return Result.success()
    }
}

object Maintenance {
    private const val WORK_NAME = "maintenance"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(12, TimeUnit.HOURS).build()
        // UPDATE replaces the earlier request that still carried a network constraint for the update.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cleanUp(app: TapedeckApp): Int {
        val victims = AutoDelete.victims(
            app.library.entries.value, app.settings.current, System.currentTimeMillis(), NowPlaying.entryId, app.library.onMixtapes(),
        )
        if (victims.isNotEmpty()) {
            Log.i("TapedeckMaintenance", "auto-deleting ${victims.size} entries")
            app.downloads.delete(victims.map { it.id }.toSet())
        }
        return victims.size
    }
}
