package com.chorereminder.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chorereminder.AppContainer
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
    val loaded: Boolean = false,
) {
    val isEditing: Boolean get() = taskId != 0L

    val interval: Int? get() = intervalText.toIntOrNull()?.takeIf { it >= 1 }

    /** FR-1: a task needs a name and a valid recurrence rule to be saveable. */
    val canSave: Boolean
        get() = name.isNotBlank() &&
            (kind == RecurrenceKind.FIXED_WEEKDAY || kind == RecurrenceKind.ONE_OFF || interval != null)
}

class EditTaskViewModel(
    private val container: AppContainer,
    private val taskId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(EditTaskState(taskId = taskId))
    val state: StateFlow<EditTaskState> = _state.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> =
        container.repository.observeCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (taskId != 0L) {
            viewModelScope.launch {
                val view = container.repository.getTask(taskId)
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
                        loaded = true,
                    )
                } else {
                    _state.update { it.copy(loaded = true) }
                }
            }
        } else {
            _state.update { it.copy(loaded = true) }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setIcon(type: IconType, value: String) =
        _state.update { it.copy(iconType = type, iconValue = value) }
    fun setCategory(id: Long) = _state.update { it.copy(categoryId = id) }
    fun setNewCategoryName(value: String) = _state.update { it.copy(newCategoryName = value) }
    fun setKind(kind: RecurrenceKind) = _state.update { it.copy(kind = kind) }
    fun setIntervalText(value: String) =
        _state.update { it.copy(intervalText = value.filter(Char::isDigit).take(4)) }
    fun setUnit(unit: IntervalUnit) = _state.update { it.copy(unit = unit) }
    fun setAnchor(date: LocalDate) = _state.update { it.copy(anchor = date) }
    fun setDayOfWeek(day: DayOfWeek) = _state.update { it.copy(dayOfWeek = day) }

    /** Creates (or reuses) a named category and selects it (FR-12). */
    fun addCategory(onDone: () -> Unit = {}) {
        val name = _state.value.newCategoryName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val id = container.repository.createCategory(name)
            _state.update { it.copy(categoryId = id, newCategoryName = "") }
            onDone()
        }
    }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return

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
            recurrence = spec,
        )

        viewModelScope.launch {
            if (s.isEditing) container.updateTask(entity) else container.createTask(entity)
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = _state.value.taskId
        if (id == 0L) return
        viewModelScope.launch {
            container.deleteTask(id)
            onDeleted()
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
