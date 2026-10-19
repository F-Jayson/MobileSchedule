package com.example.mobileschedule.data.calendar

import com.example.mobileschedule.data.model.CourseArrangement
import com.example.mobileschedule.data.model.Semester
import com.example.mobileschedule.data.rules.ScheduleRules
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Single schedule event item mapped to a specific calendar date and time range.
 */
data class CalendarEventItem(
    val uid: String,
    val summary: String,
    val location: String?,
    val description: String?,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
) {
    fun toStartEpochMillis(zoneId: ZoneId = ZoneId.of("Asia/Shanghai")): Long =
        startDateTime.atZone(zoneId).toInstant().toEpochMilli()

    fun toEndEpochMillis(zoneId: ZoneId = ZoneId.of("Asia/Shanghai")): Long =
        endDateTime.atZone(zoneId).toInstant().toEpochMilli()
}

/**
 * Utility for building calendar events and serializing them into RFC 5545 iCalendar (.ics) format.
 */
object IcsCalendarExporter {
    val SHANGHAI_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    private val ICS_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    /**
     * Builds individual calendar event items for every active week and session of the given courses.
     */
    fun buildCalendarEvents(
        semester: Semester,
        arrangements: List<CourseArrangement>,
        schoolName: String = "福建师范大学",
    ): List<CalendarEventItem> {
        val config = semester.config ?: return emptyList()
        val semesterCourses = arrangements.filter { it.semesterId == semester.id }
        val events = mutableListOf<CalendarEventItem>()

        for (course in semesterCourses) {
            val sortedWeeks = course.weeks.sorted()
            val weekIntervalDesc = ScheduleRules.formatWeekInterval(course.weeks)

            for (week in sortedWeeks) {
                if (week < 1 || week > config.totalWeeks) continue

                val weekMonday = config.firstWeekMonday.plusWeeks((week - 1).toLong())
                val dayOffset = (course.dayOfWeek - 1).coerceIn(0, 6)
                val eventDate = weekMonday.plusDays(dayOffset.toLong())

                val startTime = ScheduleRules.sectionStartTime(config, course.startSection)
                    ?: LocalTime.of(8, 0).plusMinutes(((course.startSection - 1) * 50).toLong())
                val endTime = ScheduleRules.sectionEndTime(config, course.endSection)
                    ?: startTime.plusMinutes(45)

                val startDateTime = LocalDateTime.of(eventDate, startTime)
                val endDateTime = LocalDateTime.of(eventDate, endTime)

                val uid = "course-${course.id}-w${week}-d${course.dayOfWeek}-s${course.startSection}@mobileschedule.fjnu"

                val description = buildString {
                    append("任课教师：").append(course.teacher?.ifBlank { "暂无" } ?: "暂无").append("\n")
                    append("节次：第").append(course.startSection).append("~").append(course.endSection).append("节\n")
                    append("周次：第").append(week).append("周 (").append(weekIntervalDesc).append(")\n")
                    append("学期：").append(semester.displayName).append("\n")
                    append("来源：").append(schoolName).append("课表")
                }

                events.add(
                    CalendarEventItem(
                        uid = uid,
                        summary = course.name,
                        location = course.location?.ifBlank { null },
                        description = description,
                        startDateTime = startDateTime,
                        endDateTime = endDateTime,
                    )
                )
            }
        }

        return events.sortedWith(
            compareBy(
                { it.startDateTime },
                { it.endDateTime },
                { it.summary },
                { it.uid },
            )
        )
    }

    /**
     * Serializes calendar events into RFC 5545 compliant iCalendar string.
     */
    fun generateIcs(
        semester: Semester,
        arrangements: List<CourseArrangement>,
        schoolName: String = "福建师范大学",
        calendarName: String? = null,
        nowUtcTime: LocalDateTime = LocalDateTime.now(ZoneId.of("UTC")),
    ): String {
        val calName = calendarName ?: "${semester.displayName}课表"
        val events = buildCalendarEvents(semester, arrangements, schoolName)
        val dtStamp = nowUtcTime.format(ICS_DATE_TIME_FORMATTER) + "Z"

        val sb = StringBuilder()
        fun appendLine(line: String) {
            sb.append(line).append("\r\n")
        }

        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//MobileSchedule//FJNU Schedule Calendar//CN")
        appendLine("CALSCALE:GREGORIAN")
        appendLine("METHOD:PUBLISH")
        appendLine("X-WR-CALNAME:${escapeIcsText(calName)}")
        appendLine("X-WR-TIMEZONE:Asia/Shanghai")

        // Asia/Shanghai VTIMEZONE
        appendLine("BEGIN:VTIMEZONE")
        appendLine("TZID:Asia/Shanghai")
        appendLine("TZURL:http://tzurl.org/zoneinfo-outlook/Asia/Shanghai")
        appendLine("X-LIC-LOCATION:Asia/Shanghai")
        appendLine("BEGIN:STANDARD")
        appendLine("TZOFFSETFROM:+0800")
        appendLine("TZOFFSETTO:+0800")
        appendLine("TZNAME:CST")
        appendLine("DTSTART:19700101T000000")
        appendLine("END:STANDARD")
        appendLine("END:VTIMEZONE")

        for (event in events) {
            appendLine("BEGIN:VEVENT")
            appendLine("UID:${event.uid}")
            appendLine("DTSTAMP:$dtStamp")
            appendLine("DTSTART;TZID=Asia/Shanghai:${event.startDateTime.format(ICS_DATE_TIME_FORMATTER)}")
            appendLine("DTEND;TZID=Asia/Shanghai:${event.endDateTime.format(ICS_DATE_TIME_FORMATTER)}")
            appendLine("SUMMARY:${escapeIcsText(event.summary)}")
            if (!event.location.isNullOrBlank()) {
                appendLine("LOCATION:${escapeIcsText(event.location)}")
            }
            if (!event.description.isNullOrBlank()) {
                appendLine("DESCRIPTION:${escapeIcsText(event.description)}")
            }
            appendLine("STATUS:CONFIRMED")
            appendLine("TRANSP:OPAQUE")
            appendLine("END:VEVENT")
        }

        appendLine("END:VCALENDAR")
        return sb.toString()
    }

    /**
     * Escapes text according to RFC 5545 Section 3.3.11 TEXT formatting.
     */
    fun escapeIcsText(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")
    }
}
