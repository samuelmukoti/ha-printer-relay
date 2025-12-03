package com.harelayprint.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harelayprint.data.api.PrintJob
import com.harelayprint.data.api.QueueStatus
import com.harelayprint.data.repository.ApiResult
import com.harelayprint.data.repository.PrintRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrintQueueUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val queueStatus: QueueStatus? = null,
    val errorMessage: String? = null,
    val cancellingJobId: Int? = null
)

@HiltViewModel
class PrintQueueViewModel @Inject constructor(
    private val repository: PrintRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrintQueueUiState())
    val uiState: StateFlow<PrintQueueUiState> = _uiState.asStateFlow()

    init {
        loadQueueStatus()
    }

    fun loadQueueStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = repository.getQueueStatus()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        queueStatus = result.data
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

            when (val result = repository.getQueueStatus()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        queueStatus = result.data
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

    fun cancelJob(jobId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cancellingJobId = jobId)

            when (val result = repository.cancelJob(jobId)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(cancellingJobId = null)
                    refresh()
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        cancellingJobId = null,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
