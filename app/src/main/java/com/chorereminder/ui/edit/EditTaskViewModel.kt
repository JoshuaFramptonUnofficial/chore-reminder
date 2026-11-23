package com.chorereminder.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chorereminder.AppContainer
import com.chorereminder.data.ChoreRepository
import kotlinx.coroutines.CancellationException
import com.chorereminder.data.CategoryEntity
import com.chorereminder.data.IconType
import com.chorereminder.data.RecurrenceKind
import com.chorereminder.data.RecurrenceSpec
import com.chorereminder.data.TaskEntity
import com.chorereminder.data.UNCATEGORIZED_ID
import com.chorereminder.domain.IntervalUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

/** Editable form state. Kept separate from [TaskEntity] so invalid drafts are legal. */
data class EditTaskState(
    val taskId: Long = 0,
    val name: String = "",
    val iconType: IconType = IconType.MATERIAL,
    val iconValue: String = "CheckCircle",
    val categoryId: Long = UNCATEGORIZED_ID,
    val newCategoryName: String = "",
    val kind: RecurrenceKind = RecurrenceKind.RELATIVE,
    val intervalText: String = "14",
    val unit: IntervalUnit = IntervalUnit.DAYS,
    val anchor: LocalDate = LocalDate.now(),
    val dayOfWeek: DayOfWeek = LocalDate.now().dayOfWeek,
    val createdDate: LocalDate = LocalDate.now(),
    val visibility: com.chorereminder.data.VisibilitySpec? = null,
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val saving: Boolean = false,
    val deleting: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
) {
    val isEditing: Boolean get() = taskId != 0L

    val busy: Boolean get() = saving || deleting

    internal fun draft() = copy(loaded = true, missing = false, saving = false, deleting = false, error = null, finished = false)

    val interval: Int? get() = intervalText.toIntOrNull()?.takeIf { it >= 1 }

    /** FR-1: a task needs a name and a valid recurrence rule to be saveable. */
    val canSave: Boolean
        get() = loaded && !missing && !busy && name.isNotBlank() && (visibility == null || visibility.amount in 1..999) &&
            (kind == RecurrenceKind.FIXED_WEEKDAY || kind == RecurrenceKind.ONE_OFF || interval != null)
}

