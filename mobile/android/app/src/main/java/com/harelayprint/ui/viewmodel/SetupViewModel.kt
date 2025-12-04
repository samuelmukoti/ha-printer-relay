package com.harelayprint.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harelayprint.data.auth.AuthResult
import com.harelayprint.data.auth.HaAuthManager
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
    val haUrl: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isConnected: Boolean = false,
    val serverVersion: String? = null,
    val tunnelUrl: String? = null,
    val setupStep: SetupStep = SetupStep.ENTER_URL,
    val authUrl: String? = null  // URL for WebView OAuth
)

enum class SetupStep {
    ENTER_URL,          // User enters HA URL
    AUTHENTICATE,       // OAuth login in WebView
    DISCOVERING,        // Discovering tunnel URL
    ENTER_TUNNEL_URL,   // Manual tunnel URL entry (if auto-discovery fails)
    COMPLETE            // Ready to use
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val authManager: HaAuthManager,
    private val apiFactory: ApiClientFactory
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        // Check if already configured and refresh tunnel URL
        viewModelScope.launch {
            val isConfigured = settings.isConfigured.first()
            if (isConfigured) {
                // Show loading while we refresh the tunnel URL
                val savedTunnelUrl = settings.tunnelUrl.first()
                _uiState.value = _uiState.value.copy(
                    tunnelUrl = savedTunnelUrl.ifEmpty { null },
                    setupStep = SetupStep.COMPLETE,
                    isConnected = true,
                    isLoading = true  // Show loading while refreshing
                )

                // Refresh the tunnel URL in the background
                refreshTunnelUrl()
            }
        }
    }

    /**
     * Refresh the tunnel URL on app startup.
     * LocalTunnel URLs change each time the addon restarts, so we need to
     * re-discover the current URL.
     */
    private fun refreshTunnelUrl() {
        viewModelScope.launch {
            val haUrl = settings.haUrl.first()
            val token = settings.haToken.first()
            val cookies = settings.haCookies.first()
            val savedTunnelUrl = settings.tunnelUrl.first()

            if (haUrl.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            // First, try to verify the saved tunnel URL still works
            if (savedTunnelUrl.isNotEmpty()) {
                val probeResult = apiFactory.discoverAddon(savedTunnelUrl, token)
                if (probeResult is AddonDiscoveryResult.Success) {
                    // Saved URL still works
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        serverVersion = probeResult.version,
                        tunnelUrl = savedTunnelUrl
                    )
                    return@launch
                }
            }

            // Saved URL doesn't work, try to discover new one
            apiFactory.clearCache()
            val cookiesOrNull = cookies.ifEmpty { null }

            when (val discoveryResult = apiFactory.discoverTunnelUrl(haUrl, token, cookiesOrNull)) {
                is AddonDiscoveryResult.Success -> {
                    // Update with new tunnel URL
                    if (!discoveryResult.tunnelUrl.isNullOrEmpty()) {
                        settings.saveTunnelUrl(
                            tunnelUrl = discoveryResult.tunnelUrl,
                            provider = discoveryResult.tunnelProvider ?: "localtunnel"
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        serverVersion = discoveryResult.version,
                        tunnelUrl = discoveryResult.tunnelUrl,
                        errorMessage = null
                    )
                }
                is AddonDiscoveryResult.Error -> {
                    // Discovery failed - show error but stay on complete screen
                    // User can manually re-enter the tunnel URL
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = if (discoveryResult.message == "MANUAL_ENTRY_REQUIRED") {
                            "Could not auto-discover tunnel URL. Please check the addon dashboard for the current URL."
                        } else {
                            "Connection error: ${discoveryResult.message}"
                        }
                    )
                }
            }
        }
    }

    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            haUrl = url,
            errorMessage = null,
            isConnected = false
        )
    }

    /**
     * Start the OAuth2 login flow.
     * Builds the auth URL and transitions to the AUTHENTICATE step.
     */
    fun startLogin() {
        val url = _uiState.value.haUrl.trim()

        if (url.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Please enter your Home Assistant URL"
            )
            return
        }

        viewModelScope.launch {
            // Save the URL first
            settings.saveHaUrl(url)

            // Build the OAuth URL
            val authUrl = authManager.buildAuthUrl(url)

            _uiState.value = _uiState.value.copy(
                setupStep = SetupStep.AUTHENTICATE,
                authUrl = authUrl,
                errorMessage = null
            )
        }
    }

    /**
     * Handle successful OAuth authorization code from WebView.
     */
    fun handleAuthCode(code: String, cookies: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                setupStep = SetupStep.DISCOVERING,
                authUrl = null,
                errorMessage = null
            )

            // Save cookies for later ingress access
            if (!cookies.isNullOrEmpty()) {
                settings.saveCookies(cookies)
            }

            // Exchange code for token
            when (val authResult = authManager.exchangeCodeForToken(code)) {
                is AuthResult.Success -> {
                    // Now discover the tunnel URL using the token and cookies
                    discoverTunnelUrl(authResult.accessToken, cookies)
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        setupStep = SetupStep.ENTER_URL,
                        errorMessage = "Authentication failed: ${authResult.message}"
                    )
                }
            }
        }
    }

    /**
     * Handle OAuth error from WebView.
     */
    fun handleAuthError(error: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            setupStep = SetupStep.ENTER_URL,
            authUrl = null,
            errorMessage = "Login failed: $error"
        )
    }

    /**
     * Cancel OAuth flow and return to URL entry.
     */
    fun cancelAuth() {
        _uiState.value = _uiState.value.copy(
            setupStep = SetupStep.ENTER_URL,
            authUrl = null,
            errorMessage = null
        )
    }

    /**
     * Discover the tunnel URL using the OAuth token and session cookies.
     * The token is used to access HA API and discover the RelayPrint addon's tunnel URL.
     * After discovery, the tunnel URL is used for all API calls (no token needed).
     */
    private suspend fun discoverTunnelUrl(token: String, cookies: String? = null) {
        val haUrl = settings.haUrl.first()

        when (val discoveryResult = apiFactory.discoverTunnelUrl(haUrl, token, cookies)) {
            is AddonDiscoveryResult.Success -> {
                // Save the tunnel URL
                if (!discoveryResult.tunnelUrl.isNullOrEmpty()) {
                    settings.saveTunnelUrl(
                        tunnelUrl = discoveryResult.tunnelUrl,
                        provider = discoveryResult.tunnelProvider ?: "localtunnel"
                    )
                }

                settings.saveIngressUrl(
                    ingressUrl = discoveryResult.ingressUrl,
                    addonSlug = discoveryResult.addonSlug
                )
                settings.setConfigured(true)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isConnected = true,
                    serverVersion = discoveryResult.version,
                    tunnelUrl = discoveryResult.tunnelUrl,
                    setupStep = SetupStep.COMPLETE,
                    errorMessage = null
                )
            }
            is AddonDiscoveryResult.Error -> {
                if (discoveryResult.message == "MANUAL_ENTRY_REQUIRED") {
                    // Supervisor API not accessible, prompt for manual tunnel URL entry
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        setupStep = SetupStep.ENTER_TUNNEL_URL,
                        errorMessage = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        setupStep = SetupStep.DISCOVERING,
                        errorMessage = discoveryResult.message
                    )
                }
            }
        }
    }

    /**
     * Update the tunnel URL (for manual entry step).
     */
    fun updateTunnelUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            tunnelUrl = url,
            errorMessage = null
        )
    }

    /**
     * Connect using manually entered tunnel URL.
     */
    fun connectWithTunnelUrl() {
        val tunnelUrl = _uiState.value.tunnelUrl?.trim()

        if (tunnelUrl.isNullOrEmpty()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Please enter the tunnel URL"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )

            // Get the token we saved earlier
            val token = settings.haToken.first()

            // Verify the tunnel URL works
            when (val result = apiFactory.discoverAddon(tunnelUrl, token)) {
                is AddonDiscoveryResult.Success -> {
                    // Save the tunnel URL
                    settings.saveTunnelUrl(
                        tunnelUrl = tunnelUrl,
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
                        tunnelUrl = tunnelUrl,
                        setupStep = SetupStep.COMPLETE,
                        errorMessage = null
                    )
                }
                is AddonDiscoveryResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    /**
     * Retry discovery (e.g., after enabling tunnel in addon settings).
     */
    fun retryDiscovery() {
        viewModelScope.launch {
            val token = settings.haToken.first()
            if (token.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Please log in first",
                    setupStep = SetupStep.ENTER_URL
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )

            apiFactory.clearCache()
            discoverTunnelUrl(token)
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
     * Reset setup (e.g., to use a different HA instance).
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
