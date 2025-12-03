package com.harelayprint.data.auth

import android.net.Uri
import android.util.Log
import com.harelayprint.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages OAuth2 authentication with Home Assistant.
 *
 * Flow:
 * 1. Generate PKCE code verifier and challenge
 * 2. Build authorization URL for WebView
 * 3. User logs in and approves in WebView
 * 4. WebView intercepts redirect with authorization code
 * 5. App exchanges code for access token
 * 6. Token is used for ingress API access
 *
 * Note: Home Assistant OAuth requires redirect_uri to start with client_id,
 * so we use a WebView to intercept the redirect ourselves.
 */
@Singleton
class HaAuthManager @Inject constructor(
    private val settings: SettingsDataStore,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "HaAuthManager"

        // OAuth2 configuration for Home Assistant
        // client_id can be any URL that identifies our app
        const val CLIENT_ID = "https://github.com/samuelmukoti/ha-printer-relay"

        // For HA OAuth, redirect_uri must start with client_id
        // We'll use this and intercept the redirect in WebView
        const val REDIRECT_URI = "https://github.com/samuelmukoti/ha-printer-relay/oauth/callback"

        // HA OAuth2 endpoints (relative to base URL)
        private const val AUTHORIZE_PATH = "/auth/authorize"
        private const val TOKEN_PATH = "/auth/token"

        // PKCE parameters
        private const val CODE_VERIFIER_LENGTH = 64
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Store code verifier for PKCE flow
    private var codeVerifier: String? = null

    /**
     * Build the authorization URL for the OAuth2 flow.
     * The calling code should display this in a WebView and intercept
     * redirects to REDIRECT_URI.
     */
    fun buildAuthUrl(haBaseUrl: String): String {
        // Generate PKCE code verifier and challenge
        codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier!!)

        val normalizedUrl = normalizeUrl(haBaseUrl)

        // Build authorization URL
        val authUrl = Uri.parse(normalizedUrl + AUTHORIZE_PATH)
            .buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()

        Log.d(TAG, "Built auth URL: $authUrl")
        return authUrl
    }

    /**
     * Check if a URL is the OAuth callback redirect.
     */
    fun isOAuthCallback(url: String): Boolean {
        return url.startsWith(REDIRECT_URI)
    }

    /**
     * Extract authorization code from callback URL.
     */
    fun extractAuthCode(callbackUrl: String): String? {
        val uri = Uri.parse(callbackUrl)
        return uri.getQueryParameter("code")
    }

    /**
     * Extract error from callback URL if present.
     */
    fun extractError(callbackUrl: String): String? {
        val uri = Uri.parse(callbackUrl)
        val error = uri.getQueryParameter("error")
        val errorDescription = uri.getQueryParameter("error_description")
        return if (error != null) {
            errorDescription ?: error
        } else {
            null
        }
    }

    /**
     * Exchange authorization code for access token.
     */
    suspend fun exchangeCodeForToken(code: String): AuthResult = withContext(Dispatchers.IO) {
        if (codeVerifier == null) {
            return@withContext AuthResult.Error("No code verifier found. Please restart the login process.")
        }

        val haBaseUrl = settings.haUrl.first()
        if (haBaseUrl.isEmpty()) {
            return@withContext AuthResult.Error("Home Assistant URL not configured")
        }

        val normalizedUrl = normalizeUrl(haBaseUrl)
        val tokenUrl = normalizedUrl + TOKEN_PATH

        val formBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("client_id", CLIENT_ID)
            .add("redirect_uri", REDIRECT_URI)
            .add("code_verifier", codeVerifier!!)
            .build()

        val request = Request.Builder()
            .url(tokenUrl)
            .post(formBody)
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val tokenResponse = json.decodeFromString<TokenResponse>(responseBody)

                // Save the token
                settings.saveOAuthToken(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken,
                    expiresIn = tokenResponse.expiresIn
                )

                Log.d(TAG, "Successfully obtained access token (expires in ${tokenResponse.expiresIn}s)")
                AuthResult.Success(tokenResponse.accessToken)
            } else {
                Log.e(TAG, "Token exchange failed: ${response.code} - $responseBody")
                AuthResult.Error("Failed to obtain access token: ${response.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange error", e)
            AuthResult.Error(e.message ?: "Network error during authentication")
        } finally {
            // Clear the code verifier
            codeVerifier = null
        }
    }

    /**
     * Refresh the access token using the refresh token.
     */
    suspend fun refreshToken(): AuthResult = withContext(Dispatchers.IO) {
        val haBaseUrl = settings.haUrl.first()
        val currentRefreshToken = settings.refreshToken.first()

        if (haBaseUrl.isEmpty() || currentRefreshToken.isEmpty()) {
            return@withContext AuthResult.Error("Not authenticated")
        }

        val normalizedUrl = normalizeUrl(haBaseUrl)
        val tokenUrl = normalizedUrl + TOKEN_PATH

        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", currentRefreshToken)
            .add("client_id", CLIENT_ID)
            .build()

        val request = Request.Builder()
            .url(tokenUrl)
            .post(formBody)
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val tokenResponse = json.decodeFromString<TokenResponse>(responseBody)

                // Save the new token
                settings.saveOAuthToken(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken ?: currentRefreshToken,
                    expiresIn = tokenResponse.expiresIn
                )

                Log.d(TAG, "Successfully refreshed access token")
                AuthResult.Success(tokenResponse.accessToken)
            } else {
                Log.e(TAG, "Token refresh failed: ${response.code} - $responseBody")
                AuthResult.Error("Failed to refresh token")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh error", e)
            AuthResult.Error(e.message ?: "Network error during token refresh")
        }
    }

    /**
     * Check if we have a valid (non-expired) token.
     */
    suspend fun hasValidToken(): Boolean {
        val token = settings.haToken.first()
        val expiry = settings.tokenExpiry.first()

        if (token.isEmpty()) return false
        if (expiry <= 0) return true // Long-lived token (no expiry)

        return System.currentTimeMillis() < expiry
    }

    /**
     * Get the current access token, refreshing if needed.
     */
    suspend fun getValidToken(): String? {
        if (!hasValidToken()) {
            // Try to refresh
            val result = refreshToken()
            if (result is AuthResult.Error) {
                return null
            }
        }
        return settings.haToken.first().takeIf { it.isNotEmpty() }
    }

    /**
     * Clear all authentication data.
     */
    suspend fun logout() {
        settings.clearAuth()
    }

    // PKCE helper functions

    private fun generateCodeVerifier(): String {
        val random = SecureRandom()
        val bytes = ByteArray(CODE_VERIFIER_LENGTH)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        return normalized.trimEnd('/')
    }
}

sealed class AuthResult {
    data class Success(val accessToken: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("token_type") val tokenType: String = "Bearer"
)
