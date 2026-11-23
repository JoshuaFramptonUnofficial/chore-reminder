package com.chorereminder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chorereminder.AppContainer
import com.chorereminder.data.CategoryGroup
import com.chorereminder.data.TaskView
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
    val followCompleted = container.uiPreferences.followCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val taskChange = container.taskChange
    val appContainer = container
    val receipts = container.undoQueue.receipts
    val today = container.today
    val includeHidden = kotlinx.coroutines.flow.MutableStateFlow(false)
    fun toggleHidden() { includeHidden.value = !includeHidden.value }

    val homeData: StateFlow<com.chorereminder.data.HomeData> =
        container.repository.observeHomeData(container.today, includeHidden)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.chorereminder.data.HomeData())

    /** FR-14: identical effect to confirming from a notification. */
    fun complete(task: TaskView) {
        viewModelScope.launch { container.completeTask(task.task.id, task.occurrenceRevision) }
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
