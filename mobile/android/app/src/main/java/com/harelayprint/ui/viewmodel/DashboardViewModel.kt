package com.harelayprint.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harelayprint.data.api.Printer
import com.harelayprint.data.api.QueueStatus
import com.harelayprint.data.repository.ApiResult
import com.harelayprint.data.repository.PrintRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val printers: List<Printer> = emptyList(),
    val queueStatus: QueueStatus? = null,
    val errorMessage: String? = null,
    val defaultPrinter: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: PrintRepository
) : ViewModel() {

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

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
