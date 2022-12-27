package com.leaf.explorer.backend

import android.content.Context
import android.net.Uri
import android.media.MediaScannerConnection
import android.util.Log
import com.yanzhenjie.andserver.Server
import dagger.hilt.android.qualifiers.ApplicationContext
import com.leaf.explorer.data.WebDataRepository
import com.leaf.explorer.di.WebShareServer
import com.leaf.explorer.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Services @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webDataRepository: WebDataRepository,
    @WebShareServer private val webShareServer: Server,
) {

    val isServingAnything
        get() = webDataRepository.isServing

    private val mediaScannerConnectionClient = MediaScannerConnectionClient()
    val mediaScannerConnection = MediaScannerConnection(context, mediaScannerConnectionClient)

    val notifications = Notifications(NotificationBackend(context))

    fun start() {
        val webServerRunning = webShareServer.isRunning

        if (!mediaScannerConnection.isConnected) {
            mediaScannerConnection.connect()
        }

        if (webServerRunning) {
            Log.d(TAG, "start: Services are already up")
            return
        }

        try {
            if (!Permissions.checkRunningConditions(context)) throw Exception(
                "The app doesn't have the satisfactory permissions to start the services."
            )

            if (!webServerRunning) {
                webShareServer.startup()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        if (mediaScannerConnection.isConnected) {
            mediaScannerConnection.disconnect()
        }

        webShareServer.shutdown()
    }

    private class MediaScannerConnectionClient : MediaScannerConnection.MediaScannerConnectionClient {
        override fun onScanCompleted(path: String?, uri: Uri?) {
            Log.d(TAG, "onScanCompleted: $path")
        }

        override fun onMediaScannerConnected() {
            Log.d(TAG, "onMediaScannerConnected: Service connected")
        }
    }

    companion object {
        private const val TAG = "Services"
    }
}