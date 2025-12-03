package com.harelayprint.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Home Assistant Supervisor API for discovering the RelayPrint addon.
 * Used to find the ingress URL automatically.
 */
interface HaSupervisorApi {

    companion object {
        // Known addon slugs to search for
        val KNOWN_ADDON_SLUGS = listOf(
            "relay_print",           // Local addon
            "885c40c0_relay_print",  // Store addon with repo prefix
            "local_relay_print"      // Alternative local name
        )
    }

    /**
     * List all installed addons to find RelayPrint.
     */
    @GET("api/hassio/addons")
    suspend fun listAddons(): Response<SupervisorAddonsListResponse>

    /**
     * Get detailed addon info including ingress URL.
     */
    @GET("api/hassio/addons/{slug}/info")
    suspend fun getAddonInfo(@Path("slug") slug: String): Response<SupervisorAddonInfoResponse>

    /**
     * Create an ingress session for an addon.
     * This is the proper way to access addon ingress externally with OAuth tokens.
     * Returns a session token that can be used to construct the ingress URL.
     */
    @POST("api/hassio/ingress/session")
    suspend fun createIngressSession(@Body request: IngressSessionRequest): Response<IngressSessionResponse>

    /**
     * Get HA panels/sidebar configuration to find addon ingress paths.
     */
    @GET("api/config")
    suspend fun getConfig(): Response<HaConfigResponse>
}

/**
 * RelayPrint addon API - accessed via direct port or Cloudflare Tunnel.
 * Base URL will be dynamically set based on discovery.
 */
interface RelayPrintApi {

    @GET("api/health")
    suspend fun healthCheck(): Response<HealthResponse>

    /**
     * Get remote access configuration.
     * Returns tunnel URL if Cloudflare Tunnel is configured.
     * This endpoint is unauthenticated to allow discovery.
     */
    @GET("api/config/remote")
    suspend fun getRemoteConfig(): Response<RemoteConfigResponse>

    @GET("api/printers")
    suspend fun getPrinters(): Response<PrintersResponse>

    @Multipart
    @POST("api/print")
    suspend fun submitPrintJob(
        @Part file: MultipartBody.Part,
        @Part("printer_name") printerName: RequestBody,
        @Part("copies") copies: RequestBody? = null,
        @Part("duplex") duplex: RequestBody? = null,
        @Part("quality") quality: RequestBody? = null
    ): Response<PrintJobSubmitResponse>

    @GET("api/print/{jobId}/status")
    suspend fun getJobStatus(@Path("jobId") jobId: Int): Response<PrintJob>

    @DELETE("api/print/{jobId}")
    suspend fun cancelJob(@Path("jobId") jobId: Int): Response<CancelResponse>

    @GET("api/queue/status")
    suspend fun getQueueStatus(): Response<QueueStatus>
}
