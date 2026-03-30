package com.velobrowser.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.velobrowser.R
import com.velobrowser.ui.isolated.IsolatedBrowserActivity

abstract class TabKeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "aero_isolated_tab_channel"
        private const val NOTIFICATION_BASE_ID = 7001

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.isolated_tab_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.isolated_tab_channel_desc)
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(channel)
            }
        }
    }

    protected abstract val slot: Int

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
        val notificationId = NOTIFICATION_BASE_ID + slot
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            slot,
            IsolatedBrowserActivity.createIntent(this, slot, ""),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.isolated_tab_notification_title, slot))
            .setContentText(getString(R.string.isolated_tab_notification_text))
            .setSmallIcon(R.drawable.ic_isolated_tab)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .build()
    }
}
