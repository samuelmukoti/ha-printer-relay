package com.harelayprint.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface RelayPrintApi {

    @GET("api/health")
    suspend fun healthCheck(): Response<HealthResponse>

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
