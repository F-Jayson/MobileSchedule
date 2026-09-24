package com.example.mobileschedule.di

import com.example.mobileschedule.data.repository.CourseRepository
import com.example.mobileschedule.data.repository.OfflineCourseRepository
import com.example.mobileschedule.data.repository.ScheduleRepository
import com.example.mobileschedule.data.repository.OfflineScheduleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindCourseRepository(repository: OfflineCourseRepository): CourseRepository

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(repository: OfflineScheduleRepository): ScheduleRepository
}
