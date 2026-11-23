package com.chorereminder.ui.edit

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.chorereminder.data.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EditTaskViewModelTest {
    private lateinit var db: ChoreDatabase
    private lateinit var repository: ChoreRepository
    private val store = ViewModelStore()

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ChoreDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = ChoreRepository(db.choreDao())
        db.choreDao().insertCategory(CategoryEntity(1, "Uncategorized"))
        Unit
    }
    @After fun tearDown() { store.clear(); shadowOf(Looper.getMainLooper()).idle(); db.close() }

    private fun editor(id: Long = 0, save: suspend (TaskEntity) -> Long = { 42L }, delete: suspend (Long) -> Unit = {}) =
        EditTaskViewModel(repository, id, save, delete).also { store.put("editor", it) }

    private fun pumpUntil(condition: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("Editor did not reach the expected state")
    }

    @Test fun invalidFormCannotBeSubmitted() {
        var calls = 0
        val vm = editor(save = { calls++; 42L })
        assertFalse(vm.state.value.canSave)
        vm.save()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, calls)
        assertFalse(vm.state.value.saving)
    }

    @Test fun rapidSaveTapsSubmitOnce_andKeepGateClosedThroughNavigation() {
        var calls = 0
        val finish = CompletableDeferred<Long>()
        val vm = editor(save = { calls++; finish.await() })
        vm.setName("Water plants")
        repeat(3) { vm.save() }
        assertTrue(vm.state.value.saving)
        assertFalse(vm.state.value.canSave)
        pumpUntil { calls == 1 }
        vm.setName("Unexpected edit")
        vm.setIntervalText("999")
        assertEquals("Water plants", vm.state.value.name)
        assertEquals("14", vm.state.value.intervalText)
        finish.complete(42)
        pumpUntil { vm.state.value.finished }
        vm.save()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, calls)
        assertEquals(42L, vm.state.value.taskId)
        assertFalse(vm.hasUnsavedChanges)
    }

    @Test fun failedSaveKeepsDraftAndAllowsOneRetry() {
        var calls = 0
        val vm = editor(save = { if (++calls == 1) error("Disk unavailable"); 42L })
        vm.setName("Wash towels")
        vm.save()
        pumpUntil { vm.state.value.error != null }
        assertEquals("Wash towels", vm.state.value.name)
        assertTrue(vm.state.value.canSave)
        assertTrue(vm.hasUnsavedChanges)
        vm.save()
        pumpUntil { vm.state.value.finished }
        assertEquals(2, calls)
        assertFalse(vm.hasUnsavedChanges)
    }

    @Test fun unsavedChangesReflectDraft_andRevertingRestoresCleanState() {
        val vm = editor()
        assertFalse(vm.hasUnsavedChanges)
        vm.setName("Test")
        assertTrue(vm.hasUnsavedChanges)
        vm.setName("")
        assertFalse(vm.hasUnsavedChanges)
        vm.setNewCategoryName("Kitchen")
        assertTrue(vm.hasUnsavedChanges)
        vm.setNewCategoryName("")
        assertFalse(vm.hasUnsavedChanges)
        vm.setVisibility(VisibilitySpec("BEFORE_DUE", 3))
        assertTrue(vm.hasUnsavedChanges)
        vm.setVisibility(null)
        assertFalse(vm.hasUnsavedChanges)
    }

    @Test fun loadedChoreStartsClean_andSavePreservesCreationAndSchedule() = runBlocking {
        val original = TaskEntity(name = "Rent", createdDate = LocalDate.parse("2026-01-01"),
            recurrence = RecurrenceSpec(RecurrenceKind.FIXED_INTERVAL, interval = 1,
                unit = com.chorereminder.domain.IntervalUnit.MONTHS, anchor = LocalDate.parse("2026-01-15")))
        val id = repository.insertTask(original)
        var persisted: TaskEntity? = null
        val vm = editor(id, save = { persisted = it; id })
        pumpUntil { vm.state.value.loaded }
        assertFalse(vm.hasUnsavedChanges)
        vm.setName("Rent payment")
        assertTrue(vm.hasUnsavedChanges)
        vm.setName("Rent")
        assertFalse(vm.hasUnsavedChanges)
        vm.setName("Rent payment")
        vm.save()
        pumpUntil { persisted != null }
        assertEquals(original.createdDate, persisted!!.createdDate)
        assertEquals(original.recurrence, persisted!!.recurrence)
    }

    @Test fun missingChoreCannotBecomeAnEditableBlankForm() {
        val vm = editor(999)
        pumpUntil { vm.state.value.loaded }
        assertTrue(vm.state.value.missing)
        vm.setName("Should not be saved")
        assertFalse(vm.state.value.canSave)
        assertFalse(vm.hasUnsavedChanges)
    }

    @Test fun deleteCannotRaceSave() = runBlocking {
        val id = repository.insertTask(TaskEntity(name = "Test", createdDate = LocalDate.now(), recurrence = RecurrenceSpec(RecurrenceKind.RELATIVE)))
        val finish = CompletableDeferred<Long>()
        var deletes = 0
        val vm = editor(id, save = { finish.await() }, delete = { deletes++ })
        pumpUntil { vm.state.value.loaded }
        vm.save()
        vm.delete()
        assertEquals(0, deletes)
        finish.complete(id)
        pumpUntil { !vm.hasUnsavedChanges }
    }

    @Test fun failedDeleteKeepsChoreAndReenablesActions() = runBlocking {
        val id = repository.insertTask(TaskEntity(name = "Test", createdDate = LocalDate.now(), recurrence = RecurrenceSpec(RecurrenceKind.RELATIVE)))
        val vm = editor(id, delete = { error("Disk unavailable") })
        pumpUntil { vm.state.value.loaded }
        vm.delete()
        pumpUntil { vm.state.value.error != null }
        assertFalse(vm.state.value.busy)
        assertTrue(vm.state.value.canSave)
        assertNotNull(repository.getTask(id))
    }
}
