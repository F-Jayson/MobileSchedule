package com.example.mobileschedule.data.repository

import androidx.room.withTransaction
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.toArrangement
import com.example.mobileschedule.data.local.entity.toModel
import com.example.mobileschedule.data.model.CourseOrigin
import javax.inject.Inject
import kotlinx.serialization.json.*

class ScheduleManagementService @Inject constructor(private val database: AppDatabase) {
    suspend fun clearSemester(semesterId: Long): Int = database.withTransaction {
        requireNotNull(database.semesterDao().getSemester(semesterId)) { "Semester no longer exists" }
        val removed = database.courseDao().deleteSemesterCourses(semesterId)
        database.importBatchDao().deleteSemesterBatches(semesterId)
        database.semesterDao().deleteSourceBindings(semesterId)
        // Every pending preview of this semester must be prepared again after a clear operation.
        database.semesterDao().advanceConfigRevision(semesterId)
        removed
    }

    suspend fun exportSemester(semesterId: Long): String = database.withTransaction {
        val semester = requireNotNull(database.semesterDao().getSemester(semesterId)).toModel()
        val courses = database.courseDao().getSemesterCourses(semesterId).map { it.toArrangement() }
        val snapshot = buildJsonObject {
            put("formatVersion", 1)
            putJsonObject("semester") {
                put("displayName", semester.displayName)
                semester.config?.let { config ->
                    put("firstWeekMonday", config.firstWeekMonday.toString())
                    put("totalWeeks", config.totalWeeks)
                    put("totalSections", config.totalSections)
                    putJsonArray("sectionTimes") {
                        config.sectionTimes.forEach { time -> addJsonObject {
                            put("section", time.section)
                            put("start", time.start.toString())
                            put("end", time.end.toString())
                        } }
                    }
                }
            }
            putJsonArray("arrangements") {
                courses.forEach { course -> addJsonObject {
                    put("name", course.name)
                    put("teacher", course.teacher?.let(::JsonPrimitive) ?: JsonNull)
                    put("location", course.location?.let(::JsonPrimitive) ?: JsonNull)
                    put("dayOfWeek", course.dayOfWeek)
                    put("startSection", course.startSection)
                    put("endSection", course.endSection)
                    putJsonArray("weeks") { course.weeks.sorted().forEach { add(JsonPrimitive(it)) } }
                    putJsonObject("origin") {
                        when (val origin = course.origin) {
                            is CourseOrigin.SchoolImport -> {
                                put("type", "SCHOOL_IMPORT")
                                put("schoolId", origin.scope.schoolId)
                                put("sourceId", origin.scope.sourceId)
                                put("sourceTermId", origin.scope.sourceTermId)
                                put("sourceEntryId", origin.sourceEntryId?.let(::JsonPrimitive) ?: JsonNull)
                            }
                            CourseOrigin.Manual -> put("type", "MANUAL")
                            CourseOrigin.Legacy -> put("type", "LEGACY")
                        }
                    }
                } }
            }
        }
        Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), snapshot)
    }
}
