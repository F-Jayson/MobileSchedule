package com.example.mobileschedule.data.importer

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentAcademicTermTest {
    @Test fun resolvesAcademicYearAcrossCalendarAndSemesterBoundaries() {
        listOf(
            Triple("2026-01-31", 2025, 1),
            Triple("2026-02-01", 2025, 2),
            Triple("2026-08-31", 2025, 2),
            Triple("2026-09-01", 2026, 1),
            Triple("2026-10-13", 2026, 1),
        ).forEach { (date, year, term) ->
            val selected = ZhengfangOnlineRead.currentTerm(LocalDate.parse(date))
            assertEquals(year, selected.yearStart)
            assertEquals(term, selected.term)
        }
    }
}
