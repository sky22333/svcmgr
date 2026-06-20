package com.androidservice.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.androidservice.AndroidServiceApplication
import com.androidservice.R
import com.androidservice.data.BinaryConfig
import com.androidservice.manager.AppConfigManager
import com.androidservice.singbox.SingBoxPlatform
import com.androidservice.singbox.SingBoxRuntime
import com.androidservice.singbox.SingBoxServiceContract
import com.androidservice.singbox.readBinaryConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

class SingBoxProxyService : Service() {

    inner class SingBoxBinder : Binder() {
        fun getService(): SingBoxProxyService = this@SingBoxProxyService
    }

    private val binder = SingBoxBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val appConfigManager by lazy { AppConfigManager(this) }
    private lateinit var runtime: SingBoxRuntime

    override fun onCreate() {
        super.onCreate()
        runtime = SingBoxRuntime(
            scope = serviceScope,
            platform = SingBoxPlatform(),
            packageName = packageName,
            loadConfigContent = { config ->
                withContext(Dispatchers.IO) {
                    appConfigManager.loadConfigFile(config.configFileName)?.content
                }
            },
            stopForegroundAndSelf = {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            SingBoxServiceContract.ACTION_START -> intent.readBinaryConfig()?.let { startSingBox(it) }
            SingBoxServiceContract.ACTION_STOP -> runtime.stop(getString(R.string.singbox_proxy_stopped))
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    val serviceState get() = runtime.serviceState
    val logs get() = runtime.logs
    val trafficStats get() = runtime.trafficStats

    private fun startSingBox(config: BinaryConfig) {
        startForeground()
        runtime.start(config, getString(R.string.singbox_proxy_started))
    }

    fun stopSingBox() {
        runtime.stop(getString(R.string.singbox_proxy_stopped))
    }

    private fun startForeground() {
        ServiceCompat.startForeground(
            this,
            AndroidServiceApplication.SINGBOX_PROXY_NOTIFICATION_ID,
            createNotification(running = false),
            foregroundServiceType(),
        )
    }

    private fun foregroundServiceType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
    }

    private fun createNotification(running: Boolean): Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            Intent(this, SingBoxProxyService::class.java).setAction(SingBoxServiceContract.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, AndroidServiceApplication.SINGBOX_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.singbox_proxy_notification_title))
            .setContentText(
                if (running) getString(R.string.singbox_proxy_notification_running)
                else getString(R.string.singbox_proxy_notification_starting),
            )
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentIntent(launchIntent)
            .setOngoing(running)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_notification_stop, getString(R.string.action_stop), stopIntent)
            .build()
    }

    companion object {
        private const val STOP_REQUEST_CODE = 2003
    }
}
