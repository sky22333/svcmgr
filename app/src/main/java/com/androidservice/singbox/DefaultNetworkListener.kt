package com.androidservice.singbox

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.androidservice.AndroidServiceApplication
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.runBlocking

object DefaultNetworkListener {
    private sealed class NetworkMessage {
        class Start(val key: Any, val listener: (Network?) -> Unit) : NetworkMessage()
        class Stop(val key: Any) : NetworkMessage()
        class Put(val network: Network) : NetworkMessage()
        class Update(val network: Network) : NetworkMessage()
        class Lost(val network: Network) : NetworkMessage()
    }

    @OptIn(DelicateCoroutinesApi::class, ObsoleteCoroutinesApi::class)
    private val networkActor = GlobalScope.actor<NetworkMessage>(Dispatchers.Unconfined) {
        val listeners = mutableMapOf<Any, (Network?) -> Unit>()
        var network: Network? = null
        for (message in channel) {
            when (message) {
                is NetworkMessage.Start -> {
                    if (listeners.isEmpty()) register()
                    listeners[message.key] = message.listener
                    if (network != null) message.listener(network)
                }
                is NetworkMessage.Stop -> {
                    if (listeners.isNotEmpty() && listeners.remove(message.key) != null && listeners.isEmpty()) {
                        network = null
                        unregister()
                    }
                }
                is NetworkMessage.Put -> {
                    network = message.network
                    listeners.values.forEach { it(network) }
                }
                is NetworkMessage.Update -> {
                    if (network == message.network) {
                        listeners.values.forEach { it(network) }
                    }
                }
                is NetworkMessage.Lost -> {
                    if (network == message.network) {
                        network = null
                        listeners.values.forEach { it(null) }
                    }
                }
            }
        }
    }

    suspend fun start(key: Any, listener: (Network?) -> Unit) {
        networkActor.send(NetworkMessage.Start(key, listener))
    }

    suspend fun stop(key: Any) {
        networkActor.send(NetworkMessage.Stop(key))
    }

    private object Callback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = runBlocking {
            networkActor.send(NetworkMessage.Put(network))
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            runBlocking { networkActor.send(NetworkMessage.Update(network)) }
        }

        override fun onLost(network: Network) = runBlocking {
            networkActor.send(NetworkMessage.Lost(network))
        }
    }

    private val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun register() {
        val connectivity = AndroidServiceApplication.connectivity
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                connectivity.registerBestMatchingNetworkCallback(request, Callback, mainHandler)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                connectivity.requestNetwork(request, Callback, mainHandler)
            }
            else -> {
                connectivity.registerDefaultNetworkCallback(Callback, mainHandler)
            }
        }
    }

    private fun unregister() {
        runCatching {
            AndroidServiceApplication.connectivity.unregisterNetworkCallback(Callback)
        }
    }
}
