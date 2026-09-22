package com.example.mobileschedule.di

import com.example.mobileschedule.data.repository.CourseRepository
import com.example.mobileschedule.data.repository.OfflineCourseRepository
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
}
