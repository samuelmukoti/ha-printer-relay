package com.harelayprint.data.auth

import android.util.Log
import com.harelayprint.data.api.IngressSessionResponse
import com.harelayprint.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Home Assistant ingress sessions for accessing addon APIs.
 *
 * The ingress session flow:
 * 1. Create session: POST /api/hassio/ingress/session with Bearer token
 * 2. Validate/refresh: POST /api/hassio/ingress/validate_session
 * 3. Use session: Include Cookie: ingress_session=<token> in requests
 *
 * Sessions expire periodically (roughly 60 seconds), so we validate and refresh
 * before each major operation or when requests fail with 401.
 */
@Singleton
class IngressSessionManager @Inject constructor(
    private val settings: SettingsDataStore,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "IngressSessionManager"
        private const val SESSION_TIMEOUT_SECONDS = 10L
        private const val SESSION_REFRESH_INTERVAL_MS = 50_000L // Refresh every 50 seconds (sessions last ~60s)
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var lastRefreshTime: Long = 0
    private var cachedSession: String? = null

    private val sessionClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(SESSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(SESSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Get a valid ingress session, creating or refreshing as needed.
     * Returns null if unable to create/refresh session.
     */
    suspend fun getValidSession(): String? = mutex.withLock {
        val haUrl = settings.haUrl.first()
        val token = settings.haToken.first()
        val addonSlug = settings.addonSlug.first()

        if (haUrl.isEmpty() || token.isEmpty() || addonSlug.isEmpty()) {
            Log.d(TAG, "Missing required settings for session: haUrl=$haUrl, hasToken=${token.isNotEmpty()}, slug=$addonSlug")
            return null
        }

        val now = System.currentTimeMillis()
        val existingSession = cachedSession ?: settings.ingressSessionToken.first()

        // Check if we need to refresh
        if (existingSession.isNotEmpty() && (now - lastRefreshTime) < SESSION_REFRESH_INTERVAL_MS) {
            Log.d(TAG, "Using cached session (${(now - lastRefreshTime) / 1000}s old)")
            return existingSession
        }

        // Try to validate/extend existing session
        if (existingSession.isNotEmpty()) {
            if (validateSession(haUrl, token, existingSession)) {
                Log.d(TAG, "Session validated successfully")
                lastRefreshTime = now
                cachedSession = existingSession
                return existingSession
            }
            Log.d(TAG, "Session validation failed, creating new session")
        }

        // Create new session
        val newSession = createSession(haUrl, token, addonSlug)
        if (newSession != null) {
            Log.d(TAG, "Created new session")
            cachedSession = newSession
            lastRefreshTime = now
            settings.updateIngressSessionToken(newSession, "$haUrl/api/hassio_ingress/$addonSlug")
            return newSession
        }

        Log.e(TAG, "Failed to create ingress session")
        return null
    }

    /**
     * Force refresh the session (e.g., after a 401 error).
     */
    suspend fun refreshSession(): String? = mutex.withLock {
        cachedSession = null
        lastRefreshTime = 0
        return@withLock null // Force next getValidSession to create new
    }.let {
        getValidSession()
    }

    /**
     * Create a new ingress session.
     */
    private suspend fun createSession(haUrl: String, token: String, addonSlug: String): String? {
        val sessionUrl = "$haUrl/api/hassio/ingress/session"
        Log.d(TAG, "Creating ingress session: $sessionUrl for addon: $addonSlug")

        return withContext(Dispatchers.IO) {
            try {
                val requestBody = """{"addon":"$addonSlug"}""".toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(sessionUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = sessionClient.newCall(request).execute()
                val body = response.body?.string()

                Log.d(TAG, "Session create response: ${response.code}, body: ${body?.take(200)}")

                if (response.isSuccessful && body != null) {
                    try {
                        val sessionResponse = json.decodeFromString<IngressSessionResponse>(body)
                        sessionResponse.data.session
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse session response", e)
                        null
                    }
                } else {
                    Log.e(TAG, "Session creation failed: ${response.code}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Session creation error", e)
                null
            }
        }
    }

    /**
     * Validate and extend an existing session.
     */
    private suspend fun validateSession(haUrl: String, token: String, session: String): Boolean {
        val validateUrl = "$haUrl/api/hassio/ingress/validate_session"
        Log.d(TAG, "Validating session: $validateUrl")

        return withContext(Dispatchers.IO) {
            try {
                val requestBody = """{"session":"$session"}""".toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(validateUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = sessionClient.newCall(request).execute()
                Log.d(TAG, "Session validate response: ${response.code}")

                response.isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "Session validation error", e)
                false
            }
        }
    }

    /**
     * Clear the cached session (e.g., on logout).
     */
    fun clearSession() {
        cachedSession = null
        lastRefreshTime = 0
    }
}
