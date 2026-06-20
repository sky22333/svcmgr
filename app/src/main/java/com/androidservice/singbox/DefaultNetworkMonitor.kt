package com.androidservice.singbox

import android.net.Network
import com.androidservice.AndroidServiceApplication
import io.nekohasekai.libbox.InterfaceUpdateListener
import java.net.NetworkInterface

object DefaultNetworkMonitor {
    var defaultNetwork: Network? = null
    private var listener: InterfaceUpdateListener? = null

    suspend fun start() {
        val connectivity = AndroidServiceApplication.connectivity
        NetworkTracker.start(connectivity)
        DefaultNetworkListener.start(this) { network ->
            defaultNetwork = network
            checkDefaultInterfaceUpdate(network)
        }
        defaultNetwork = connectivity.activeNetwork
    }

    suspend fun stop() {
        DefaultNetworkListener.stop(this)
        NetworkTracker.stop(AndroidServiceApplication.connectivity)
    }

    fun setListener(listener: InterfaceUpdateListener?) {
        this.listener = listener
        checkDefaultInterfaceUpdate(defaultNetwork)
    }

    private fun checkDefaultInterfaceUpdate(newNetwork: Network?) {
        val listener = listener ?: return
        if (newNetwork != null) {
            repeat(10) {
                val linkProperties = AndroidServiceApplication.connectivity.getLinkProperties(newNetwork)
                    ?: return@repeat Thread.sleep(100)
                val interfaceIndex = runCatching {
                    NetworkInterface.getByName(linkProperties.interfaceName).index
                }.getOrNull() ?: return@repeat Thread.sleep(100)
                listener.updateDefaultInterface(linkProperties.interfaceName, interfaceIndex, false, false)
                return
            }
        } else {
            listener.updateDefaultInterface("", -1, false, false)
        }
    }
}
