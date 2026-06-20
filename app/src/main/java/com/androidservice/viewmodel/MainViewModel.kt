package com.androidservice.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidservice.data.AppConfigFile
import com.androidservice.data.BinaryConfig
import com.androidservice.data.BinaryInfo
import com.androidservice.data.LogEntry
import com.androidservice.data.LogLevel
import com.androidservice.data.ServiceState
import com.androidservice.data.ServiceStatus
import com.androidservice.manager.AppConfigManager
import com.androidservice.manager.BinaryManager
import com.androidservice.manager.ConfigManager
import com.androidservice.service.BinaryProcessService
import com.androidservice.service.SingBoxVpnService
import com.androidservice.singbox.SingBoxConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = getApplication<Application>()
    private val binaryManager = BinaryManager(context)
    private val configManager = ConfigManager(context)
    private val appConfigManager = AppConfigManager(context)

    private var boundBinaryService: BinaryProcessService? = null
    private var boundSingBoxService: SingBoxVpnService? = null
    private var binaryBound = false
    private var singBoxBound = false
    private var stateJob: Job? = null
    private var logJob: Job? = null
    private var activeServiceKind: ServiceKind? = null

    private val logDeque = ArrayDeque<LogEntry>(MAX_LOG_ENTRIES)
    private var nextLogId = 0L
    private var pendingLogUpdate = false

    private val _editingFileName = MutableStateFlow<String?>(null)
    val editingFileName = _editingFileName.asStateFlow()

    private val _serviceState = MutableStateFlow(ServiceState())
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    private val _serviceStatus = MutableStateFlow(ServiceStatus.STOPPED)
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    private val _binaryList = MutableStateFlow<List<BinaryInfo>>(emptyList())
    val binaryList: StateFlow<List<BinaryInfo>> = _binaryList.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _logCount = MutableStateFlow(0)
    val logCount: StateFlow<Int> = _logCount.asStateFlow()

    private val _currentConfig = MutableStateFlow(BinaryConfig())
    val currentConfig: StateFlow<BinaryConfig> = _currentConfig.asStateFlow()

    private val _appConfigFiles = MutableStateFlow<List<AppConfigFile>>(emptyList())
    val appConfigFiles: StateFlow<List<AppConfigFile>> = _appConfigFiles.asStateFlow()

    private val _availableBinaryNames = MutableStateFlow<List<String>>(emptyList())
    val availableBinaryNames: StateFlow<List<String>> = _availableBinaryNames.asStateFlow()

    private val _vpnPermissionIntent = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val vpnPermissionIntent: SharedFlow<Intent> = _vpnPermissionIntent.asSharedFlow()

    private val binaryServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            boundBinaryService = (service as BinaryProcessService.BinaryProcessBinder).getService()
            binaryBound = true
            if (activeServiceKind == ServiceKind.BINARY) observeActiveService()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (activeServiceKind == ServiceKind.BINARY) clearServiceObservation(ServiceStatus.STOPPED)
            boundBinaryService = null
            binaryBound = false
        }
    }

    private val singBoxServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            boundSingBoxService = (service as SingBoxVpnService.SingBoxBinder).getService()
            singBoxBound = true
            if (activeServiceKind == ServiceKind.SINGBOX) observeActiveService()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (activeServiceKind == ServiceKind.SINGBOX) clearServiceObservation(ServiceStatus.STOPPED)
            boundSingBoxService = null
            singBoxBound = false
        }
    }

    init {
        bindServices()
        loadBinaryList()
        loadInitialConfig()
        refreshAppConfigFiles()
    }

    fun setEditingFileName(fileName: String?) {
        _editingFileName.value = fileName
    }

    fun startService() {
        if (_serviceStatus.value == ServiceStatus.RUNNING) {
            addLog(LogLevel.WARN, "服务已经在运行")
            return
        }

        val config = _currentConfig.value
        if (config.binaryName.isBlank()) {
            addLog(LogLevel.ERROR, "请先选择程序")
            _serviceStatus.value = ServiceStatus.ERROR
            return
        }

        if (SingBoxVpnService.usesSingBox(config)) {
            startSingBoxService(config)
        } else {
            startBinaryService(config)
        }
    }

    fun onVpnPermissionGranted() {
        val config = _currentConfig.value
        if (!SingBoxVpnService.usesSingBox(config)) return
        launchSingBoxService(config)
    }

    fun onVpnPermissionDenied() {
        _serviceStatus.value = ServiceStatus.ERROR
        addLog(LogLevel.ERROR, "未授予 VPN 权限")
    }

    fun stopService() {
        if (_serviceStatus.value == ServiceStatus.STOPPED) {
            addLog(LogLevel.WARN, "服务未运行")
            return
        }

        _serviceStatus.value = ServiceStatus.STOPPING
        when (activeServiceKind) {
            ServiceKind.SINGBOX -> {
                context.startService(
                    Intent(context, SingBoxVpnService::class.java).apply {
                        action = SingBoxVpnService.ACTION_STOP
                    },
                )
                addLog(LogLevel.INFO, "正在停止 sing-box VPN...")
            }
            ServiceKind.BINARY, null -> {
                context.startService(
                    Intent(context, BinaryProcessService::class.java).apply {
                        action = BinaryProcessService.ACTION_STOP_BINARY
                    },
                )
                addLog(LogLevel.INFO, "正在停止服务...")
            }
        }
    }

    fun updateConfig(config: BinaryConfig) {
        _currentConfig.value = config
        viewModelScope.launch {
            runCatching { configManager.saveConfig(config) }
                .onFailure { addLog(LogLevel.ERROR, "保存配置失败: ${it.message}") }
        }
    }

    fun saveConfig() {
        viewModelScope.launch {
            val success = configManager.saveConfigToFile(_currentConfig.value)
            if (success) {
                addLog(LogLevel.INFO, "配置已保存: ${configManager.getConfigFilePath()}")
            } else {
                addLog(LogLevel.ERROR, "保存配置文件失败")
            }
        }
    }

    fun loadConfig() {
        viewModelScope.launch {
            val config = configManager.loadConfigFromFile()
            if (config == null) {
                addLog(LogLevel.WARN, "配置文件不存在或格式错误")
                return@launch
            }

            _currentConfig.value = config
            configManager.saveConfig(config)
            addLog(LogLevel.INFO, "配置已加载")
        }
    }

    fun importConfig(jsonString: String) {
        viewModelScope.launch {
            val config = configManager.importConfigFromJson(jsonString)
            if (config == null) {
                addLog(LogLevel.ERROR, "配置格式错误，导入失败")
                return@launch
            }

            updateConfig(config)
            addLog(LogLevel.INFO, "配置导入成功")
        }
    }

    fun resetConfig() {
        viewModelScope.launch {
            configManager.clearConfig()
            _currentConfig.value = BinaryConfig()
            addLog(LogLevel.INFO, "配置已重置")
        }
    }

    fun loadAvailableBinaryNames() {
        viewModelScope.launch {
            val names = (binaryManager.getAvailableKernelNames() + SingBoxConstants.BINARY_NAME)
                .distinct()
                .sorted()
            _availableBinaryNames.value = names
            if (_currentConfig.value.binaryName.isBlank() && names.isNotEmpty()) {
                updateConfig(_currentConfig.value.copy(binaryName = names.first()))
            }
        }
    }

    fun deleteAppConfigFile(fileName: String) {
        if (fileName.isBlank()) {
            addLog(LogLevel.ERROR, "文件名不能为空")
            return
        }

        viewModelScope.launch {
            val success = appConfigManager.deleteConfigFile(fileName)
            if (success) {
                _appConfigFiles.value = _appConfigFiles.value.filterNot { it.fileName == fileName }
                addLog(LogLevel.INFO, "已删除配置文件: $fileName")
            } else {
                addLog(LogLevel.ERROR, "删除配置文件失败: $fileName")
            }
        }
    }

    suspend fun saveAppConfigFile(appConfigFile: AppConfigFile): Boolean {
        return withContext(Dispatchers.IO) {
            val success = appConfigManager.saveConfigFile(appConfigFile)
            withContext(Dispatchers.Main) {
                if (success) {
                    refreshAppConfigFiles()
                    addLog(LogLevel.INFO, "已保存配置文件: ${appConfigFile.fileName}")
                } else {
                    addLog(LogLevel.ERROR, "保存配置文件失败: ${appConfigFile.fileName}")
                }
            }
            success
        }
    }

    suspend fun loadAppConfigFile(fileName: String): AppConfigFile? {
        return appConfigManager.loadConfigFile(fileName).also {
            if (it == null) addLog(LogLevel.WARN, "配置文件不存在: $fileName")
        }
    }

    fun getAppConfigFilePath(fileName: String): String = appConfigManager.getConfigFilePath(fileName)

    fun requestIgnoreBatteryOptimizations() {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val packageName = context.packageName
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            addLog(LogLevel.INFO, "已允许不受电池优化限制")
            return
        }

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:$packageName".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
    }

    private fun startSingBoxService(config: BinaryConfig) {
        if (config.configFileName.isBlank()) {
            addLog(LogLevel.ERROR, "请先选择 sing-box 配置文件")
            _serviceStatus.value = ServiceStatus.ERROR
            return
        }

        val prepareIntent = SingBoxVpnService.prepareIntent(context)
        if (prepareIntent != null) {
            _serviceStatus.value = ServiceStatus.STARTING
            viewModelScope.launch { _vpnPermissionIntent.emit(prepareIntent) }
            return
        }

        launchSingBoxService(config)
    }

    private fun launchSingBoxService(config: BinaryConfig) {
        activeServiceKind = ServiceKind.SINGBOX
        _serviceStatus.value = ServiceStatus.STARTING
        val intent = Intent(context, SingBoxVpnService::class.java).apply {
            action = SingBoxVpnService.ACTION_START
            putExtra(SingBoxVpnService.EXTRA_BINARY_CONFIG, config)
        }
        context.startForegroundService(intent)
        addLog(LogLevel.INFO, "正在启动 sing-box VPN...")
        observeActiveService()
    }

    private fun startBinaryService(config: BinaryConfig) {
        activeServiceKind = ServiceKind.BINARY
        _serviceStatus.value = ServiceStatus.STARTING
        val intent = Intent(context, BinaryProcessService::class.java).apply {
            action = BinaryProcessService.ACTION_START_BINARY
            putExtra(BinaryProcessService.EXTRA_BINARY_CONFIG, config)
        }
        context.startForegroundService(intent)
        addLog(LogLevel.INFO, "正在启动服务...")
        observeActiveService()
    }

    private fun bindServices() {
        context.bindService(
            Intent(context, BinaryProcessService::class.java),
            binaryServiceConnection,
            Context.BIND_AUTO_CREATE,
        )
        context.bindService(
            Intent(context, SingBoxVpnService::class.java),
            singBoxServiceConnection,
            Context.BIND_AUTO_CREATE,
        )
    }

    private fun observeActiveService() {
        stateJob?.cancel()
        logJob?.cancel()

        when (activeServiceKind) {
            ServiceKind.SINGBOX -> {
                val service = boundSingBoxService ?: return
                stateJob = viewModelScope.launch {
                    service.serviceState.collectLatest { state ->
                        _serviceState.value = state
                        _serviceStatus.value = if (state.isRunning) ServiceStatus.RUNNING else ServiceStatus.STOPPED
                    }
                }
                logJob = viewModelScope.launch {
                    service.logs.collect { appendLog(it) }
                }
            }
            ServiceKind.BINARY -> {
                val service = boundBinaryService ?: return
                stateJob = viewModelScope.launch {
                    service.serviceState.collectLatest { state ->
                        _serviceState.value = state
                        _serviceStatus.value = if (state.isRunning) ServiceStatus.RUNNING else ServiceStatus.STOPPED
                    }
                }
                logJob = viewModelScope.launch {
                    service.logs.collect { appendLog(it) }
                }
            }
            null -> Unit
        }
    }

    private fun clearServiceObservation(status: ServiceStatus) {
        stateJob?.cancel()
        logJob?.cancel()
        activeServiceKind = null
        _serviceStatus.value = status
    }

    private fun loadBinaryList() {
        viewModelScope.launch {
            val binaries = binaryManager.initializeBinaries()
            _binaryList.value = binaries
            _availableBinaryNames.value = (binaries.map { it.name } + SingBoxConstants.BINARY_NAME)
                .distinct()
                .sorted()

            if (_currentConfig.value.binaryName.isBlank() && _availableBinaryNames.value.isNotEmpty()) {
                _currentConfig.value = _currentConfig.value.copy(binaryName = _availableBinaryNames.value.first())
            }
        }
    }

    private fun loadInitialConfig() {
        viewModelScope.launch {
            configManager.configFlow.collectLatest { config ->
                if (config != _currentConfig.value) {
                    _currentConfig.value = config
                }
            }
        }
    }

    private fun refreshAppConfigFiles() {
        viewModelScope.launch {
            _appConfigFiles.value = appConfigManager.getAllConfigFiles()
        }
    }

    private fun addLog(level: LogLevel, message: String) {
        appendLog(
            LogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                message = message,
                source = "app",
            ),
        )
    }

    private fun appendLog(log: LogEntry) {
        synchronized(logDeque) {
            if (logDeque.size >= MAX_LOG_ENTRIES) logDeque.removeFirst()
            logDeque.addLast(log.copy(id = nextLogId++))

            if (!pendingLogUpdate) {
                pendingLogUpdate = true
                viewModelScope.launch {
                    delay(LOG_UPDATE_DELAY_MS)
                    synchronized(logDeque) {
                        _logs.value = logDeque.toList()
                        _logCount.value = logDeque.size
                        pendingLogUpdate = false
                    }
                }
            }
        }
    }

    override fun onCleared() {
        stateJob?.cancel()
        logJob?.cancel()
        if (binaryBound) {
            runCatching { context.unbindService(binaryServiceConnection) }
                .onFailure { Log.w(TAG, "二进制服务已经解除绑定", it) }
        }
        if (singBoxBound) {
            runCatching { context.unbindService(singBoxServiceConnection) }
                .onFailure { Log.w(TAG, "sing-box 服务已经解除绑定", it) }
        }
        boundBinaryService = null
        boundSingBoxService = null
        super.onCleared()
    }

    private enum class ServiceKind {
        BINARY,
        SINGBOX,
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val MAX_LOG_ENTRIES = 500
        private const val LOG_UPDATE_DELAY_MS = 200L
    }
}
