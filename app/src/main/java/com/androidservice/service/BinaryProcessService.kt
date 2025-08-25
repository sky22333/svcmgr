package com.androidservice.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.androidservice.AndroidServiceApplication
import com.androidservice.R
import com.androidservice.data.BinaryConfig
import com.androidservice.data.LogEntry
import com.androidservice.data.ServiceState
import com.androidservice.manager.BinaryManager
import com.androidservice.manager.ProcessManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class BinaryProcessService : Service() {

    companion object {
        const val ACTION_START_BINARY = "com.androidservice.START_BINARY"
        const val ACTION_STOP_BINARY = "com.androidservice.STOP_BINARY"
        const val ACTION_RESTART_BINARY = "com.androidservice.RESTART_BINARY"
        
        const val EXTRA_BINARY_CONFIG = "binary_config"
    }

    private val binder = BinaryProcessBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private lateinit var binaryManager: BinaryManager
    private lateinit var processManager: ProcessManager
    
    private val _serviceState = MutableStateFlow(ServiceState())
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()
    
    private val _logs = MutableSharedFlow<LogEntry>(extraBufferCapacity = 1000)
    val logs: SharedFlow<LogEntry> = _logs.asSharedFlow()
    
    private var currentConfig: BinaryConfig? = null
    private var restartAttempts = 0
    private var isAutoRestarting = false

    inner class BinaryProcessBinder : Binder() {
        fun getService(): BinaryProcessService = this@BinaryProcessService
    }

    override fun onCreate() {
        super.onCreate()
        
        binaryManager = BinaryManager(this)
        processManager = ProcessManager()
        
        // 监听进程状态变化
        serviceScope.launch {
            processManager.processState.collect { processState ->
                val currentState = _serviceState.value
                _serviceState.value = currentState.copy(
                    isRunning = processState.isRunning,
                    processId = processState.pid,
                    startTime = processState.startTime
                )
                
                // 更新通知
                updateNotification(processState.isRunning)
                
                // 处理进程退出
                if (!processState.isRunning && processState.exitCode != null) {
                    handleProcessExit(processState.exitCode)
                }
            }
        }
        
        // 转发进程日志
        serviceScope.launch {
            processManager.processLogs.collect { logEntry ->
                _logs.emit(logEntry)
            }
        }
        
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BINARY -> {
                val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_BINARY_CONFIG, BinaryConfig::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_BINARY_CONFIG)
                }
                config?.let { startBinary(it) }
            }
            ACTION_STOP_BINARY -> {
                stopBinary()
            }
            ACTION_RESTART_BINARY -> {
                currentConfig?.let { restartBinary(it) }
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AndroidServiceApplication.NOTIFICATION_CHANNEL_ID,
                AndroidServiceApplication.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(isRunning: Boolean): Notification {
        val notificationTitle = getString(R.string.service_notification_title)
        val notificationText = if (isRunning) {
            "二进制进程正在运行"
        } else {
            getString(R.string.service_notification_text)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AndroidServiceApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startForegroundService() {
        startForeground(AndroidServiceApplication.NOTIFICATION_ID, createNotification(false))
    }

    private fun updateNotification(isRunning: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(AndroidServiceApplication.NOTIFICATION_ID, createNotification(isRunning))
    }

    fun startBinary(config: BinaryConfig) {
        serviceScope.launch {
            try {
                if (processManager.isProcessRunning()) {
                    _logs.emit(LogEntry(
                        timestamp = System.currentTimeMillis(),
                        level = com.androidservice.data.LogLevel.WARN,
                        message = "进程已经在运行中",
                        source = "service"
                    ))
                    return@launch
                }

                currentConfig = config
                restartAttempts = 0
                isAutoRestarting = false

                startForegroundService()

                // 获取二进制文件路径
                val binary = binaryManager.getPreferredBinary(config.binaryName)
                if (binary == null) {
                    _logs.emit(LogEntry(
                        timestamp = System.currentTimeMillis(),
                        level = com.androidservice.data.LogLevel.ERROR,
                        message = "未找到可用的二进制文件: ${config.binaryName}",
                        source = "service"
                    ))
                    return@launch
                }

                val success = processManager.startProcess(binary.path, config, serviceScope)
                if (success) {
                    _serviceState.value = _serviceState.value.copy(
                        binaryName = config.binaryName
                    )
                    _logs.emit(LogEntry(
                        timestamp = System.currentTimeMillis(),
                        level = com.androidservice.data.LogLevel.INFO,
                        message = "服务启动成功",
                        source = "service"
                    ))
                } else {
                    _logs.emit(LogEntry(
                        timestamp = System.currentTimeMillis(),
                        level = com.androidservice.data.LogLevel.ERROR,
                        message = "服务启动失败",
                        source = "service"
                    ))
                    stopSelf()
                }
            } catch (e: Exception) {
                _logs.emit(LogEntry(
                    timestamp = System.currentTimeMillis(),
                    level = com.androidservice.data.LogLevel.ERROR,
                    message = "启动服务异常: ${e.message}",
                    source = "service"
                ))
                stopSelf()
            }
        }
    }

    fun stopBinary() {
        serviceScope.launch {
            isAutoRestarting = false
            val success = processManager.stopProcess()
            if (success) {
                _logs.emit(LogEntry(
                    timestamp = System.currentTimeMillis(),
                    level = com.androidservice.data.LogLevel.INFO,
                    message = "服务停止成功",
                    source = "service"
                ))
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun restartBinary(config: BinaryConfig) {
        serviceScope.launch {
            isAutoRestarting = true
            processManager.stopProcess()
            
            delay(config.restartDelay)
            
            if (isAutoRestarting) {
                startBinary(config)
            }
        }
    }

    private fun handleProcessExit(exitCode: Int) {
        serviceScope.launch {
            val config = currentConfig
            if (config != null && config.autoRestart && !isAutoRestarting) {
                restartAttempts++
                
                if (config.maxRestarts == -1 || restartAttempts <= config.maxRestarts) {
                    _logs.emit(LogEntry(
                        timestamp = System.currentTimeMillis(),
                        level = com.androidservice.data.LogLevel.WARN,
                        message = "进程异常退出 (退出码: $exitCode)，${config.restartDelay / 1000}秒后自动重启 (第${restartAttempts}次重启)",
                        source = "service"
                    ))
                    
                    _serviceState.value = _serviceState.value.copy(
                        restartCount = restartAttempts
                    )
                    
                    delay(config.restartDelay)
                    restartBinary(config)
                } else {
                    _logs.emit(LogEntry(
                        timestamp = System.currentTimeMillis(),
                        level = com.androidservice.data.LogLevel.ERROR,
                        message = "已达到最大重启次数 (${config.maxRestarts})，停止自动重启",
                        source = "service"
                    ))
                    stopSelf()
                }
            } else {
                stopSelf()
            }
        }
    }

    fun getBinaryManager(): BinaryManager = binaryManager

    fun getCurrentState(): ServiceState = _serviceState.value

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            processManager.stopProcess()
            processManager.destroy()
        }
        serviceScope.cancel()
    }
}