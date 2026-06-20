package com.androidservice.singbox

import android.content.Intent
import android.os.Build
import com.androidservice.data.BinaryConfig

object SingBoxServiceContract {
    const val ACTION_START = "com.androidservice.START_SINGBOX"
    const val ACTION_STOP = "com.androidservice.STOP_SINGBOX"
    const val EXTRA_BINARY_CONFIG = "binary_config"

    fun usesSingBox(config: BinaryConfig): Boolean =
        config.binaryName == SingBoxConstants.BINARY_NAME
}

fun Intent.readBinaryConfig(): BinaryConfig? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(SingBoxServiceContract.EXTRA_BINARY_CONFIG, BinaryConfig::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(SingBoxServiceContract.EXTRA_BINARY_CONFIG)
    }
}
