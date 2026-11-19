package net.devemperor.tapedeck

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CHANNEL_DOWNLOADS = "downloads"
    const val CHANNEL_DONE = "downloads_done"
    const val GROUP_DONE = "net.devemperor.tapedeck.DONE"
    const val ID_DOWNLOADS = 1

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_DOWNLOADS, context.getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_DONE, context.getString(R.string.channel_done), NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }
}
