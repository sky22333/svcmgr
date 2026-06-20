package com.androidservice.manager

import android.content.Context
import android.util.Log
import com.androidservice.data.AppConfigFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AppConfigManager(private val context: Context) {

    private val configDir: File by lazy {
        File(context.filesDir, CONFIG_DIR_NAME).apply { mkdirs() }
    }

    private val gson = Gson()

    suspend fun saveConfigFile(appConfigFile: AppConfigFile): Boolean = withContext(Dispatchers.IO) {
        val fileName = appConfigFile.fileName.trim()
        val contentBytes = appConfigFile.content.toByteArray(Charsets.UTF_8)

        if (!isValidFileName(fileName) || contentBytes.size > MAX_CONFIG_FILE_SIZE) {
            return@withContext false
        }
        if (configDir.usableSpace < contentBytes.size + DISK_SPACE_BUFFER) {
            return@withContext false
        }

        val saved = runCatching {
            val target = File(configDir, fileName)
            val temp = File(configDir, "$fileName.tmp")
            temp.outputStream().buffered().use { it.write(contentBytes) }
            if (target.exists() && !target.delete()) return@withContext false
            temp.renameTo(target)
        }.onFailure {
            Log.e(TAG, "保存配置文件失败: $fileName", it)
        }.getOrDefault(false)

        if (!saved) return@withContext false
        saveRemoteUrl(fileName, appConfigFile.remoteUrl.trim())
        true
    }

    suspend fun loadConfigFile(fileName: String): AppConfigFile? = withContext(Dispatchers.IO) {
        val safeName = fileName.trim()
        if (!isValidFileName(safeName)) return@withContext null

        runCatching {
            val file = File(configDir, safeName)
            if (!file.exists() || !file.canRead()) return@withContext null

            AppConfigFile(
                fileName = file.name,
                content = file.readText(Charsets.UTF_8),
                lastModified = file.lastModified(),
                size = file.length(),
                remoteUrl = getRemoteUrl(file.name).orEmpty(),
            )
        }.onFailure {
            Log.e(TAG, "读取配置文件失败: $safeName", it)
        }.getOrNull()
    }

    suspend fun getAllConfigFiles(): List<AppConfigFile> = withContext(Dispatchers.IO) {
        val remoteSources = loadRemoteSources()
        configDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.canRead() && !isInternalFile(it.name) }
            .map { file ->
                AppConfigFile(
                    fileName = file.name,
                    lastModified = file.lastModified(),
                    size = file.length(),
                    remoteUrl = remoteSources[file.name].orEmpty(),
                )
            }
            .sortedByDescending { it.lastModified }
    }

    suspend fun refreshFromRemote(
        fileName: String,
        headers: Map<String, String> = emptyMap(),
    ): RemoteConfigRefreshResult = withContext(Dispatchers.IO) {
        val safeName = fileName.trim()
        if (!isValidFileName(safeName)) {
            return@withContext RemoteConfigRefreshResult.Failure("文件名无效")
        }

        val remoteUrl = getRemoteUrl(safeName)?.trim().orEmpty()
        if (remoteUrl.isBlank()) {
            return@withContext RemoteConfigRefreshResult.Failure("未设置远程 URL")
        }
        if (!RemoteConfigFetcher.isSupportedUrl(remoteUrl)) {
            return@withContext RemoteConfigRefreshResult.Failure("远程 URL 无效")
        }

        val existing = loadConfigFile(safeName)
            ?: return@withContext RemoteConfigRefreshResult.Failure("配置文件不存在")

        RemoteConfigFetcher.fetch(remoteUrl, headers)
            .fold(
                onSuccess = { content ->
                    val saved = saveConfigFile(
                        existing.copy(
                            content = content,
                            remoteUrl = remoteUrl,
                        ),
                    )
                    if (saved) {
                        RemoteConfigRefreshResult.Success(content)
                    } else {
                        RemoteConfigRefreshResult.Failure("保存远程配置失败")
                    }
                },
                onFailure = {
                    Log.e(TAG, "拉取远程配置失败: $safeName", it)
                    RemoteConfigRefreshResult.Failure(it.message ?: "拉取远程配置失败")
                },
            )
    }

    suspend fun fetchRemotePreview(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): RemoteConfigRefreshResult = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            return@withContext RemoteConfigRefreshResult.Failure("远程 URL 不能为空")
        }
        if (!RemoteConfigFetcher.isSupportedUrl(trimmedUrl)) {
            return@withContext RemoteConfigRefreshResult.Failure("仅支持 http/https 链接")
        }

        RemoteConfigFetcher.fetch(trimmedUrl, headers).fold(
            onSuccess = { RemoteConfigRefreshResult.Success(it) },
            onFailure = {
                Log.e(TAG, "预览远程配置失败", it)
                RemoteConfigRefreshResult.Failure(it.message ?: "拉取远程配置失败")
            },
        )
    }

    suspend fun deleteConfigFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidFileName(fileName)) return@withContext false
        val deleted = File(configDir, fileName).let { it.exists() && it.delete() }
        if (deleted) removeRemoteUrl(fileName)
        deleted
    }

    fun getConfigFilePath(fileName: String): String = File(configDir, fileName).absolutePath

    fun isValidFileName(fileName: String): Boolean {
        val trimmed = fileName.trim()
        val invalidChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        return trimmed.isNotBlank() &&
            trimmed.length <= 255 &&
            !trimmed.startsWith(".") &&
            !isInternalFile(trimmed) &&
            trimmed.none { it in invalidChars }
    }

    private fun isInternalFile(fileName: String): Boolean = fileName == REMOTE_SOURCES_FILE_NAME

    private fun remoteSourcesFile(): File = File(configDir, REMOTE_SOURCES_FILE_NAME)

    private fun loadRemoteSources(): Map<String, String> {
        val file = remoteSourcesFile()
        if (!file.exists() || !file.canRead()) return emptyMap()

        return runCatching {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(file.readText(Charsets.UTF_8), type).orEmpty()
        }.onFailure {
            Log.e(TAG, "读取远程配置索引失败", it)
        }.getOrDefault(emptyMap())
    }

    private fun saveRemoteSources(sources: Map<String, String>) {
        val sanitized = sources.filterValues { it.isNotBlank() }
        if (sanitized.isEmpty()) {
            remoteSourcesFile().delete()
            return
        }
        remoteSourcesFile().writeText(gson.toJson(sanitized), Charsets.UTF_8)
    }

    private fun getRemoteUrl(fileName: String): String? = loadRemoteSources()[fileName]

    private fun saveRemoteUrl(fileName: String, remoteUrl: String) {
        val sources = loadRemoteSources().toMutableMap()
        if (remoteUrl.isBlank()) {
            sources.remove(fileName)
        } else {
            sources[fileName] = remoteUrl
        }
        saveRemoteSources(sources)
    }

    private fun removeRemoteUrl(fileName: String) {
        saveRemoteUrl(fileName, "")
    }

    companion object {
        private const val TAG = "AppConfigManager"
        private const val CONFIG_DIR_NAME = "app_configs"
        private const val REMOTE_SOURCES_FILE_NAME = "_remote_sources.json"
        private const val MAX_CONFIG_FILE_SIZE = 2 * 1024 * 1024
        private const val DISK_SPACE_BUFFER = 10 * 1024 * 1024
    }
}

sealed class RemoteConfigRefreshResult {
    data class Success(val content: String) : RemoteConfigRefreshResult()
    data class Failure(val message: String) : RemoteConfigRefreshResult()
}
