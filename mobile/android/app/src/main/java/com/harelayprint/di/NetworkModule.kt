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
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
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
    fun provideIngressSessionManager(
        settings: SettingsDataStore,
        okHttpClient: OkHttpClient
    ): com.harelayprint.data.auth.IngressSessionManager =
        com.harelayprint.data.auth.IngressSessionManager(settings, okHttpClient)

    @Provides
    @Singleton
    fun provideApiClientFactory(
        okHttpClient: OkHttpClient,
        settings: SettingsDataStore,
        ingressSessionManager: com.harelayprint.data.auth.IngressSessionManager
    ): ApiClientFactory = ApiClientFactory(okHttpClient, json, settings, ingressSessionManager)

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
 *
 * Connection Strategy (in order of preference):
 * 1. **HA Ingress** - Direct access through Home Assistant's ingress proxy
 *    - Uses OAuth token + ingress session cookie
 *    - Works if user has remote access to HA (Nabu Casa, reverse proxy, etc.)
 *    - No tunnel needed!
 * 2. **Tunnel** - Fallback to LocalTunnel/Cloudflare if ingress fails
 *    - For users who can't access HA remotely
 */
class ApiClientFactory(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val settings: SettingsDataStore,
    private val ingressSessionManager: com.harelayprint.data.auth.IngressSessionManager
) {
    companion object {
        private const val TAG = "ApiClientFactory"
        private const val PROBE_TIMEOUT_SECONDS = 10L  // Timeout for probing
    }

    private var cachedApi: RelayPrintApi? = null
    private var cachedBaseUrl: String? = null
    private var cachedTunnelUrl: String? = null
    private var cachedIngressUrl: String? = null
    private var useIngress: Boolean = false  // Whether to use ingress vs tunnel

    // OkHttpClient with timeout for probing
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
     * Create the RelayPrint API client.
     *
     * Priority:
     * 1. Use ingress URL if available (with session cookie)
     * 2. Fall back to tunnel URL if ingress not available
     */
    @Suppress("UNUSED_PARAMETER")
    fun createApi(tunnelUrl: String, token: String, cookies: String? = null): RelayPrintApi {
        val effectiveUrl = when {
            useIngress && cachedIngressUrl != null -> cachedIngressUrl!!
            cachedTunnelUrl != null -> cachedTunnelUrl!!
            else -> tunnelUrl
        }

        // Return cached API if URL hasn't changed
        if (cachedApi != null && cachedBaseUrl == effectiveUrl) {
            return cachedApi!!
        }

        Log.d(TAG, "Creating API for: $effectiveUrl (ingress=$useIngress)")
        val client = createAuthenticatedClient(token, useIngress)

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
     * Create API for ingress access.
     * This is the preferred method - uses HA ingress proxy directly.
     */
    fun createApiForIngress(haBaseUrl: String, addonSlug: String, token: String): RelayPrintApi {
        val ingressUrl = "$haBaseUrl/api/hassio_ingress/$addonSlug"
        cachedIngressUrl = ingressUrl
        useIngress = true
        return createApi(ingressUrl, token)
    }

    /**
     * Create API for tunnel access (fallback when ingress not available).
     */
    fun createApiForTunnel(tunnelUrl: String, token: String): RelayPrintApi {
        cachedTunnelUrl = tunnelUrl
        useIngress = false
        return createApi(tunnelUrl, token)
    }

    /**
     * Create API with explicit tunnel URL.
     */
    @Suppress("UNUSED_PARAMETER")
    fun createApiWithIngress(haBaseUrl: String, tunnelUrl: String, token: String): RelayPrintApi {
        cachedTunnelUrl = tunnelUrl
        return createApi(tunnelUrl, token)
    }

    private fun createAuthenticatedClient(token: String, includeIngressSession: Boolean = false): OkHttpClient {
        return okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()

                // Add Bearer token
                if (token.isNotEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }

                // Add ingress session cookie if using ingress
                if (includeIngressSession) {
                    // Get session synchronously (blocking) for the interceptor
                    // This is safe because OkHttp interceptors run on IO thread
                    val session = kotlinx.coroutines.runBlocking {
                        ingressSessionManager.getValidSession()
                    }
                    if (session != null) {
                        requestBuilder.addHeader("Cookie", "ingress_session=$session")
                        Log.d(TAG, "Added ingress session cookie to request")
                    }
                }

                chain.proceed(requestBuilder.build())
            }
            .build()
    }

    fun clearCache() {
        cachedApi = null
        cachedBaseUrl = null
        cachedTunnelUrl = null
        cachedIngressUrl = null
        useIngress = false
        ingressSessionManager.clearSession()
    }

    /**
     * Discover the RelayPrint addon using the OAuth token.
     *
     * **INGRESS-FIRST STRATEGY:**
     * 1. Try to create an ingress session using the OAuth token
     * 2. If ingress works, we can access the addon directly through HA - no tunnel needed!
     * 3. Only fall back to tunnel URL if ingress access fails
     *
     * This means users with remote access to HA (Nabu Casa, reverse proxy, etc.)
     * don't need to enable the tunnel at all.
     *
     * @param haUrl The Home Assistant base URL
     * @param token OAuth access token for HA API
     * @param cookies WebView cookies from HA login session (optional, for additional auth)
     * @return AddonDiscoveryResult with connection info
     */
    suspend fun discoverTunnelUrl(haUrl: String, token: String, cookies: String? = null): AddonDiscoveryResult {
        val normalizedHaUrl = normalizeUrl(haUrl).trimEnd('/')
        Log.d(TAG, "Discovering RelayPrint addon at: $normalizedHaUrl")
        Log.d(TAG, "Using INGRESS-FIRST strategy")

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Step 1: Try to create an ingress session
                val ingressSession = ingressSessionManager.getValidSession()

                if (ingressSession != null) {
                    Log.d(TAG, "Got valid ingress session, trying ingress access")

                    // Try to access addon via ingress with the session
                    val addonSlug = settings.addonSlug.first().ifEmpty { null }
                        ?: findAddonSlugViaIngress(normalizedHaUrl, token, ingressSession)

                    if (addonSlug != null) {
                        val ingressResult = probeIngressEndpoint(normalizedHaUrl, addonSlug, token, ingressSession)
                        if (ingressResult is ProbeResult.Success) {
                            Log.d(TAG, "Ingress access successful - no tunnel needed!")
                            cachedIngressUrl = "$normalizedHaUrl/api/hassio_ingress/$addonSlug"
                            useIngress = true

                            return@withContext AddonDiscoveryResult.Success(
                                ingressUrl = cachedIngressUrl!!,
                                addonSlug = addonSlug,
                                version = ingressResult.version,
                                sessionToken = ingressSession,
                                tunnelUrl = null,  // No tunnel needed!
                                tunnelProvider = null
                            )
                        }
                    }
                }

                // Step 2: Ingress didn't work, try Supervisor API to find addon
                Log.d(TAG, "Ingress session not available, trying Supervisor API")
                val addonSlug = findRelayPrintAddon(normalizedHaUrl, token)

                if (addonSlug == null) {
                    // Supervisor API access failed
                    // Try using cookies for direct ingress access as last resort
                    Log.d(TAG, "Supervisor API not accessible, trying ingress with cookies")

                    if (!cookies.isNullOrEmpty()) {
                        val cookieResult = tryIngressWithCookies(normalizedHaUrl, cookies)
                        if (cookieResult != null) {
                            return@withContext cookieResult
                        }
                    }

                    return@withContext AddonDiscoveryResult.Error("MANUAL_ENTRY_REQUIRED")
                }

                Log.d(TAG, "Found addon: $addonSlug")

                // Get addon info to verify it's running
                val addonInfo = getAddonInfo(normalizedHaUrl, token, addonSlug)
                    ?: return@withContext AddonDiscoveryResult.Error("MANUAL_ENTRY_REQUIRED")

                if (addonInfo.state != "started") {
                    return@withContext AddonDiscoveryResult.Error(
                        "RelayPrint addon is not running.\n\n" +
                        "Current state: ${addonInfo.state}\n" +
                        "Please start the addon from Home Assistant."
                    )
                }

                Log.d(TAG, "Addon is running, version: ${addonInfo.version}")

                // Step 3: Try ingress access with the discovered slug
                val newSession = ingressSessionManager.refreshSession()
                if (newSession != null) {
                    val ingressResult = probeIngressEndpoint(normalizedHaUrl, addonSlug, token, newSession)
                    if (ingressResult is ProbeResult.Success) {
                        Log.d(TAG, "Ingress access successful after discovery!")
                        cachedIngressUrl = "$normalizedHaUrl/api/hassio_ingress/$addonSlug"
                        useIngress = true

                        // Save the addon slug for future use
                        settings.saveIngressUrl(cachedIngressUrl!!, newSession, addonSlug)

                        return@withContext AddonDiscoveryResult.Success(
                            ingressUrl = cachedIngressUrl!!,
                            addonSlug = addonSlug,
                            version = ingressResult.version,
                            sessionToken = newSession,
                            tunnelUrl = null,
                            tunnelProvider = null
                        )
                    }
                }

                // Step 4: Ingress failed, check for tunnel URL
                val remoteConfig = getRemoteConfigViaIngress(normalizedHaUrl, token, addonSlug, cookies)

                if (remoteConfig != null && remoteConfig.tunnelEnabled && !remoteConfig.tunnelUrl.isNullOrEmpty()) {
                    Log.d(TAG, "Using tunnel URL: ${remoteConfig.tunnelUrl}")
                    cachedTunnelUrl = remoteConfig.tunnelUrl
                    useIngress = false

                    val ingressUrl = "$normalizedHaUrl${addonInfo.ingressEntry ?: "/api/hassio_ingress/$addonSlug"}"

                    AddonDiscoveryResult.Success(
                        ingressUrl = ingressUrl,
                        addonSlug = addonSlug,
                        version = addonInfo.version,
                        tunnelUrl = remoteConfig.tunnelUrl,
                        tunnelProvider = remoteConfig.tunnelProvider
                    )
                } else {
                    // Neither ingress nor tunnel available
                    Log.d(TAG, "Neither ingress nor tunnel available")
                    AddonDiscoveryResult.Error(
                        "Cannot connect to RelayPrint remotely.\n\n" +
                        "Options:\n" +
                        "1. If you have remote access to HA (Nabu Casa, reverse proxy),\n" +
                        "   check that ingress is working\n\n" +
                        "2. Enable Remote Access in RelayPrint settings to create a tunnel URL"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery failed", e)
                AddonDiscoveryResult.Error("MANUAL_ENTRY_REQUIRED")
            }
        }
    }

    /**
     * Probe the addon health endpoint via ingress.
     */
    private suspend fun probeIngressEndpoint(
        haUrl: String,
        addonSlug: String,
        token: String,
        ingressSession: String
    ): ProbeResult {
        val healthUrl = "$haUrl/api/hassio_ingress/$addonSlug/api/health"
        Log.d(TAG, "Probing ingress endpoint: $healthUrl")

        return try {
            val request = okhttp3.Request.Builder()
                .url(healthUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Cookie", "ingress_session=$ingressSession")
                .addHeader("Accept", "application/json")
                .build()

            val response = probeClient.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "Ingress probe response: ${response.code}")

            when {
                response.isSuccessful && responseBody != null -> {
                    if (responseBody.trimStart().startsWith("{")) {
                        try {
                            val healthResponse = json.decodeFromString<HealthResponse>(responseBody)
                            ProbeResult.Success(healthResponse.version)
                        } catch (e: Exception) {
                            ProbeResult.Error("Invalid JSON response", null)
                        }
                    } else {
                        ProbeResult.Error("Got HTML instead of JSON", null)
                    }
                }
                response.code == 401 -> ProbeResult.Error("Unauthorized", 401)
                response.code == 404 -> ProbeResult.Error("Not found", 404)
                else -> ProbeResult.Error(response.message, response.code)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ingress probe error", e)
            ProbeResult.Error(e.message ?: "Network error", null)
        }
    }

    /**
     * Try to find the addon slug by probing known slugs via ingress.
     */
    private suspend fun findAddonSlugViaIngress(
        haUrl: String,
        token: String,
        ingressSession: String
    ): String? {
        val possibleSlugs = listOf(
            "local_relay_print",
            "a0d7b954_relayprint",
            "relay_print",
            "relayprint"
        )

        for (slug in possibleSlugs) {
            val result = probeIngressEndpoint(haUrl, slug, token, ingressSession)
            if (result is ProbeResult.Success) {
                Log.d(TAG, "Found addon via ingress probe: $slug")
                return slug
            }
        }

        return null
    }

    /**
     * Try to access ingress directly using WebView session cookies.
     * This works when OAuth Supervisor API access fails but we have a valid HA session.
     */
    private suspend fun tryIngressWithCookies(haUrl: String, cookies: String): AddonDiscoveryResult? {
        Log.d(TAG, "Attempting ingress access with cookies")

        // Common addon slugs to try
        val possibleSlugs = listOf(
            "local_relay_print",
            "a0d7b954_relayprint",
            "relay_print",
            "relayprint"
        )

        for (slug in possibleSlugs) {
            Log.d(TAG, "Trying slug: $slug")

            // Try to access the addon's remote config via ingress with cookies
            val configUrl = "$haUrl/api/hassio_ingress/$slug/api/config/remote"
            Log.d(TAG, "Trying ingress URL: $configUrl")

            val request = okhttp3.Request.Builder()
                .url(configUrl)
                .addHeader("Cookie", cookies)
                .addHeader("Accept", "application/json")
                .build()

            try {
                val response = probeClient.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d(TAG, "Ingress response for $slug: ${response.code}")

                if (response.isSuccessful && responseBody != null) {
                    Log.d(TAG, "Got response: ${responseBody.take(200)}")

                    if (responseBody.trimStart().startsWith("{")) {
                        try {
                            val remoteConfig = json.decodeFromString<com.harelayprint.data.api.RemoteConfigResponse>(responseBody)

                            if (remoteConfig.tunnelEnabled && !remoteConfig.tunnelUrl.isNullOrEmpty()) {
                                Log.d(TAG, "Found tunnel URL via cookies: ${remoteConfig.tunnelUrl}")
                                cachedTunnelUrl = remoteConfig.tunnelUrl

                                return AddonDiscoveryResult.Success(
                                    ingressUrl = "$haUrl/api/hassio_ingress/$slug",
                                    addonSlug = slug,
                                    version = remoteConfig.version ?: "unknown",
                                    tunnelUrl = remoteConfig.tunnelUrl,
                                    tunnelProvider = remoteConfig.tunnelProvider
                                )
                            } else {
                                Log.d(TAG, "Addon found but tunnel not enabled")
                                return AddonDiscoveryResult.Error(
                                    "Remote access is not enabled.\n\n" +
                                    "Please enable Remote Access in the RelayPrint addon settings."
                                )
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "JSON parse failed for $slug: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Request failed for $slug: ${e.message}")
            }
        }

        Log.d(TAG, "No addon found via cookies")
        return null
    }

    /**
     * Find the RelayPrint addon slug by listing all addons.
     */
    private suspend fun findRelayPrintAddon(haUrl: String, token: String): String? {
        val addonsUrl = "$haUrl/api/hassio/addons"
        Log.d(TAG, "Listing addons: $addonsUrl")

        val request = okhttp3.Request.Builder()
            .url(addonsUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        val response = probeClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to list addons: ${response.code}")
            return null
        }

        val body = response.body?.string() ?: return null
        Log.d(TAG, "Addons response: ${body.take(500)}")

        try {
            val addonsResponse = json.decodeFromString<com.harelayprint.data.api.SupervisorAddonsListResponse>(body)

            // Search for RelayPrint addon using known slugs
            for (slug in com.harelayprint.data.api.HaSupervisorApi.KNOWN_ADDON_SLUGS) {
                val found = addonsResponse.data.addons.find {
                    it.slug == slug || it.slug.contains("relay_print")
                }
                if (found != null) {
                    return found.slug
                }
            }

            // Also check by name
            val byName = addonsResponse.data.addons.find {
                it.name.lowercase().contains("relayprint") ||
                it.name.lowercase().contains("relay print")
            }
            if (byName != null) {
                return byName.slug
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse addons response", e)
        }

        return null
    }

    /**
     * Get detailed addon info.
     */
    private suspend fun getAddonInfo(haUrl: String, token: String, slug: String): com.harelayprint.data.api.AddonInfo? {
        val infoUrl = "$haUrl/api/hassio/addons/$slug/info"
        Log.d(TAG, "Getting addon info: $infoUrl")

        val request = okhttp3.Request.Builder()
            .url(infoUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        val response = probeClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to get addon info: ${response.code}")
            return null
        }

        val body = response.body?.string() ?: return null
        Log.d(TAG, "Addon info response: ${body.take(500)}")

        return try {
            val infoResponse = json.decodeFromString<com.harelayprint.data.api.SupervisorAddonInfoResponse>(body)
            infoResponse.data
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse addon info", e)
            null
        }
    }

    /**
     * Get remote config via HA ingress session.
     * Uses both OAuth token for API session and WebView cookies for browser session.
     */
    private suspend fun getRemoteConfigViaIngress(
        haUrl: String,
        token: String,
        addonSlug: String,
        webViewCookies: String? = null
    ): com.harelayprint.data.api.RemoteConfigResponse? {
        // First, try to create an ingress session using OAuth token
        val sessionUrl = "$haUrl/api/hassio/ingress/session"
        Log.d(TAG, "Creating ingress session: $sessionUrl")

        val sessionRequest = okhttp3.Request.Builder()
            .url(sessionUrl)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post("""{"addon":"$addonSlug"}""".toRequestBody("application/json".toMediaType()))
            .build()

        var sessionToken: String? = null
        val sessionResponse = probeClient.newCall(sessionRequest).execute()
        if (sessionResponse.isSuccessful) {
            val sessionBody = sessionResponse.body?.string()
            Log.d(TAG, "Session response: $sessionBody")

            sessionToken = try {
                val parsed = json.decodeFromString<com.harelayprint.data.api.IngressSessionResponse>(sessionBody ?: "")
                parsed.data.session
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse session response", e)
                null
            }
        } else {
            Log.d(TAG, "Failed to create ingress session: ${sessionResponse.code}, will try with cookies")
        }

        // Now call the remote config endpoint via ingress
        val configUrl = "$haUrl/api/hassio_ingress/$addonSlug/api/config/remote"
        Log.d(TAG, "Getting remote config via ingress: $configUrl")

        // Build cookies string combining ingress session and WebView cookies
        val cookiesList = mutableListOf<String>()
        if (sessionToken != null) {
            cookiesList.add("ingress_session=$sessionToken")
        }
        if (!webViewCookies.isNullOrEmpty()) {
            cookiesList.add(webViewCookies)
        }
        val combinedCookies = cookiesList.joinToString("; ")

        val configRequestBuilder = okhttp3.Request.Builder()
            .url(configUrl)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")

        // Add combined cookies if we have any
        if (combinedCookies.isNotEmpty()) {
            Log.d(TAG, "Adding cookies to request: ${combinedCookies.take(100)}...")
            configRequestBuilder.addHeader("Cookie", combinedCookies)
        }

        val configResponse = probeClient.newCall(configRequestBuilder.build()).execute()
        val configBody = configResponse.body?.string()

        Log.d(TAG, "Remote config response: ${configResponse.code}")
        Log.d(TAG, "Remote config body: ${configBody?.take(200)}")

        if (!configResponse.isSuccessful) {
            Log.e(TAG, "Failed to get remote config: ${configResponse.code}")
            // Try direct approach without session
            return getRemoteConfigDirect(haUrl, token, addonSlug)
        }

        return try {
            json.decodeFromString<com.harelayprint.data.api.RemoteConfigResponse>(configBody ?: "")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse remote config", e)
            null
        }
    }

    /**
     * Fallback: try to get remote config directly via port 7779.
     */
    private suspend fun getRemoteConfigDirect(haUrl: String, token: String, addonSlug: String): com.harelayprint.data.api.RemoteConfigResponse? {
        // Try direct API access via exposed port
        val baseHost = try {
            java.net.URI(haUrl).host
        } catch (e: Exception) {
            return null
        }

        val directUrl = "http://$baseHost:7779/api/config/remote"
        Log.d(TAG, "Trying direct access: $directUrl")

        val request = okhttp3.Request.Builder()
            .url(directUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        return try {
            val response = probeClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                json.decodeFromString<com.harelayprint.data.api.RemoteConfigResponse>(body ?: "")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Direct access failed: ${e.message}")
            null
        }
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
