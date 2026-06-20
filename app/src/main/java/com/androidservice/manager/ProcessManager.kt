package com.androidservice.manager

import android.util.Log
import com.androidservice.data.BinaryConfig
import com.androidservice.data.LogEntry
import com.androidservice.data.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.IOException

class ProcessManager {

    data class ProcessState(
        val isRunning: Boolean = false,
        val pid: Int? = null,
        val startTime: Long? = null,
        val exitCode: Int? = null
    )

    private var currentProcess: Process? = null
    private var monitorJob: Job? = null
    private var outputJob: Job? = null
    private var errorJob: Job? = null

    private val _processLogs = MutableSharedFlow<LogEntry>(extraBufferCapacity = 256)
    val processLogs: SharedFlow<LogEntry> = _processLogs.asSharedFlow()

    private val _processState = MutableSharedFlow<ProcessState>(extraBufferCapacity = 16)
    val processState: SharedFlow<ProcessState> = _processState.asSharedFlow()

    suspend fun startProcess(
        binaryPath: String,
        config: BinaryConfig,
        scope: CoroutineScope
    ): Boolean = withContext(Dispatchers.IO) {
        if (isProcessRunning()) {
            log(LogLevel.WARN, "进程已经在运行")
            return@withContext false
        }

        try {
            val command = buildCommand(binaryPath, config.argumentsString)
            log(LogLevel.INFO, "启动进程: ${command.joinToString(" ")}")

            val process = ProcessBuilder(command)
                .apply { environment().putAll(config.environmentVariables) }
                .start()

            currentProcess = process
            val pid = process.pidCompat()
            val startTime = System.currentTimeMillis()
            _processState.emit(ProcessState(isRunning = true, pid = pid, startTime = startTime))
            log(LogLevel.INFO, "进程启动成功${pid?.let { ", PID: $it" }.orEmpty()}")

            readOutput(process, scope)
            monitorJob = scope.launch(Dispatchers.IO) {
                val exitCode = process.waitFor()
                log(LogLevel.INFO, "进程已退出，退出码: $exitCode")
                _processState.emit(
                    ProcessState(
                        isRunning = false,
                        pid = pid,
                        startTime = startTime,
                        exitCode = exitCode
                    )
                )
                cleanup(force = false)
            }
            return@withContext true
        } catch (e: Exception) {
            log(LogLevel.ERROR, "启动进程失败: ${e.message}")
            return@withContext false
        }
    }

    suspend fun stopProcess(emitState: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        val process = currentProcess ?: run {
            log(LogLevel.WARN, "没有正在运行的进程")
            if (emitState) _processState.emit(ProcessState())
            return@withContext true
        }

        try {
            log(LogLevel.INFO, "正在停止进程...")
            monitorJob?.cancel()
            process.destroy()
            val terminated = withTimeoutOrNull(STOP_TIMEOUT_MS) {
                while (process.isAlive) delay(100)
                true
            } == true

            if (!terminated) {
                process.destroyForcibly()
                withTimeoutOrNull(FORCE_STOP_TIMEOUT_MS) {
                    while (process.isAlive) delay(100)
                }
            }

            cleanup(force = false)
            if (emitState) _processState.emit(ProcessState())
            log(LogLevel.INFO, "进程已停止")
            return@withContext true
        } catch (e: Exception) {
            log(LogLevel.ERROR, "停止进程失败: ${e.message}")
            return@withContext false
        }
    }

    fun isProcessRunning(): Boolean = currentProcess?.isAlive == true

    fun destroy() {
        cleanup(force = true)
    }

    private fun buildCommand(binaryPath: String, argumentsString: String): List<String> {
        val command = if (argumentsString.isBlank()) binaryPath else "$binaryPath $argumentsString"
        return listOf("sh", "-c", command)
    }

    private fun readOutput(process: Process, scope: CoroutineScope) {
        outputJob = scope.launch(SupervisorJob() + Dispatchers.IO) {
            readLines(process.inputStream.bufferedReader(), LogLevel.INFO, "stdout")
        }
        errorJob = scope.launch(SupervisorJob() + Dispatchers.IO) {
            readLines(process.errorStream.bufferedReader(), LogLevel.ERROR, "stderr")
        }
    }

    private suspend fun readLines(reader: BufferedReader, level: LogLevel, source: String) {
        try {
            reader.use { bufferedReader ->
                while (currentCoroutineContext().isActive) {
                    val line = bufferedReader.readLine() ?: break
                    emitLog(level, line, source)
                }
            }
        } catch (e: IOException) {
            if (currentCoroutineContext().isActive) {
                log(LogLevel.WARN, "$source 输出中断")
            }
        } catch (e: Exception) {
            log(LogLevel.ERROR, "读取 $source 失败: ${e.message}")
        }
    }

    private fun cleanup(force: Boolean) {
        monitorJob?.cancel()
        outputJob?.cancel()
        errorJob?.cancel()
        if (force) {
            currentProcess?.takeIf { it.isAlive }?.destroyForcibly()
        }
        currentProcess = null
        monitorJob = null
        outputJob = null
        errorJob = null
    }

    private fun log(level: LogLevel, message: String) {
        Log.d(TAG, message)
        emitLog(level, message, "system")
    }

    private fun emitLog(level: LogLevel, message: String, source: String) {
        _processLogs.tryEmit(
            LogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                message = message,
                source = source
            )
        )
    }

    private fun Process.pidCompat(): Int? {
        return runCatching {
            val field = javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(this)
        }.getOrNull()
    }

    companion object {
        private const val TAG = "ProcessManager"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val FORCE_STOP_TIMEOUT_MS = 1_000L
    }
}
