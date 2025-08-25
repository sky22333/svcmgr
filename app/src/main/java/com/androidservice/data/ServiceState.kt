package com.androidservice.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

data class ServiceState(
    val isRunning: Boolean = false,
    val processId: Int? = null,
    val binaryName: String = "",
    val startTime: Long? = null,
    val restartCount: Int = 0
)

enum class ServiceStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

data class BinaryInfo(
    val name: String,
    val abi: String,
    val path: String,
    val size: Long,
    val isExecutable: Boolean = false
)

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val message: String,
    val source: String = "system"
)

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

@Parcelize
data class BinaryConfig(
    val binaryName: String = "",
    val arguments: List<String> = emptyList(),
    val workingDirectory: String = "",
    val environmentVariables: Map<String, String> = emptyMap(),
    val autoRestart: Boolean = true,
    val restartDelay: Long = 5000L,
    val maxRestarts: Int = -1
) : Parcelable