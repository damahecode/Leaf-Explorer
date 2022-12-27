package com.leaf.explorer.util

import com.leaf.explorer.config.AppConfig
import java.net.Inet4Address
import java.net.NetworkInterface

object Networks {
    fun NetworkInterface.getFirstInet4Address(): Inet4Address? {
        val addresses = this.inetAddresses
        while (addresses.hasMoreElements()) {
            val address = addresses.nextElement()
            if (address is Inet4Address) return address
        }
        return null
    }

    fun getInterfaces(
        ipV4only: Boolean = true,
        avoidedInterfaces: Array<String>? = AppConfig.DEFAULT_DISABLED_INTERFACES
    ): List<NetworkInterface> {
        val filteredInterfaceList: MutableList<NetworkInterface> = ArrayList()
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                var avoidedInterface = false
                if (avoidedInterfaces != null && avoidedInterfaces.isNotEmpty()) {
                    for (match in avoidedInterfaces) {
                        if (networkInterface.displayName.startsWith(match)) avoidedInterface = true
                    }
                }
                if (avoidedInterface) continue
                val addressList = networkInterface.inetAddresses
                while (addressList.hasMoreElements()) {
                    val address = addressList.nextElement()
                    if (!address.isLoopbackAddress && (address is Inet4Address || !ipV4only)) {
                        filteredInterfaceList.add(networkInterface)
                        break
                    }
                }
            }
        } catch (ignored: Exception) {
        }
        return filteredInterfaceList
    }
}