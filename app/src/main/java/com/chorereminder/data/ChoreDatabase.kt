package com.chorereminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CategoryEntity::class, TaskEntity::class, CompletionEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ChoreDatabase : RoomDatabase() {

    abstract fun choreDao(): ChoreDao

    companion object {
        @Volatile
        private var instance: ChoreDatabase? = null

        fun get(context: Context): ChoreDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): ChoreDatabase =
            Room.databaseBuilder(context, ChoreDatabase::class.java, "chores.db")
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
