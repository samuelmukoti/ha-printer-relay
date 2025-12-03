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
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val TOKEN_EXPIRY = longPreferencesKey("token_expiry")
        val INGRESS_URL = stringPreferencesKey("ingress_url")
        val INGRESS_SESSION_TOKEN = stringPreferencesKey("ingress_session_token")  // HA ingress session token
        val ADDON_SLUG = stringPreferencesKey("addon_slug")  // Discovered addon slug
        val TUNNEL_URL = stringPreferencesKey("tunnel_url")  // Remote tunnel URL (LocalTunnel/Cloudflare)
        val TUNNEL_PROVIDER = stringPreferencesKey("tunnel_provider")  // "localtunnel", "cloudflare_quick", "cloudflare_named"
        val IS_CONFIGURED = booleanPreferencesKey("is_configured")
        val AUTH_TYPE = stringPreferencesKey("auth_type") // "oauth" or "long_lived"
        val DEFAULT_PRINTER = stringPreferencesKey("default_printer")
        val DEFAULT_COPIES = intPreferencesKey("default_copies")
        val DEFAULT_DUPLEX = booleanPreferencesKey("default_duplex")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val DEFAULT_PAPER_SIZE = stringPreferencesKey("default_paper_size")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }

    val haUrl: Flow<String> = context.dataStore.data.map { it[Keys.HA_URL] ?: "" }
    val haToken: Flow<String> = context.dataStore.data.map { it[Keys.HA_TOKEN] ?: "" }
    val refreshToken: Flow<String> = context.dataStore.data.map { it[Keys.REFRESH_TOKEN] ?: "" }
    val tokenExpiry: Flow<Long> = context.dataStore.data.map { it[Keys.TOKEN_EXPIRY] ?: 0L }
    val ingressUrl: Flow<String> = context.dataStore.data.map { it[Keys.INGRESS_URL] ?: "" }
    val ingressSessionToken: Flow<String> = context.dataStore.data.map { it[Keys.INGRESS_SESSION_TOKEN] ?: "" }
    val addonSlug: Flow<String> = context.dataStore.data.map { it[Keys.ADDON_SLUG] ?: "" }
    val tunnelUrl: Flow<String> = context.dataStore.data.map { it[Keys.TUNNEL_URL] ?: "" }
    val tunnelProvider: Flow<String> = context.dataStore.data.map { it[Keys.TUNNEL_PROVIDER] ?: "" }
    val isConfigured: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_CONFIGURED] ?: false }
    val authType: Flow<String> = context.dataStore.data.map { it[Keys.AUTH_TYPE] ?: "oauth" }
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

    /**
     * Save OAuth token data after successful authentication.
     */
    suspend fun saveOAuthToken(accessToken: String, refreshToken: String?, expiresIn: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HA_TOKEN] = accessToken
            prefs[Keys.AUTH_TYPE] = "oauth"
            if (refreshToken != null) {
                prefs[Keys.REFRESH_TOKEN] = refreshToken
            }
            if (expiresIn > 0) {
                // Calculate expiry timestamp (current time + expires_in seconds - 60 second buffer)
                prefs[Keys.TOKEN_EXPIRY] = System.currentTimeMillis() + (expiresIn - 60) * 1000
            }
        }
    }

    /**
     * Save the discovered ingress URL and session token.
     */
    suspend fun saveIngressUrl(ingressUrl: String, sessionToken: String? = null, addonSlug: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[Keys.INGRESS_URL] = ingressUrl
            if (sessionToken != null) {
                prefs[Keys.INGRESS_SESSION_TOKEN] = sessionToken
            }
            if (addonSlug != null) {
                prefs[Keys.ADDON_SLUG] = addonSlug
            }
        }
    }

    /**
     * Save the tunnel URL for remote access.
     */
    suspend fun saveTunnelUrl(tunnelUrl: String, provider: String = "localtunnel") {
        context.dataStore.edit { prefs ->
            prefs[Keys.TUNNEL_URL] = tunnelUrl
            prefs[Keys.TUNNEL_PROVIDER] = provider
        }
    }

    /**
     * Clear the tunnel URL (when tunnel is disabled or URL changes).
     */
    suspend fun clearTunnelUrl() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.TUNNEL_URL)
            prefs.remove(Keys.TUNNEL_PROVIDER)
        }
    }

    /**
     * Update just the ingress session token (for session refresh).
     */
    suspend fun updateIngressSessionToken(sessionToken: String, ingressUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.INGRESS_SESSION_TOKEN] = sessionToken
            prefs[Keys.INGRESS_URL] = ingressUrl
        }
    }

    /**
     * Save just the HA URL (before OAuth flow).
     */
    suspend fun saveHaUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HA_URL] = url
        }
    }

    /**
     * Mark configuration as complete.
     */
    suspend fun setConfigured(configured: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_CONFIGURED] = configured
        }
    }

    /**
     * Clear authentication data (logout).
     */
    suspend fun clearAuth() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.HA_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.TOKEN_EXPIRY)
            prefs.remove(Keys.INGRESS_URL)
            prefs.remove(Keys.INGRESS_SESSION_TOKEN)
            prefs.remove(Keys.ADDON_SLUG)
            prefs[Keys.IS_CONFIGURED] = false
        }
    }
}
