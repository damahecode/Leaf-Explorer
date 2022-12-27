package com.leaf.explorer.util

import android.content.Context
import androidx.collection.ArrayMap
import com.leaf.explorer.R
import com.leaf.explorer.config.AppConfig
import java.net.NetworkInterface

object TextManipulators {
    fun getWebShareAddress(context: Context, address: String?): String {
        return context.getString(R.string.web_share_address, address, AppConfig.SERVER_PORT_WEBSHARE)
    }

    fun toNetworkTitle(adapterName: String): Int {
        val unknownInterface = R.string.unknown_interface
        val associatedNames: MutableMap<String, Int> = ArrayMap()
        associatedNames["wlan"] = R.string.wifi
        associatedNames["p2p"] = R.string.wifi_direct
        associatedNames["bt-pan"] = R.string.bluetooth
        associatedNames["eth"] = R.string.ethernet
        associatedNames["tun"] = R.string.vpn_interface
        associatedNames["unk"] = unknownInterface
        for (displayName in associatedNames.keys) if (adapterName.startsWith(displayName)) {
            return associatedNames[displayName] ?: unknownInterface
        }
        return -1
    }

    fun String.toNetworkTitle(context: Context): String {
        val adapterNameResource = toNetworkTitle(this)
        return if (adapterNameResource == -1) this else context.getString(adapterNameResource)
    }

    fun NetworkInterface.toNetworkTitle(context: Context): String {
        return this.displayName.toNetworkTitle(context)
    }
}