package net.devemperor.tapedeck.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import net.devemperor.tapedeck.AppVisibility
import net.devemperor.tapedeck.TapedeckApp

/**
 * The package installer's answer to Tapedeck's own update. It either needs the user's "Update" tap (the
 * system dialog is shown from here), fails, or succeeds — in which case Android has already ended the app
 * and [PackageReplacedReceiver] takes over.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TapedeckApp
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                when {
                    confirm == null -> app.appUpdates.onInstallFailed(message)
                    AppVisibility.foreground -> context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    else -> app.appUpdates.holdConfirmation(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                PackageReplacedReceiver.clearPending(context)
                app.appUpdates.onInstallAborted()
            }
            else -> {
                Log.w("TapedeckAppUpdates", "install failed: $status $message")
                PackageReplacedReceiver.clearPending(context)
                app.appUpdates.onInstallFailed(message)
            }
        }
    }
}
