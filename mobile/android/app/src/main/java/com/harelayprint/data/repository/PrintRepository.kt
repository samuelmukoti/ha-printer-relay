package com.harelayprint.data.repository

import com.harelayprint.data.api.*
import com.harelayprint.data.local.SettingsDataStore
import com.harelayprint.di.ApiClientFactory
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
}

@Singleton
class PrintRepository @Inject constructor(
    private val apiFactory: ApiClientFactory,
    private val settings: SettingsDataStore
) {
    private suspend fun getApi(): RelayPrintApi {
        val url = settings.haUrl.first()
        val token = settings.haToken.first()
        return apiFactory.createApi(url, token)
    }

    suspend fun testConnection(url: String, token: String): ApiResult<HealthResponse> {
        return try {
            val api = apiFactory.createApi(url, token)
            val response = api.healthCheck()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(
                    response.errorBody()?.string() ?: "Connection failed",
                    response.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun getPrinters(): ApiResult<List<Printer>> {
        return try {
            val response = getApi().getPrinters()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!.printers)
            } else {
                ApiResult.Error(
                    response.errorBody()?.string() ?: "Failed to get printers",
                    response.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun submitPrintJob(
        file: File,
        printerName: String,
        options: PrintOptions = PrintOptions()
    ): ApiResult<PrintJobSubmitResponse> {
        return try {
            val requestFile = file.asRequestBody("application/pdf".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val printerNameBody = printerName.toRequestBody("text/plain".toMediaTypeOrNull())
            val copiesBody = options.copies.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val duplexBody = options.duplex.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val qualityBody = options.quality.value.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = getApi().submitPrintJob(
                file = filePart,
                printerName = printerNameBody,
                copies = copiesBody,
                duplex = duplexBody,
                quality = qualityBody
            )

            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(
                    response.errorBody()?.string() ?: "Failed to submit print job",
                    response.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun getJobStatus(jobId: Int): ApiResult<PrintJob> {
        return try {
            val response = getApi().getJobStatus(jobId)
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(
                    response.errorBody()?.string() ?: "Failed to get job status",
                    response.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun cancelJob(jobId: Int): ApiResult<CancelResponse> {
        return try {
            val response = getApi().cancelJob(jobId)
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(
                    response.errorBody()?.string() ?: "Failed to cancel job",
                    response.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun getQueueStatus(): ApiResult<QueueStatus> {
        return try {
            val response = getApi().getQueueStatus()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(
                    response.errorBody()?.string() ?: "Failed to get queue status",
                    response.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun getDefaultPrinter(): String? = settings.defaultPrinter.first()

    suspend fun getDefaultOptions(): PrintOptions {
        return PrintOptions(
            copies = settings.defaultCopies.first(),
            duplex = settings.defaultDuplex.first(),
            quality = settings.defaultQuality.first(),
            paperSize = settings.defaultPaperSize.first()
        )
    }
}
