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

// Home Assistant Supervisor API models for addon discovery
@Serializable
data class SupervisorAddonInfoResponse(
    val result: String,
    val data: AddonInfo
)

@Serializable
data class AddonInfo(
    val name: String,
    val slug: String,
    val version: String,
    val state: String,
    val ingress: Boolean = false,
    @SerialName("ingress_entry") val ingressEntry: String? = null,
    @SerialName("ingress_url") val ingressUrl: String? = null,
    @SerialName("ingress_port") val ingressPort: Int? = null
)

@Serializable
data class SupervisorAddonsListResponse(
    val result: String,
    val data: AddonsListData
)

@Serializable
data class AddonsListData(
    val addons: List<AddonSummary>
)

@Serializable
data class AddonSummary(
    val name: String,
    val slug: String,
    val state: String
)

// Ingress session API models
@Serializable
data class IngressSessionRequest(
    val addon: String
)

@Serializable
data class IngressSessionResponse(
    val result: String,
    val data: IngressSessionData
)

@Serializable
data class IngressSessionData(
    val session: String
)

// HA Config API response
@Serializable
data class HaConfigResponse(
    val components: List<String> = emptyList(),
    val version: String = "",
    @SerialName("config_dir") val configDir: String = "",
    @SerialName("whitelist_external_dirs") val whitelistExternalDirs: List<String> = emptyList(),
    @SerialName("allowlist_external_dirs") val allowlistExternalDirs: List<String> = emptyList(),
    @SerialName("allowlist_external_urls") val allowlistExternalUrls: List<String> = emptyList(),
    @SerialName("latitude") val latitude: Double = 0.0,
    @SerialName("longitude") val longitude: Double = 0.0,
    @SerialName("unit_system") val unitSystem: UnitSystem? = null,
    @SerialName("location_name") val locationName: String = "",
    @SerialName("time_zone") val timeZone: String = "",
    @SerialName("external_url") val externalUrl: String? = null,
    @SerialName("internal_url") val internalUrl: String? = null,
    @SerialName("state") val state: String = ""
)

@Serializable
data class UnitSystem(
    val length: String = "",
    val mass: String = "",
    val temperature: String = "",
    val volume: String = ""
)

// Remote access configuration for mobile apps
@Serializable
data class RemoteConfigResponse(
    @SerialName("tunnel_enabled") val tunnelEnabled: Boolean = false,
    @SerialName("tunnel_active") val tunnelActive: Boolean = false,
    @SerialName("tunnel_url") val tunnelUrl: String? = null,
    @SerialName("tunnel_mode") val tunnelMode: String = "quick",  // "quick" or "named"
    @SerialName("direct_port") val directPort: Int = 7779,
    @SerialName("api_version") val apiVersion: String = ""
)
