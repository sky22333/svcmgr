package com.androidservice.manager

import android.util.Log
import com.androidservice.data.BinaryConfig
import com.androidservice.data.LogEntry
import com.androidservice.data.LogLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class ProcessManager {
    
    companion object {
        private const val TAG = "ProcessManager"
        private const val BATCH_SIZE = 20
        private const val BATCH_DELAY_MS = 50L
    }

    private var currentProcess: Process? = null
    private var processJob: Job? = null
    private var outputJob: Job? = null
    private var errorJob: Job? = null
    
    private val _processLogs = MutableSharedFlow<LogEntry>()
    val processLogs: SharedFlow<LogEntry> = _processLogs.asSharedFlow()
    
    private val logBuffer = mutableListOf<LogEntry>()
    private val bufferMutex = Mutex()
    private var lastFlushTime = 0L
    
    private val _processState = MutableSharedFlow<ProcessState>()
    val processState: SharedFlow<ProcessState> = _processState.asSharedFlow()

    data class ProcessState(
        val isRunning: Boolean = false,
        val pid: Int? = null,
        val startTime: Long? = null,
        val exitCode: Int? = null
    )

    suspend fun startProcess(
        binaryPath: String,
        config: BinaryConfig,
        scope: CoroutineScope
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isProcessRunning()) {
                logMessage(LogLevel.WARN, "进程已经在运行中")
                return@withContext false
            }

            // 构建命令参数
            val command = if (config.argumentsString.isNotEmpty()) {
                listOf("sh", "-c", "$binaryPath ${config.argumentsString}")
            } else {
                listOf("sh", "-c", binaryPath)
            }

            logMessage(LogLevel.INFO, "启动进程: ${command.joinToString(" ")}")

            // 创建ProcessBuilder
            val processBuilder = ProcessBuilder(command)
            
            // 设置环境变量
            val env = processBuilder.environment()
            config.environmentVariables.forEach { (key, value) ->
                env[key] = value
            }

            // 启动进程
            currentProcess = processBuilder.start()
            val process = currentProcess!!

            // 获取进程ID
            val pid = try {
                // 反射获取PID（部分安卓版本可能会失效）
                val field = process.javaClass.getDeclaredField("pid")
                field.isAccessible = true
                field.getInt(process)
            } catch (e: Exception) {
                null
            }

            val startTime = System.currentTimeMillis()
            
            // 发送进程启动状态
            _processState.emit(ProcessState(
                isRunning = true,
                pid = pid,
                startTime = startTime
            ))

            logMessage(LogLevel.INFO, "进程启动成功${pid?.let { ", PID: $it" } ?: ""}")

            // 启动输出读取协程
            startOutputReading(process, scope)

            // 启动进程监控协程
            processJob = scope.launch(Dispatchers.IO) {
                try {
                    val exitCode = process.waitFor()
                    logMessage(LogLevel.INFO, "进程退出，退出码: $exitCode")
                    
                    _processState.emit(ProcessState(
                        isRunning = false,
                        pid = pid,
                        startTime = startTime,
                        exitCode = exitCode
                    ))
                    
                    cleanup()
                } catch (e: InterruptedException) {
                    logMessage(LogLevel.WARN, "进程监控被中断")
                } catch (e: Exception) {
                    logMessage(LogLevel.ERROR, "进程监控异常: ${e.message}")
                }
            }

            true
        } catch (e: IOException) {
            logMessage(LogLevel.ERROR, "启动进程失败: ${e.message}")
            false
        } catch (e: Exception) {
            logMessage(LogLevel.ERROR, "启动进程异常: ${e.message}")
            false
        }
    }

    private suspend fun batchEmitLogs(entry: LogEntry) {
        bufferMutex.withLock {
            logBuffer.add(entry)
            val currentTime = System.currentTimeMillis()
            
            if (logBuffer.size >= BATCH_SIZE || currentTime - lastFlushTime >= BATCH_DELAY_MS) {
                val logsToEmit = logBuffer.toList()
                logBuffer.clear()
                lastFlushTime = currentTime
                
                logsToEmit.forEach { _processLogs.emit(it) }
            }
        }
    }
    
    private fun startOutputReading(process: Process, scope: CoroutineScope) {
        // 读取标准输出
        outputJob = scope.launch(SupervisorJob() + Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null && currentCoroutineContext().isActive) {
                        line?.let {
                            batchEmitLogs(LogEntry(
                                timestamp = System.currentTimeMillis(),
                                level = LogLevel.INFO,
                                message = it,
                                source = "stdout"
                            ))
                        }
                    }
                }
            } catch (e: IOException) {
                if (currentCoroutineContext().isActive) {
                    logMessage(LogLevel.WARN, "标准输出读取被中断")
                }
            } catch (e: Exception) {
                logMessage(LogLevel.ERROR, "读取标准输出异常: ${e.message}")
            }
        }

        // 读取错误输出
        errorJob = scope.launch(SupervisorJob() + Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null && currentCoroutineContext().isActive) {
                        line?.let {
                            batchEmitLogs(LogEntry(
                                timestamp = System.currentTimeMillis(),
                                level = LogLevel.ERROR,
                                message = it,
                                source = "stderr"
                            ))
                        }
                    }
                }
            } catch (e: IOException) {
                if (currentCoroutineContext().isActive) {
                    logMessage(LogLevel.WARN, "错误输出读取被中断")
                }
            } catch (e: Exception) {
                logMessage(LogLevel.ERROR, "读取错误输出异常: ${e.message}")
            }
        }
    }

    suspend fun stopProcess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = currentProcess
            if (process == null) {
                logMessage(LogLevel.WARN, "没有运行中的进程")
                return@withContext true
            }

            logMessage(LogLevel.INFO, "正在停止进程...")
            process.destroy()

            // 等待进程结束，最多等待5秒
            val terminated = withTimeoutOrNull(5000) {
                while (process.isAlive) {
                    delay(100)
                }
                true
            }

            if (terminated == null) {
                process.destroyForcibly()
                
                // 兜底策略，强制结束进程
                withTimeoutOrNull(1000) {
                    while (process.isAlive) {
                        delay(100)
                    }
                }
            }

            cleanup()
            logMessage(LogLevel.INFO, "进程已停止")
            true
        } catch (e: Exception) {
            logMessage(LogLevel.ERROR, "停止进程异常: ${e.message}")
            false
        }
    }

    fun isProcessRunning(): Boolean {
        return currentProcess?.isAlive == true
    }

    suspend fun writeToProcess(input: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = currentProcess
            if (process != null && process.isAlive) {
                process.outputStream.write("$input\n".toByteArray())
                process.outputStream.flush()
                logMessage(LogLevel.DEBUG, "向进程发送输入: $input")
                true
            } else {
                logMessage(LogLevel.WARN, "进程未运行，无法发送输入")
                false
            }
        } catch (e: IOException) {
            logMessage(LogLevel.ERROR, "发送输入失败: ${e.message}")
            false
        }
    }

    private fun cleanup() {
        processJob?.cancel()
        outputJob?.cancel()
        errorJob?.cancel()
        
        currentProcess?.let { process ->
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
        
        currentProcess = null
        processJob = null
        outputJob = null
        errorJob = null
    }

    private suspend fun logMessage(level: LogLevel, message: String) {
        Log.d(TAG, message)
        _processLogs.emit(LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            message = message,
            source = "system"
        ))
    }
    


    fun destroy() {
        try {
            processJob?.cancel()
            outputJob?.cancel()
            errorJob?.cancel()
            
            // 强制终止进程，不等待协程完成
            currentProcess?.let { process ->
                if (process.isAlive) {
                    try {
                        process.destroy()
                        // 如果进程在500ms内没有终止，强制杀死
                        Thread.sleep(500)
                        if (process.isAlive) {
                            process.destroyForcibly()
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        process.destroyForcibly()
                    }
                }
            }
            
            cleanup()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during ProcessManager destroy", e)
            currentProcess?.destroyForcibly()
        }
    }
}