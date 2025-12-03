package com.harelayprint.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Printer(
    val name: String,
    val status: String,
    val location: String? = null,
    val info: String? = null,
    @SerialName("make_model") val makeModel: String? = null,
    val uri: String? = null,
    @SerialName("is_shared") val isShared: Boolean = false,
    @SerialName("supported_formats") val supportedFormats: List<String> = emptyList()
)

@Serializable
data class PrintersResponse(
    val printers: List<Printer>
)

@Serializable
data class PrintJob(
    @SerialName("job_id") val jobId: Int,
    val status: String,
    val message: String? = null,
    val progress: Int? = null,
    @SerialName("error_message") val errorMessage: String? = null
)

@Serializable
data class PrintJobSubmitResponse(
    @SerialName("job_id") val jobId: Int,
    val status: String,
    val message: String
)

@Serializable
data class QueueStatus(
    @SerialName("active_jobs") val activeJobs: Int,
    @SerialName("queued_jobs") val queuedJobs: Int,
    @SerialName("completed_jobs") val completedJobs: Int
)

@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: String,
    val version: String
)

@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
data class CancelResponse(
    val message: String
)

enum class PrintJobStatus(val value: String) {
    QUEUED("queued"),
    PRINTING("printing"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled");

    companion object {
        fun fromString(value: String): PrintJobStatus {
            return entries.find { it.value == value } ?: QUEUED
        }
    }
}

enum class PrintQuality(val value: String, val displayName: String) {
    DRAFT("draft", "Draft"),
    NORMAL("normal", "Normal"),
    HIGH("high", "High");
}

enum class PaperSize(val value: String, val displayName: String) {
    A4("A4", "A4"),
    LETTER("Letter", "Letter"),
    LEGAL("Legal", "Legal");
}

data class PrintOptions(
    val copies: Int = 1,
    val duplex: Boolean = false,
    val quality: PrintQuality = PrintQuality.NORMAL,
    val paperSize: PaperSize = PaperSize.A4
)
