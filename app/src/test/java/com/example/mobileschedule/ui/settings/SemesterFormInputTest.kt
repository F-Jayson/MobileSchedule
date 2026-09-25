package com.example.mobileschedule.ui.settings

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemesterFormInputTest {
    private val monday = LocalDate.of(2026, 9, 21)

    @Test fun missingFieldsCannotProduceDraft() {
        val result = SemesterFormInput().validate()
        assertNull(result.draft)
        assertEquals(setOf("name", "monday", "weeks", "sections"), result.errors.keys)
    }

    @Test fun selectedNonMondayNeedsExplicitCorrection() {
        val selected = valid().copy(firstWeekDate = monday.plusDays(2))
        assertEquals(monday, selected.suggestedMonday)
        assertNull(selected.validate().draft)
        assertTrue(selected.validate().errors.getValue("monday").contains("周一"))
        val corrected = selected.confirmSuggestedMonday()
        assertEquals(monday, corrected.firstWeekDate)
        assertNotNull(corrected.validate().draft)
    }

    @Test fun validCompleteTimesBecomeOrderedConfigDraft() {
        val input = valid().copy(timesEnabled = true, sectionTimes = mapOf(
            1 to SectionTimeText("08:00", "08:45"),
            2 to SectionTimeText("08:45", "09:30"),
        ))
        val result = input.validate()
        assertTrue(result.errors.isEmpty())
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(8, 45)), result.draft!!.sectionTimes.map { it.start })
        assertEquals("合成学期", result.draft.displayName)
    }

    @Test fun incompleteInvalidAndOverlappingTimesStayNearTheirRows() {
        val incomplete = valid().copy(timesEnabled = true, sectionTimes = mapOf(
            1 to SectionTimeText("08:00", "08:45"),
            2 to SectionTimeText("", "bad"),
        )).validate()
        assertNull(incomplete.draft)
        assertTrue(incomplete.errors.containsKey("time_2"))

        val overlap = valid().copy(timesEnabled = true, sectionTimes = mapOf(
            1 to SectionTimeText("08:00", "08:45"),
            2 to SectionTimeText("08:30", "09:10"),
        )).validate()
        assertNull(overlap.draft)
        assertTrue(overlap.errors.getValue("time_2").contains("重叠"))

        val reversed = valid().copy(timesEnabled = true, sectionTimes = mapOf(
            1 to SectionTimeText("08:45", "08:00"),
            2 to SectionTimeText("09:00", "09:45"),
        )).validate()
        assertTrue(reversed.errors.getValue("time_1").contains("早于"))
    }

    @Test fun invalidNumbersUseFieldErrorsAndEmptyTimeTableIsAllowed() {
        val invalid = valid().copy(totalWeeks = "0", totalSections = "abc").validate()
        assertNull(invalid.draft)
        assertTrue(invalid.errors.containsKey("weeks"))
        assertTrue(invalid.errors.containsKey("sections"))
        val withoutTimes = valid().validate().draft!!
        assertFalse(withoutTimes.sectionTimes.isNotEmpty())
    }

    private fun valid() = SemesterFormInput("合成学期", monday, "12", "2")
}
