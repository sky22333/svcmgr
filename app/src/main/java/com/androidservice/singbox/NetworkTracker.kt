package com.androidservice.singbox

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

object NetworkTracker {
    data class TrackedNetwork(
        val network: Network,
        @Volatile var linkProperties: LinkProperties? = null,
        @Volatile var capabilities: NetworkCapabilities? = null,
    )

    private val tracked = ConcurrentHashMap<Network, TrackedNetwork>()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun start(connectivity: ConnectivityManager) {
        if (networkCallback != null) return

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                tracked[network] = TrackedNetwork(network)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                tracked[network]?.capabilities = networkCapabilities
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                tracked[network]?.linkProperties = linkProperties
            }

            override fun onLost(network: Network) {
                tracked.remove(network)
            }
        }

        connectivity.registerNetworkCallback(buildNetworkTrackerRequest(), callback)
        networkCallback = callback
    }

    fun stop(connectivity: ConnectivityManager) {
        val callback = networkCallback ?: return
        runCatching { connectivity.unregisterNetworkCallback(callback) }
        networkCallback = null
        tracked.clear()
    }

    fun trackedNetworks(): Collection<TrackedNetwork> = tracked.values

    private fun buildNetworkTrackerRequest(): NetworkRequest {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            NetworkRequest.Builder().clearCapabilities()
        } else {
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setIncludeOtherUidNetworks(true)
        }
        return builder.build()
    }
}
