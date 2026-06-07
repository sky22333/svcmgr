package com.androidservice.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocale
import java.text.SimpleDateFormat

@Composable
fun rememberDateTimeFormatter(pattern: String): SimpleDateFormat {
    val locale = LocalLocale.current.platformLocale
    return remember(locale, pattern) { SimpleDateFormat(pattern, locale) }
}
