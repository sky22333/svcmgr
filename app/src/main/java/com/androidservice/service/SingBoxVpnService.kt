package com.androidservice.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.androidservice.AndroidServiceApplication
import com.androidservice.R
import com.androidservice.data.BinaryConfig
import com.androidservice.manager.AppConfigManager
import com.androidservice.singbox.SingBoxRuntime
import com.androidservice.singbox.SingBoxServiceContract
import com.androidservice.singbox.SingBoxVpnPlatform
import com.androidservice.singbox.readBinaryConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

class SingBoxVpnService : VpnService() {

    inner class SingBoxBinder : Binder() {
        fun getService(): SingBoxVpnService = this@SingBoxVpnService
    }

    private val binder = SingBoxBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val appConfigManager by lazy { AppConfigManager(this) }
    private var tunInterface: ParcelFileDescriptor? = null
    private lateinit var runtime: SingBoxRuntime

    override fun onCreate() {
        super.onCreate()
        val platform = SingBoxVpnPlatform(this) { pfd ->
            tunInterface?.close()
            tunInterface = pfd
        }
        runtime = SingBoxRuntime(
            scope = serviceScope,
            platform = platform,
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
            onRelease = {
                tunInterface?.close()
                tunInterface = null
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            SingBoxServiceContract.ACTION_START -> intent.readBinaryConfig()?.let { startSingBox(it) }
            SingBoxServiceContract.ACTION_STOP -> runtime.stop(getString(R.string.singbox_vpn_stopped))
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onRevoke() {
        runtime.stop(getString(R.string.singbox_vpn_revoked))
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    val serviceState get() = runtime.serviceState
    val logs get() = runtime.logs
    val trafficStats get() = runtime.trafficStats

    private fun startSingBox(config: BinaryConfig) {
        startForeground()
        runtime.start(config, getString(R.string.singbox_vpn_started))
    }

    fun stopSingBox() {
        runtime.stop(getString(R.string.singbox_vpn_stopped))
    }

    private fun startForeground() {
        ServiceCompat.startForeground(
            this,
            AndroidServiceApplication.SINGBOX_NOTIFICATION_ID,
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
            Intent(this, SingBoxVpnService::class.java).setAction(SingBoxServiceContract.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, AndroidServiceApplication.SINGBOX_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.singbox_notification_title))
            .setContentText(
                if (running) getString(R.string.singbox_notification_running)
                else getString(R.string.singbox_notification_starting),
            )
            .setSmallIcon(R.drawable.ic_notification_service)
            .setContentIntent(launchIntent)
            .setOngoing(running)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_notification_stop, getString(R.string.action_stop), stopIntent)
            .build()
    }

    companion object {
        private const val STOP_REQUEST_CODE = 2002

        fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)
    }
}
