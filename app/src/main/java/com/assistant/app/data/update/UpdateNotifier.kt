package com.assistant.app.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.assistant.app.R
import com.assistant.app.data.update.model.UpdateInfo

interface UpdateNotifier {
    fun showUpdateNotification(updateInfo: UpdateInfo): Boolean
}

class AndroidUpdateNotifier(private val context: Context) : UpdateNotifier {

    private val appContext = context.applicationContext

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val name = appContext.getString(R.string.update_notification_channel_name)
        val descriptionText = appContext.getString(R.string.update_notification_channel_desc)
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = descriptionText
        }
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.createNotificationChannel(channel)
    }

    override fun showUpdateNotification(updateInfo: UpdateInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }

        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false

        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.htmlUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = appContext.getString(R.string.update_notification_title, updateInfo.latestVersion)
        val shortBody = appContext.getString(R.string.update_notification_body, updateInfo.latestVersion)

        val bigText = buildString {
            append(shortBody)
            if (updateInfo.releaseTitle.isNotBlank() && updateInfo.releaseTitle != updateInfo.latestVersion) {
                append("\n\n")
                append(updateInfo.releaseTitle)
            }
            if (updateInfo.releaseNotes.isNotBlank()) {
                append("\n\n")
                append(updateInfo.releaseNotes.take(300))
            }
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_inlet_logo)
            .setContentTitle(title)
            .setContentText(shortBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
            return true
        } catch (_: SecurityException) {
            return false
        }
    }

    companion object {
        const val CHANNEL_ID = "app_updates"
        const val NOTIFICATION_ID = 2001
    }
}
