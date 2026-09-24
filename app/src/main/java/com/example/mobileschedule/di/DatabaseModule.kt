package com.example.mobileschedule.di

import android.content.Context
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
        AppDatabase.open(context)

    @Provides
    fun provideCourseDao(database: AppDatabase) = database.courseDao()

    @Provides
    fun provideSemesterDao(database: AppDatabase) = database.semesterDao()

    @Provides
    fun provideImportBatchDao(database: AppDatabase) = database.importBatchDao()
}
