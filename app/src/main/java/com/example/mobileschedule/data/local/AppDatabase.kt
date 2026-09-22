package com.example.mobileschedule.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.mobileschedule.data.local.dao.CourseDao
import com.example.mobileschedule.data.local.entity.CourseEntity
import com.example.mobileschedule.data.local.entity.CourseWeekEntity

@Database(entities = [CourseEntity::class, CourseWeekEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
}
