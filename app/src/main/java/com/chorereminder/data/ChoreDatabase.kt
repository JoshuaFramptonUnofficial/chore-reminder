package com.chorereminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CategoryEntity::class, TaskEntity::class, CompletionEntity::class, VisibilitySettingsEntity::class],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ChoreDatabase : RoomDatabase() {

    abstract fun choreDao(): ChoreDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN completionRevision INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN visibility_mode TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN visibility_amount INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN visibility_unit TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS visibility_settings (id INTEGER NOT NULL PRIMARY KEY, mode TEXT NOT NULL, amount INTEGER NOT NULL, unit TEXT NOT NULL)")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE completions ADD COLUMN scheduledDue INTEGER")
                db.execSQL("ALTER TABLE completions ADD COLUMN scheduleKey TEXT")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN snoozedUntil INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN snoozeRevision INTEGER")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE completions ADD COLUMN outcome TEXT NOT NULL DEFAULT 'DONE'")
            }
        }

        @Volatile
        private var instance: ChoreDatabase? = null

        fun get(context: Context): ChoreDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): ChoreDatabase =
            Room.databaseBuilder(context, ChoreDatabase::class.java, "chores.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // FR-12: a default category must always exist so a task can
                        // be saved without the user naming one.
                        db.execSQL(
                            "INSERT INTO categories (id, name, sortOrder) VALUES (?, ?, ?)",
                            arrayOf<Any>(UNCATEGORIZED_ID, UNCATEGORIZED_NAME, 1000),
                        )
                    }
                })
                // Foreign keys must be on for ON DELETE CASCADE/SET DEFAULT to fire.
                .build()
    }
}
