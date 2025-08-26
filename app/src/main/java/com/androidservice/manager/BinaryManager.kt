package com.androidservice.manager

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.androidservice.data.BinaryInfo
import java.io.*

class BinaryManager(private val context: Context) {

    companion object {
        private const val BINARIES_DIR = "binaries"
        private val SUPPORTED_ABIS = listOf("arm64-v8a")
        private const val LIB_PREFIX = "lib"
        private const val LIB_SUFFIX = ".so"
    }

    private val binariesDir: File by lazy {
        File(context.filesDir, BINARIES_DIR).apply {
            if (!exists()) mkdirs()
        }
    }
    
    private val nativeLibDir: File by lazy {
        File(context.applicationInfo.nativeLibraryDir)
    }
    
    // 缓存可用内核名称，直到应用重启
    private var cachedAvailableKernelNames: List<String>? = null

    suspend fun initializeBinaries(): List<BinaryInfo> = withContext(Dispatchers.IO) {
        val binaryInfoList = mutableListOf<BinaryInfo>()
        
        // 检查系统自动提取的native library
        val nativeLibBinary = checkNativeLibrary()
        if (nativeLibBinary != null) {
            binaryInfoList.add(nativeLibBinary)
        }
        
        binaryInfoList
    }

    private var cachedNativeLibraryInfo: BinaryInfo? = null
    private var lastNativeLibraryCheck: Long = 0
    private val CACHE_VALIDITY_MS = 28800000L // 8小时缓存有效期

    private suspend fun checkNativeLibrary(): BinaryInfo? = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        
        // 使用缓存减少重复文件系统访问
        if (cachedNativeLibraryInfo != null && (currentTime - lastNativeLibraryCheck) < CACHE_VALIDITY_MS) {
            return@withContext cachedNativeLibraryInfo
        }
        
        try {
            val binaryInfo = createBinaryInfoFromNativeLib()
            cachedNativeLibraryInfo = binaryInfo
            lastNativeLibraryCheck = currentTime
            return@withContext binaryInfo
        } catch (e: Exception) {
            Log.e("BinaryManager", "Failed to check native library", e)
        }
        null
    }
    
    private suspend fun createBinaryInfoFromNativeLib(): BinaryInfo? = withContext(Dispatchers.IO) {
        return@withContext try {
            val kernelNames = mutableSetOf<String>()
            
            if (nativeLibDir.exists() && nativeLibDir.canRead()) {
                nativeLibDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.startsWith(LIB_PREFIX) && file.name.endsWith(LIB_SUFFIX)) {
                        val kernelName = extractKernelName(file.name)
                        if (kernelName.isNotEmpty()) {
                            kernelNames.add(kernelName)
                        }
                    }
                }
            }
            
            val primaryKernel = kernelNames.firstOrNull() ?: return@withContext null
            val libFileName = "$LIB_PREFIX$primaryKernel$LIB_SUFFIX"
            
            val libFile = File(nativeLibDir, libFileName)
            if (libFile.exists() && libFile.canRead()) {
                BinaryInfo(
                    name = primaryKernel,
                    abi = "arm64-v8a",
                    path = libFile.absolutePath,
                    size = libFile.length(),
                    isExecutable = libFile.canExecute()
                )
            } else {
                val privateBinaryFile = File(binariesDir, primaryKernel)
                if (privateBinaryFile.exists() && privateBinaryFile.canRead()) {
                    BinaryInfo(
                        name = primaryKernel,
                        abi = "arm64-v8a",
                        path = privateBinaryFile.absolutePath,
                        size = privateBinaryFile.length(),
                        isExecutable = privateBinaryFile.canExecute()
                    )
                } else null
            }
        } catch (e: SecurityException) {
            Log.w("BinaryManager", "Access denied to native library, using private directory fallback")
            null
        }
    }



    fun getBinaryPath(abi: String, binaryName: String): String {
        return File(File(binariesDir, abi), binaryName).absolutePath
    }


    private fun getSupportedAbis(): List<String> {
        val deviceAbis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.toList()
        } else {
            @Suppress("DEPRECATION")
            listOf(Build.CPU_ABI, Build.CPU_ABI2).filterNotNull()
        }
        
        return SUPPORTED_ABIS.filter { it in deviceAbis }
    }

    suspend fun getPreferredBinary(binaryName: String): BinaryInfo? = withContext(Dispatchers.IO) {
        try {
            // 检查是否为可用的内核名称
            val availableKernels = getAvailableKernelNames()
            if (binaryName in availableKernels) {
                val libFileName = "$LIB_PREFIX$binaryName$LIB_SUFFIX"
                val libFile = File(nativeLibDir, libFileName)
                if (libFile.exists() && libFile.canRead()) {
                    return@withContext BinaryInfo(
                        name = binaryName,
                        abi = "arm64-v8a",
                        path = libFile.absolutePath,
                        size = libFile.length(),
                        isExecutable = libFile.canExecute()
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("BinaryManager", "Failed to get preferred binary: $binaryName", e)
        }
        
        val deviceAbis = getSupportedAbis()
        val availableBinaries = initializeBinaries().filter { it.name == binaryName }
        
        // 按ABI优先级查找
        for (abi in deviceAbis) {
            val binary = availableBinaries.find { it.abi == abi }
            if (binary != null && binary.isExecutable) {
                return@withContext binary
            }
        }
        
        return@withContext null
    }

    suspend fun validateBinary(binaryPath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(binaryPath)
        file.exists() && file.canExecute() && file.length() > 0
    }
    
    /**
     * 获取可用的内核名称列表
     * 从native library目录扫描.so文件并提取内核名称
     */
    suspend fun getAvailableKernelNames(): List<String> = withContext(Dispatchers.IO) {
        // 使用缓存减少重复文件系统访问
        if (cachedAvailableKernelNames != null) {
            return@withContext cachedAvailableKernelNames!!
        }
        
        val kernelNames = mutableSetOf<String>()
        
        try {
            // 扫描native library目录
            if (nativeLibDir.exists() && nativeLibDir.canRead()) {
                nativeLibDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.startsWith(LIB_PREFIX) && file.name.endsWith(LIB_SUFFIX)) {
                        val kernelName = extractKernelName(file.name)
                        if (kernelName.isNotEmpty()) {
                            kernelNames.add(kernelName)
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e("BinaryManager", "Failed to scan kernel names", e)
            // 发生异常时返回空列表
        }
        
        val result = kernelNames.toList().sorted()
        cachedAvailableKernelNames = result
        
        return@withContext result
    }
    
    /**
     * 从.so文件名提取内核名称
     */
    private fun extractKernelName(fileName: String): String {
        return if (fileName.startsWith(LIB_PREFIX) && fileName.endsWith(LIB_SUFFIX)) {
            fileName.substring(LIB_PREFIX.length, fileName.length - LIB_SUFFIX.length)
        } else {
            ""
        }
    }
    
    /**
     * 清除内核名称缓存，强制重新扫描
     */
    fun clearKernelNamesCache() {
        cachedAvailableKernelNames = null
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