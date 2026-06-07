package com.androidservice.manager

import android.content.Context
import com.androidservice.data.BinaryInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BinaryManager(private val context: Context) {

    private val nativeLibDir: File by lazy { File(context.applicationInfo.nativeLibraryDir) }
    private var cachedKernelNames: List<String>? = null

    suspend fun initializeBinaries(): List<BinaryInfo> = withContext(Dispatchers.IO) {
        getAvailableKernelNames().mapNotNull { name -> buildInfo(name) }
    }

    suspend fun getAvailableKernelNames(): List<String> = withContext(Dispatchers.IO) {
        cachedKernelNames?.let { return@withContext it }

        val names = nativeLibDir
            .takeIf { it.exists() && it.canRead() }
            ?.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.name.startsWith(LIB_PREFIX) && it.name.endsWith(LIB_SUFFIX) }
            .map { it.name.removePrefix(LIB_PREFIX).removeSuffix(LIB_SUFFIX) }
            .filter { it.isNotBlank() && !isHiddenBinaryName(it) }
            .sorted()
            .toList()

        cachedKernelNames = names
        names
    }

    suspend fun getPreferredBinary(binaryName: String): BinaryInfo? = withContext(Dispatchers.IO) {
        if (binaryName.isBlank()) return@withContext null
        buildInfo(binaryName)?.takeIf { it.isExecutable || File(it.path).canRead() }
    }

    private fun buildInfo(binaryName: String): BinaryInfo? {
        val nativeFile = File(nativeLibDir, "$LIB_PREFIX$binaryName$LIB_SUFFIX")
        return nativeFile
            .takeIf { it.exists() && it.canRead() }
            ?.toBinaryInfo(binaryName)
    }

    private fun File.toBinaryInfo(name: String): BinaryInfo {
        return BinaryInfo(
            name = name,
            abi = PRIMARY_ABI,
            path = absolutePath,
            size = length(),
            isExecutable = canExecute()
        )
    }

    companion object {
        private const val PRIMARY_ABI = "arm64-v8a"
        private const val LIB_PREFIX = "lib"
        private const val LIB_SUFFIX = ".so"
        private val HIDDEN_BINARY_PATTERNS = listOf(
            "androidx.graphics*",
            "datastore_shared_*",
        )

        private fun isHiddenBinaryName(name: String): Boolean =
            HIDDEN_BINARY_PATTERNS.any { matchesWildcard(it, name) }

        private fun matchesWildcard(pattern: String, text: String): Boolean {
            if (!pattern.contains('*')) return text == pattern
            val regex = "^" + pattern
                .split('*')
                .joinToString(".*") { Regex.escape(it) } + "$"
            return regex.toRegex().matches(text)
        }
    }
}
