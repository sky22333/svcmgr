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

        val request = NetworkRequest.Builder()
            .clearCapabilities()
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setIncludeOtherUidNetworks(true)
                }
            }
            .build()

        connectivity.registerNetworkCallback(request, callback)
        networkCallback = callback
    }

    fun stop(connectivity: ConnectivityManager) {
        val callback = networkCallback ?: return
        runCatching { connectivity.unregisterNetworkCallback(callback) }
        networkCallback = null
        tracked.clear()
    }

    fun trackedNetworks(): Collection<TrackedNetwork> = tracked.values
}
