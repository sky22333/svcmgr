package com.androidservice.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
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
import com.androidservice.manager.RemoteConfigRefreshResult
import com.androidservice.manager.BinaryManager
import com.androidservice.manager.ConfigManager
import com.androidservice.service.BinaryProcessService
import com.androidservice.service.SingBoxProxyService
import com.androidservice.service.SingBoxVpnService
import com.androidservice.singbox.SingBoxConfigInspector
import com.androidservice.singbox.SingBoxConstants
import com.androidservice.singbox.SingBoxRunMode
import com.androidservice.singbox.SingBoxServiceContract
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
    private var boundSingBoxVpnService: SingBoxVpnService? = null
    private var boundSingBoxProxyService: SingBoxProxyService? = null
    private var binaryBound = false
    private var singBoxVpnBound = false
    private var singBoxProxyBound = false
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

    private val _singBoxRunMode = MutableStateFlow<SingBoxRunMode?>(null)
    val singBoxRunMode: StateFlow<SingBoxRunMode?> = _singBoxRunMode.asStateFlow()

    private val _singBoxListenEndpoint = MutableStateFlow<String?>(null)
    val singBoxListenEndpoint: StateFlow<String?> = _singBoxListenEndpoint.asStateFlow()

    private val _serviceErrorMessage = MutableStateFlow<String?>(null)
    val serviceErrorMessage: StateFlow<String?> = _serviceErrorMessage.asStateFlow()

    private val _refreshingRemoteFiles = MutableStateFlow<Set<String>>(emptySet())
    val refreshingRemoteFiles: StateFlow<Set<String>> = _refreshingRemoteFiles.asStateFlow()

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

    private val singBoxVpnServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            boundSingBoxVpnService = (service as SingBoxVpnService.SingBoxBinder).getService()
            singBoxVpnBound = true
            if (activeServiceKind == ServiceKind.SINGBOX_VPN) observeActiveService()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (activeServiceKind == ServiceKind.SINGBOX_VPN) clearServiceObservation(ServiceStatus.STOPPED)
            boundSingBoxVpnService = null
            singBoxVpnBound = false
        }
    }

    private val singBoxProxyServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            boundSingBoxProxyService = (service as SingBoxProxyService.SingBoxBinder).getService()
            singBoxProxyBound = true
            if (activeServiceKind == ServiceKind.SINGBOX_PROXY) observeActiveService()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (activeServiceKind == ServiceKind.SINGBOX_PROXY) clearServiceObservation(ServiceStatus.STOPPED)
            boundSingBoxProxyService = null
            singBoxProxyBound = false
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

        clearServiceError()
        val config = _currentConfig.value
        if (config.binaryName.isBlank()) {
            setServiceError("请先选择程序")
            return
        }

        if (SingBoxServiceContract.usesSingBox(config)) {
            startSingBoxService(config)
        } else {
            startBinaryService(config)
        }
    }

    fun onVpnPermissionGranted() {
        val config = _currentConfig.value
        if (!SingBoxServiceContract.usesSingBox(config)) return
        launchSingBoxVpn(config)
    }

    fun onVpnPermissionDenied() {
        setServiceError("未授予 VPN 权限")
    }

    fun stopService() {
        when (_serviceStatus.value) {
            ServiceStatus.STOPPED -> {
                addLog(LogLevel.WARN, "服务未运行")
                return
            }
            ServiceStatus.STOPPING -> return
            ServiceStatus.STARTING, ServiceStatus.RUNNING, ServiceStatus.ERROR -> Unit
        }

        clearServiceError()
        _serviceStatus.value = ServiceStatus.STOPPING
        when (activeServiceKind) {
            ServiceKind.SINGBOX_VPN -> {
                context.startService(
                    Intent(context, SingBoxVpnService::class.java).apply {
                        action = SingBoxServiceContract.ACTION_STOP
                    },
                )
                addLog(LogLevel.INFO, "正在停止 sing-box VPN...")
            }
            ServiceKind.SINGBOX_PROXY -> {
                context.startService(
                    Intent(context, SingBoxProxyService::class.java).apply {
                        action = SingBoxServiceContract.ACTION_STOP
                    },
                )
                addLog(LogLevel.INFO, "正在停止 sing-box 代理...")
            }
            ServiceKind.BINARY, null -> {
                if (activeServiceKind == null && _serviceStatus.value == ServiceStatus.STARTING) {
                    clearServiceObservation(ServiceStatus.STOPPED)
                    addLog(LogLevel.INFO, "已取消启动")
                    return
                }
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
        refreshSingBoxRunMode()
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
            refreshSingBoxRunMode()
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
            _singBoxRunMode.value = null
            _singBoxListenEndpoint.value = null
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

    fun refreshSingBoxRunMode() {
        viewModelScope.launch {
            val config = _currentConfig.value
            if (!SingBoxServiceContract.usesSingBox(config) || config.configFileName.isBlank()) {
                _singBoxRunMode.value = null
                _singBoxListenEndpoint.value = null
                return@launch
            }
            val content = withContext(Dispatchers.IO) {
                appConfigManager.loadConfigFile(config.configFileName)?.content
            }
            val mode = when {
                content.isNullOrBlank() -> null
                SingBoxConfigInspector.hasTunInbound(content) -> SingBoxRunMode.VPN
                else -> SingBoxRunMode.PROXY
            }
            _singBoxRunMode.value = mode
            _singBoxListenEndpoint.value = if (mode == SingBoxRunMode.PROXY) {
                content?.let(SingBoxConfigInspector::extractListenEndpoint)
            } else {
                null
            }
        }
    }

    fun clearLogs() {
        synchronized(logDeque) {
            logDeque.clear()
            _logs.value = emptyList()
            _logCount.value = 0
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
                refreshSingBoxRunMode()
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
                    refreshSingBoxRunMode()
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

    suspend fun refreshRemoteConfigFile(fileName: String): RemoteConfigRefreshResult {
        val safeName = fileName.trim()
        if (safeName.isBlank()) {
            return RemoteConfigRefreshResult.Failure("文件名不能为空")
        }

        _refreshingRemoteFiles.value += safeName
        return try {
            appConfigManager.refreshFromRemote(safeName).also { result ->
                when (result) {
                    is RemoteConfigRefreshResult.Success -> {
                        refreshAppConfigFiles()
                        refreshSingBoxRunMode()
                        addLog(LogLevel.INFO, "已更新远程配置: $safeName")
                    }
                    is RemoteConfigRefreshResult.Failure -> {
                        addLog(LogLevel.ERROR, "更新远程配置失败: ${result.message}")
                    }
                }
            }
        } finally {
            _refreshingRemoteFiles.value -= safeName
        }
    }

    suspend fun fetchRemoteConfigPreview(url: String): RemoteConfigRefreshResult {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            return RemoteConfigRefreshResult.Failure("远程 URL 不能为空")
        }
        return appConfigManager.fetchRemotePreview(trimmedUrl).also { result ->
            if (result is RemoteConfigRefreshResult.Failure) {
                addLog(LogLevel.ERROR, "拉取远程配置失败: ${result.message}")
            }
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
            setServiceError("请先选择 sing-box 配置文件")
            return
        }

        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                appConfigManager.loadConfigFile(config.configFileName)?.content
            }
            if (content.isNullOrBlank()) {
                setServiceError("配置文件不存在或为空: ${config.configFileName}")
                return@launch
            }

            if (SingBoxConfigInspector.hasTunInbound(content)) {
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent != null) {
                    _serviceStatus.value = ServiceStatus.STARTING
                    _vpnPermissionIntent.emit(prepareIntent)
                    return@launch
                }
                launchSingBoxVpn(config)
            } else {
                launchSingBoxProxy(config)
            }
        }
    }

    private fun launchSingBoxVpn(config: BinaryConfig) {
        activeServiceKind = ServiceKind.SINGBOX_VPN
        _serviceStatus.value = ServiceStatus.STARTING
        val intent = Intent(context, SingBoxVpnService::class.java).apply {
            action = SingBoxServiceContract.ACTION_START
            putExtra(SingBoxServiceContract.EXTRA_BINARY_CONFIG, config)
        }
        context.startForegroundService(intent)
        addLog(LogLevel.INFO, "正在启动 sing-box VPN...")
        observeActiveService()
    }

    private fun launchSingBoxProxy(config: BinaryConfig) {
        activeServiceKind = ServiceKind.SINGBOX_PROXY
        _serviceStatus.value = ServiceStatus.STARTING
        val intent = Intent(context, SingBoxProxyService::class.java).apply {
            action = SingBoxServiceContract.ACTION_START
            putExtra(SingBoxServiceContract.EXTRA_BINARY_CONFIG, config)
        }
        context.startForegroundService(intent)
        addLog(LogLevel.INFO, "正在启动 sing-box 代理...")
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
            singBoxVpnServiceConnection,
            Context.BIND_AUTO_CREATE,
        )
        context.bindService(
            Intent(context, SingBoxProxyService::class.java),
            singBoxProxyServiceConnection,
            Context.BIND_AUTO_CREATE,
        )
    }

    private fun observeActiveService() {
        stateJob?.cancel()
        logJob?.cancel()

        when (activeServiceKind) {
            ServiceKind.SINGBOX_VPN -> {
                val service = boundSingBoxVpnService ?: return
                bindSingBoxObservers(service.serviceState, service.logs)
            }
            ServiceKind.SINGBOX_PROXY -> {
                val service = boundSingBoxProxyService ?: return
                bindSingBoxObservers(service.serviceState, service.logs)
            }
            ServiceKind.BINARY -> {
                val service = boundBinaryService ?: return
                stateJob = viewModelScope.launch {
                    service.serviceState.collectLatest { state ->
                        _serviceState.value = state
                        _serviceStatus.value = if (state.isRunning) {
                            clearServiceError()
                            ServiceStatus.RUNNING
                        } else {
                            ServiceStatus.STOPPED
                        }
                    }
                }
                logJob = viewModelScope.launch {
                    service.logs.collect { appendLog(it) }
                }
            }
            null -> Unit
        }
    }

    private fun bindSingBoxObservers(
        state: StateFlow<ServiceState>,
        logs: SharedFlow<LogEntry>,
    ) {
        stateJob = viewModelScope.launch {
            state.collectLatest { serviceState ->
                _serviceState.value = serviceState
                _serviceStatus.value = if (serviceState.isRunning) {
                    clearServiceError()
                    ServiceStatus.RUNNING
                } else {
                    ServiceStatus.STOPPED
                }
            }
        }
        logJob = viewModelScope.launch {
            logs.collect { appendLog(it) }
        }
    }

    private fun clearServiceObservation(status: ServiceStatus) {
        stateJob?.cancel()
        logJob?.cancel()
        activeServiceKind = null
        _serviceStatus.value = status
        if (status != ServiceStatus.ERROR) {
            clearServiceError()
        }
    }

    private fun setServiceError(message: String) {
        _serviceErrorMessage.value = message
        _serviceStatus.value = ServiceStatus.ERROR
        addLog(LogLevel.ERROR, message)
    }

    private fun clearServiceError() {
        _serviceErrorMessage.value = null
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
            refreshSingBoxRunMode()
        }
    }

    private fun loadInitialConfig() {
        viewModelScope.launch {
            configManager.configFlow.collectLatest { config ->
                if (config != _currentConfig.value) {
                    _currentConfig.value = config
                    refreshSingBoxRunMode()
                }
            }
        }
    }

    private fun refreshAppConfigFiles() {
        viewModelScope.launch {
            _appConfigFiles.value = appConfigManager.getAllConfigFiles()
            refreshSingBoxRunMode()
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
        if (singBoxVpnBound) {
            runCatching { context.unbindService(singBoxVpnServiceConnection) }
                .onFailure { Log.w(TAG, "sing-box VPN 服务已经解除绑定", it) }
        }
        if (singBoxProxyBound) {
            runCatching { context.unbindService(singBoxProxyServiceConnection) }
                .onFailure { Log.w(TAG, "sing-box 代理服务已经解除绑定", it) }
        }
        boundBinaryService = null
        boundSingBoxVpnService = null
        boundSingBoxProxyService = null
        super.onCleared()
    }

    private enum class ServiceKind {
        BINARY,
        SINGBOX_VPN,
        SINGBOX_PROXY,
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val MAX_LOG_ENTRIES = 500
        private const val LOG_UPDATE_DELAY_MS = 200L
    }
}
