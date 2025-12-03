package com.harelayprint.di

import android.content.Context
import android.util.Log
import com.harelayprint.data.api.HaSupervisorApi
import com.harelayprint.data.api.HealthResponse
import com.harelayprint.data.api.RelayPrintApi
import com.harelayprint.data.local.SettingsDataStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): SettingsDataStore = SettingsDataStore(context)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideApiClientFactory(
        okHttpClient: OkHttpClient
    ): ApiClientFactory = ApiClientFactory(okHttpClient, json)

    @Provides
    @Singleton
    fun provideHaAuthManager(
        settings: SettingsDataStore,
        okHttpClient: OkHttpClient
    ): com.harelayprint.data.auth.HaAuthManager =
        com.harelayprint.data.auth.HaAuthManager(settings, okHttpClient)
}

/**
 * Result of addon discovery process.
 */
sealed class AddonDiscoveryResult {
    data class Success(
        val ingressUrl: String,
        val addonSlug: String,
        val version: String,
        val sessionToken: String? = null  // Ingress session token if used
    ) : AddonDiscoveryResult()
    data class Error(val message: String, val code: Int? = null) : AddonDiscoveryResult()
}

/**
 * Factory for creating API clients with dynamic base URLs.
 * Automatically discovers the RelayPrint addon's ingress URL.
 */
