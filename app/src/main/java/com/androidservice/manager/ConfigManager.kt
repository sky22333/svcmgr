package com.androidservice.manager

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.androidservice.data.BinaryConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "config")

class ConfigManager(private val context: Context) {

    private val gson = Gson()

    val configFlow: Flow<BinaryConfig> = context.dataStore.data.map { preferences ->
        val environmentVariables = parseEnvironment(preferences[ENVIRONMENT_VARIABLES_KEY].orEmpty())
        BinaryConfig(
            binaryName = preferences[BINARY_NAME_KEY].orEmpty(),
            argumentsString = preferences[ARGUMENTS_KEY].orEmpty(),
            configFileName = preferences[CONFIG_FILE_NAME_KEY].orEmpty(),
            environmentVariables = environmentVariables,
            autoRestart = preferences[AUTO_RESTART_KEY] ?: false,
            restartDelay = preferences[RESTART_DELAY_KEY] ?: DEFAULT_RESTART_DELAY_MS,
            maxRestarts = preferences[MAX_RESTARTS_KEY] ?: -1
        )
    }

    suspend fun saveConfig(config: BinaryConfig) {
        context.dataStore.edit { preferences ->
            preferences[BINARY_NAME_KEY] = config.binaryName
            preferences[ARGUMENTS_KEY] = config.argumentsString
            preferences[CONFIG_FILE_NAME_KEY] = config.configFileName
            preferences[ENVIRONMENT_VARIABLES_KEY] = gson.toJson(config.environmentVariables)
            preferences[AUTO_RESTART_KEY] = config.autoRestart
            preferences[RESTART_DELAY_KEY] = config.restartDelay
            preferences[MAX_RESTARTS_KEY] = config.maxRestarts
        }
    }

    fun exportConfigToJson(config: BinaryConfig): String = gson.toJson(config)

    fun importConfigFromJson(jsonString: String): BinaryConfig? {
        return runCatching { gson.fromJson(jsonString, BinaryConfig::class.java) }
            .onFailure { Log.w(TAG, "配置 JSON 无效", it) }
            .getOrNull()
    }

    suspend fun saveConfigToFile(config: BinaryConfig): Boolean {
        return runCatching {
            File(context.filesDir, CONFIG_FILE_NAME).writeText(exportConfigToJson(config), Charsets.UTF_8)
            true
        }.onFailure {
            Log.e(TAG, "保存配置文件失败", it)
        }.getOrDefault(false)
    }

    suspend fun loadConfigFromFile(): BinaryConfig? {
        return runCatching {
            val configFile = File(context.filesDir, CONFIG_FILE_NAME)
            if (configFile.exists()) importConfigFromJson(configFile.readText(Charsets.UTF_8)) else null
        }.onFailure {
            Log.e(TAG, "加载配置文件失败", it)
        }.getOrNull()
    }

    fun getConfigFilePath(): String = File(context.filesDir, CONFIG_FILE_NAME).absolutePath

    private fun parseEnvironment(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(json, type)
        }.getOrDefault(emptyMap())
    }

    companion object {
        private const val TAG = "ConfigManager"
        private const val CONFIG_FILE_NAME = "config.json"
        private const val DEFAULT_RESTART_DELAY_MS = 5_000L

        private val BINARY_NAME_KEY = stringPreferencesKey("binary_name")
        private val ARGUMENTS_KEY = stringPreferencesKey("arguments")
        private val CONFIG_FILE_NAME_KEY = stringPreferencesKey("config_file_name")
        private val ENVIRONMENT_VARIABLES_KEY = stringPreferencesKey("environment_variables")
        private val AUTO_RESTART_KEY = booleanPreferencesKey("auto_restart")
        private val RESTART_DELAY_KEY = longPreferencesKey("restart_delay")
        private val MAX_RESTARTS_KEY = intPreferencesKey("max_restarts")
    }
}
