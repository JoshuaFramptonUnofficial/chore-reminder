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
import kotlinx.coroutines.CancellationException

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val glassBlur = container.uiPreferences.liquidGlassBlur
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.chorereminder.data.UiPreferences.DEFAULT_GLASS_BLUR)
    fun setGlassBlur(value: Int) {
        container.applicationScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            container.uiPreferences.setLiquidGlassBlur(value)
        }
    }

    val followCompleted = container.uiPreferences.followCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setFollowCompleted(value: Boolean) {
        container.applicationScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            container.uiPreferences.setFollowCompleted(value)
        }
    }

    val visibility = container.repository.observeVisibility()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.chorereminder.data.VisibilitySpec())
    fun setVisibility(rule: com.chorereminder.data.VisibilitySpec) {
        if (rule.amount !in 1..999) return
        container.setDefaultVisibility(rule)
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _operation = MutableStateFlow<String?>(null)
    val operation = _operation.asStateFlow()

    val categories: StateFlow<List<CategoryWithCount>> =
        container.repository.observeCategoriesWithCounts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** FR-12: a new category the user creates here needs no chore to exist yet. */
    fun addCategory(name: String, onDone: () -> Unit) {
        if (name.isBlank() || _busy.value) return
        viewModelScope.launch {
            try { container.repository.createCategory(name); onDone() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value = "Could not add the category. Your text has been kept." }
        }
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
        if (!_busy.compareAndSet(false, true)) return
        _operation.value = "Exporting backup..."
        viewModelScope.launch {
            try {
                _message.value = when (val result = container.backupManager.export(uri)) {
                    is BackupResult.Success -> result.message
                    is BackupResult.Failure -> result.message
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value = "Could not export the backup. Please try another location." }
            finally { _busy.value = false; _operation.value = null }
        }
    }

    fun import(uri: Uri) {
        if (!_busy.compareAndSet(false, true)) return
        _operation.value = "Importing backup..."
        container.applicationScope.launch {
            try {
                _message.value = when (val result = container.importBackup(uri)) {
                    is BackupResult.Success -> result.message
                    is BackupResult.Failure -> result.message
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value = "Import could not finish. Check your chores before trying again." }
            finally { _busy.value = false; _operation.value = null }
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
