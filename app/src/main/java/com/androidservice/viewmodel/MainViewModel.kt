package com.androidservice.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidservice.data.*
import com.androidservice.manager.BinaryManager
import com.androidservice.manager.ConfigManager
import com.androidservice.service.BinaryProcessService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = getApplication<Application>()
    private lateinit var binaryManager: BinaryManager
    private lateinit var configManager: ConfigManager
    
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

    private val _currentConfig = MutableStateFlow(BinaryConfig())
    val currentConfig: StateFlow<BinaryConfig> = _currentConfig.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as BinaryProcessService.BinaryProcessBinder
            boundService = binder.getService()
            isBound = true
            
            // 开始收集服务状态和日志
            viewModelScope.launch {
                boundService?.serviceState?.collect { state ->
                    _serviceState.value = state
                    _serviceStatus.value = when {
                        state.isRunning -> ServiceStatus.RUNNING
                        else -> ServiceStatus.STOPPED
                    }
                }
            }
            
            viewModelScope.launch {
                boundService?.logs?.collect { logEntry ->
                    _logs.value = _logs.value + logEntry
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
        bindToService()
        loadBinaryList()
        loadInitialConfig()
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
                
                // 如果有可用的二进制文件，设置默认配置
                if (binaries.isNotEmpty() && _currentConfig.value.binaryName.isEmpty()) {
                    _currentConfig.value = _currentConfig.value.copy(
                        binaryName = binaries.first().name
                    )
                }
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "加载二进制文件失败: ${e.message}")
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
                    addLog(LogLevel.ERROR, "未配置二进制文件")
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
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "停止服务失败: ${e.message}")
            }
        }
    }

    fun restartService() {
        if (_serviceStatus.value != ServiceStatus.RUNNING) {
            addLog(LogLevel.WARN, "服务未运行，无法重启")
            return
        }
        
        viewModelScope.launch {
            try {
                val intent = Intent(context, BinaryProcessService::class.java).apply {
                    action = BinaryProcessService.ACTION_RESTART_BINARY
                }
                context.startService(intent)
                
                addLog(LogLevel.INFO, "正在重启服务...")
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "重启服务失败: ${e.message}")
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
                    if (config.binaryName.isNotEmpty() || config.arguments.isNotEmpty()) {
                        _currentConfig.value = config
                    }
                }
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "加载初始配置失败: ${e.message}")
            }
        }
    }

    fun exportConfig(): String? {
        return try {
            viewModelScope.launch {
                configManager.exportConfigToJson(_currentConfig.value)
            }
            null // 由于是异步的，这里先返回null，实际可以通过Flow来处理
        } catch (e: Exception) {
            null
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

    fun refreshBinaryList() {
        loadBinaryList()
    }

    fun deleteBinary(binary: BinaryInfo) {
        viewModelScope.launch {
            try {
                val success = binaryManager.deleteBinary(binary)
                if (success) {
                    addLog(LogLevel.INFO, "删除二进制文件成功: ${binary.name}")
                    refreshBinaryList()
                } else {
                    addLog(LogLevel.ERROR, "删除二进制文件失败: ${binary.name}")
                }
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "删除二进制文件异常: ${e.message}")
            }
        }
    }

    fun installBinaryFromExternal(externalPath: String, binaryName: String, abi: String) {
        viewModelScope.launch {
            try {
                val binaryInfo = binaryManager.installBinaryFromExternal(externalPath, binaryName, abi)
                if (binaryInfo != null) {
                    addLog(LogLevel.INFO, "安装二进制文件成功: $binaryName")
                    refreshBinaryList()
                } else {
                    addLog(LogLevel.ERROR, "安装二进制文件失败: $binaryName")
                }
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "安装二进制文件异常: ${e.message}")
            }
        }
    }

    private fun addLog(level: LogLevel, message: String) {
        val newLog = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            message = message,
            source = "app"
        )
        _logs.value = _logs.value + newLog
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }
}