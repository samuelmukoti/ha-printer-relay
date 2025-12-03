package com.harelayprint.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.harelayprint.R
import com.harelayprint.app.HARelayPrintApp
import com.harelayprint.app.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun showJobStartedNotification(jobId: Int, printerName: String) {
        if (!hasNotificationPermission()) return

        val notification = NotificationCompat.Builder(context, HARelayPrintApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_print)
            .setContentTitle(context.getString(R.string.notification_job_started))
            .setContentText("Printing to $printerName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent())
            .build()

        notificationManager.notify(jobId, notification)
    }

    fun showJobCompletedNotification(jobId: Int, printerName: String) {
        if (!hasNotificationPermission()) return

        val notification = NotificationCompat.Builder(context, HARelayPrintApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_print_done)
            .setContentTitle(context.getString(R.string.notification_job_completed))
            .setContentText("Document printed on $printerName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent())
            .build()

        notificationManager.notify(jobId, notification)
    }

    fun showJobFailedNotification(jobId: Int, errorMessage: String?) {
        if (!hasNotificationPermission()) return

        val notification = NotificationCompat.Builder(context, HARelayPrintApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_print_error)
            .setContentTitle(context.getString(R.string.notification_job_failed))
            .setContentText(errorMessage ?: "Print job failed")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent())
            .build()

        notificationManager.notify(jobId, notification)
    }

    fun cancelNotification(jobId: Int) {
        notificationManager.cancel(jobId)
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
