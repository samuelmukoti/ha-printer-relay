package com.harelayprint.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.harelayprint.data.api.PaperSize
import com.harelayprint.data.api.PrintQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val HA_URL = stringPreferencesKey("ha_url")
        val HA_TOKEN = stringPreferencesKey("ha_token")
        val IS_CONFIGURED = booleanPreferencesKey("is_configured")
        val DEFAULT_PRINTER = stringPreferencesKey("default_printer")
        val DEFAULT_COPIES = intPreferencesKey("default_copies")
        val DEFAULT_DUPLEX = booleanPreferencesKey("default_duplex")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val DEFAULT_PAPER_SIZE = stringPreferencesKey("default_paper_size")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }

    val haUrl: Flow<String> = context.dataStore.data.map { it[Keys.HA_URL] ?: "" }
    val haToken: Flow<String> = context.dataStore.data.map { it[Keys.HA_TOKEN] ?: "" }
    val isConfigured: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_CONFIGURED] ?: false }
    val defaultPrinter: Flow<String?> = context.dataStore.data.map { it[Keys.DEFAULT_PRINTER] }
    val defaultCopies: Flow<Int> = context.dataStore.data.map { it[Keys.DEFAULT_COPIES] ?: 1 }
    val defaultDuplex: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEFAULT_DUPLEX] ?: false }
    val defaultQuality: Flow<PrintQuality> = context.dataStore.data.map {
        PrintQuality.entries.find { q -> q.value == it[Keys.DEFAULT_QUALITY] } ?: PrintQuality.NORMAL
    }
    val defaultPaperSize: Flow<PaperSize> = context.dataStore.data.map {
        PaperSize.entries.find { s -> s.value == it[Keys.DEFAULT_PAPER_SIZE] } ?: PaperSize.A4
    }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }

    suspend fun saveConnection(url: String, token: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HA_URL] = url
            prefs[Keys.HA_TOKEN] = token
            prefs[Keys.IS_CONFIGURED] = true
        }
    }

    suspend fun setDefaultPrinter(printerName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_PRINTER] = printerName
        }
    }

    suspend fun setDefaultCopies(copies: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_COPIES] = copies
        }
    }

    suspend fun setDefaultDuplex(duplex: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_DUPLEX] = duplex
        }
    }

    suspend fun setDefaultQuality(quality: PrintQuality) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_QUALITY] = quality.value
        }
    }

    suspend fun setDefaultPaperSize(paperSize: PaperSize) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_PAPER_SIZE] = paperSize.value
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
