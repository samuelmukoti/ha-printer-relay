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
        val sessionToken: String? = null,  // Ingress session token if used
        val tunnelUrl: String? = null,     // Remote tunnel URL if available
        val tunnelProvider: String? = null // Tunnel provider (localtunnel, cloudflare_quick, etc.)
    ) : AddonDiscoveryResult()
    data class Error(val message: String, val code: Int? = null) : AddonDiscoveryResult()
}

/**
 * Factory for creating API clients with dynamic base URLs.
 * Connects to RelayPrint via tunnel URL.
 */
class ApiClientFactory(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    companion object {
        private const val TAG = "ApiClientFactory"
        private const val PROBE_TIMEOUT_SECONDS = 10L  // Timeout for tunnel probing
    }

    private var cachedApi: RelayPrintApi? = null
    private var cachedBaseUrl: String? = null
    private var cachedTunnelUrl: String? = null

    // OkHttpClient with timeout for tunnel probing
    private val probeClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Result of tunnel URL fetch.
     */
    data class TunnelInfo(
        val url: String,
        val provider: String
    )

    /**
     * Connect to RelayPrint using the provided tunnel URL.
     *
     * The user enters the tunnel URL from the RelayPrint dashboard.
     * This is the simplest and most reliable approach since:
     * - No complex auto-discovery needed
     * - Works from anywhere (no local network required)
     * - User can see and verify the URL
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun discoverAddon(tunnelUrl: String, token: String, cookies: String? = null): AddonDiscoveryResult {
        val normalizedUrl = normalizeUrl(tunnelUrl).trimEnd('/')
        Log.d(TAG, "Connecting to RelayPrint at: $normalizedUrl")

        // Verify the tunnel URL works by calling the health endpoint
        val probeResult = probeEndpoint(normalizedUrl, token)
        return when (probeResult) {
            is ProbeResult.Success -> {
                Log.d(TAG, "Connected to RelayPrint - version: ${probeResult.version}")
                cachedTunnelUrl = normalizedUrl

                // Try to get provider info from remote config
                val provider = getProviderInfo(normalizedUrl, token)

                AddonDiscoveryResult.Success(
                    ingressUrl = normalizedUrl,
                    addonSlug = "relay_print",
                    version = probeResult.version,
                    tunnelUrl = normalizedUrl,
                    tunnelProvider = provider
                )
            }
            is ProbeResult.Error -> {
                Log.e(TAG, "Connection failed: ${probeResult.message}")
                AddonDiscoveryResult.Error(
                    "Cannot connect to RelayPrint.\n\n" +
                    "Error: ${probeResult.message}\n\n" +
                    "Please check:\n" +
                    "1. The tunnel URL is correct\n" +
                    "2. The RelayPrint addon is running\n" +
                    "3. Remote access is enabled in the addon",
                    probeResult.code
                )
            }
        }
    }

    /**
     * Get the tunnel provider from remote config endpoint.
     */
    private suspend fun getProviderInfo(baseUrl: String, token: String): String {
        return try {
            val configUrl = normalizeUrl(baseUrl) + "api/config/remote"
            Log.d(TAG, "Fetching provider info from: $configUrl")

            val request = okhttp3.Request.Builder()
                .url(configUrl)
                .addHeader("Authorization", "Bearer $token")
                .build()

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val response = probeClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null && body.contains("tunnel_provider")) {
                        val remoteConfig = json.decodeFromString<com.harelayprint.data.api.RemoteConfigResponse>(body)
                        remoteConfig.tunnelProvider
                    } else {
                        "unknown"
                    }
                } else {
                    "unknown"
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to get provider info: ${e.message}")
            "unknown"
        }
    }

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

    /**
     * Create the RelayPrint API client for the given tunnel URL.
     */
    @Suppress("UNUSED_PARAMETER")
    fun createApi(tunnelUrl: String, token: String, cookies: String? = null): RelayPrintApi {
        val effectiveUrl = cachedTunnelUrl ?: tunnelUrl

        // Return cached API if URL hasn't changed
        if (cachedApi != null && cachedBaseUrl == effectiveUrl) {
            return cachedApi!!
        }

        Log.d(TAG, "Creating API for tunnel: $effectiveUrl")
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
     * Create API with explicit tunnel URL.
     */
    @Suppress("UNUSED_PARAMETER")
    fun createApiWithIngress(haBaseUrl: String, tunnelUrl: String, token: String): RelayPrintApi {
        cachedTunnelUrl = tunnelUrl
        return createApi(tunnelUrl, token)
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
        cachedTunnelUrl = null
    }

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"  // Default to HTTPS for tunnel URLs
        }
        if (!normalized.endsWith("/")) {
            normalized = "$normalized/"
        }
        return normalized
    }
}
