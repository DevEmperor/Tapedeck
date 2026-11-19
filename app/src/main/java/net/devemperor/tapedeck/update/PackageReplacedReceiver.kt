package net.devemperor.tapedeck.update

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import net.devemperor.tapedeck.BuildConfig
import net.devemperor.tapedeck.MainActivity
import net.devemperor.tapedeck.Notifications
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.engine.AppUpdates

/**
 * Runs after any update of Tapedeck. Android ends the app while installing, so a notification says that it
 * worked and opens the new version with one tap; the downloaded APK is no longer needed.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        AppUpdates.updateDir(context).deleteRecursively()
        val updatedHere = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PENDING, false)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_PENDING).apply()
        // Only after an update Tapedeck installed itself, not after adb or a store.
        if (!updatedHere) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        Notifications.createChannels(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_updated_title, BuildConfig.VERSION_NAME))
            .setContentText(context.getString(R.string.app_updated_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_UPDATED, notification)
    }

    companion object {
        const val PREFS = "app_update"
        const val KEY_PENDING = "pending"
        private const val ID_UPDATED = 2

        /** Marks that the next package replacement is Tapedeck's own update. */
        fun markPending(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PENDING, true).commit()
        }

        fun clearPending(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_PENDING).apply()
        }
    }
}
