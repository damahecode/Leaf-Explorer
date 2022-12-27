package com.leaf.explorer.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.genonbeta.android.framework.io.DocumentFile
import com.leaf.explorer.R
import com.leaf.explorer.activity.SharingActivity
import com.leaf.explorer.service.BackgroundService
import com.leaf.explorer.service.BackgroundService.Companion.ACTION_STOP_ALL

class Notifications(private val backend: NotificationBackend) {
    val context: Context
        get() = backend.context

    val foregroundNotification: DynamicNotification by lazy {
        val notification = backend.buildDynamicNotification(
            ID_BG_SERVICE, NotificationBackend.NOTIFICATION_CHANNEL_LOW
        )
        val sendString = context.getString(R.string.sends)
        val receiveString = context.getString(R.string.receive)
        val sendIntent: PendingIntent = getActivityIntent(
            context,
            ID_BG_SERVICE + 1,
            Intent(context, SharingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val receiveIntent: PendingIntent = getActivityIntent(
            context,
            ID_BG_SERVICE + 2,
            Intent(context, SharingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val exitAction = NotificationCompat.Action(
            R.drawable.ic_close_white_24dp, context.getString(R.string.exit),
            getServiceIntent(
                context,
                ID_BG_SERVICE + 3,
                Intent(context, BackgroundService::class.java).setAction(ACTION_STOP_ALL)
            )
        )
        val homeIntent = getActivityIntent(
            context,
            ID_BG_SERVICE + 4,
            Intent(context, SharingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        notification.setSmallIcon(R.drawable.ic_trebleshot)
            .setContentTitle(context.getString(R.string.service_running_notice))
            .setContentText(context.getString(R.string.tap_to_launch_notice))
            .setContentIntent(homeIntent)
            .addAction(exitAction)
            .addAction(R.drawable.ic_arrow_up_24dp, sendString, sendIntent)
            .addAction(R.drawable.ic_arrow_down_24dp, receiveString, receiveIntent)

        notification.show()
    }

    fun notifyReceivingOnWeb(file: DocumentFile): DynamicNotification {
        val notification = backend.buildDynamicNotification(
            file.getUri().hashCode(), NotificationBackend.NOTIFICATION_CHANNEL_LOW
        )
        val homeIntent = getActivityIntent(context,
            ID_BG_SERVICE + 1,
            Intent(context, SharingActivity::class.java)
        )
        notification
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentText(file.getName())
            .setContentTitle(context.getString(R.string.receiving_using_web_web_share))
            .setContentIntent(homeIntent)
        notification.show()

        return notification
    }

    private fun getActivityIntent(context: Context, id: Int, intent: Intent): PendingIntent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    private fun getServiceIntent(context: Context, id: Int, intent: Intent): PendingIntent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getService(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getService(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    companion object {
        const val ID_BG_SERVICE = 1

        const val REQUEST_CODE_NEUTRAL = 3
    }
}