class EditTaskViewModel internal constructor(
    private val repository: ChoreRepository,
    private val taskId: Long,
    private val persistTask: suspend (TaskEntity) -> Long,
    private val removeTask: suspend (Long) -> Unit,
) : ViewModel() {
    constructor(container: AppContainer, taskId: Long) : this(container.repository, taskId,
        { entity -> if (entity.id == 0L) container.createTask(entity) else { container.updateTask(entity); entity.id } },
        { id -> container.deleteTask(id) })


    private val _state = MutableStateFlow(EditTaskState(taskId = taskId))
    val state: StateFlow<EditTaskState> = _state.asStateFlow()

    private var baseline: EditTaskState? = null
    val hasUnsavedChanges: Boolean get() = _state.value.let { it.loaded && !it.missing && it.draft() != baseline?.draft() }

    private fun updateDraft(change: (EditTaskState) -> EditTaskState) {
        _state.update { if (it.loaded && !it.busy && !it.missing) change(it).copy(error = null) else it }
    }

    val defaultVisibility = repository.observeVisibility()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.chorereminder.data.VisibilitySpec())

    val categories: StateFlow<List<CategoryEntity>> =
        repository.observeCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (taskId != 0L) {
            viewModelScope.launch {
                val view = repository.getTask(taskId)
                if (view != null) {
                    val t = view.task
                    val r = t.recurrence
                    _state.value = EditTaskState(
                        taskId = t.id,
                        name = t.name,
                        iconType = t.iconType,
                        iconValue = t.iconValue,
                        categoryId = t.categoryId,
                        kind = r.kind,
                        intervalText = r.interval.toString(),
                        unit = r.unit,
                        anchor = r.anchor ?: t.createdDate,
                        dayOfWeek = r.dayOfWeek ?: t.createdDate.dayOfWeek,
                        createdDate = t.createdDate,
                        visibility = t.visibility,
                        loaded = true,
                    )
                } else {
                    _state.update { it.copy(loaded = true, missing = true) }
                }
                baseline = _state.value
            }
        } else {
            _state.update { it.copy(loaded = true) }
            baseline = _state.value
        }
    }

    fun setVisibility(rule: com.chorereminder.data.VisibilitySpec?) = updateDraft { it.copy(visibility = rule) }

    fun setName(value: String) = updateDraft { it.copy(name = value) }
    fun setIcon(type: IconType, value: String) =
        updateDraft { it.copy(iconType = type, iconValue = value) }
    fun setCategory(id: Long) = updateDraft { it.copy(categoryId = id) }
    fun setNewCategoryName(value: String) = updateDraft { it.copy(newCategoryName = value) }
    fun setKind(kind: RecurrenceKind) = updateDraft { it.copy(kind = kind) }
    fun setIntervalText(value: String) =
        updateDraft { it.copy(intervalText = value.filter(Char::isDigit).take(4)) }
    fun setUnit(unit: IntervalUnit) = updateDraft { it.copy(unit = unit) }
    fun setAnchor(date: LocalDate) = updateDraft { it.copy(anchor = date) }
    fun setDayOfWeek(day: DayOfWeek) = updateDraft { it.copy(dayOfWeek = day) }

    /** Creates (or reuses) a named category and selects it (FR-12). */
    fun addCategory(onDone: () -> Unit = {}) {
        val name = _state.value.newCategoryName.trim()
        if (name.isEmpty() || _state.value.busy || !_state.value.loaded) return
        viewModelScope.launch {
            try {
                val id = repository.createCategory(name)
                updateDraft { it.copy(categoryId = id, newCategoryName = "") }
                onDone()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.update { it.copy(error = "Could not add the category. Please try again.") } }
        }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.value = s.copy(saving = true, error = null)

        val spec = when (s.kind) {
            RecurrenceKind.RELATIVE -> RecurrenceSpec(
                kind = RecurrenceKind.RELATIVE,
                interval = s.interval ?: 1,
                unit = s.unit,
            )
            RecurrenceKind.FIXED_INTERVAL -> RecurrenceSpec(
                kind = RecurrenceKind.FIXED_INTERVAL,
                interval = s.interval ?: 1,
                unit = s.unit,
                anchor = s.anchor,
            )
            RecurrenceKind.FIXED_WEEKDAY -> RecurrenceSpec(
                kind = RecurrenceKind.FIXED_WEEKDAY,
                dayOfWeek = s.dayOfWeek,
            )
            RecurrenceKind.ONE_OFF -> RecurrenceSpec(
                kind = RecurrenceKind.ONE_OFF,
                anchor = s.anchor,
            )
        }

        val entity = TaskEntity(
            id = s.taskId,
            name = s.name.trim(),
            iconType = s.iconType,
            iconValue = s.iconValue,
            categoryId = s.categoryId,
            // FR-2: editing never rewrites the creation date, so relative rules on
            // a never-completed task keep their original reference point.
            createdDate = s.createdDate,
            visibility = s.visibility,
            recurrence = spec,
        )

        viewModelScope.launch {
            try {
                val id = persistTask(entity)
                baseline = s.copy(taskId = id)
                // Keep the gate closed through navigation, including repeated queued taps.
                _state.update { it.copy(taskId = id, finished = true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _state.update { it.copy(saving = false, error = "Could not save this chore. Your edits are still here. Please try again.") }
            }
        }
    }

    fun delete() {
        val current = _state.value
        val id = current.taskId
        if (id == 0L || current.busy || !current.loaded || current.missing) return
        _state.value = current.copy(deleting = true, error = null)
        viewModelScope.launch {
            try { removeTask(id); _state.update { it.copy(finished = true) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.update { it.copy(deleting = false, error = "Could not delete this chore. Please try again.") } }
        }
    }

    companion object {
        fun factory(container: AppContainer, taskId: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EditTaskViewModel(container, taskId) as T
        }
    }
}
