package com.chorereminder.notify

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.chorereminder.data.RecurrenceKind
import com.chorereminder.data.RecurrenceSpec
import com.chorereminder.data.TaskEntity
import com.chorereminder.data.TaskView
import com.chorereminder.domain.DueState
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercise the real WorkManager graph, including a running (not just pending) worker. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReminderSchedulerTest {
    private lateinit var context: Context
    private lateinit var manager: WorkManager
    private lateinit var started: CountDownLatch
    private val now = ZonedDateTime.parse("2026-11-13T08:00:00Z[Europe/London]")
    private val view = TaskView(
        task = TaskEntity(
            id = 42,
            name = "Test chore",
            createdDate = LocalDate.parse("2026-11-01"),
            recurrence = RecurrenceSpec(RecurrenceKind.RELATIVE),
        ),
        categoryName = "Uncategorized",
        nextDue = now.toLocalDate(),
        dueState = DueState.DUE_TODAY,
        lastCompletedOn = null,
        completionCount = 0,
    )

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        started = CountDownLatch(1)
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = object : ListenableWorker(appContext, workerParameters) {
                private val pending = com.google.common.util.concurrent.SettableFuture.create<Result>()
                override fun startWork(): com.google.common.util.concurrent.ListenableFuture<Result> {
                    started.countDown()
                    return pending
                }
                override fun onStopped() { pending.cancel(true) }
            }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build(),
        )
        manager = WorkManager.getInstance(context)
    }

    @After
    fun tearDown() {
        manager.cancelAllWork().result.get(5, TimeUnit.SECONDS)
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    private fun work(): List<WorkInfo> =
        manager.getWorkInfosForUniqueWork("reminder-task-42").get(5, TimeUnit.SECONDS)

    private suspend fun startReminder(): WorkInfo {
        ReminderScheduler.schedule(context, view, now)
        val first = work().single()
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(first.id)
        assertTrue("worker did not start", started.await(5, TimeUnit.SECONDS))
        assertEquals(WorkInfo.State.RUNNING, work().single().state)
        return first
    }

    @Test
    fun coldStartRecovery_keepsTheWorkerThatStartedTheProcess() = runBlocking {
        val first = startReminder()
        ReminderScheduler.schedule(context, view, now, policy = ExistingWorkPolicy.KEEP)
        val recovered = work().single()
        assertEquals(first.id, recovered.id)
        assertEquals(WorkInfo.State.RUNNING, recovered.state)
    }

    @Test
    fun dailySuccessor_doesNotCancelItsRunningParent() = runBlocking {
        val first = startReminder()
        ReminderScheduler.scheduleNext(context, view, now.plusHours(1))
        val chain = work()
        assertEquals(2, chain.size)
        assertEquals(WorkInfo.State.RUNNING, chain.single { it.id == first.id }.state)
        assertEquals(WorkInfo.State.BLOCKED, chain.single { it.id != first.id }.state)
    }

    @Test
    fun completionReschedule_replacesOldChainWithTheNextOccurrence() = runBlocking {
        val first = startReminder()
        ReminderScheduler.scheduleNext(context, view, now.plusHours(1))
        ReminderScheduler.schedule(context, view.copy(nextDue = view.nextDue.plusDays(14)), now)
        val next = work().single()
        assertNotEquals(first.id, next.id)
        assertEquals(WorkInfo.State.ENQUEUED, next.state)
        val replaced = manager.getWorkInfoById(first.id).get()
        assertTrue(replaced == null || replaced.state == WorkInfo.State.CANCELLED)
    }

    @Test
    fun deletion_cancelsBothRunningReminderAndDailySuccessor() = runBlocking {
        startReminder()
        ReminderScheduler.scheduleNext(context, view, now.plusHours(1))
        ReminderScheduler.cancel(context, view.task.id)
        assertTrue(work().all { it.state == WorkInfo.State.CANCELLED })
    }
}
