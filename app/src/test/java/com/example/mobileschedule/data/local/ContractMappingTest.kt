package com.example.mobileschedule.data.local

import com.example.mobileschedule.data.local.entity.*
import com.example.mobileschedule.data.model.CourseOrigin
import com.example.mobileschedule.data.model.SourceScope
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ContractMappingTest {
    @Test
    fun importedArrangementKeepsIdentityScopeSparseWeeksAndMissingFields() {
        val batch = ImportBatchEntity("batch", 4, "school", "channel", "term", 1_000, 1, 2)
        val row = CourseWithWeeks(
            CourseEntity(8, "课程", null, null, 2, 3, 4, 4, CourseOriginType.SCHOOL_IMPORT, "batch", "row-7"),
            listOf(CourseWeekEntity(8, 9), CourseWeekEntity(8, 1), CourseWeekEntity(8, 3)),
            batch,
        )

        val model = row.toArrangement()
        assertEquals(8L, model.id)
        assertEquals(4L, model.semesterId)
        assertEquals(listOf(1, 3, 9), model.weeks.toList())
        assertNull(model.teacher)
        assertNull(model.location)
        assertEquals(CourseOrigin.SchoolImport(SourceScope("school", "channel", "term"), "batch", "row-7"), model.origin)
        assertEquals(Instant.ofEpochMilli(1_000), batch.toModel().committedAt)
        assertEquals("", row.toModel().teacher)
    }

    @Test
    fun legacyAndManualRemainDistinctWithoutInventingSource() {
        val legacy = CourseWithWeeks(CourseEntity(8, "旧课", "", "", 2, 1, 2, 1, CourseOriginType.LEGACY), emptyList())
        assertEquals(CourseOrigin.Legacy, legacy.toArrangement().origin)
        assertNull(legacy.toArrangement().teacher)
        assertEquals(CourseOrigin.Manual, legacy.copy(course = legacy.course.copy(originType = CourseOriginType.MANUAL)).toArrangement().origin)
    }

    @Test(expected = IllegalArgumentException::class)
    fun importedArrangementCannotSilentlyBecomeLegacyWhenBatchIsMissing() {
        CourseWithWeeks(
            CourseEntity(8, "课", null, null, 2, 1, 2, 1, CourseOriginType.SCHOOL_IMPORT, "missing"), emptyList(),
        ).toArrangement()
    }

    @Test
    fun missingConfigurationStaysMissingAndConfiguredDatesUseLocalUnits() {
        val row = SemesterWithDetails(SemesterEntity(4, "测试学期"), null, emptyList(), emptyList())
        assertNull(row.toModel().config)
        val monday = LocalDate.of(2026, 9, 7)
        val configured = row.copy(
            config = SemesterConfigEntity(4, monday.toEpochDay(), 18, 2, 3),
            sectionTimes = listOf(SectionTimeEntity(4, 2, 550, 595), SectionTimeEntity(4, 1, 490, 535)),
            sourceBindings = listOf(SourceBindingEntity("school", "channel", "term", 4, "显示标签")),
        ).toModel()
        assertEquals(setOf(SourceScope("school", "channel", "term")), configured.sourceBindings)
        val config = requireNotNull(configured.config)
        assertEquals(monday, config.firstWeekMonday)
        assertEquals(3L, config.revision)
        assertEquals(listOf(1, 2), config.sectionTimes.map { it.section })
        assertEquals(LocalTime.of(8, 10), config.sectionTimes.first().start)
        assertEquals(LocalTime.of(9, 55), config.sectionTimes.last().end)
    }
}
