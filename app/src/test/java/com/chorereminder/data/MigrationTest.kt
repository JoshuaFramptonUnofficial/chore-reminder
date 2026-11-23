package com.chorereminder.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MigrationTest {
    @Test fun visibilityAndConsumedOccurrence_surviveDatabaseCloseAndReopen() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val name = "visibility-restart.db"
        app.deleteDatabase(name)
        fun open() = Room.databaseBuilder(app, ChoreDatabase::class.java, name)
            .allowMainThreadQueries().build()
        var db = open()
        val global = VisibilitySpec("AFTER_COMPLETION", 2, "WEEKS")
        val custom = VisibilitySpec("BEFORE_DUE", 1, "MONTHS")
        try {
            val repo = ChoreRepository(db.choreDao())
            db.choreDao().insertCategory(CategoryEntity(1, "Uncategorized"))
            repo.setVisibility(global)
            val id = repo.insertTask(TaskEntity(name = "Restart test", createdDate = java.time.LocalDate.parse("2026-11-13"),
                recurrence = RecurrenceSpec(RecurrenceKind.FIXED_WEEKDAY, dayOfWeek = java.time.DayOfWeek.MONDAY), visibility = custom))
            repo.completeTask(id, 0, java.time.Instant.parse("2026-11-13T12:00:00Z"))
            db.close()
            db = open()
            assertEquals(global, db.choreDao().getVisibility()!!.rule)
            assertEquals(custom, ChoreRepository(db.choreDao()).getTask(id)!!.task.visibility)
            assertEquals(java.time.LocalDate.parse("2026-11-23"), ChoreRepository(db.choreDao()).getTask(id)!!.nextDue)
        } finally { db.close(); app.deleteDatabase(name) }
    }

    @Test fun oldDatabases_migrateWithoutLosingChoresOrHistory() = runBlocking {
        for (version in 1..3) {
            val app = RuntimeEnvironment.getApplication()
            val name = "migration-test-$version.db"
            app.deleteDatabase(name)
            val path = app.getDatabasePath(name)
            path.parentFile!!.mkdirs()
            val schema = javaClass.classLoader!!.getResourceAsStream(
                "com.chorereminder.data.ChoreDatabase/$version.json")!!.bufferedReader().use { it.readText() }
            val database = Json.parseToJsonElement(schema).jsonObject.getValue("database").jsonObject
            SQLiteDatabase.openOrCreateDatabase(path, null).use { sqlite ->
                database.getValue("entities").jsonArray.forEach { entity ->
                    val definition = entity.jsonObject
                    sqlite.execSQL(definition.getValue("createSql").jsonPrimitive.content.replace(
                        "\${TABLE_NAME}", definition.getValue("tableName").jsonPrimitive.content))
                    definition["indices"]?.jsonArray.orEmpty().forEach { index ->
                        sqlite.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace(
                            "\${TABLE_NAME}", definition.getValue("tableName").jsonPrimitive.content))
                    }
                }
                database.getValue("setupQueries").jsonArray.forEach { sqlite.execSQL(it.jsonPrimitive.content) }
                sqlite.execSQL("INSERT INTO categories (id, name, sortOrder) VALUES (1, 'Uncategorized', 0)")
                sqlite.execSQL("""INSERT INTO tasks (id, name, iconType, iconValue, categoryId, createdDate,
                    rec_kind, rec_interval, rec_unit) VALUES (1, 'Existing chore', 'MATERIAL', 'CheckCircle',
                    1, 20000, 'RELATIVE', 7, 'DAYS')""")
                sqlite.execSQL("INSERT INTO completions (id, taskId, completedAt, completedOn) VALUES (1, 1, 1728000000000, 20010)")
                sqlite.version = version
            }
            val migrated = Room.databaseBuilder(app, ChoreDatabase::class.java, name)
                .addMigrations(
                    ChoreDatabase.MIGRATION_1_2, ChoreDatabase.MIGRATION_2_3, ChoreDatabase.MIGRATION_3_4,
                    ChoreDatabase.MIGRATION_4_5, ChoreDatabase.MIGRATION_5_6,
                ).allowMainThreadQueries().build()
            try {
                val task = migrated.choreDao().getTaskWithDetails(1)!!.task
                assertEquals("Existing chore", task.name)
                assertEquals(0L, task.completionRevision)
                assertNull(task.visibility)
                assertNull(task.snoozedUntil)
                assertNull(task.snoozeRevision)
                val legacy = migrated.choreDao().getTaskWithDetails(1)!!.lastCompletion!!
                assertEquals(java.time.LocalDate.ofEpochDay(20010), legacy.completedOn)
                assertNull(legacy.scheduledDue)
                assertNull(legacy.scheduleKey)
                assertEquals(CompletionOutcome.DONE, legacy.outcome)
                assertNotNull(migrated.choreDao().complete(1, 0, java.time.Instant.now()))
            } finally { migrated.close(); app.deleteDatabase(name) }
        }
    }
}
