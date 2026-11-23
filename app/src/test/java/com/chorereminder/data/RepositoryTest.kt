package com.chorereminder.data

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RepositoryTest {
    private lateinit var db: ChoreDatabase
    private lateinit var repository: ChoreRepository
    private val date = LocalDate.parse("2026-11-13")
    private val at = Instant.parse("2026-11-13T12:00:00Z")

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ChoreDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = ChoreRepository(db.choreDao())
        db.choreDao().insertCategory(CategoryEntity(1, "Uncategorized"))
        Unit
    }

    @After fun tearDown() { db.close() }

    private suspend fun task(kind: RecurrenceKind = RecurrenceKind.RELATIVE): Long =
        repository.insertTask(TaskEntity(name = "Test", createdDate = date.minusDays(7),
            recurrence = RecurrenceSpec(kind, interval = 7, anchor = date)))

    @Test fun concurrentDuplicateActions_completeOnlyOnce() = runBlocking {
        val id = task()
        val results = (1..2).map { async(Dispatchers.IO) { repository.completeTask(id, 0, at) } }.awaitAll()
        assertEquals(1, results.count { it != null })
        assertEquals(1, repository.snapshot().third.size)
    }

    @Test fun undoRestoresDueDate_andInvalidatesStaleActionsEvenAfterUndo() = runBlocking {
        val id = task()
        val original = repository.getTask(id)!!
        val completion = repository.completeTask(id, 0, at)!!
        assertEquals(date.plusDays(7), repository.getTask(id)!!.nextDue)
        assertNotNull(repository.undo(completion.id))
        val restored = repository.getTask(id)!!
        assertEquals(original.nextDue, restored.nextDue)
        assertEquals(2L, restored.occurrenceRevision)
        assertNull(repository.completeTask(id, 0, at))
        assertNotNull(repository.completeTask(id, restored.occurrenceRevision, at))
    }

    @Test fun completedOneOff_survivesRepositoryRecreation_andCanBeUndone() = runBlocking {
        val id = task(RecurrenceKind.ONE_OFF)
        val completion = repository.completeTask(id, 0, at)!!
        val reopened = ChoreRepository(db.choreDao())
        assertNull(reopened.getTask(id))
        assertTrue(reopened.getAllTasks().isEmpty())
        assertEquals(completion.id, reopened.observeLatestCompletion().first()!!.id)
        assertNotNull(reopened.undo(completion.id))
        assertEquals(date, reopened.getTask(id)!!.nextDue)
    }

    @Test fun staleUndo_doesNotRemoveANewerCompletion() = runBlocking {
        val first = repository.completeTask(task(), 0, at)!!
        val second = repository.completeTask(task(), 0, at.plusSeconds(1))!!
        assertNull(repository.undo(first.id))
        assertNotNull(repository.undo(second.id))
        assertNull(repository.undo(second.id))
        assertEquals(first.id, repository.observeLatestCompletion().first()!!.id)
    }

    @Test fun snapshotIncludesHiddenOneOff_andBackupMappingPreservesUndoState() = runBlocking {
        val id = task(RecurrenceKind.ONE_OFF)
        repository.completeTask(id, 0, at)
        val (categories, tasks, completions) = repository.snapshot()
        repository.replaceAll(categories.map { it.toBackup().toEntity() },
            tasks.map { it.toBackup().toEntity() }, completions.map { it.toBackup().toEntity() })
        assertNull(repository.getTask(id))
        repository.undo(repository.observeLatestCompletion().first()!!.id)
        assertEquals(2L, repository.getTask(id)!!.occurrenceRevision)
    }

    @Test fun failedReplacement_rollsBackExistingData() = runBlocking {
        val id = task()
        val before = repository.snapshot()
        try {
            repository.replaceAll(emptyList(), listOf(before.second.single().copy(categoryId = 999)), emptyList())
            fail("foreign-key violation expected")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertNotNull(repository.getTask(id))
        assertEquals(before, repository.snapshot())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun dateChanges_refreshStatusWithoutDatabaseWrites() = runBlocking {
        task(RecurrenceKind.ONE_OFF)
        val dates = MutableStateFlow(date.minusDays(1))
        val emissions = repository.observeGroupedTasks(dates).produceIn(this)
        try {
            assertEquals(com.chorereminder.domain.DueState.UPCOMING,
                withTimeout(5000) { emissions.receive() }.single().tasks.single().dueState)
            dates.value = date
            assertEquals(com.chorereminder.domain.DueState.DUE_TODAY,
                withTimeout(5000) { emissions.receive() }.single().tasks.single().dueState)
            dates.value = date.plusDays(1)
            assertEquals(com.chorereminder.domain.DueState.OVERDUE,
                withTimeout(5000) { emissions.receive() }.single().tasks.single().dueState)
        } finally { emissions.cancel() }
    }

    @Test fun backupRoundTrip_preservesCompletedOneOffAndUndo() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val manager = BackupManager(app, repository)
        val file = java.io.File(app.cacheDir, "round-trip.json")
        val uri = android.net.Uri.fromFile(file)
        val id = task(RecurrenceKind.ONE_OFF)
        repository.completeTask(id, 0, at)
        assertTrue(manager.export(uri) is BackupResult.Success)
        assertTrue(file.readText().contains("\"version\": 5"))
        repository.replaceAll(listOf(CategoryEntity(1, "Uncategorized")), emptyList(), emptyList())
        assertTrue(manager.import(uri) is BackupResult.Success)
        assertNull(repository.getTask(id))
        assertNotNull(repository.undo(repository.observeLatestCompletion().first()!!.id))
        assertNotNull(repository.getTask(id))
    }

    @Test fun malformedBackup_doesNotChangeData() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        task()
        val before = repository.snapshot()
        val file = java.io.File(app.cacheDir, "bad.json").apply { writeText("{broken") }
        assertTrue(BackupManager(app, repository).import(android.net.Uri.fromFile(file)) is BackupResult.Failure)
        assertEquals(before, repository.snapshot())
    }

    @Test fun inheritanceFiltersOnlyHome_andShowHiddenKeepsTasksEditable() = runBlocking {
        val id = task(RecurrenceKind.ONE_OFF)
        val future = date.plusDays(20)
        repository.updateTask(repository.getTask(id)!!.task.copy(recurrence = RecurrenceSpec(RecurrenceKind.ONE_OFF, anchor = future)))
        repository.setVisibility(VisibilitySpec("BEFORE_DUE", 1))
        assertTrue(repository.observeGroupedTasks(flowOf(date)).first().isEmpty())
        assertNotNull(repository.getTask(id)) // Scheduler uses unfiltered active reads.
        assertEquals(1, repository.getAllTasks().size)
        assertEquals(1, repository.observeGroupedTasks(flowOf(date), flowOf(true)).first().size)
        repository.updateTask(repository.getTask(id)!!.task.copy(visibility = VisibilitySpec()))
        assertEquals(1, repository.observeGroupedTasks(flowOf(date)).first().size)
        repository.setVisibility(VisibilitySpec("AFTER_COMPLETION", 999))
        assertEquals(1, repository.observeGroupedTasks(flowOf(date)).first().size)
    }

    @Test fun visibilitySurvivesBackupImport_andOldBackupsDefaultToImmediate() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val manager = BackupManager(app, repository)
        val id = task()
        val custom = VisibilitySpec("AFTER_COMPLETION", 2, "WEEKS")
        repository.updateTask(repository.getTask(id)!!.task.copy(visibility = custom))
        repository.setVisibility(VisibilitySpec("BEFORE_DUE", 3))
        val file = java.io.File(app.cacheDir, "visibility.json")
        assertTrue(manager.export(android.net.Uri.fromFile(file)) is BackupResult.Success)
        repository.setVisibility(VisibilitySpec())
        assertTrue(manager.import(android.net.Uri.fromFile(file)) is BackupResult.Success)
        assertEquals(VisibilitySpec("BEFORE_DUE", 3), repository.observeVisibility().first())
        assertEquals(custom, repository.getTask(id)!!.task.visibility)
        file.writeText("""{"exportedAt":"test","categories":[],"tasks":[],"completions":[]}""")
        assertTrue(manager.import(android.net.Uri.fromFile(file)) is BackupResult.Success)
        assertEquals(VisibilitySpec(), repository.observeVisibility().first())
    }

    @Test fun specificUndoIgnoresOtherTasks_butRejectsNewerSameTaskChangesAndRepeatTaps() = runBlocking {
        val a = task(); val b = task()
        val first = repository.completeTask(a, 0, at)!!
        val second = repository.completeTask(b, 0, at.plusSeconds(1))!!
        assertNotNull(repository.undoSpecific(first.id, 1))
        assertNull(repository.undoSpecific(first.id, 1))
        assertNotNull(repository.undoSpecific(second.id, 1))
        val next = repository.completeTask(a, 2, at.plusSeconds(2))!!
        repository.completeTask(a, 3, at.plusSeconds(3))
        assertNull(repository.undoSpecific(next.id, 3))
    }

    @Test fun scrollSnapshotKeepsHiddenCompletionAndUndoRevisionAtomicWithRows() = runBlocking {
        val id = task()
        repository.setVisibility(VisibilitySpec("AFTER_COMPLETION", 7))
        val completion = repository.completeTask(id, 0, at)!!
        val hidden = repository.observeHomeData(flowOf(date)).first()
        assertTrue(hidden.groups.isEmpty())
        assertEquals(1L, hidden.revisions[id])
        repository.undoSpecific(completion.id, 1)
        val restored = repository.observeHomeData(flowOf(date)).first()
        assertEquals(2L, restored.revisions[id])
        assertEquals(id, restored.groups.single().tasks.single().task.id)
    }

    @Test fun consecutiveEarlyCompletionsAdvanceAndUndoRestoresExactScheduledOccurrence() = runBlocking {
        val id = repository.insertTask(TaskEntity(name = "Monday", createdDate = date,
            recurrence = RecurrenceSpec(RecurrenceKind.FIXED_WEEKDAY, dayOfWeek = java.time.DayOfWeek.MONDAY)))
        val first = repository.completeTask(id, 0, at)!!
        assertEquals(LocalDate.parse("2026-11-16"), first.scheduledDue)
        assertEquals(LocalDate.parse("2026-11-23"), repository.getTask(id)!!.nextDue)
        val second = repository.completeTask(id, 1, at.minusSeconds(10))!! // clock moved backwards
        assertEquals(LocalDate.parse("2026-11-23"), second.scheduledDue)
        assertEquals(LocalDate.parse("2026-11-30"), repository.getTask(id)!!.nextDue)
        assertNull(repository.undoSpecific(first.id, 1))
        assertNotNull(repository.undoSpecific(second.id, 2))
        assertEquals(LocalDate.parse("2026-11-23"), repository.getTask(id)!!.nextDue)
    }

    @Test fun changedRecurrenceDoesNotReuseOldConsumedFutureDate() = runBlocking {
        val id = repository.insertTask(TaskEntity(name = "Grid", createdDate = date,
            recurrence = RecurrenceSpec(RecurrenceKind.FIXED_INTERVAL, interval = 2,
                unit = com.chorereminder.domain.IntervalUnit.MONTHS, anchor = date)))
        repeat(3) { repository.completeTask(id, it.toLong(), at) }
        assertTrue(repository.getTask(id)!!.nextDue > date.plusMonths(4))
        val task = repository.getTask(id)!!.task
        repository.updateTask(task.copy(recurrence = RecurrenceSpec(RecurrenceKind.FIXED_WEEKDAY, dayOfWeek = java.time.DayOfWeek.MONDAY)))
        assertEquals(LocalDate.parse("2026-11-16"), repository.getTask(id)!!.nextDue)
    }

    @Test fun earlyCompletionVisibilityUsesActualDateWhileSchedulingUsesConsumedDate() = runBlocking {
        val id = repository.insertTask(TaskEntity(name = "Monday", createdDate = date,
            recurrence = RecurrenceSpec(RecurrenceKind.FIXED_WEEKDAY, dayOfWeek = java.time.DayOfWeek.MONDAY)))
        repository.completeTask(id, 0, at)
        repository.setVisibility(VisibilitySpec("AFTER_COMPLETION", 2))
        assertTrue(repository.observeGroupedTasks(flowOf(date.plusDays(1))).first().isEmpty())
        assertEquals(id, repository.observeGroupedTasks(flowOf(date.plusDays(2))).first().single().tasks.single().task.id)
        repository.setVisibility(VisibilitySpec("BEFORE_DUE", 1))
        assertTrue(repository.observeGroupedTasks(flowOf(LocalDate.parse("2026-11-21"))).first().isEmpty())
        assertEquals(LocalDate.parse("2026-11-23"), repository.getAllTasks().single().nextDue)
        assertEquals(id, repository.observeGroupedTasks(flowOf(LocalDate.parse("2026-11-22"))).first().single().tasks.single().task.id)
    }

    @Test fun versionFourBackupPreservesEarlyOccurrenceAndVersionThreeHistoryRemainsReadable() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val id = repository.insertTask(TaskEntity(name = "Monday", createdDate = date,
            recurrence = RecurrenceSpec(RecurrenceKind.FIXED_WEEKDAY, dayOfWeek = java.time.DayOfWeek.MONDAY)))
        repository.completeTask(id, 0, at)
        val file = java.io.File(app.cacheDir, "occurrence-round-trip.json")
        val uri = android.net.Uri.fromFile(file)
        val manager = BackupManager(app, repository)
        assertTrue(manager.export(uri) is BackupResult.Success)
        assertTrue(manager.import(uri) is BackupResult.Success)
        assertEquals(LocalDate.parse("2026-11-23"), repository.getTask(id)!!.nextDue)
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val backup = json.decodeFromString<BackupFile>(file.readText())
        file.writeText(json.encodeToString(backup.copy(version = 3,
            completions = backup.completions.map { it.copy(scheduledDue = null, scheduleKey = null) })))
        assertTrue(manager.import(uri) is BackupResult.Success)
        assertEquals(LocalDate.parse("2026-11-16"), repository.getTask(id)!!.nextDue) // legacy fallback, no fabricated date
    }

    @Test fun snoozeIsHeldAsLongAsTheRevisionMatches_andLapsesOnAnyLaterMutation() = runBlocking {
        val id = task()
        val until = at.plusSeconds(3600)
        val snoozed = repository.snoozeTask(id, 0, until)!!
        assertEquals(until, snoozed.snoozedUntil)
        assertEquals(1L, snoozed.snoozeRevision)
        assertEquals(1L, snoozed.completionRevision)
        // Completing bumps the revision; the stale snooze no longer matches it.
        repository.completeTask(id, 1, at)
        val after = repository.getTask(id)!!.task
        assertNotEquals(after.completionRevision, after.snoozeRevision)
    }

    @Test fun snoozeRejectsAStaleRevision() = runBlocking {
        val id = task()
        repository.completeTask(id, 0, at)
        assertNull(repository.snoozeTask(id, 0, at.plusSeconds(3600)))
        assertNotNull(repository.snoozeTask(id, 1, at.plusSeconds(3600)))
    }

    @Test fun restoreSnoozeUndoesADelay() = runBlocking {
        val id = task()
        repository.snoozeTask(id, 0, at.plusSeconds(3600))
        val restored = repository.restoreSnooze(id, 1, previousSnoozedUntil = null, previousSnoozeRevision = null)!!
        assertNull(restored.snoozedUntil)
        assertNull(restored.snoozeRevision)
        assertEquals(2L, restored.completionRevision)
    }

    @Test fun skippingAdvancesTheNextDueDate_justLikeCompleting() = runBlocking {
        val id = task()
        val original = repository.getTask(id)!!.nextDue
        repository.completeTask(id, 0, at, outcome = CompletionOutcome.SKIPPED)
        assertEquals(original.plusDays(7), repository.getTask(id)!!.nextDue)
    }

    @Test fun skippedOccurrencesDoNotCountTowardCompletionCount() = runBlocking {
        val id = task()
        repository.completeTask(id, 0, at, outcome = CompletionOutcome.DONE)
        repository.completeTask(id, 1, at.plusSeconds(1), outcome = CompletionOutcome.SKIPPED)
        assertEquals(1, repository.getTask(id)!!.completionCount)
    }

    @Test fun skippingAOneOffHidesItJustLikeCompletingDoes() = runBlocking {
        val id = task(RecurrenceKind.ONE_OFF)
        repository.completeTask(id, 0, at, outcome = CompletionOutcome.SKIPPED)
        assertNull(repository.getTask(id))
    }

    @Test fun undoRestoresASkippedOccurrence() = runBlocking {
        val id = task()
        val skip = repository.completeTask(id, 0, at, outcome = CompletionOutcome.SKIPPED)!!
        assertNotNull(repository.undo(skip.id))
        assertEquals(0, repository.getTask(id)!!.completionCount)
    }

    @Test fun localCompletionDateSurvivesTimezoneChange() = runBlocking {
        val oldZone = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Auckland"))
            val id = repository.insertTask(TaskEntity(name = "Monday", createdDate = date,
                recurrence = RecurrenceSpec(RecurrenceKind.FIXED_WEEKDAY, dayOfWeek = java.time.DayOfWeek.MONDAY)))
            repository.completeTask(id, 0, Instant.parse("2026-11-15T12:30:00Z")) // Monday locally
            val before = repository.getTask(id)!!
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(before.nextDue, repository.getTask(id)!!.nextDue)
            assertEquals(LocalDate.parse("2026-11-16"), repository.getTask(id)!!.lastCompletedOn)
        } finally { java.util.TimeZone.setDefault(oldZone) }
    }
}
