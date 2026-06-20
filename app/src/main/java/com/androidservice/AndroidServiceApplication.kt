package com.androidservice

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

class AndroidServiceApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        appScope.launch { setupLibbox() }
    }

    private fun setupLibbox() {
        runCatching {
            Libbox.setLocale(Locale.getDefault().toLanguageTag().replace("-", "_"))
        }
        val workingDir = getExternalFilesDir(null) ?: filesDir
        workingDir.mkdirs()
        cacheDir.mkdirs()
        Libbox.setup(
            SetupOptions().apply {
                basePath = filesDir.path
                workingPath = workingDir.path
                tempPath = cacheDir.path
                fixAndroidStack = true
            },
        )
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示二进制进程服务状态"
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                SINGBOX_NOTIFICATION_CHANNEL_ID,
                SINGBOX_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示 sing-box VPN 状态"
                setShowBadge(false)
            },
        )
    }

    companion object {
        lateinit var instance: AndroidServiceApplication
            private set

        const val NOTIFICATION_CHANNEL_ID = "binary_service_channel"
        const val NOTIFICATION_CHANNEL_NAME = "服务进程"
        const val NOTIFICATION_ID = 1001

        const val SINGBOX_NOTIFICATION_CHANNEL_ID = "singbox_vpn_channel"
        const val SINGBOX_NOTIFICATION_CHANNEL_NAME = "sing-box VPN"
        const val SINGBOX_NOTIFICATION_ID = 1002

        val connectivity: ConnectivityManager
            get() = instance.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
}
