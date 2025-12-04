package com.harelayprint.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harelayprint.data.api.Printer
import com.harelayprint.data.api.QueueStatus
import com.harelayprint.data.local.SettingsDataStore
import com.harelayprint.data.repository.ApiResult
import com.harelayprint.data.repository.PrintRepository
import com.harelayprint.di.AddonDiscoveryResult
import com.harelayprint.di.ApiClientFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isRefreshingTunnel: Boolean = false,
    val printers: List<Printer> = emptyList(),
    val queueStatus: QueueStatus? = null,
    val errorMessage: String? = null,
    val defaultPrinter: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: PrintRepository,
    private val apiFactory: ApiClientFactory,
    private val settings: SettingsDataStore
) : ViewModel() {

    companion object {
        private const val TAG = "DashboardViewModel"
    }

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Load default printer
            val defaultPrinter = repository.getDefaultPrinter()
            _uiState.value = _uiState.value.copy(defaultPrinter = defaultPrinter)

            // Load printers and queue status in parallel
            val printersResult = repository.getPrinters()
            val queueResult = repository.getQueueStatus()

            // Check if we got connection errors that might indicate tunnel URL changed
            val hasConnectionError = (printersResult is ApiResult.Error && isConnectionError(printersResult)) ||
                                     (queueResult is ApiResult.Error && isConnectionError(queueResult))

            if (hasConnectionError) {
                Log.d(TAG, "Connection error detected, attempting tunnel URL refresh")
                refreshTunnelUrlAndRetry()
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                printers = when (printersResult) {
                    is ApiResult.Success -> printersResult.data
                    is ApiResult.Error -> emptyList()
                },
                queueStatus = when (queueResult) {
                    is ApiResult.Success -> queueResult.data
                    is ApiResult.Error -> null
                },
                errorMessage = when {
                    printersResult is ApiResult.Error -> printersResult.message
                    queueResult is ApiResult.Error -> queueResult.message
                    else -> null
                }
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)

            val printersResult = repository.getPrinters()
            val queueResult = repository.getQueueStatus()

            // Check if we got connection errors that might indicate tunnel URL changed
            val hasConnectionError = (printersResult is ApiResult.Error && isConnectionError(printersResult)) ||
                                     (queueResult is ApiResult.Error && isConnectionError(queueResult))

            if (hasConnectionError) {
                Log.d(TAG, "Connection error on refresh, attempting tunnel URL refresh")
                _uiState.value = _uiState.value.copy(isRefreshing = false)
                refreshTunnelUrlAndRetry()
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isRefreshing = false,
                printers = when (printersResult) {
                    is ApiResult.Success -> printersResult.data
                    is ApiResult.Error -> _uiState.value.printers
                },
                queueStatus = when (queueResult) {
                    is ApiResult.Success -> queueResult.data
                    is ApiResult.Error -> _uiState.value.queueStatus
                },
                errorMessage = when {
                    printersResult is ApiResult.Error -> printersResult.message
                    queueResult is ApiResult.Error -> queueResult.message
                    else -> null
                }
            )
        }
    }

    /**
     * Check if the error indicates a connection failure (tunnel URL might have changed).
     */
    private fun isConnectionError(result: ApiResult.Error): Boolean {
        val message = result.message.lowercase()
        val code = result.code

        // Network errors, timeouts, and 5xx errors suggest connection issues
        return code == 503 || code == 502 || code == 504 ||
               message.contains("timeout") ||
               message.contains("unable to resolve") ||
               message.contains("failed to connect") ||
               message.contains("connection refused") ||
               message.contains("network") ||
               message.contains("unreachable")
    }

    /**
     * Attempt to refresh the tunnel URL and retry loading data.
     */
    private fun refreshTunnelUrlAndRetry() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRefreshingTunnel = true,
                errorMessage = "Connection lost. Refreshing tunnel URL..."
            )

            val haUrl = settings.haUrl.first()
            val token = settings.haToken.first()
            val cookies = settings.haCookies.first()

            if (haUrl.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshingTunnel = false,
                    errorMessage = "Please reconfigure the app - no Home Assistant URL saved"
                )
                return@launch
            }

            apiFactory.clearCache()
            val cookiesOrNull = cookies.ifEmpty { null }

            when (val discoveryResult = apiFactory.discoverTunnelUrl(haUrl, token, cookiesOrNull)) {
                is AddonDiscoveryResult.Success -> {
                    Log.d(TAG, "Tunnel URL refreshed: ${discoveryResult.tunnelUrl}")

                    // Save new tunnel URL
                    if (!discoveryResult.tunnelUrl.isNullOrEmpty()) {
                        settings.saveTunnelUrl(
                            tunnelUrl = discoveryResult.tunnelUrl,
                            provider = discoveryResult.tunnelProvider ?: "localtunnel"
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        isRefreshingTunnel = false,
                        errorMessage = null
                    )

                    // Retry loading data with new URL
                    loadData()
                }
                is AddonDiscoveryResult.Error -> {
                    Log.e(TAG, "Failed to refresh tunnel URL: ${discoveryResult.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshingTunnel = false,
                        errorMessage = "Connection lost. Could not discover new tunnel URL.\n\n" +
                                      "The addon may have restarted with a new URL. " +
                                      "Please check the RelayPrint dashboard for the current URL and reconfigure the app."
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
