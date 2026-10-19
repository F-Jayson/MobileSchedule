package com.example.mobileschedule.data.calendar

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CalendarSyncTest {

    @Test
    fun chunkList_correctlyChunksLargeOperationBatches() {
        val empty = emptyList<Int>()
        assertTrue(SystemCalendarSyncManager.chunkList(empty, 200).isEmpty())

        val smallList = (1..10).toList()
        val smallChunks = SystemCalendarSyncManager.chunkList(smallList, 4)
        assertEquals(3, smallChunks.size)
        assertEquals(listOf(1, 2, 3, 4), smallChunks[0])
        assertEquals(listOf(5, 6, 7, 8), smallChunks[1])
        assertEquals(listOf(9, 10), smallChunks[2])

        // Large list of 450 items with default batch size 200
        val largeList = (1..450).toList()
        val largeChunks = SystemCalendarSyncManager.chunkList(largeList, 200)
        assertEquals(3, largeChunks.size)
        assertEquals(200, largeChunks[0].size)
        assertEquals(200, largeChunks[1].size)
        assertEquals(50, largeChunks[2].size)
    }

    @Test
    fun calendarEventItem_calculatesAccurateEpochMillisInShanghaiTimezone() {
        val start = LocalDateTime.of(2026, 9, 9, 8, 20)
        val end = LocalDateTime.of(2026, 9, 9, 10, 0)
        val item = CalendarEventItem(
            uid = "test-uid-1",
            summary = "高等数学",
            location = "致远楼201",
            description = "任课教师：王老师",
            startDateTime = start,
            endDateTime = end,
        )

        val shanghaiZone = ZoneId.of("Asia/Shanghai")
        val expectedStartMillis = ZonedDateTime.of(start, shanghaiZone).toInstant().toEpochMilli()
        val expectedEndMillis = ZonedDateTime.of(end, shanghaiZone).toInstant().toEpochMilli()

        assertEquals(expectedStartMillis, item.toStartEpochMillis(shanghaiZone))
        assertEquals(expectedEndMillis, item.toEndEpochMillis(shanghaiZone))

        // 1 hour 40 minutes = 100 minutes = 6,000,000 millis
        assertEquals(6_000_000L, item.toEndEpochMillis(shanghaiZone) - item.toStartEpochMillis(shanghaiZone))
    }

    @Test
    fun systemCalendarConfiguration_hasExpectedConstants() {
        assertEquals("MobileSchedule_FJNU", SystemCalendarSyncManager.ACCOUNT_NAME)
        assertEquals("FJNU_Schedule", SystemCalendarSyncManager.CALENDAR_NAME)
        assertEquals("福建师大课表", SystemCalendarSyncManager.CALENDAR_DISPLAY_NAME)
        assertEquals("Asia/Shanghai", SystemCalendarSyncManager.CALENDAR_TIMEZONE)
        assertEquals(200, SystemCalendarSyncManager.BATCH_SIZE)
    }
}