class ApiClientFactory(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    companion object {
        private const val TAG = "ApiClientFactory"
        private const val PROBE_TIMEOUT_SECONDS = 5L  // Short timeout for discovery probing
    }

    private var cachedApi: RelayPrintApi? = null
    private var cachedBaseUrl: String? = null
    private var cachedIngressUrl: String? = null
    private var cachedAddonSlug: String? = null

    // OkHttpClient with short timeout for discovery probing
    private val probeClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Discover the RelayPrint addon.
     *
     * Discovery order:
     * 1. Check for Cloudflare Tunnel URL (best for remote access)
     * 2. Direct port 7779 on HA host (local network)
     * 3. Try common local IPs (fallback for remote URLs)
     *
     * Note: HA Ingress does NOT work for REST API calls from mobile apps.
     * Ingress requires session cookies (browser) or Supervisor access (which
     * OAuth/LLAT tokens don't have). See docs/MOBILE_APP_AUTH.md for details.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun discoverAddon(haBaseUrl: String, token: String, cookies: String? = null): AddonDiscoveryResult {
        val normalizedBase = normalizeUrl(haBaseUrl).trimEnd('/')

        // First, verify the token works by checking HA config
        try {
            val supervisorApi = createSupervisorApi(normalizedBase, token)
            val configResponse = supervisorApi.getConfig()
            if (configResponse.isSuccessful) {
                Log.d(TAG, "Token validated - HA version: ${configResponse.body()?.version}")
            } else {
                Log.d(TAG, "Token validation failed: ${configResponse.code()}")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Token validation error: ${e.message}")
        }

        // Extract host from URL for port 7779 access
        val host = try {
            java.net.URL(normalizedBase).host
        } catch (e: Exception) {
            normalizedBase.removePrefix("https://").removePrefix("http://").split("/")[0]
        }

        // Pattern 1: Try direct port 7779 (primary method for mobile app)
        // This requires the addon port to be accessible (local network or tunneled)
        val directUrls = listOf(
            "https://$host:7779",  // HTTPS (if behind reverse proxy)
            "http://$host:7779"    // HTTP (local network)
        )

        Log.d(TAG, "Trying direct port 7779 access...")
        for (directUrl in directUrls) {
            Log.d(TAG, "Trying: $directUrl")
            val probeResult = probeEndpoint(directUrl, token)
            if (probeResult is ProbeResult.Success) {
                Log.d(TAG, "Found RelayPrint at: $directUrl (version: ${probeResult.version})")

                // Check if there's a Cloudflare Tunnel URL configured
                val tunnelUrl = checkForTunnelUrl(directUrl, token)
                if (tunnelUrl != null) {
                    Log.d(TAG, "Cloudflare Tunnel URL found: $tunnelUrl")
                    // Verify tunnel URL works
                    val tunnelProbe = probeEndpoint(tunnelUrl, token)
                    if (tunnelProbe is ProbeResult.Success) {
                        Log.d(TAG, "Using Cloudflare Tunnel for remote access")
                        cachedIngressUrl = tunnelUrl
                        cachedAddonSlug = "relay_print"
                        return AddonDiscoveryResult.Success(
                            ingressUrl = tunnelUrl,
                            addonSlug = "relay_print",
                            version = tunnelProbe.version
                        )
                    }
                }

                // No tunnel or tunnel failed, use direct URL
                cachedIngressUrl = directUrl
                cachedAddonSlug = "relay_print"
                return AddonDiscoveryResult.Success(
                    ingressUrl = directUrl,
                    addonSlug = "relay_print",
                    version = probeResult.version
                )
            }
            if (probeResult is ProbeResult.Error) {
                Log.d(TAG, "Direct port failed: ${probeResult.message} (code: ${probeResult.code})")
            }
        }

        // Pattern 2: Try common local IPs if the host is a remote URL (Nabu Casa, etc)
        // This helps when user is on local WiFi but entered their remote URL
        if (isRemoteUrl(host)) {
            Log.d(TAG, "Remote URL detected, trying common local IPs...")
            val localIps = listOf(
                "192.168.1.1", "192.168.0.1", "192.168.1.100",
                "10.0.0.1", "homeassistant.local"
            )
            for (localIp in localIps) {
                val localUrl = "http://$localIp:7779"
                Log.d(TAG, "Trying local: $localUrl")
                val probeResult = probeEndpoint(localUrl, token)
                if (probeResult is ProbeResult.Success) {
                    Log.d(TAG, "Found RelayPrint at local IP: $localUrl")

                    // Check for tunnel URL
                    val tunnelUrl = checkForTunnelUrl(localUrl, token)
                    if (tunnelUrl != null) {
                        Log.d(TAG, "Cloudflare Tunnel URL found: $tunnelUrl - saving for remote access")
                        // Store tunnel URL but return local for now (faster)
                        // App can switch to tunnel when on mobile data
                        cachedIngressUrl = localUrl
                        cachedAddonSlug = "relay_print"
                        return AddonDiscoveryResult.Success(
                            ingressUrl = localUrl,
                            addonSlug = "relay_print",
                            version = probeResult.version,
                            sessionToken = tunnelUrl  // Store tunnel URL in sessionToken field for now
                        )
                    }

                    cachedIngressUrl = localUrl
                    cachedAddonSlug = "relay_print"
                    return AddonDiscoveryResult.Success(
                        ingressUrl = localUrl,
                        addonSlug = "relay_print",
                        version = probeResult.version
                    )
                }
            }
        }

        // Nothing worked - provide helpful error with setup instructions
        val isNabuCasa = host.contains("nabu.casa") || host.contains("ui.nabu.casa")
        val errorMessage = if (isNabuCasa) {
            "Cannot reach RelayPrint addon remotely.\n\n" +
            "Nabu Casa only proxies Home Assistant (port 8123), not addon APIs.\n\n" +
            "Options:\n" +
            "1. Connect to your home WiFi\n" +
            "2. Enable Cloudflare Tunnel in RelayPrint addon settings\n" +
            "3. Use VPN to access your home network\n\n" +
            "See docs/MOBILE_APP_AUTH.md for setup guide."
        } else {
            "Cannot reach RelayPrint addon.\n\n" +
            "Please ensure:\n" +
            "1. The addon is installed and running\n" +
            "2. Port 7779 is accessible from your network\n" +
            "3. If remote: enable Cloudflare Tunnel in addon settings\n\n" +
            "For local access, connect to your home WiFi."
        }

        return AddonDiscoveryResult.Error(errorMessage, 0)
    }

    /**
     * Check if the addon has a Cloudflare Tunnel URL configured.
     */
    private suspend fun checkForTunnelUrl(baseUrl: String, token: String): String? {
        return try {
            val configUrl = normalizeUrl(baseUrl) + "api/config/remote"
            Log.d(TAG, "Checking for tunnel URL at: $configUrl")

            val request = okhttp3.Request.Builder()
                .url(configUrl)
                .addHeader("Authorization", "Bearer $token")
                .build()

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val response = probeClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null && body.contains("tunnel_url")) {
                        val remoteConfig = json.decodeFromString<com.harelayprint.data.api.RemoteConfigResponse>(body)
                        if (remoteConfig.tunnelEnabled && !remoteConfig.tunnelUrl.isNullOrEmpty()) {
                            remoteConfig.tunnelUrl
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to check tunnel URL: ${e.message}")
            null
        }
    }

    /**
     * Check if URL appears to be a remote/cloud URL (not local network)
     */
    private fun isRemoteUrl(host: String): Boolean {
        return host.contains("nabu.casa") ||
               host.contains("duckdns.org") ||
               host.contains(".com") ||
               host.contains(".net") ||
               host.contains(".org") ||
               (!host.startsWith("192.168.") &&
                !host.startsWith("10.") &&
                !host.startsWith("172.") &&
                !host.contains("localhost") &&
                !host.contains(".local"))
    }

    // Note: Ingress session API (POST /api/hassio/ingress/session) requires
    // Supervisor-level access which OAuth/LLAT tokens don't have.
    // We use direct port 7779 access instead. See docs/MOBILE_APP_AUTH.md.

    private sealed class ProbeResult {
        data class Success(val version: String) : ProbeResult()
        data class Error(val message: String, val code: Int?) : ProbeResult()
    }

    /**
     * Probe endpoint with authentication (bearer token).
     * Uses short timeout for fast discovery.
     * First does a raw HTTP call to check response, then parses JSON if valid.
     */
    private suspend fun probeEndpoint(baseUrl: String, token: String): ProbeResult {
        val healthUrl = normalizeUrl(baseUrl) + "api/health"
        Log.d(TAG, "Probing: $healthUrl")

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Do a raw HTTP call with headers that indicate API request
                val request = okhttp3.Request.Builder()
                    .url(healthUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .addHeader("X-Requested-With", "XMLHttpRequest")  // Indicate AJAX request
                    .build()

                val response = probeClient.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d(TAG, "Probe response: ${response.code} ${response.message}")
                Log.d(TAG, "Response body (first 500 chars): ${responseBody?.take(500)}")

                when {
                    response.isSuccessful && responseBody != null -> {
                        // Check if it's JSON
                        if (responseBody.trimStart().startsWith("{")) {
                            try {
                                val healthResponse = json.decodeFromString<HealthResponse>(responseBody)
                                ProbeResult.Success(healthResponse.version)
                            } catch (e: Exception) {
                                Log.e(TAG, "JSON parse error: ${e.message}")
                                ProbeResult.Error("Invalid JSON response", null)
                            }
                        } else {
                            Log.e(TAG, "Not JSON - got HTML or other content")
                            ProbeResult.Error("Got HTML instead of JSON (auth redirect?)", null)
                        }
                    }
                    response.code == 401 -> {
                        Log.d(TAG, "401 body: ${responseBody?.take(300)}")
                        ProbeResult.Error("Unauthorized", 401)
                    }
                    response.code == 404 -> {
                        ProbeResult.Error("Not found", 404)
                    }
                    else -> {
                        ProbeResult.Error(response.message, response.code)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Probe error: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                ProbeResult.Error(e.message ?: "Network error", null)
            }
        }
    }

    // Note: Cookie-based and no-auth probing removed.
    // Direct port 7779 uses Bearer token auth. See docs/MOBILE_APP_AUTH.md.

    // Note: Supervisor API (listAddons, getAddonInfo) requires Supervisor-level access
    // which OAuth/LLAT tokens don't have. We use direct port 7779 instead.

    /**
     * Create the RelayPrint API client.
     *
     * For direct port 7779 access, we use Bearer token authentication.
     * The addon validates tokens against Home Assistant's API.
     */
    @Suppress("UNUSED_PARAMETER")
    fun createApi(baseUrl: String, token: String, cookies: String? = null): RelayPrintApi {
        // Use cached URL if available, otherwise use baseUrl directly
        val effectiveUrl = cachedIngressUrl ?: baseUrl

        // Return cached API if URL hasn't changed
        if (cachedApi != null && cachedBaseUrl == effectiveUrl) {
            return cachedApi!!
        }

        Log.d(TAG, "Creating API with bearer token for: $effectiveUrl")
        val client = createAuthenticatedClient(token)

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizeUrl(effectiveUrl))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        cachedApi = retrofit.create(RelayPrintApi::class.java)
        cachedBaseUrl = effectiveUrl
        return cachedApi!!
    }

    /**
     * Create API with explicit URL (after discovery or manual entry).
     */
    @Suppress("UNUSED_PARAMETER")
    fun createApiWithIngress(haBaseUrl: String, ingressUrl: String, token: String): RelayPrintApi {
        cachedIngressUrl = ingressUrl
        return createApi(ingressUrl, token)
    }

    private fun createSupervisorApi(baseUrl: String, token: String): HaSupervisorApi {
        val authClient = createAuthenticatedClient(token)

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizeUrl(baseUrl))
            .client(authClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(HaSupervisorApi::class.java)
    }

    private fun createAuthenticatedClient(token: String): OkHttpClient {
        return okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = if (token.isNotEmpty()) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .build()
    }

    fun clearCache() {
        cachedApi = null
        cachedBaseUrl = null
        cachedIngressUrl = null
        cachedAddonSlug = null
    }

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }
        if (!normalized.endsWith("/")) {
            normalized = "$normalized/"
        }
        return normalized
    }
}
