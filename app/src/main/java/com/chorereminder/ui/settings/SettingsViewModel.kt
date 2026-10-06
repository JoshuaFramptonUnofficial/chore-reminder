package com.chorereminder.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chorereminder.AppContainer
import com.chorereminder.data.BackupResult
import com.chorereminder.data.CategoryWithCount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val categories: StateFlow<List<CategoryWithCount>> =
        container.repository.observeCategoriesWithCounts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** FR-12: a new category the user creates here needs no chore to exist yet. */
    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { container.repository.createCategory(name) }
    }

    fun renameCategory(categoryId: Long, newName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = container.repository.renameCategory(categoryId, newName)
            if (!ok) _message.value = "That name is already in use."
            onResult(ok)
        }
    }

    /** Deleting re-homes its chores to Uncategorized -- see ChoreDao.deleteCategory. */
    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch { container.repository.deleteCategory(categoryId) }
    }

    fun suggestedFileName(): String = container.backupManager.suggestedFileName()

    fun export(uri: Uri) {
        _busy.value = true
        viewModelScope.launch {
            _message.value = when (val r = container.backupManager.export(uri)) {
                is BackupResult.Success -> r.message
                is BackupResult.Failure -> r.message
            }
            _busy.value = false
        }
    }

    fun import(uri: Uri) {
        _busy.value = true
        viewModelScope.launch {
            val result = container.backupManager.import(uri)
            if (result is BackupResult.Success) {
                // Imported tasks have new due dates -- re-arm every reminder.
                container.rescheduleAll()
            }
            _message.value = when (result) {
                is BackupResult.Success -> result.message
                is BackupResult.Failure -> result.message
            }
            _busy.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(container) as T
        }
    }
}
