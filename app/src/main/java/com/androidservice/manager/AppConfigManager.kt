package com.androidservice.manager

import android.content.Context
import android.util.Log
import com.androidservice.data.AppConfigFile
import com.androidservice.data.ConfigFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class AppConfigManager(private val context: Context) {

    private val configDir: File by lazy {
        File(context.filesDir, CONFIG_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private var configFileCache = mutableMapOf<String, Pair<AppConfigFile, Long>>()
    private val CACHE_VALIDITY_MS = 10000L // 10秒缓存
    
    suspend fun saveConfigFile(appConfigFile: AppConfigFile): Boolean = withContext(Dispatchers.IO) {
        try {
            val trimmedFileName = appConfigFile.fileName.trim()
            if (trimmedFileName.isBlank()) {
                Log.w(TAG, "Cannot save config file with empty name")
                return@withContext false
            }
            
            val file = File(configDir, trimmedFileName)
            val contentBytes = appConfigFile.content.toByteArray(Charsets.UTF_8)
            
            // 在写入之前进行所有验证
            if (!validateFileForSaving(contentBytes, file)) {
                return@withContext false
            }
            
            Log.d(TAG, "Saving config file: ${file.absolutePath} (${contentBytes.size} bytes)")
            
            // 使用原子性写入 - 先写临时文件再重命名
            val tempFile = File(file.parentFile, "${trimmedFileName}.tmp")
            
            try {
                // 高效缓冲写入
                FileOutputStream(tempFile).buffered(8192).use { output ->
                    output.write(contentBytes)
                    output.flush()
                }
                
                // 原子性重命名，确保数据完整性
                if (tempFile.renameTo(file)) {
                    // 更新缓存
                    val updatedConfigFile = appConfigFile.copy(
                        fileName = trimmedFileName,
                        filePath = file.absolutePath,
                        lastModified = System.currentTimeMillis(),
                        size = file.length()
                    )
                    configFileCache[trimmedFileName] = Pair(updatedConfigFile, System.currentTimeMillis())
                    
                    Log.d(TAG, "Config file saved successfully: $trimmedFileName")
                    return@withContext true
                } else {
                    Log.e(TAG, "Failed to rename temp file to final file")
                    tempFile.delete() // 清理临时文件
                    return@withContext false
                }
            } catch (e: Exception) {
                tempFile.delete() // 发生异常时清理临时文件
                throw e
            }
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory while saving config file: ${appConfigFile.fileName}", e)
            System.gc()
            false
        } catch (e: IOException) {
            Log.e(TAG, "IO error saving config file: ${appConfigFile.fileName}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error saving config file: ${appConfigFile.fileName}", e)
            false
        }
    }
    
    private fun validateFileForSaving(contentBytes: ByteArray, file: File): Boolean {
        // 内存压力检查
        if (contentBytes.size > MAX_CONFIG_FILE_SIZE) {
            Log.w(TAG, "Config file too large: ${contentBytes.size} bytes > $MAX_CONFIG_FILE_SIZE bytes")
            return false
        }
        
        // 磁盘空间检查
        val availableSpace = configDir.usableSpace
        val requiredSpace = contentBytes.size + DISK_SPACE_BUFFER
        
        if (availableSpace < requiredSpace) {
            Log.w(TAG, "Insufficient disk space: available=$availableSpace, required=$requiredSpace")
            return false
        }
        
        return true
    }

    companion object {
        private const val TAG = "AppConfigManager"
        private const val CONFIG_DIR_NAME = "app_configs"
        private const val MAX_CONFIG_FILE_SIZE = 2 * 1024 * 1024 // 2MB 限制
        private const val DISK_SPACE_BUFFER = 10 * 1024 * 1024 // 10MB 缓冲空间
    }

    suspend fun loadConfigFile(fileName: String): AppConfigFile? = withContext(Dispatchers.IO) {
        try {
            val trimmedFileName = fileName.trim()
            if (trimmedFileName.isBlank()) {
                Log.w(TAG, "Cannot load config file with empty name")
                return@withContext null
            }
            
            // 检查缓存
            val currentTime = System.currentTimeMillis()
            configFileCache[trimmedFileName]?.let { (cachedFile, cacheTime) ->
                if (currentTime - cacheTime < CACHE_VALIDITY_MS) {
                    // 验证缓存文件是否仍然有效
                    val file = File(cachedFile.filePath)
                    if (file.exists() && file.lastModified() == cachedFile.lastModified) {
                        Log.d(TAG, "Using cached config file: $trimmedFileName")
                        return@withContext cachedFile
                    }
                }
            }
            
            val file = File(configDir, trimmedFileName)
            
            if (!file.exists()) {
                Log.w(TAG, "Config file does not exist: $trimmedFileName")
                configFileCache.remove(trimmedFileName) // 清理失效缓存
                return@withContext null
            }
            
            val content = FileInputStream(file).buffered(8192).use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
            
            val extension = file.extension.let { ext ->
                if (ext.isNotEmpty()) ".$ext" else ""
            }
            
            val configFile = AppConfigFile(
                fileName = trimmedFileName,
                content = content,
                filePath = file.absolutePath,
                lastModified = file.lastModified(),
                size = file.length(),
                fileExtension = extension
            )
            
            // 更新缓存
            configFileCache[trimmedFileName] = Pair(configFile, currentTime)
            
            Log.d(TAG, "Config file loaded successfully: $trimmedFileName")
            configFile
            
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load config file: $fileName", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error loading config file: $fileName", e)
            null
        }
    }

    suspend fun getAllConfigFiles(): List<AppConfigFile> = withContext(Dispatchers.IO) {
        try {
            val files = configDir.listFiles()
            
            if (files.isNullOrEmpty()) {
                Log.d(TAG, "No config files found")
                return@withContext emptyList()
            }
            
            val result = ArrayList<AppConfigFile>(files.size)
            
            for (file in files) {
                try {
                    // 快速过滤：跳过不可读的文件
                    if (!file.isFile || !file.canRead()) continue
                    
                    val extension = if (file.extension.isNotEmpty()) {
                        ".${file.extension}"
                    } else {
                        ""
                    }
                    
                    result.add(AppConfigFile(
                        fileName = file.name,
                        content = "", // 列表显示时不加载内容，提升性能
                        filePath = file.absolutePath,
                        lastModified = file.lastModified(),
                        size = file.length(),
                        fileExtension = extension
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing file: ${file.name}", e)
                }
            }
            
            result.sortByDescending { it.lastModified }
            result
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied accessing config directory", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list config files", e)
            emptyList()
        }
    }

    suspend fun deleteConfigFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(configDir, fileName)
            
            if (!file.exists()) {
                Log.w(TAG, "Config file does not exist for deletion: $fileName")
                return@withContext false
            }
            
            val deleted = file.delete()
            
            if (deleted) {
                Log.d(TAG, "Config file deleted successfully: $fileName")
            } else {
                Log.e(TAG, "Failed to delete config file: $fileName")
            }
            
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting config file: $fileName", e)
            false
        }
    }

    suspend fun renameConfigFile(oldFileName: String, newFileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val oldFile = File(configDir, oldFileName)
            val newFile = File(configDir, newFileName)
            
            if (!oldFile.exists()) {
                Log.w(TAG, "Source file does not exist for rename: $oldFileName")
                return@withContext false
            }
            
            if (newFile.exists()) {
                Log.w(TAG, "Target file already exists for rename: $newFileName")
                return@withContext false
            }
            
            val renamed = oldFile.renameTo(newFile)
            
            if (renamed) {
                Log.d(TAG, "Config file renamed successfully: $oldFileName -> $newFileName")
            } else {
                Log.e(TAG, "Failed to rename config file: $oldFileName -> $newFileName")
            }
            
            renamed
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming config file: $oldFileName -> $newFileName", e)
            false
        }
    }

    fun getConfigDirectory(): File = configDir

    fun getConfigFilePath(fileName: String): String {
        return File(configDir, fileName).absolutePath
    }

    fun isValidFileName(fileName: String): Boolean {
        if (fileName.isBlank()) return false
        
        // 检查文件名是否包含非法字符
        val invalidChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        return fileName.none { it in invalidChars } && fileName.length <= 255
    }

    fun getSuggestedFileExtension(fileName: String): String {
        val extension = File(fileName).extension
        return if (extension.isNotEmpty()) {
            ".$extension"
        } else {
            ".json" // 默认扩展名
        }
    }

    fun getSupportedConfigTypes(): List<ConfigFileType> {
        return ConfigFileType.values().toList()
    }

    suspend fun copyConfigFile(sourceFileName: String, targetFileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(configDir, sourceFileName)
            val targetFile = File(configDir, targetFileName)
            
            if (!sourceFile.exists()) {
                Log.w(TAG, "Source file does not exist for copy: $sourceFileName")
                return@withContext false
            }
            
            if (targetFile.exists()) {
                Log.w(TAG, "Target file already exists for copy: $targetFileName")
                return@withContext false
            }
            
            sourceFile.copyTo(targetFile)
            Log.d(TAG, "Config file copied successfully: $sourceFileName -> $targetFileName")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error copying config file: $sourceFileName -> $targetFileName", e)
            false
        }
    }
}