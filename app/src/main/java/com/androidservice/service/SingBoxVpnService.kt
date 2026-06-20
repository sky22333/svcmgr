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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.androidservice.AndroidServiceApplication
import com.androidservice.R
import com.androidservice.data.BinaryConfig
import com.androidservice.data.LogEntry
import com.androidservice.data.LogLevel
import com.androidservice.data.ServiceState
import com.androidservice.manager.AppConfigManager
import com.androidservice.singbox.DefaultNetworkMonitor
import com.androidservice.singbox.LibboxStringIterator
import com.androidservice.singbox.SingBoxConstants
import com.androidservice.singbox.SingBoxPlatform
import com.androidservice.singbox.toIpPrefix
import com.androidservice.singbox.toList
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SingBoxVpnService : VpnService() {

    inner class SingBoxBinder : Binder() {
        fun getService(): SingBoxVpnService = this@SingBoxVpnService
    }

    private val binder = SingBoxBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val appConfigManager by lazy { AppConfigManager(this) }
    private val platform = SingBoxVpnPlatform()

    private lateinit var commandServer: CommandServer
    private var tunInterface: ParcelFileDescriptor? = null
    private var currentConfig: BinaryConfig? = null
    private var manualStopRequested = false

    private val _serviceState = MutableStateFlow(ServiceState())
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    private val _logs = MutableSharedFlow<LogEntry>(extraBufferCapacity = 512)
    val logs: SharedFlow<LogEntry> = _logs.asSharedFlow()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> intent.readConfig()?.let { startSingBox(it) }
            ACTION_STOP -> stopSingBox()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onRevoke() {
        serviceScope.launch { stopSingBoxInternal(revoked = true) }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    fun startSingBox(config: BinaryConfig) {
        serviceScope.launch {
            if (_serviceState.value.isRunning) {
                emitLog(LogLevel.WARN, "sing-box 已在运行", "service")
                return@launch
            }
            currentConfig = config
            manualStopRequested = false
            startForeground()

            val configContent = withContext(Dispatchers.IO) {
                appConfigManager.loadConfigFile(config.configFileName)?.content
            }
            if (configContent.isNullOrBlank()) {
                emitLog(LogLevel.ERROR, "配置文件不存在或为空: ${config.configFileName}", "service")
                stopSingBoxInternal(revoked = false)
                return@launch
            }

            try {
                DefaultNetworkMonitor.start()
                commandServer = CommandServer(commandHandler, platform)
                commandServer.start()
                commandServer.startOrReloadService(
                    configContent,
                    OverrideOptions().apply {
                        excludePackage = LibboxStringIterator(listOf(packageName))
                    },
                )
                _serviceState.value = ServiceState(
                    isRunning = true,
                    binaryName = SingBoxConstants.BINARY_NAME,
                    startTime = System.currentTimeMillis(),
                )
                emitLog(LogLevel.INFO, "sing-box VPN 已启动", "service")
            } catch (e: Exception) {
                Log.e(TAG, "启动 sing-box 失败", e)
                emitLog(LogLevel.ERROR, "启动失败: ${e.message}", "service")
                stopSingBoxInternal(revoked = false)
            }
        }
    }

    fun stopSingBox() {
        serviceScope.launch { stopSingBoxInternal(revoked = false) }
    }

    private suspend fun stopSingBoxInternal(revoked: Boolean) {
        manualStopRequested = true
        runCatching {
            if (::commandServer.isInitialized) {
                commandServer.closeService()
                commandServer.close()
            }
        }
        tunInterface?.close()
        tunInterface = null
        DefaultNetworkMonitor.stop()
        currentConfig = null
        _serviceState.value = ServiceState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        val message = if (revoked) "VPN 权限已撤销" else "sing-box VPN 已停止"
        emitLog(LogLevel.INFO, message, "service")
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
            Intent(this, SingBoxVpnService::class.java).setAction(ACTION_STOP),
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

    private fun emitLog(level: LogLevel, message: String, source: String) {
        _logs.tryEmit(
            LogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                message = message,
                source = source,
            ),
        )
    }

    private fun Intent.readConfig(): BinaryConfig? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_BINARY_CONFIG, BinaryConfig::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(EXTRA_BINARY_CONFIG)
        }
    }

    private inner class SingBoxVpnPlatform : SingBoxPlatform() {
        override fun autoDetectInterfaceControl(fd: Int) {
            protect(fd)
        }

        override fun openTun(options: TunOptions): Int {
            if (prepare(this@SingBoxVpnService) != null) error("missing vpn permission")

            val builder = Builder()
                .setSession("sing-box")
                .setMtu(options.mtu)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            val inet4Address = options.inet4Address
            while (inet4Address.hasNext()) {
                val address = inet4Address.next()
                builder.addAddress(address.address(), address.prefix())
            }

            val inet6Address = options.inet6Address
            while (inet6Address.hasNext()) {
                val address = inet6Address.next()
                builder.addAddress(address.address(), address.prefix())
            }

            if (options.autoRoute) {
                if (options.dnsMode.value != Libbox.DNSModeDisabled) {
                    val dnsServerAddress = options.dnsServerAddress
                    while (dnsServerAddress.hasNext()) {
                        builder.addDnsServer(dnsServerAddress.next())
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val inet4RouteAddress = options.inet4RouteAddress
                    if (inet4RouteAddress.hasNext()) {
                        while (inet4RouteAddress.hasNext()) {
                            builder.addRoute(inet4RouteAddress.next().toIpPrefix())
                        }
                    } else if (options.inet4Address.hasNext()) {
                        builder.addRoute("0.0.0.0", 0)
                    }

                    val inet6RouteAddress = options.inet6RouteAddress
                    if (inet6RouteAddress.hasNext()) {
                        while (inet6RouteAddress.hasNext()) {
                            builder.addRoute(inet6RouteAddress.next().toIpPrefix())
                        }
                    } else if (options.inet6Address.hasNext()) {
                        builder.addRoute("::", 0)
                    }

                    val inet4RouteExcludeAddress = options.inet4RouteExcludeAddress
                    while (inet4RouteExcludeAddress.hasNext()) {
                        builder.excludeRoute(inet4RouteExcludeAddress.next().toIpPrefix())
                    }

                    val inet6RouteExcludeAddress = options.inet6RouteExcludeAddress
                    while (inet6RouteExcludeAddress.hasNext()) {
                        builder.excludeRoute(inet6RouteExcludeAddress.next().toIpPrefix())
                    }
                } else {
                    val inet4RouteAddress = options.inet4RouteRange
                    while (inet4RouteAddress.hasNext()) {
                        val address = inet4RouteAddress.next()
                        builder.addRoute(address.address(), address.prefix())
                    }

                    val inet6RouteAddress = options.inet6RouteRange
                    while (inet6RouteAddress.hasNext()) {
                        val address = inet6RouteAddress.next()
                        builder.addRoute(address.address(), address.prefix())
                    }
                }

                val includePackage = options.includePackage
                while (includePackage.hasNext()) {
                    builder.addAllowedApplication(includePackage.next())
                }

                val excludePackage = options.excludePackage
                while (excludePackage.hasNext()) {
                    builder.addDisallowedApplication(excludePackage.next())
                }
            }

            val pfd = builder.establish() ?: error("vpn establish failed")
            tunInterface?.close()
            tunInterface = pfd
            return pfd.fd
        }
    }

    private val commandHandler = object : CommandServerHandler {
        override fun serviceStop() {
            serviceScope.launch { stopSingBoxInternal(revoked = false) }
        }

        override fun serviceReload() {
            val config = currentConfig ?: return
            serviceScope.launch {
                val configContent = withContext(Dispatchers.IO) {
                    appConfigManager.loadConfigFile(config.configFileName)?.content
                } ?: return@launch
                runCatching {
                    commandServer.startOrReloadService(
                        configContent,
                        OverrideOptions().apply {
                            excludePackage = LibboxStringIterator(listOf(packageName))
                        },
                    )
                }.onFailure {
                    emitLog(LogLevel.ERROR, "重载失败: ${it.message}", "service")
                }
            }
        }

        override fun getSystemProxyStatus(): SystemProxyStatus? = null

        override fun setSystemProxyEnabled(isEnabled: Boolean) = Unit

        override fun triggerNativeCrash() = Unit

        override fun writeDebugMessage(message: String?) {
            if (!message.isNullOrBlank()) {
                emitLog(LogLevel.DEBUG, message, "sing-box")
            }
        }

        override fun connectSSHAgent(): Int = -1
    }

    companion object {
        private const val TAG = "SingBoxVpnService"
        const val ACTION_START = "com.androidservice.START_SINGBOX"
        const val ACTION_STOP = "com.androidservice.STOP_SINGBOX"
        const val EXTRA_BINARY_CONFIG = "binary_config"
        private const val STOP_REQUEST_CODE = 2002

        fun usesSingBox(config: BinaryConfig): Boolean =
            config.binaryName == SingBoxConstants.BINARY_NAME

        fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)
    }
}
