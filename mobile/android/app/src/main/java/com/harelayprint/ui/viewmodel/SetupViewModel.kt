package com.harelayprint.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harelayprint.data.local.SettingsDataStore
import com.harelayprint.data.repository.ApiResult
import com.harelayprint.data.repository.PrintRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val haUrl: String = "",
    val haToken: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isConnected: Boolean = false,
    val serverVersion: String? = null
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val repository: PrintRepository,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            haUrl = url,
            errorMessage = null,
            isConnected = false
        )
    }

    fun updateToken(token: String) {
        _uiState.value = _uiState.value.copy(
            haToken = token,
            errorMessage = null,
            isConnected = false
        )
    }

    fun testConnection() {
        val url = _uiState.value.haUrl.trim()
        val token = _uiState.value.haToken.trim()

        if (url.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Please enter your Home Assistant URL"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = repository.testConnection(url, token)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isConnected = true,
                        serverVersion = result.data.version,
                        errorMessage = null
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isConnected = false,
                        errorMessage = getErrorMessage(result)
                    )
                }
            }
        }
    }

    fun saveAndContinue(onComplete: () -> Unit) {
        if (!_uiState.value.isConnected) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Please test the connection first"
            )
            return
        }

        viewModelScope.launch {
            settings.saveConnection(
                url = _uiState.value.haUrl.trim(),
                token = _uiState.value.haToken.trim()
            )
            onComplete()
        }
    }

    private fun getErrorMessage(error: ApiResult.Error): String {
        return when (error.code) {
            401 -> "Invalid or expired token"
            403 -> "Access forbidden - check token permissions"
            404 -> "RelayPrint add-on not found at this URL"
            in 500..599 -> "Server error - please try again"
            else -> error.message
        }
    }
}
