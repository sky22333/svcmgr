package com.androidservice.manager

import android.content.Context
import android.util.Log
import com.androidservice.data.AppConfigFile
import com.androidservice.data.ConfigFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AppConfigManager(private val context: Context) {

    private val configDir: File by lazy {
        File(context.filesDir, CONFIG_DIR_NAME).apply { mkdirs() }
    }

    suspend fun saveConfigFile(appConfigFile: AppConfigFile): Boolean = withContext(Dispatchers.IO) {
        val fileName = appConfigFile.fileName.trim()
        val contentBytes = appConfigFile.content.toByteArray(Charsets.UTF_8)

        if (!isValidFileName(fileName) || contentBytes.size > MAX_CONFIG_FILE_SIZE) {
            return@withContext false
        }
        if (configDir.usableSpace < contentBytes.size + DISK_SPACE_BUFFER) {
            return@withContext false
        }

        runCatching {
            val target = File(configDir, fileName)
            val temp = File(configDir, "$fileName.tmp")
            temp.outputStream().buffered().use { it.write(contentBytes) }
            if (target.exists() && !target.delete()) return@withContext false
            temp.renameTo(target)
        }.onFailure {
            Log.e(TAG, "保存配置文件失败: $fileName", it)
        }.getOrDefault(false)
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
                filePath = file.absolutePath,
                lastModified = file.lastModified(),
                size = file.length(),
                fileExtension = file.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
            )
        }.onFailure {
            Log.e(TAG, "读取配置文件失败: $safeName", it)
        }.getOrNull()
    }

    suspend fun getAllConfigFiles(): List<AppConfigFile> = withContext(Dispatchers.IO) {
        configDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.canRead() }
            .map { file ->
                AppConfigFile(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    lastModified = file.lastModified(),
                    size = file.length(),
                    fileExtension = file.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
                )
            }
            .sortedByDescending { it.lastModified }
    }

    suspend fun deleteConfigFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidFileName(fileName)) return@withContext false
        File(configDir, fileName).let { it.exists() && it.delete() }
    }

    suspend fun renameConfigFile(oldFileName: String, newFileName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidFileName(oldFileName) || !isValidFileName(newFileName)) return@withContext false
        val oldFile = File(configDir, oldFileName)
        val newFile = File(configDir, newFileName)
        oldFile.exists() && !newFile.exists() && oldFile.renameTo(newFile)
    }

    suspend fun copyConfigFile(sourceFileName: String, targetFileName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidFileName(sourceFileName) || !isValidFileName(targetFileName)) return@withContext false
        val source = File(configDir, sourceFileName)
        val target = File(configDir, targetFileName)
        source.exists() && !target.exists() && runCatching {
            source.copyTo(target)
            true
        }.getOrDefault(false)
    }

    fun getConfigDirectory(): File = configDir

    fun getConfigFilePath(fileName: String): String = File(configDir, fileName).absolutePath

    fun isValidFileName(fileName: String): Boolean {
        val trimmed = fileName.trim()
        val invalidChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        return trimmed.isNotBlank() &&
            trimmed.length <= 255 &&
            !trimmed.startsWith(".") &&
            trimmed.none { it in invalidChars }
    }

    fun getSuggestedFileExtension(fileName: String): String {
        return File(fileName).extension.takeIf { it.isNotBlank() }?.let { ".$it" } ?: ".json"
    }

    fun getSupportedConfigTypes(): List<ConfigFileType> = ConfigFileType.entries

    companion object {
        private const val TAG = "AppConfigManager"
        private const val CONFIG_DIR_NAME = "app_configs"
        private const val MAX_CONFIG_FILE_SIZE = 2 * 1024 * 1024
        private const val DISK_SPACE_BUFFER = 10 * 1024 * 1024
    }
}
