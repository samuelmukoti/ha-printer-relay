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
    val setupStep: SetupStep = SetupStep.ENTER_URL,
    val authUrl: String? = null  // URL for WebView OAuth
)

enum class SetupStep {
    ENTER_URL,      // User enters HA URL
    AUTHENTICATE,   // OAuth login in WebView
    DISCOVERING,    // Finding RelayPrint addon
    COMPLETE        // Ready to use
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
        // Check if already configured
        viewModelScope.launch {
            val isConfigured = settings.isConfigured.first()
            if (isConfigured) {
                _uiState.value = _uiState.value.copy(
                    setupStep = SetupStep.COMPLETE,
                    isConnected = true
                )
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
     * Also receives session cookies from WebView for panel ingress access.
     */
    fun handleAuthCode(code: String, cookies: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                setupStep = SetupStep.DISCOVERING,
                authUrl = null,
                errorMessage = null
            )

            // Exchange code for token
            when (val authResult = authManager.exchangeCodeForToken(code)) {
                is AuthResult.Success -> {
                    // Now discover the addon - pass cookies for panel ingress
                    discoverAddon(authResult.accessToken, cookies)
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
     * Discover the RelayPrint addon and verify connection.
     * Uses session cookies for panel ingress, or bearer token for API ingress.
     */
    private suspend fun discoverAddon(token: String, cookies: String? = null) {
        val haUrl = settings.haUrl.first()

        when (val discoveryResult = apiFactory.discoverAddon(haUrl, token, cookies)) {
            is AddonDiscoveryResult.Success -> {
                // Save the ingress URL and session token (if used)
                settings.saveIngressUrl(
                    ingressUrl = discoveryResult.ingressUrl,
                    sessionToken = discoveryResult.sessionToken,
                    addonSlug = discoveryResult.addonSlug
                )
                settings.setConfigured(true)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isConnected = true,
                    serverVersion = discoveryResult.version,
                    setupStep = SetupStep.COMPLETE,
                    errorMessage = null
                )
            }
            is AddonDiscoveryResult.Error -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    setupStep = SetupStep.DISCOVERING,
                    errorMessage = discoveryResult.message
                )
            }
        }
    }

    /**
     * Retry discovery (e.g., after starting the addon).
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

            // Clear any cached session and re-discover
            apiFactory.clearCache()
            discoverAddon(token)
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
            apiFactory.clearCache()

            _uiState.value = SetupUiState()
        }
    }
}
