package com.leaf.explorer.service

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import dagger.hilt.android.AndroidEntryPoint
import com.leaf.explorer.backend.Backend
import com.leaf.explorer.util.*
import javax.inject.Inject

@AndroidEntryPoint
class BackgroundService : LifecycleService() {
    private val binder = LocalBinder()

    @Inject
    lateinit var backend: Backend

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(Notifications.ID_BG_SERVICE, backend.bgNotification.build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (!Permissions.checkRunningConditions(this) || intent == null) {
            return START_NOT_STICKY
        } else if (intent.action == ACTION_STOP_BG_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        } else if (intent.action == ACTION_STOP_ALL) {
            backend.takeBgServiceFgIfNeeded(newlySwitchedGrounds = false, forceStop = true)
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(false)
    }

    inner class LocalBinder : Binder() {
        val service: BackgroundService
            get() = this@BackgroundService
    }

    companion object {
        const val ACTION_STOP_ALL = "com.leaf.explorer.action.STOP_ALL"

        const val ACTION_STOP_BG_SERVICE = "com.leaf.explorer.action.STOP_BG_SERVICE"
    }
}