package com.example.mobileschedule.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.mobileschedule.data.local.dao.CourseDao
import com.example.mobileschedule.data.local.dao.CourseTaskDao
import com.example.mobileschedule.data.local.dao.SemesterDao
import com.example.mobileschedule.data.local.dao.ImportBatchDao
import com.example.mobileschedule.data.local.entity.*

@Database(
    entities = [
        CourseEntity::class, CourseWeekEntity::class, SemesterEntity::class, SemesterConfigEntity::class,
        SectionTimeEntity::class, SourceBindingEntity::class, ImportBatchEntity::class, AppSettingsEntity::class,
        CourseTaskEntity::class
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun courseTaskDao(): CourseTaskDao
    abstract fun semesterDao(): SemesterDao
    abstract fun importBatchDao(): ImportBatchDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun open(context: Context, name: String = "mobile_schedule.db"): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3)
                .build()

        /**
         * Returns a cached database instance for non-DI contexts (boot receiver, reminder scheduler).
         * Thread-safe double-checked locking ensures a single instance across callers.
         */
        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: open(context.applicationContext).also { instance = it }
            }
    }
}
