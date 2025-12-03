package com.harelayprint.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harelayprint.data.api.Printer
import com.harelayprint.data.local.SettingsDataStore
import com.harelayprint.data.repository.ApiResult
import com.harelayprint.data.repository.PrintRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrinterListUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val printers: List<Printer> = emptyList(),
    val defaultPrinter: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class PrinterListViewModel @Inject constructor(
    private val repository: PrintRepository,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrinterListUiState())
    val uiState: StateFlow<PrinterListUiState> = _uiState.asStateFlow()

    init {
        loadPrinters()
    }

    fun loadPrinters() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val defaultPrinter = settings.defaultPrinter.first()

            when (val result = repository.getPrinters()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        printers = result.data,
                        defaultPrinter = defaultPrinter
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)

            when (val result = repository.getPrinters()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        printers = result.data
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun setDefaultPrinter(printerName: String) {
        viewModelScope.launch {
            settings.setDefaultPrinter(printerName)
            _uiState.value = _uiState.value.copy(defaultPrinter = printerName)
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
