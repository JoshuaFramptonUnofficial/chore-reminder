package com.chorereminder.ui.complete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chorereminder.AppContainer
import com.chorereminder.data.TaskView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CompleteUiState {
    data object Loading : CompleteUiState
    /** The task was deleted before the user got to the notification. */
    data object Missing : CompleteUiState
    data class Ready(val task: TaskView, val saving: Boolean = false) : CompleteUiState
}

class CompleteViewModel(
    private val container: AppContainer,
    private val taskId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow<CompleteUiState>(CompleteUiState.Loading)
    val state: StateFlow<CompleteUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val view = container.repository.getTask(taskId)
            _state.value =
                if (view == null) CompleteUiState.Missing else CompleteUiState.Ready(view)
        }
    }

    /** FR-10: timestamped at the moment of confirmation, then next-due recalculated. */
    fun confirm(onFinished: () -> Unit) {
        val current = _state.value
        if (current !is CompleteUiState.Ready || current.saving) return
        _state.value = current.copy(saving = true)
        viewModelScope.launch {
            container.completeTask(taskId)
            onFinished()
        }
    }

    companion object {
        fun factory(container: AppContainer, taskId: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CompleteViewModel(container, taskId) as T
        }
    }
}
