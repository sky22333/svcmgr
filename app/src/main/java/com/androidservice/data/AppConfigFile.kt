package com.androidservice.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppConfigFile(
    val fileName: String = "",
    val content: String = "",
    val filePath: String = "",
    val lastModified: Long = 0L,
    val size: Long = 0L,
    val fileExtension: String = ""
) : Parcelable

enum class ConfigFileType(val extension: String, val displayName: String) {
    JSON(".json", "JSON 配置"),
    TOML(".toml", "TOML 配置"),
    YAML(".yaml", "YAML 配置"),
    YML(".yml", "YML 配置"),
    CONF(".conf", "配置文件"),
    TXT(".txt", "文本文件"),
    XML(".xml", "XML 配置");
    
    companion object {
        fun fromExtension(extension: String): ConfigFileType {
            return values().find { it.extension.equals(extension, ignoreCase = true) } ?: TXT
        }
        
        fun getSupportedExtensions(): List<String> {
            return values().map { it.extension }
        }
    }
}