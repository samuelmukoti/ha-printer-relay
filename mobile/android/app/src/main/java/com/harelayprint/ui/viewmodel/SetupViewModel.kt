package com.harelayprint.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harelayprint.data.local.SettingsDataStore
import com.harelayprint.di.AddonDiscoveryResult
import com.harelayprint.di.ApiClientFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val tunnelUrl: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isConnected: Boolean = false,
    val serverVersion: String? = null,
    val setupStep: SetupStep = SetupStep.ENTER_URL
)

enum class SetupStep {
    ENTER_URL,      // User enters tunnel URL
    CONNECTING,     // Connecting to RelayPrint
    COMPLETE        // Ready to use
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val apiFactory: ApiClientFactory
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        // Check if already configured
        viewModelScope.launch {
            val isConfigured = settings.isConfigured.first()
            if (isConfigured) {
                // Load saved tunnel URL
                val savedTunnelUrl = settings.tunnelUrl.first()
                _uiState.value = _uiState.value.copy(
                    tunnelUrl = savedTunnelUrl,
                    setupStep = SetupStep.COMPLETE,
                    isConnected = true
                )
            }
        }
    }

    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            tunnelUrl = url,
            errorMessage = null,
            isConnected = false
        )
    }

    /**
     * Connect to RelayPrint using the tunnel URL.
     * No authentication needed - the tunnel provides public access.
     */
    fun connect() {
        val url = _uiState.value.tunnelUrl.trim()

        if (url.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Please enter the RelayPrint tunnel URL"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                setupStep = SetupStep.CONNECTING,
                errorMessage = null
            )

            // Connect to the tunnel URL (no token needed for tunnel access)
            when (val result = apiFactory.discoverAddon(url, "", null)) {
                is AddonDiscoveryResult.Success -> {
                    // Save the tunnel URL
                    settings.saveTunnelUrl(
                        tunnelUrl = result.tunnelUrl ?: url,
                        provider = result.tunnelProvider ?: "localtunnel"
                    )
                    settings.saveIngressUrl(
                        ingressUrl = result.ingressUrl,
                        addonSlug = result.addonSlug
                    )
                    settings.setConfigured(true)

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isConnected = true,
                        serverVersion = result.version,
                        setupStep = SetupStep.COMPLETE,
                        errorMessage = null
                    )
                }
                is AddonDiscoveryResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        setupStep = SetupStep.ENTER_URL,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    /**
     * Retry connection (e.g., after fixing tunnel issues).
     */
    fun retryConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )

            apiFactory.clearCache()
            connect()
        }
    }

    /**
     * Continue to main app after successful setup.
     */
    fun continueToApp(onComplete: () -> Unit) {
        if (_uiState.value.isConnected) {
            onComplete()
        } else {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Please complete setup first"
            )
        }
    }

    /**
     * Reset setup (e.g., to use a different tunnel URL).
     */
    fun resetSetup() {
        viewModelScope.launch {
            settings.clearAuth()
            settings.clearTunnelUrl()
            apiFactory.clearCache()

            _uiState.value = SetupUiState()
        }
    }
}
