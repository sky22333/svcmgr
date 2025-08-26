package com.androidservice.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidservice.data.*
import com.androidservice.manager.AppConfigManager
import com.androidservice.manager.BinaryManager
import com.androidservice.manager.ConfigManager
import com.androidservice.service.BinaryProcessService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    // 编辑文件名状态管理
    private val _editingFileName = MutableStateFlow<String?>(null)
    val editingFileName = _editingFileName.asStateFlow()
    
    fun setEditingFileName(fileName: String?) {
        _editingFileName.value = fileName
    }

    private val context = getApplication<Application>()
    private lateinit var binaryManager: BinaryManager
    private lateinit var configManager: ConfigManager
    private lateinit var appConfigManager: AppConfigManager
    
    private var boundService: BinaryProcessService? = null
    private var isBound = false

    private val _serviceState = MutableStateFlow(ServiceState())
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    private val _serviceStatus = MutableStateFlow(ServiceStatus.STOPPED)
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    private val _binaryList = MutableStateFlow<List<BinaryInfo>>(emptyList())
    val binaryList: StateFlow<List<BinaryInfo>> = _binaryList.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    
    private val logDeque = ArrayDeque<LogEntry>(MAX_LOG_ENTRIES)
    private var pendingUIUpdate = false

    private val _currentConfig = MutableStateFlow(BinaryConfig())
    val currentConfig: StateFlow<BinaryConfig> = _currentConfig.asStateFlow()

    private val _appConfigFiles = MutableStateFlow<List<AppConfigFile>>(emptyList())
    val appConfigFiles: StateFlow<List<AppConfigFile>> = _appConfigFiles.asStateFlow()
    
    private val _availableBinaryNames = MutableStateFlow<List<String>>(emptyList())
    val availableBinaryNames: StateFlow<List<String>> = _availableBinaryNames.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as BinaryProcessService.BinaryProcessBinder
            boundService = binder.getService()
            isBound = true
            
            // 服务状态和日志
            viewModelScope.launch(SupervisorJob()) {
                boundService?.serviceState?.collect { state ->
                    _serviceState.value = state
                    _serviceStatus.value = when {
                        state.isRunning -> ServiceStatus.RUNNING
                        else -> ServiceStatus.STOPPED
                    }
                }
            }
            
            viewModelScope.launch(SupervisorJob()) {
                boundService?.logs?.collect { logEntry ->
                    createAndEmitLog(logEntry.level, logEntry.message, logEntry.source)
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            boundService = null
            isBound = false
            _serviceStatus.value = ServiceStatus.STOPPED
        }
    }

    init {
        binaryManager = BinaryManager(context)
        configManager = ConfigManager(context)
        appConfigManager = AppConfigManager(context)
        bindToService()
        loadBinaryList()
        loadInitialConfig()
        loadAppConfigFiles()
    }

    private fun bindToService() {
        val intent = Intent(context, BinaryProcessService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun loadBinaryList() {
        viewModelScope.launch {
            try {
                val binaries = binaryManager.initializeBinaries()
                _binaryList.value = binaries
                
                // 如果有可用的程序文件，设置默认配置
                if (binaries.isNotEmpty() && _currentConfig.value.binaryName.isEmpty()) {
                    _currentConfig.value = _currentConfig.value.copy(
                        binaryName = binaries.first().name
                    )
                }
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "加载程序文件失败: ${e.message}")
            }
        }
    }

    fun startService() {
        if (_serviceStatus.value == ServiceStatus.RUNNING) {
            addLog(LogLevel.WARN, "服务已经在运行中")
            return
        }
        
        _serviceStatus.value = ServiceStatus.STARTING
        
        viewModelScope.launch {
            try {
                val config = _currentConfig.value
                if (config.binaryName.isEmpty()) {
                    addLog(LogLevel.ERROR, "未配置程序文件")
                    _serviceStatus.value = ServiceStatus.ERROR
                    return@launch
                }

                val intent = Intent(context, BinaryProcessService::class.java).apply {
                    action = BinaryProcessService.ACTION_START_BINARY
                    putExtra(BinaryProcessService.EXTRA_BINARY_CONFIG, config)
                }
                context.startForegroundService(intent)
                
                addLog(LogLevel.INFO, "正在启动服务...")
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "启动服务失败: ${e.message}")
                _serviceStatus.value = ServiceStatus.ERROR
            }
        }
    }

    fun stopService() {
        if (_serviceStatus.value == ServiceStatus.STOPPED) {
            addLog(LogLevel.WARN, "服务未运行")
            return
        }
        
        _serviceStatus.value = ServiceStatus.STOPPING
        
        viewModelScope.launch {
            try {
                val intent = Intent(context, BinaryProcessService::class.java).apply {
                    action = BinaryProcessService.ACTION_STOP_BINARY
                }
                context.startService(intent)
                
                addLog(LogLevel.INFO, "正在停止服务...")
                
                val timeoutJob = withTimeoutOrNull(2000L) {
                    serviceState.first { !it.isRunning }
                }
                
                // 如果超时或服务状态未正确更新，设置为STOPPED
                if (timeoutJob == null && _serviceStatus.value == ServiceStatus.STOPPING) {
                    _serviceStatus.value = ServiceStatus.STOPPED
                    _serviceState.value = ServiceState()
                    addLog(LogLevel.INFO, "服务停止完成")
                }
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "停止服务失败: ${e.message}")
                _serviceStatus.value = ServiceStatus.STOPPED
            }
        }
    }

    fun updateConfig(config: BinaryConfig) {
        _currentConfig.value = config
        viewModelScope.launch {
            try {
                configManager.saveConfig(config)
                addLog(LogLevel.DEBUG, "配置已自动保存")
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "自动保存配置失败: ${e.message}")
            }
        }
    }

    fun saveConfig() {
        viewModelScope.launch {
            try {
                val success = configManager.saveConfigToFile(_currentConfig.value)
                if (success) {
                    addLog(LogLevel.INFO, "配置已保存到文件: ${configManager.getConfigFilePath()}")
                } else {
                    addLog(LogLevel.ERROR, "保存配置文件失败")
                }
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "保存配置失败: ${e.message}")
            }
        }
    }

    fun loadConfig() {
        viewModelScope.launch {
            try {
                val config = configManager.loadConfigFromFile()
                if (config != null) {
                    _currentConfig.value = config
                    configManager.saveConfig(config) // 同步到DataStore
                    addLog(LogLevel.INFO, "配置已从文件加载")
                } else {
                    addLog(LogLevel.WARN, "配置文件不存在或格式错误")
                }
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "加载配置失败: ${e.message}")
            }
        }
    }

    private fun loadInitialConfig() {
        viewModelScope.launch {
            try {
                // 先尝试从DataStore加载配置
                configManager.configFlow.collect { config ->
                    if (config.binaryName.isNotEmpty() || config.argumentsString.isNotEmpty()) {
                        _currentConfig.value = config
                    }
                }
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "加载初始配置失败: ${e.message}")
            }
        }
    }



    fun importConfig(jsonString: String) {
        viewModelScope.launch {
            try {
                val config = configManager.importConfigFromJson(jsonString)
                if (config != null) {
                    _currentConfig.value = config
                    configManager.saveConfig(config)
                    addLog(LogLevel.INFO, "配置导入成功")
                } else {
                    addLog(LogLevel.ERROR, "配置格式错误，导入失败")
                }
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "导入配置失败: ${e.message}")
            }
        }
    }

    fun resetConfig() {
        viewModelScope.launch {
            try {
                configManager.clearConfig()
                _currentConfig.value = BinaryConfig()
                addLog(LogLevel.INFO, "配置已重置")
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "重置配置失败: ${e.message}")
            }
        }
    }


    
    /**
     * 加载可用的内核名称列表
     */
    fun loadAvailableBinaryNames() {
        viewModelScope.launch {
            try {
                val kernelNames = binaryManager.getAvailableKernelNames()
                _availableBinaryNames.value = kernelNames
                
                val currentBinaryName = _currentConfig.value.binaryName
                if (currentBinaryName.isEmpty() || !kernelNames.contains(currentBinaryName)) {
                    if (kernelNames.isNotEmpty()) {
                        _currentConfig.value = _currentConfig.value.copy(
                            binaryName = kernelNames.first()
                        )
                    }
                }
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "加载可用内核名称失败: ${e.message}")
            }
        }
    }
    


    fun deleteBinary(binary: BinaryInfo) {
        viewModelScope.launch {
            try {
                val success = binaryManager.deleteBinary(binary)
                if (success) {
                    addLog(LogLevel.INFO, "删除程序文件成功: ${binary.name}")
                    loadBinaryList()
                } else {
                    addLog(LogLevel.ERROR, "删除程序文件失败: ${binary.name}")
                }
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "删除程序文件异常: ${e.message}")
            }
        }
    }

    fun installBinaryFromExternal(externalPath: String, binaryName: String, abi: String) {
        viewModelScope.launch {
            try {
                val binaryInfo = binaryManager.installBinaryFromExternal(externalPath, binaryName, abi)
                if (binaryInfo != null) {
                    addLog(LogLevel.INFO, "安装程序文件成功: $binaryName")
                    loadBinaryList()
                } else {
                    addLog(LogLevel.ERROR, "安装程序文件失败: $binaryName")
                }
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "安装程序文件异常: ${e.message}")
            }
        }
    }

    private fun addLog(level: LogLevel, message: String) {
        createAndEmitLog(level, message, "app")
    }
    
    private fun createAndEmitLog(level: LogLevel, message: String, source: String) {
        val newLog = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            message = message,
            source = source
        )
        
        synchronized(logDeque) {
            if (logDeque.size >= MAX_LOG_ENTRIES) {
                logDeque.removeFirst()
            }
            logDeque.addLast(newLog)
            
            if (!pendingUIUpdate) {
                pendingUIUpdate = true
                viewModelScope.launch {
                    delay(50) // 50ms延迟批量更新UI
                    synchronized(logDeque) {
                        _logs.value = logDeque.toList()
                        pendingUIUpdate = false
                    }
                }
            }
        }
    }

    companion object {
        private const val MAX_LOG_ENTRIES = 500
    }

    // 应用配置文件管理 - 优化电量消耗和性能
    private fun loadAppConfigFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = appConfigManager.getAllConfigFiles()
                
                withContext(Dispatchers.Main) {
                    val currentFiles = _appConfigFiles.value
                    
                    if (currentFiles.size != files.size) {
                        _appConfigFiles.value = files
                        return@withContext
                    }
                    
                    val hasChanges = files.zip(currentFiles).any { (new, old) ->
                        new.fileName != old.fileName || new.lastModified != old.lastModified
                    }
                    
                    if (hasChanges) {
                        _appConfigFiles.value = files
                    }
                }
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "加载应用配置文件列表失败: ${e.message}")
            }
        }
    }

    suspend fun saveAppConfigFile(appConfigFile: AppConfigFile): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val success = appConfigManager.saveConfigFile(appConfigFile)
                withContext(Dispatchers.Main) {
                    if (success) {
                        addLog(LogLevel.INFO, "应用配置文件保存成功: ${appConfigFile.fileName}")
                        updateSingleConfigFile(appConfigFile)
                    } else {
                        addLog(LogLevel.ERROR, "应用配置文件保存失败: ${appConfigFile.fileName}")
                    }
                }
                success
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addLog(LogLevel.ERROR, "保存应用配置文件异常: ${e.message}")
                }
                false
            }
        }
    }

    private suspend fun updateSingleConfigFile(configFile: AppConfigFile) {
        val currentFiles = _appConfigFiles.value.toMutableList()
        
        val existingIndex = currentFiles.indexOfFirst { it.fileName == configFile.fileName }
        
        val updatedFile = configFile.copy(
            lastModified = System.currentTimeMillis(),
            size = configFile.content.toByteArray(Charsets.UTF_8).size.toLong()
        )
        
        if (existingIndex >= 0) {
            currentFiles[existingIndex] = updatedFile
        } else {
            currentFiles.add(0, updatedFile)
        }
        
        _appConfigFiles.value = currentFiles.sortedByDescending { it.lastModified }
    }

    suspend fun loadAppConfigFile(fileName: String): AppConfigFile? {
        return withContext(Dispatchers.IO) {
            try {
                val result = appConfigManager.loadConfigFile(fileName)
                withContext(Dispatchers.Main) {
                    if (result == null) {
                        addLog(LogLevel.WARN, "应用配置文件不存在: $fileName")
                    }
                }
                result
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addLog(LogLevel.ERROR, "加载应用配置文件失败: ${e.message}")
                }
                null
            }
        }
    }

    fun deleteAppConfigFile(fileName: String) {
        // 边界条件检查
        if (fileName.isBlank()) {
            addLog(LogLevel.ERROR, "删除失败：文件名不能为空")
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = appConfigManager.deleteConfigFile(fileName)
                if (success) {
                    addLog(LogLevel.INFO, "应用配置文件删除成功: $fileName")
                    withContext(Dispatchers.Main) {
                        val currentFiles = _appConfigFiles.value.toMutableList()
                        currentFiles.removeAll { it.fileName == fileName }
                        _appConfigFiles.value = currentFiles
                    }
                } else {
                    addLog(LogLevel.ERROR, "应用配置文件删除失败: $fileName")
                }
            } catch (e: SecurityException) {
                addLog(LogLevel.ERROR, "删除文件权限被拒绝: $fileName")
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "删除应用配置文件异常: ${e.localizedMessage ?: "未知错误"}")
            }
        }
    }
    
    fun getAppConfigFilePath(fileName: String): String {
        return appConfigManager.getConfigFilePath(fileName)
    }



    override fun onCleared() {
        super.onCleared()
        
        try {
            // 取消所有协程作业
            viewModelScope.coroutineContext[Job]?.cancelChildren()
            
            // 解绑服务连接
            if (isBound) {
                try {
                    context.unbindService(serviceConnection)
                } catch (e: IllegalArgumentException) {
                    Log.w("MainViewModel", "Service already unbound", e)
                }
                isBound = false
            }
            
            // 清理服务引用
            boundService = null
            
            // 检查是否需要垃圾回收
            val shouldGc = _logs.value.size > 100 || _appConfigFiles.value.size > 50
            
            // 清理大型数据结构
            _logs.value = emptyList()
            _appConfigFiles.value = emptyList()
            _binaryList.value = emptyList()
            
            // 建议垃圾回收
            if (shouldGc) {
                System.gc()
            }
            
            Log.d("MainViewModel", "ViewModel resources cleaned up successfully")
            
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error during ViewModel cleanup", e)
        }
    }
}