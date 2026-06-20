package com.androidservice.singbox

import android.util.Log
import com.androidservice.data.BinaryConfig
import com.androidservice.data.LogEntry
import com.androidservice.data.LogLevel
import com.androidservice.data.ServiceState
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SystemProxyStatus
import com.androidservice.data.SingBoxTrafficStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SingBoxRuntime(
    private val scope: CoroutineScope,
    private val platform: PlatformInterface,
    private val packageName: String,
    private val loadConfigContent: suspend (BinaryConfig) -> String?,
    private val stopForegroundAndSelf: () -> Unit,
    private val onRelease: suspend () -> Unit = {},
) {
    private val _serviceState = MutableStateFlow(ServiceState())
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    private val _logs = MutableSharedFlow<LogEntry>(extraBufferCapacity = 512)
    val logs: SharedFlow<LogEntry> = _logs.asSharedFlow()

    private val _trafficStats = MutableStateFlow(SingBoxTrafficStats())
    val trafficStats: StateFlow<SingBoxTrafficStats> = _trafficStats.asStateFlow()

    private var commandServer: CommandServer? = null
    private var commandClient: CommandClient? = null
    private var currentConfig: BinaryConfig? = null

    fun start(config: BinaryConfig, successMessage: String) {
        scope.launch {
            if (_serviceState.value.isRunning) {
                emitLog(LogLevel.WARN, "sing-box 已在运行", "service")
                return@launch
            }
            currentConfig = config
            val content = loadConfigContent(config)
            if (content.isNullOrBlank()) {
                emitLog(LogLevel.ERROR, "配置文件不存在或为空: ${config.configFileName}", "service")
                stopInternal("sing-box 已停止")
                return@launch
            }

            try {
                DefaultNetworkMonitor.start()
                commandServer = CommandServer(createHandler(), platform).also { it.start() }
                commandServer!!.startOrReloadService(content, overrideOptions())
                startTrafficMonitor()
                _serviceState.value = ServiceState(
                    isRunning = true,
                    binaryName = SingBoxConstants.BINARY_NAME,
                    startTime = System.currentTimeMillis(),
                )
                emitLog(LogLevel.INFO, successMessage, "service")
            } catch (e: Exception) {
                Log.e(TAG, "启动 sing-box 失败", e)
                emitLog(LogLevel.ERROR, "启动失败: ${e.message}", "service")
                stopInternal("sing-box 已停止")
            }
        }
    }

    fun stop(message: String) {
        scope.launch { stopInternal(message) }
    }

    private suspend fun stopInternal(message: String) {
        stopTrafficMonitor()
        runCatching {
            commandServer?.closeService()
            commandServer?.close()
        }
        commandServer = null
        onRelease()
        DefaultNetworkMonitor.stop()
        currentConfig = null
        _serviceState.value = ServiceState()
        stopForegroundAndSelf()
        emitLog(LogLevel.INFO, message, "service")
    }

    private fun startTrafficMonitor() {
        stopTrafficMonitor()
        val handler = SingBoxTrafficHandler { message ->
            val next = message.toTrafficStats()
            if (next != _trafficStats.value) {
                _trafficStats.value = next
            }
        }
        val options = CommandClientOptions().apply {
            addCommand(Libbox.CommandStatus)
            statusInterval = TRAFFIC_STATUS_INTERVAL_NS
        }
        val client = Libbox.newCommandClient(handler, options)
        commandClient = client
        scope.launch(Dispatchers.IO) {
            runCatching { client.connect() }
                .onFailure { Log.w(TAG, "流量统计订阅失败", it) }
        }
    }

    private fun stopTrafficMonitor() {
        commandClient?.let { client ->
            runCatching { client.disconnect() }
        }
        commandClient = null
        _trafficStats.value = SingBoxTrafficStats()
    }

    private fun overrideOptions() = OverrideOptions().apply {
        excludePackage = LibboxStringIterator(listOf(packageName))
    }

    private fun createHandler() = object : CommandServerHandler {
        override fun serviceStop() {
            scope.launch { stopInternal("sing-box 已停止") }
        }

        override fun serviceReload() {
            val config = currentConfig ?: return
            scope.launch {
                val content = loadConfigContent(config) ?: return@launch
                runCatching {
                    commandServer?.startOrReloadService(content, overrideOptions())
                }.onFailure {
                    emitLog(LogLevel.ERROR, "重载失败: ${it.message}", "service")
                }
            }
        }

        override fun getSystemProxyStatus(): SystemProxyStatus? = null

        override fun setSystemProxyEnabled(isEnabled: Boolean) = Unit

        override fun triggerNativeCrash() = Unit

        override fun writeDebugMessage(message: String?) {
            if (!message.isNullOrBlank()) emitLog(LogLevel.DEBUG, message, "sing-box")
        }

        override fun connectSSHAgent(): Int = -1
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

    companion object {
        private const val TAG = "SingBoxRuntime"
        private const val TRAFFIC_STATUS_INTERVAL_NS = 1_000_000_000L
    }
}
