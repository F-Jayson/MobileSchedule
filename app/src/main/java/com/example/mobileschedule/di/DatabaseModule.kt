package com.example.mobileschedule.di

import android.content.Context
import androidx.room.Room
import com.example.mobileschedule.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "mobile_schedule.db").build()

    @Provides
    fun provideCourseDao(database: AppDatabase) = database.courseDao()
}
