package com.leaf.explorer.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.leaf.explorer.R

class NotificationBackend(val context: Context) {
    val manager = NotificationManagerCompat.from(context)

    val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun buildDynamicNotification(notificationId: Int, channelId: String): DynamicNotification {
        return DynamicNotification(context, manager, channelId, notificationId)
    }

    companion object {
        private const val TAG = "NotificationBackend"

        const val NOTIFICATION_CHANNEL_HIGH = "tsHighPriority"

        const val NOTIFICATION_CHANNEL_LOW = "tsLowPriority"

        const val CHANNEL_INSTRUCTIVE = "instructiveNotifications"
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelHigh = NotificationChannel(
                NOTIFICATION_CHANNEL_HIGH,
                context.getString(R.string.high_priority_notifications), NotificationManager.IMPORTANCE_HIGH
            )
            channelHigh.enableLights(preferences.getBoolean("notification_light", false))
            channelHigh.enableVibration(preferences.getBoolean("notification_vibrate", false))
            manager.createNotificationChannel(channelHigh)
            val channelLow = NotificationChannel(
                NOTIFICATION_CHANNEL_LOW,
                context.getString(R.string.low_priority_notifications), NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channelLow)

            val channelInstructive = NotificationChannel(
                CHANNEL_INSTRUCTIVE,
                context.getString(R.string.notifications_instructive),
                NotificationManager.IMPORTANCE_MAX
            )
            manager.createNotificationChannel(channelInstructive)
        }
    }
}