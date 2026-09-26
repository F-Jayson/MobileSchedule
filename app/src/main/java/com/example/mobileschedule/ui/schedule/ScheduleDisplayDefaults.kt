package com.example.mobileschedule.ui.schedule

import com.example.mobileschedule.data.model.SectionTime
import com.example.mobileschedule.data.model.SemesterConfig
import java.time.LocalTime

/** User-provided timetable display defaults; configured semester times always take precedence. */
internal object ScheduleDisplayDefaults {
    private val firstEight = listOf(
        "08:20" to "09:05", "09:15" to "10:00",
        "10:20" to "11:05", "11:15" to "12:00",
        "14:00" to "14:45", "14:55" to "15:40",
        "15:50" to "16:35", "16:45" to "17:30",
    ).mapIndexed { index, (start, end) ->
        SectionTime(index + 1, LocalTime.parse(start), LocalTime.parse(end))
    }

    fun forSection(config: SemesterConfig, section: Int): SectionTime? =
        config.sectionTimes.firstOrNull { it.section == section }
            ?: if (config.sectionTimes.isEmpty()) firstEight.getOrNull(section - 1) else null

    fun forUnconfiguredSection(section: Int): SectionTime? = firstEight.getOrNull(section - 1)
}
