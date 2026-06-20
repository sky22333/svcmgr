package com.androidservice.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
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
import com.androidservice.data.LogEntry
import com.androidservice.data.LogLevel
import com.androidservice.data.ServiceState
import com.androidservice.manager.BinaryManager
import com.androidservice.manager.ProcessManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BinaryProcessService : Service() {

    inner class BinaryProcessBinder : Binder() {
        fun getService(): BinaryProcessService = this@BinaryProcessService
    }

    private val binder = BinaryProcessBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var binaryManager: BinaryManager
    private lateinit var processManager: ProcessManager

    private val _serviceState = MutableStateFlow(ServiceState())
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    private val _logs = MutableSharedFlow<LogEntry>(extraBufferCapacity = 512)
    val logs: SharedFlow<LogEntry> = _logs.asSharedFlow()

    private var currentConfig: BinaryConfig? = null
    private var restartAttempts = 0
    private var manualStopRequested = false
    private var notifiedRunning: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        binaryManager = BinaryManager(this)
        processManager = ProcessManager()
        createNotificationChannel()
        observeProcess()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BINARY -> intent.readConfig()?.let { startBinary(it) }
            ACTION_STOP_BINARY -> stopBinary()
            ACTION_RESTART_BINARY -> currentConfig?.let {
                restartAttempts = 0
                restartBinary(it)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    fun startBinary(config: BinaryConfig, resetRestartAttempts: Boolean = true) {
        serviceScope.launch {
            if (processManager.isProcessRunning()) {
                log(LogLevel.WARN, "进程已经在运行")
                return@launch
            }

            currentConfig = config
            if (resetRestartAttempts) restartAttempts = 0
            manualStopRequested = false
            startAsForeground(false)

            val binary = binaryManager.getPreferredBinary(config.binaryName)
            if (binary == null) {
                log(LogLevel.ERROR, "未找到可用程序: ${config.binaryName}")
                stopSelf()
                return@launch
            }

            val success = processManager.startProcess(binary.path, config, serviceScope)
            if (success) {
                _serviceState.value = _serviceState.value.copy(binaryName = config.binaryName)
                log(LogLevel.INFO, "服务启动成功")
            } else {
                log(LogLevel.ERROR, "服务启动失败")
                stopSelf()
            }
        }
    }

    fun stopBinary() {
        serviceScope.launch {
            manualStopRequested = true
            processManager.stopProcess(emitState = false)
            currentConfig = null
            restartAttempts = 0
            _serviceState.value = ServiceState()
            log(LogLevel.INFO, "服务已停止")
            stopForeground(STOP_FOREGROUND_REMOVE)
            notifiedRunning = null
            stopSelf()
        }
    }

    override fun onDestroy() {
        processManager.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeProcess() {
        serviceScope.launch {
            processManager.processState.collect { state ->
                val nextState = _serviceState.value.copy(
                    isRunning = state.isRunning,
                    processId = state.pid,
                    startTime = state.startTime
                )
                if (nextState != _serviceState.value) {
                    _serviceState.value = nextState
                }
                if (!manualStopRequested) {
                    updateNotificationIfNeeded(state.isRunning)
                }

                if (!manualStopRequested && !state.isRunning && state.exitCode != null) {
                    handleProcessExit(state.exitCode)
                }
            }
        }

        serviceScope.launch {
            processManager.processLogs.collect { _logs.emit(it) }
        }
    }

    private fun restartBinary(config: BinaryConfig, delayMillis: Long = config.restartDelay) {
        serviceScope.launch {
            processManager.stopProcess()
            delay(delayMillis)
            if (!manualStopRequested) startBinary(config, resetRestartAttempts = false)
        }
    }

    private fun handleProcessExit(exitCode: Int) {
        val config = currentConfig ?: run {
            stopSelf()
            return
        }
        if (manualStopRequested || !config.autoRestart) {
            log(LogLevel.INFO, "进程已退出，退出码: $exitCode")
            stopSelf()
            return
        }

        restartAttempts += 1
        if (config.maxRestarts != -1 && restartAttempts > config.maxRestarts) {
            log(LogLevel.ERROR, "已达到最大重启次数: ${config.maxRestarts}")
            stopSelf()
            return
        }

        _serviceState.value = _serviceState.value.copy(restartCount = restartAttempts)
        val delayMillis = restartDelay(config)
        log(LogLevel.WARN, "进程异常退出，退出码: $exitCode，${delayMillis / 1000} 秒后自动重启")
        restartBinary(config, delayMillis)
    }

    private fun restartDelay(config: BinaryConfig): Long {
        return (config.restartDelay * restartAttempts.toLong()).coerceAtMost(MAX_RESTART_DELAY_MS)
    }

    private fun startAsForeground(isRunning: Boolean) {
        ServiceCompat.startForeground(
            this,
            AndroidServiceApplication.NOTIFICATION_ID,
            createNotification(isRunning),
            foregroundServiceType()
        )
        notifiedRunning = isRunning
    }

    private fun foregroundServiceType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            AndroidServiceApplication.NOTIFICATION_CHANNEL_ID,
            AndroidServiceApplication.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(isRunning: Boolean): Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            Intent(this, BinaryProcessService::class.java).setAction(ACTION_STOP_BINARY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AndroidServiceApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(if (isRunning) "程序进程正在运行" else getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentIntent(launchIntent)
            .setOngoing(isRunning)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_notification_stop, "停止", stopIntent)
            .build()
    }

    private fun updateNotificationIfNeeded(isRunning: Boolean) {
        if (notifiedRunning == isRunning) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(AndroidServiceApplication.NOTIFICATION_ID, createNotification(isRunning))
        notifiedRunning = isRunning
    }

    private fun log(level: LogLevel, message: String) {
        _logs.tryEmit(
            LogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                message = message,
                source = "service"
            )
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

    companion object {
        const val ACTION_START_BINARY = "com.androidservice.START_BINARY"
        const val ACTION_STOP_BINARY = "com.androidservice.STOP_BINARY"
        const val ACTION_RESTART_BINARY = "com.androidservice.RESTART_BINARY"
        const val EXTRA_BINARY_CONFIG = "binary_config"

        private const val STOP_REQUEST_CODE = 1002
        private const val MAX_RESTART_DELAY_MS = 60_000L
    }
}
