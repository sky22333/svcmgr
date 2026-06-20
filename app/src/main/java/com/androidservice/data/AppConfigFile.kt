package com.androidservice.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppConfigFile(
    val fileName: String = "",
    val content: String = "",
    val lastModified: Long = 0L,
    val size: Long = 0L,
    val remoteUrl: String = "",
) : Parcelable

enum class ConfigFileType(val extension: String) {
    JSON(".json"),
    TOML(".toml"),
    YAML(".yaml"),
    YML(".yml"),
    CONF(".conf"),
    TXT(".txt"),
    XML(".xml"),
}
