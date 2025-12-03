package com.harelayprint.di

import android.content.Context
import android.util.Log
import com.harelayprint.data.api.HaSupervisorApi
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
    data class Success(val ingressUrl: String, val addonSlug: String, val version: String) : AddonDiscoveryResult()
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
    }

    private var cachedApi: RelayPrintApi? = null
    private var cachedBaseUrl: String? = null
    private var cachedIngressUrl: String? = null

    /**
     * Discover the RelayPrint addon using the HA ingress session API.
     *
     * For external access with OAuth tokens, we need to:
     * 1. Create an ingress session via POST /api/hassio/ingress/session
     * 2. Use the session token to construct the ingress URL
     * 3. Access the addon via /api/hassio/ingress/<session>/
     */
    suspend fun discoverAddon(haBaseUrl: String, token: String): AddonDiscoveryResult {
        val normalizedBase = normalizeUrl(haBaseUrl).trimEnd('/')

        // Known addon slug patterns to try
        val addonSlugs = listOf(
            "885c40c0_relay_print",  // Store addon with repo prefix
            "local_relay_print",     // Local addon
            "relay_print"            // Direct slug
        )

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

        // Extract host from HA URL to try direct addon port
        val haHost = try {
            java.net.URL(normalizedBase).host
        } catch (e: Exception) {
            null
        }

        // Pattern 1: Try direct addon API port (works when on same network)
        // The addon exposes port 7779 directly with host_network: true
        if (haHost != null) {
            val directUrl = "http://$haHost:7779"
            Log.d(TAG, "Trying direct addon API: $directUrl")
            val directResult = probeEndpoint(directUrl, token)
            if (directResult is ProbeResult.Success) {
                Log.d(TAG, "Found working direct addon API at: $directUrl")
                cachedIngressUrl = directUrl
                return AddonDiscoveryResult.Success(
                    ingressUrl = directUrl,
                    addonSlug = "relay_print",
                    version = directResult.version
                )
            }
            if (directResult is ProbeResult.Error) {
                Log.d(TAG, "Direct addon API failed: ${directResult.message} (code: ${directResult.code})")
            }

            // Also try HTTPS direct
            val directHttpsUrl = "https://$haHost:7779"
            Log.d(TAG, "Trying direct addon API (HTTPS): $directHttpsUrl")
            val directHttpsResult = probeEndpoint(directHttpsUrl, token)
            if (directHttpsResult is ProbeResult.Success) {
                Log.d(TAG, "Found working direct addon API at: $directHttpsUrl")
                cachedIngressUrl = directHttpsUrl
                return AddonDiscoveryResult.Success(
                    ingressUrl = directHttpsUrl,
                    addonSlug = "relay_print",
                    version = directHttpsResult.version
                )
            }
        }

        // Pattern 2: Try HA proxy to addon ingress (for remote access)
        for (slug in addonSlugs) {
            // Try the hassio_ingress proxy endpoint
            val ingressProxyUrl = "$normalizedBase/api/hassio_ingress/$slug"
            Log.d(TAG, "Trying ingress proxy URL: $ingressProxyUrl")
            val proxyResult = probeEndpoint(ingressProxyUrl, token)
            if (proxyResult is ProbeResult.Success) {
                Log.d(TAG, "Found working endpoint at: $ingressProxyUrl")
                cachedIngressUrl = ingressProxyUrl
                return AddonDiscoveryResult.Success(
                    ingressUrl = ingressProxyUrl,
                    addonSlug = slug,
                    version = proxyResult.version
                )
            }
            if (proxyResult is ProbeResult.Error) {
                Log.d(TAG, "Ingress proxy URL failed: ${proxyResult.message} (code: ${proxyResult.code})")
            }
        }

        // If no panel URL worked, try Supervisor API (might work with admin tokens)
        try {
            val supervisorResult = tryViaSupervisorApi(normalizedBase, token)
            if (supervisorResult != null) {
                return supervisorResult
            }
        } catch (e: Exception) {
            Log.d(TAG, "Supervisor API approach failed: ${e.message}")
        }

        // Nothing worked
        return AddonDiscoveryResult.Error(
            "Could not find RelayPrint addon. Please ensure:\n" +
            "1. The addon is installed and running\n" +
            "2. Your Home Assistant URL is correct\n" +
            "3. Your access token is valid",
            404
        )
    }

    /**
     * Try to create an ingress session for the addon and verify it works.
     */
    private suspend fun tryIngressSession(baseUrl: String, addonSlug: String, token: String): AddonDiscoveryResult? {
        return try {
            val supervisorApi = createSupervisorApi(baseUrl, token)

            // Create ingress session
            val sessionRequest = com.harelayprint.data.api.IngressSessionRequest(addon = addonSlug)
            val sessionResponse = supervisorApi.createIngressSession(sessionRequest)

            if (!sessionResponse.isSuccessful) {
                Log.d(TAG, "Ingress session failed for $addonSlug: ${sessionResponse.code()} - ${sessionResponse.message()}")
                return null
            }

            val sessionData = sessionResponse.body()?.data ?: return null
            val sessionToken = sessionData.session

            // Construct the ingress URL with session token
            val ingressUrl = "$baseUrl/api/hassio/ingress/$sessionToken"
            Log.d(TAG, "Created ingress session, URL: $ingressUrl")

            // Verify the endpoint works
            val probeResult = probeEndpoint(ingressUrl, token)
            if (probeResult is ProbeResult.Success) {
                Log.d(TAG, "Ingress session verified for $addonSlug")
                cachedIngressUrl = ingressUrl
                return AddonDiscoveryResult.Success(
                    ingressUrl = ingressUrl,
                    addonSlug = addonSlug,
                    version = probeResult.version
                )
            }

            Log.d(TAG, "Ingress session created but health check failed: ${(probeResult as? ProbeResult.Error)?.message}")
            null
        } catch (e: Exception) {
            Log.d(TAG, "Ingress session error for $addonSlug: ${e.message}")
            null
        }
    }

    private sealed class ProbeResult {
        data class Success(val version: String) : ProbeResult()
        data class Error(val message: String, val code: Int?) : ProbeResult()
    }

    private suspend fun probeEndpoint(baseUrl: String, token: String): ProbeResult {
        return try {
            val api = createProbeApi(baseUrl, token)
            val response = api.healthCheck()

            when {
                response.isSuccessful && response.body() != null -> {
                    ProbeResult.Success(response.body()!!.version)
                }
                response.code() == 401 -> {
                    ProbeResult.Error("Unauthorized", 401)
                }
                response.code() == 404 -> {
                    ProbeResult.Error("Not found", 404)
                }
                else -> {
                    ProbeResult.Error(response.message(), response.code())
                }
            }
        } catch (e: Exception) {
            ProbeResult.Error(e.message ?: "Network error", null)
        }
    }

    private fun createProbeApi(baseUrl: String, token: String): RelayPrintApi {
        val authClient = createAuthenticatedClient(token)

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizeUrl(baseUrl))
            .client(authClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(RelayPrintApi::class.java)
    }

    private suspend fun tryViaSupervisorApi(baseUrl: String, token: String): AddonDiscoveryResult? {
        try {
            val supervisorApi = createSupervisorApi(baseUrl, token)

            // First, list all addons to find RelayPrint
            val addonsResponse = supervisorApi.listAddons()
            if (!addonsResponse.isSuccessful) {
                return null // Supervisor API not accessible with this token
            }

            val addons = addonsResponse.body()?.data?.addons ?: return null

            // Find RelayPrint addon
            val relayPrintAddon = addons.find { addon ->
                HaSupervisorApi.KNOWN_ADDON_SLUGS.any { slug ->
                    addon.slug.contains(slug, ignoreCase = true)
                } || addon.name.contains("RelayPrint", ignoreCase = true)
            } ?: return null

            // Get detailed addon info including ingress URL
            val addonInfoResponse = supervisorApi.getAddonInfo(relayPrintAddon.slug)
            if (!addonInfoResponse.isSuccessful) {
                return null
            }

            val addonInfo = addonInfoResponse.body()?.data ?: return null

            if (!addonInfo.ingress || addonInfo.state != "started") {
                return null
            }

            val ingressUrl = addonInfo.ingressUrl ?: addonInfo.ingressEntry ?: return null
            val fullIngressUrl = buildIngressUrl(baseUrl, ingressUrl)

            cachedIngressUrl = fullIngressUrl
            return AddonDiscoveryResult.Success(
                ingressUrl = fullIngressUrl,
                addonSlug = relayPrintAddon.slug,
                version = addonInfo.version
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Create the RelayPrint API client using the discovered ingress URL.
     */
    fun createApi(baseUrl: String, token: String): RelayPrintApi {
        // Use cached ingress URL if available, otherwise use baseUrl directly
        val effectiveUrl = cachedIngressUrl ?: baseUrl

        // Return cached API if URL hasn't changed
        if (cachedApi != null && cachedBaseUrl == effectiveUrl) {
            return cachedApi!!
        }

        val authClient = createAuthenticatedClient(token)

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizeUrl(effectiveUrl))
            .client(authClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        cachedApi = retrofit.create(RelayPrintApi::class.java)
        cachedBaseUrl = effectiveUrl
        return cachedApi!!
    }

    /**
     * Create API with explicit ingress URL (after discovery).
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

    private fun buildIngressUrl(haBaseUrl: String, ingressPath: String): String {
        val base = normalizeUrl(haBaseUrl).trimEnd('/')
        val path = if (ingressPath.startsWith("/")) ingressPath else "/$ingressPath"
        return "$base$path"
    }
}
