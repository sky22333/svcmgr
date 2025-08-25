package com.androidservice.manager

import android.content.Context
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "config")

class ConfigManager(private val context: Context) {

    companion object {
        private val BINARY_NAME_KEY = stringPreferencesKey("binary_name")
        private val ARGUMENTS_KEY = stringPreferencesKey("arguments")
        private val WORKING_DIRECTORY_KEY = stringPreferencesKey("working_directory")
        private val ENVIRONMENT_VARIABLES_KEY = stringPreferencesKey("environment_variables")
        private val AUTO_RESTART_KEY = booleanPreferencesKey("auto_restart")
        private val RESTART_DELAY_KEY = longPreferencesKey("restart_delay")
        private val MAX_RESTARTS_KEY = intPreferencesKey("max_restarts")
        
        private const val CONFIG_FILE_NAME = "config.json"
    }

    private val gson = Gson()

    suspend fun saveConfig(config: BinaryConfig) {
        context.dataStore.edit { preferences ->
            preferences[BINARY_NAME_KEY] = config.binaryName
            preferences[ARGUMENTS_KEY] = gson.toJson(config.arguments)
            preferences[WORKING_DIRECTORY_KEY] = config.workingDirectory
            preferences[ENVIRONMENT_VARIABLES_KEY] = gson.toJson(config.environmentVariables)
            preferences[AUTO_RESTART_KEY] = config.autoRestart
            preferences[RESTART_DELAY_KEY] = config.restartDelay
            preferences[MAX_RESTARTS_KEY] = config.maxRestarts
        }
    }

    val configFlow: Flow<BinaryConfig> = context.dataStore.data.map { preferences ->
        val argumentsJson = preferences[ARGUMENTS_KEY] ?: "[]"
        val environmentVariablesJson = preferences[ENVIRONMENT_VARIABLES_KEY] ?: "{}"
        
        val arguments = try {
            gson.fromJson<List<String>>(argumentsJson, object : TypeToken<List<String>>() {}.type)
        } catch (e: Exception) {
            emptyList<String>()
        }
        
        val environmentVariables = try {
            gson.fromJson<Map<String, String>>(environmentVariablesJson, object : TypeToken<Map<String, String>>() {}.type)
        } catch (e: Exception) {
            emptyMap<String, String>()
        }

        BinaryConfig(
            binaryName = preferences[BINARY_NAME_KEY] ?: "",
            arguments = arguments,
            workingDirectory = preferences[WORKING_DIRECTORY_KEY] ?: "",
            environmentVariables = environmentVariables,
            autoRestart = preferences[AUTO_RESTART_KEY] ?: true,
            restartDelay = preferences[RESTART_DELAY_KEY] ?: 5000L,
            maxRestarts = preferences[MAX_RESTARTS_KEY] ?: -1
        )
    }

    suspend fun loadConfig(): BinaryConfig {
        return configFlow.first()
    }

    suspend fun exportConfigToJson(config: BinaryConfig): String {
        return gson.toJson(config)
    }

    suspend fun importConfigFromJson(jsonString: String): BinaryConfig? {
        return try {
            gson.fromJson(jsonString, BinaryConfig::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveConfigToFile(config: BinaryConfig): Boolean {
        return try {
            val configFile = File(context.filesDir, CONFIG_FILE_NAME)
            val jsonString = exportConfigToJson(config)
            configFile.writeText(jsonString)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun loadConfigFromFile(): BinaryConfig? {
        return try {
            val configFile = File(context.filesDir, CONFIG_FILE_NAME)
            if (configFile.exists()) {
                val jsonString = configFile.readText()
                importConfigFromJson(jsonString)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun clearConfig() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    fun getConfigFilePath(): String {
        return File(context.filesDir, CONFIG_FILE_NAME).absolutePath
    }
}

