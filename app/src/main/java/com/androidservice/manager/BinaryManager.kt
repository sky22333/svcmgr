package com.androidservice.manager

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.androidservice.data.BinaryInfo
import java.io.*

class BinaryManager(private val context: Context) {

    companion object {
        private const val BINARIES_DIR = "binaries"
        private val SUPPORTED_ABIS = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    }

    private val binariesDir: File
        get() = File(context.filesDir, BINARIES_DIR)

    suspend fun initializeBinaries(): List<BinaryInfo> = withContext(Dispatchers.IO) {
        val binaryInfoList = mutableListOf<BinaryInfo>()
        
        // 确保二进制目录存在
        if (!binariesDir.exists()) {
            binariesDir.mkdirs()
        }
        
        // 获取当前设备支持的ABI
        val deviceAbis = getSupportedAbis()
        
        for (abi in deviceAbis) {
            try {
                val assetPath = "$abi/"
                val assets = context.assets.list(assetPath)
                
                assets?.forEach { binaryName ->
                    if (binaryName.isNotEmpty() && !binaryName.contains('.')) {
                        val binaryInfo = copyBinaryFromAssets(abi, binaryName)
                        if (binaryInfo != null) {
                            binaryInfoList.add(binaryInfo)
                        }
                    }
                }
            } catch (e: IOException) {
                // ABI目录不存在，跳过
                continue
            }
        }
        
        binaryInfoList
    }

    private suspend fun copyBinaryFromAssets(abi: String, binaryName: String): BinaryInfo? = withContext(Dispatchers.IO) {
        try {
            val assetPath = "$abi/$binaryName"
            val abiDir = File(binariesDir, abi)
            if (!abiDir.exists()) {
                abiDir.mkdirs()
            }
            
            val targetFile = File(abiDir, binaryName)
            
            // 如果文件已存在且大小匹配，跳过复制
            context.assets.openFd(assetPath).use { assetFd ->
                val assetSize = assetFd.length
                if (targetFile.exists() && targetFile.length() == assetSize) {
                    return@withContext BinaryInfo(
                        name = binaryName,
                        abi = abi,
                        path = targetFile.absolutePath,
                        size = assetSize,
                        isExecutable = targetFile.canExecute()
                    )
                }
            }
            
            // 复制文件
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            // 设置可执行权限
            val success = targetFile.setExecutable(true, false)
            
            BinaryInfo(
                name = binaryName,
                abi = abi,
                path = targetFile.absolutePath,
                size = targetFile.length(),
                isExecutable = success && targetFile.canExecute()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getBinaryPath(abi: String, binaryName: String): String {
        return File(File(binariesDir, abi), binaryName).absolutePath
    }

    fun getAllBinaries(): List<BinaryInfo> {
        val binaryInfoList = mutableListOf<BinaryInfo>()
        
        if (!binariesDir.exists()) {
            return binaryInfoList
        }
        
        binariesDir.listFiles()?.forEach { abiDir ->
            if (abiDir.isDirectory && SUPPORTED_ABIS.contains(abiDir.name)) {
                abiDir.listFiles()?.forEach { binaryFile ->
                    if (binaryFile.isFile) {
                        binaryInfoList.add(
                            BinaryInfo(
                                name = binaryFile.name,
                                abi = abiDir.name,
                                path = binaryFile.absolutePath,
                                size = binaryFile.length(),
                                isExecutable = binaryFile.canExecute()
                            )
                        )
                    }
                }
            }
        }
        
        return binaryInfoList
    }

    private fun getSupportedAbis(): List<String> {
        val deviceAbis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.toList()
        } else {
            @Suppress("DEPRECATION")
            listOf(Build.CPU_ABI, Build.CPU_ABI2).filterNotNull()
        }
        
        // 按优先级排序，优先使用设备原生ABI
        return SUPPORTED_ABIS.filter { it in deviceAbis }
    }

    fun getPreferredBinary(binaryName: String): BinaryInfo? {
        val deviceAbis = getSupportedAbis()
        val availableBinaries = getAllBinaries().filter { it.name == binaryName }
        
        // 按ABI优先级查找
        for (abi in deviceAbis) {
            val binary = availableBinaries.find { it.abi == abi }
            if (binary != null && binary.isExecutable) {
                return binary
            }
        }
        
        return null
    }

    suspend fun validateBinary(binaryPath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(binaryPath)
        file.exists() && file.canExecute() && file.length() > 0
    }

    fun deleteBinary(binaryInfo: BinaryInfo): Boolean {
        val file = File(binaryInfo.path)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    suspend fun installBinaryFromExternal(externalPath: String, binaryName: String, abi: String): BinaryInfo? = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(externalPath)
            if (!sourceFile.exists()) {
                return@withContext null
            }
            
            val abiDir = File(binariesDir, abi)
            if (!abiDir.exists()) {
                abiDir.mkdirs()
            }
            
            val targetFile = File(abiDir, binaryName)
            
            sourceFile.copyTo(targetFile, overwrite = true)
            targetFile.setExecutable(true, false)
            
            BinaryInfo(
                name = binaryName,
                abi = abi,
                path = targetFile.absolutePath,
                size = targetFile.length(),
                isExecutable = targetFile.canExecute()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}