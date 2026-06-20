package com.androidservice.manager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object RemoteConfigFetcher {

    suspend fun fetch(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmedUrl = url.trim()
            require(isSupportedUrl(trimmedUrl)) { "仅支持 http/https 链接" }

            val connection = (URL(trimmedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode !in HTTP_OK..HTTP_OK_MAX) {
                    error("HTTP $responseCode")
                }

                connection.inputStream.use { input ->
                    val bytes = input.readBytes()
                    if (bytes.size > MAX_CONFIG_BYTES) {
                        error("远程配置超过 2MB")
                    }
                    val content = bytes.toString(Charsets.UTF_8)
                    require(content.isNotBlank()) { "远程配置内容为空" }
                    content
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    fun isSupportedUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
    }

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_CONFIG_BYTES = 2 * 1024 * 1024
    private const val HTTP_OK = 200
    private const val HTTP_OK_MAX = 299
}
