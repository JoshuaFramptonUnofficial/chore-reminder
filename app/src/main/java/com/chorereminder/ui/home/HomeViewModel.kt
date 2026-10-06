package com.chorereminder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chorereminder.AppContainer
import com.chorereminder.data.CategoryGroup
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    /**
     * Due state is recomputed from `LocalDate.now()` on each emission (FR-7), so it
     * is correct whenever the list is re-collected -- including after the app has
     * been backgrounded across midnight.
     */
    val groups: StateFlow<List<CategoryGroup>> =
        container.repository.observeGroupedTasks { LocalDate.now() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** FR-14: identical effect to confirming from a notification. */
    fun complete(taskId: Long) {
        viewModelScope.launch { container.completeTask(taskId) }
    }

    fun delete(taskId: Long) {
        viewModelScope.launch { container.deleteTask(taskId) }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(container) as T
        }
    }
}
