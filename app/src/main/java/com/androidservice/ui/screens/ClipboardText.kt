package com.androidservice.ui.screens

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

internal suspend fun Clipboard.getPlainText(): String? {
    val clipData = getClipEntry()?.clipData ?: return null
    if (clipData.itemCount == 0) return null
    return clipData.getItemAt(0)?.text?.toString()
}

internal suspend fun Clipboard.setPlainText(label: String, text: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
}
