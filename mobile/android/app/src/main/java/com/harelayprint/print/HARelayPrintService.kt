package com.harelayprint.print

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintJobId
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintDocument
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.util.Log
import com.harelayprint.data.api.Printer
import com.harelayprint.data.api.PrintOptions
import com.harelayprint.data.local.SettingsDataStore
import com.harelayprint.data.repository.ApiResult
import com.harelayprint.data.repository.PrintRepository
import com.harelayprint.di.ApiClientFactory
import com.harelayprint.service.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class HARelayPrintService : PrintService() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var apiClientFactory: ApiClientFactory

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var printRepository: PrintRepository? = null

    companion object {
        private const val TAG = "HARelayPrintService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HARelayPrintService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "HARelayPrintService destroyed")
    }

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        Log.d(TAG, "Creating printer discovery session")
        return HARelayPrinterDiscoverySession()
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        Log.d(TAG, "Print job queued: ${printJob.id}")

        serviceScope.launch {
            try {
                val isConfigured = settingsDataStore.isConfigured.first()
                if (!isConfigured) {
                    Log.e(TAG, "Not configured - failing job")
                    printJob.fail("HA RelayPrint is not configured")
                    return@launch
                }

                // Get the repository
                if (printRepository == null) {
                    printRepository = PrintRepository(apiClientFactory, settingsDataStore)
                }

                // Get printer name from the print job
                val printerId = printJob.info.printerId
                val printerName = printerId?.localId ?: run {
                    printJob.fail("No printer selected")
                    return@launch
                }

                // Start the print job
                printJob.start()

                // Get notificationsEnabled setting
                val notificationsEnabled = settingsDataStore.notificationsEnabled.first()

                // Save document to temp file
                val document = printJob.document
                val tempFile = savePrintDocument(document, printJob.id)

                if (tempFile == null) {
                    Log.e(TAG, "Failed to save print document")
                    printJob.fail("Failed to process document")
                    return@launch
                }

                // Get default print options
                val defaultOptions = printRepository!!.getDefaultOptions()
                val printAttributes = printJob.info.attributes

                // Build print options from job attributes
                val options = PrintOptions(
                    copies = printJob.info.copies,
                    duplex = printAttributes.duplexMode != PrintAttributes.DUPLEX_MODE_NONE,
                    quality = defaultOptions.quality,
                    paperSize = defaultOptions.paperSize
                )

                // Submit to server
                when (val result = printRepository!!.submitPrintJob(tempFile, printerName, options)) {
                    is ApiResult.Success -> {
                        Log.d(TAG, "Print job submitted: ${result.data.jobId}")

                        if (notificationsEnabled) {
                            notificationHelper.showJobStartedNotification(
                                result.data.jobId,
                                printerName
                            )
                        }

                        // Poll for job completion
                        pollJobStatus(result.data.jobId, printerName, printJob, notificationsEnabled)
                    }
                    is ApiResult.Error -> {
                        Log.e(TAG, "Failed to submit print job: ${result.message}")
                        printJob.fail(result.message)

                        if (notificationsEnabled) {
                            notificationHelper.showJobFailedNotification(0, result.message)
                        }
                    }
                }

                // Clean up temp file
                tempFile.delete()

            } catch (e: Exception) {
                Log.e(TAG, "Error processing print job", e)
                printJob.fail("Error: ${e.message}")
            }
        }
    }

    private suspend fun pollJobStatus(
        serverJobId: Int,
        printerName: String,
        printJob: PrintJob,
        notificationsEnabled: Boolean
    ) {
        var attempts = 0
        val maxAttempts = 60 // 5 minutes max

        while (attempts < maxAttempts) {
            delay(5000) // Poll every 5 seconds

            when (val result = printRepository!!.getJobStatus(serverJobId)) {
                is ApiResult.Success -> {
                    when (result.data.status) {
                        "completed" -> {
                            Log.d(TAG, "Print job completed")
                            printJob.complete()
                            if (notificationsEnabled) {
                                notificationHelper.showJobCompletedNotification(serverJobId, printerName)
                            }
                            return
                        }
                        "failed" -> {
                            Log.e(TAG, "Print job failed: ${result.data.errorMessage}")
                            printJob.fail(result.data.errorMessage ?: "Print failed")
                            if (notificationsEnabled) {
                                notificationHelper.showJobFailedNotification(serverJobId, result.data.errorMessage)
                            }
                            return
                        }
                        "cancelled" -> {
                            Log.d(TAG, "Print job cancelled")
                            printJob.cancel()
                            return
                        }
                        else -> {
                            // Still processing, continue polling
                            attempts++
                        }
                    }
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Error polling job status: ${result.message}")
                    attempts++
                }
            }
        }

        // Timeout - assume job is still processing on server
        printJob.complete()
    }

    private fun savePrintDocument(document: PrintDocument, jobId: PrintJobId): File? {
        return try {
            val data = document.data ?: return null
            val inputStream = FileInputStream(data.fileDescriptor)
            val tempFile = File(cacheDir, "print_${jobId.hashCode()}.pdf")

            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            inputStream.close()
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error saving print document", e)
            null
        }
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        Log.d(TAG, "Cancel requested for print job: ${printJob.id}")

        serviceScope.launch {
            try {
                // We can't easily map back to server job ID, so just cancel locally
                printJob.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling print job", e)
            }
        }
    }

    inner class HARelayPrinterDiscoverySession : PrinterDiscoverySession() {

        private var discoveryJob: Job? = null

        override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
            Log.d(TAG, "Starting printer discovery")
            discoveryJob = serviceScope.launch {
                discoverPrinters()
            }
        }

        override fun onStopPrinterDiscovery() {
            Log.d(TAG, "Stopping printer discovery")
            discoveryJob?.cancel()
        }

        override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {
            Log.d(TAG, "Validating printers: ${printerIds.size}")
            // Printers are validated during discovery
        }

        override fun onStartPrinterStateTracking(printerId: PrinterId) {
            Log.d(TAG, "Start tracking printer: ${printerId.localId}")
            serviceScope.launch {
                updatePrinterState(printerId)
            }
        }

        override fun onStopPrinterStateTracking(printerId: PrinterId) {
            Log.d(TAG, "Stop tracking printer: ${printerId.localId}")
        }

        override fun onDestroy() {
            Log.d(TAG, "Discovery session destroyed")
            discoveryJob?.cancel()
        }

        private suspend fun discoverPrinters() {
            try {
                val isConfigured = settingsDataStore.isConfigured.first()
                if (!isConfigured) {
                    Log.d(TAG, "Not configured, skipping printer discovery")
                    return
                }

                if (printRepository == null) {
                    printRepository = PrintRepository(apiClientFactory, settingsDataStore)
                }

                when (val result = printRepository!!.getPrinters()) {
                    is ApiResult.Success -> {
                        val printers = result.data.map { printer ->
                            createPrinterInfo(printer)
                        }
                        addPrinters(printers)
                        Log.d(TAG, "Discovered ${printers.size} printers")
                    }
                    is ApiResult.Error -> {
                        Log.e(TAG, "Failed to discover printers: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during printer discovery", e)
            }
        }

        private suspend fun updatePrinterState(printerId: PrinterId) {
            try {
                if (printRepository == null) return

                when (val result = printRepository!!.getPrinters()) {
                    is ApiResult.Success -> {
                        val printer = result.data.find { it.name == printerId.localId }
                        if (printer != null) {
                            val printerInfo = createPrinterInfo(printer, true)
                            addPrinters(listOf(printerInfo))
                        }
                    }
                    is ApiResult.Error -> {
                        Log.e(TAG, "Failed to update printer state: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating printer state", e)
            }
        }

        private fun createPrinterInfo(printer: Printer, withCapabilities: Boolean = false): PrinterInfo {
            val printerId = generatePrinterId(printer.name)

            val status = when (printer.status.lowercase()) {
                "idle" -> PrinterInfo.STATUS_IDLE
                "printing" -> PrinterInfo.STATUS_BUSY
                else -> PrinterInfo.STATUS_UNAVAILABLE
            }

            val builder = PrinterInfo.Builder(printerId, printer.name, status)
                .setDescription(printer.makeModel ?: "")

            if (withCapabilities) {
                val capabilities = PrinterCapabilitiesInfo.Builder(printerId)
                    .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
                    .addMediaSize(PrintAttributes.MediaSize.NA_LETTER, false)
                    .addMediaSize(PrintAttributes.MediaSize.NA_LEGAL, false)
                    .addResolution(
                        PrintAttributes.Resolution("normal", "Normal", 300, 300),
                        true
                    )
                    .addResolution(
                        PrintAttributes.Resolution("high", "High", 600, 600),
                        false
                    )
                    .setColorModes(
                        PrintAttributes.COLOR_MODE_COLOR or PrintAttributes.COLOR_MODE_MONOCHROME,
                        PrintAttributes.COLOR_MODE_COLOR
                    )
                    .setDuplexModes(
                        PrintAttributes.DUPLEX_MODE_NONE or PrintAttributes.DUPLEX_MODE_LONG_EDGE,
                        PrintAttributes.DUPLEX_MODE_NONE
                    )
                    .build()

                builder.setCapabilities(capabilities)
            }

            return builder.build()
        }
    }
}
