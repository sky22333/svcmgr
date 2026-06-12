package com.androidservice.manager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme")

class ThemePreferenceManager(
    private val context: Context,
    private val defaultSeedColorArgb: Int
) {

    val seedColorArgbFlow: Flow<Int> = context.themeDataStore.data.map { preferences ->
        preferences[SEED_COLOR_KEY] ?: defaultSeedColorArgb
    }

    suspend fun saveSeedColorArgb(argb: Int) {
        context.themeDataStore.edit { preferences ->
            preferences[SEED_COLOR_KEY] = argb
        }
    }

    companion object {
        private val SEED_COLOR_KEY = intPreferencesKey("seed_color_argb")
    }
}
