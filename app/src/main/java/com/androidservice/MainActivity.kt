package com.androidservice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidservice.manager.ThemePreferenceManager
import com.androidservice.ui.MainScreen
import com.androidservice.ui.theme.AndroidServiceTheme
import com.androidservice.ui.theme.ThemeSeedOptions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()

        setContent {
            val defaultSeedArgb = ThemeSeedOptions.first().color.toArgb()
            val themePreferenceManager = remember {
                ThemePreferenceManager(
                    context = applicationContext,
                    defaultSeedColorArgb = defaultSeedArgb
                )
            }
            val scope = rememberCoroutineScope()
            val seedArgb by themePreferenceManager.seedColorArgbFlow.collectAsStateWithLifecycle(
                initialValue = defaultSeedArgb
            )

            AndroidServiceTheme(seedColor = Color(seedArgb)) {
                MainScreen(
                    seedColor = Color(seedArgb),
                    onSeedColorChange = { color ->
                        scope.launch { themePreferenceManager.saveSeedColorArgb(color.toArgb()) }
                    }
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
