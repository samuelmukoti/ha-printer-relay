package com.harelayprint.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harelayprint.data.api.PaperSize
import com.harelayprint.data.api.PrintQuality
import com.harelayprint.data.local.SettingsDataStore
import com.harelayprint.di.ApiClientFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val haUrl: String = "",
    val defaultPrinter: String? = null,
    val defaultCopies: Int = 1,
    val defaultDuplex: Boolean = false,
    val defaultQuality: PrintQuality = PrintQuality.NORMAL,
    val defaultPaperSize: PaperSize = PaperSize.A4,
    val notificationsEnabled: Boolean = true,
    val isLoading: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val apiClientFactory: ApiClientFactory
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            combine(
                settings.haUrl,
                settings.defaultPrinter,
                settings.defaultCopies,
                settings.defaultDuplex,
                settings.defaultQuality,
                settings.defaultPaperSize,
                settings.notificationsEnabled
            ) { values ->
                SettingsUiState(
                    haUrl = values[0] as String,
                    defaultPrinter = values[1] as String?,
                    defaultCopies = values[2] as Int,
                    defaultDuplex = values[3] as Boolean,
                    defaultQuality = values[4] as PrintQuality,
                    defaultPaperSize = values[5] as PaperSize,
                    notificationsEnabled = values[6] as Boolean,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setDefaultCopies(copies: Int) {
        viewModelScope.launch {
            settings.setDefaultCopies(copies.coerceIn(1, 99))
        }
    }

    fun setDefaultDuplex(duplex: Boolean) {
        viewModelScope.launch {
            settings.setDefaultDuplex(duplex)
        }
    }

    fun setDefaultQuality(quality: PrintQuality) {
        viewModelScope.launch {
            settings.setDefaultQuality(quality)
        }
    }

    fun setDefaultPaperSize(paperSize: PaperSize) {
        viewModelScope.launch {
            settings.setDefaultPaperSize(paperSize)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setNotificationsEnabled(enabled)
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            settings.clearAll()
            apiClientFactory.clearCache()
            onComplete()
        }
    }
}
