package com.example.mobileschedule.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.mobileschedule.data.local.dao.CourseDao
import com.example.mobileschedule.data.local.dao.SemesterDao
import com.example.mobileschedule.data.local.dao.ImportBatchDao
import com.example.mobileschedule.data.local.entity.*

@Database(
    entities = [CourseEntity::class, CourseWeekEntity::class, SemesterEntity::class, SemesterConfigEntity::class,
        SectionTimeEntity::class, SourceBindingEntity::class, ImportBatchEntity::class, AppSettingsEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun semesterDao(): SemesterDao
    abstract fun importBatchDao(): ImportBatchDao

    companion object {
        fun open(context: Context, name: String = "mobile_schedule.db"): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(DatabaseMigrations.MIGRATION_1_2)
                .build()
    }
}
