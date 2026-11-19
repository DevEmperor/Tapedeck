package net.devemperor.tapedeck.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.devemperor.tapedeck.TapedeckApp
import net.devemperor.tapedeck.data.AutoDelete
import net.devemperor.tapedeck.engine.Engine
import net.devemperor.tapedeck.player.NowPlaying
import java.util.concurrent.TimeUnit

/** Twice a day: delete what has expired and look for a newer yt-dlp. */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TapedeckApp
        Maintenance.cleanUp(app)
        if (app.settings.current.autoUpdate && Engine.isCheckDue(app.settings)) {
            Engine.update(app, app.settings)
        }
        return Result.success()
    }
}

object Maintenance {
    private const val WORK_NAME = "maintenance"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cleanUp(app: TapedeckApp): Int {
        val victims = AutoDelete.victims(app.library.entries.value, app.settings.current, System.currentTimeMillis(), NowPlaying.entryId)
        if (victims.isNotEmpty()) {
            Log.i("TapedeckMaintenance", "auto-deleting ${victims.size} entries")
            app.downloads.delete(victims.map { it.id }.toSet())
        }
        return victims.size
    }
}
