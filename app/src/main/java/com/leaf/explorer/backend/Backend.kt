package com.leaf.explorer.backend

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import com.leaf.explorer.service.BackgroundService
import com.leaf.explorer.util.Permissions
import javax.inject.Inject
import javax.inject.Singleton

// is this my new favorite font? I think it is!

@Singleton
class Backend @Inject constructor(
    @ApplicationContext val context: Context,
    services: Lazy<Services>,
) {

    private val bgIntent = Intent(context, BackgroundService::class.java)

    private val bgStopIntent = Intent(context, BackgroundService::class.java).also {
        it.action = BackgroundService.ACTION_STOP_BG_SERVICE
    }

    val bgNotification
        get() = services.notifications.foregroundNotification

    private var foregroundActivitiesCount = 0

    private val services: Services by lazy {
        services.get()
    }

    private fun ensureStarted() = services.start()

    fun ensureStartedAfterWelcoming() {
        takeBgServiceFgIfNeeded(true)
    }

    private fun ensureStopped() {
        services.stop()
    }

    @Synchronized
    fun notifyActivityInForeground(inForeground: Boolean) {
        if (!inForeground && foregroundActivitiesCount == 0) return
        val wasInForeground = foregroundActivitiesCount > 0
        foregroundActivitiesCount += if (inForeground) 1 else -1
        val isInForeground = foregroundActivitiesCount > 0
        val newlySwitchedGrounds = isInForeground != wasInForeground

        if (Permissions.checkRunningConditions(context)) {
            takeBgServiceFgIfNeeded(newlySwitchedGrounds)
        }
    }

    fun takeBgServiceFgIfNeeded(
        newlySwitchedGrounds: Boolean,
        forceStop: Boolean = false,
    ) {
        // Do not try to tweak this!!!
        val hasServices = services.isServingAnything
        val inForeground = foregroundActivitiesCount > 0
        val newlyInForeground = newlySwitchedGrounds && inForeground
        val newlyInBackground = newlySwitchedGrounds && !inForeground
        val keepRunning = hasServices && !forceStop

        if (newlyInForeground || !forceStop) {
            ensureStarted()
        } else if (!inForeground && !keepRunning) {
            ensureStopped()
        }

        if (newlyInBackground && keepRunning) {
            ContextCompat.startForegroundService(context, bgIntent)
        } else if (newlyInForeground || (!inForeground && !keepRunning)) {
            ContextCompat.startForegroundService(context, bgStopIntent)
        }

        if (!forceStop) {
            if (hasServices && !inForeground) {
                services.notifications.foregroundNotification.show()
            } else {
                services.notifications.foregroundNotification.cancel()
            }
        }
    }
}