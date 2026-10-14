package com.example.mobileschedule.data.repository

import com.example.mobileschedule.data.model.CourseOriginDraft
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ScheduleManagementImportTest {
    private lateinit var service: ScheduleManagementService

    @Before
    fun setUp() {
        // Construct ScheduleManagementService without DB / Context for pure schema & rules validation
        service = ScheduleManagementService()
    }

    private fun buildValidJson(
        formatVersion: Int = 1,
        displayName: String = "2026-2027学年第一学期",
        firstWeekMonday: String = "2026-09-07",
        totalWeeks: Int = 20,
        totalSections: Int = 8,
        sectionTimesJson: String = """
            [
                {"section": 1, "start": "08:00", "end": "08:45"},
                {"section": 2, "start": "08:50", "end": "09:35"},
                {"section": 3, "start": "09:55", "end": "10:40"},
                {"section": 4, "start": "10:45", "end": "11:30"},
                {"section": 5, "start": "14:00", "end": "14:45"},
                {"section": 6, "start": "14:50", "end": "15:35"},
                {"section": 7, "start": "15:55", "end": "16:40"},
                {"section": 8, "start": "16:45", "end": "17:30"}
            ]
        """.trimIndent(),
        arrangementsJson: String = """
            [
                {
                    "name": "高等数学",
                    "teacher": "张教授",
                    "location": "理科楼101",
                    "dayOfWeek": 1,
                    "startSection": 1,
                    "endSection": 2,
                    "weeks": [1, 2, 3, 4, 5],
                    "origin": {
                        "type": "SCHOOL_IMPORT",
                        "schoolId": "fjnu",
                        "sourceId": "fjnu-zhengfang-web",
                        "sourceTermId": "2026-1",
                        "sourceEntryId": "entry-001"
                    }
                },
                {
                    "name": "大学英语",
                    "teacher": "李老师",
                    "location": "文科楼202",
                    "dayOfWeek": 3,
                    "startSection": 3,
                    "endSection": 4,
                    "weeks": [2, 4, 6],
                    "origin": {
                        "type": "MANUAL"
                    }
                }
            ]
        """.trimIndent(),
    ): String = """
        {
            "formatVersion": $formatVersion,
            "semester": {
                "displayName": "$displayName",
                "firstWeekMonday": "$firstWeekMonday",
                "totalWeeks": $totalWeeks,
                "totalSections": $totalSections,
                "sectionTimes": $sectionTimesJson
            },
            "arrangements": $arrangementsJson
        }
    """.trimIndent()

    @Test
    fun validJsonParsesSuccessfully() = runTest {
        val json = buildValidJson()
        val result = service.parseSemesterJson(json)
        assertTrue("Expected parsing to succeed, but got: $result", result.isSuccess)

        val preview = result.getOrThrow()
        assertEquals("2026-2027学年第一学期", preview.displayName)
        assertEquals(20, preview.totalWeeks)
        assertEquals(8, preview.totalSections)
        assertEquals(8, preview.sectionTimes.size)
        assertEquals(2, preview.arrangementCount)

        val firstCourse = preview.arrangements[0]
        assertEquals("高等数学", firstCourse.name)
        assertEquals("张教授", firstCourse.teacher)
        assertEquals("理科楼101", firstCourse.location)
        assertEquals(1, firstCourse.dayOfWeek)
        assertEquals(1, firstCourse.startSection)
        assertEquals(2, firstCourse.endSection)
        assertEquals(setOf(1, 2, 3, 4, 5), firstCourse.weeks)
        assertTrue(firstCourse.origin is CourseOriginDraft.SchoolImport)
        val schoolOrigin = firstCourse.origin as CourseOriginDraft.SchoolImport
        assertEquals("fjnu", schoolOrigin.schoolId)
        assertEquals("fjnu-zhengfang-web", schoolOrigin.sourceId)

        val secondCourse = preview.arrangements[1]
        assertEquals("大学英语", secondCourse.name)
        assertEquals("李老师", secondCourse.teacher)
        assertEquals(3, secondCourse.dayOfWeek)
        assertEquals(3, secondCourse.startSection)
        assertEquals(4, secondCourse.endSection)
        assertEquals(setOf(2, 4, 6), secondCourse.weeks)
        assertEquals(CourseOriginDraft.Manual, secondCourse.origin)
    }

    @Test
    fun emptySectionTimesIsValid() = runTest {
        val json = buildValidJson(sectionTimesJson = "[]")
        val result = service.parseSemesterJson(json)
        assertTrue("Empty section times should be accepted", result.isSuccess)
        val preview = result.getOrThrow()
        assertTrue(preview.sectionTimes.isEmpty())
        assertEquals(2, preview.arrangementCount)
    }

    @Test
    fun unsupportedVersionFails() = runTest {
        val jsonV2 = buildValidJson(formatVersion = 2)
        val resultV2 = service.parseSemesterJson(jsonV2)
        assertTrue(resultV2.isFailure)
        assertTrue(resultV2.exceptionOrNull()!!.message!!.contains("不支持的备份格式版本"))

        val jsonV0 = buildValidJson(formatVersion = 0)
        val resultV0 = service.parseSemesterJson(jsonV0)
        assertTrue(resultV0.isFailure)
        assertTrue(resultV0.exceptionOrNull()!!.message!!.contains("不支持的备份格式版本"))
    }

    @Test
    fun missingVersionFails() = runTest {
        val jsonWithoutVersion = """
            {
                "semester": {
                    "displayName": "无版本学期",
                    "firstWeekMonday": "2026-09-07",
                    "totalWeeks": 20,
                    "totalSections": 8,
                    "sectionTimes": []
                },
                "arrangements": []
            }
        """.trimIndent()
        val result = service.parseSemesterJson(jsonWithoutVersion)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("缺少备份格式版本字段"))
    }

    @Test
    fun rootNotObjectFails() = runTest {
        val arrayJson = """[{"formatVersion": 1}]"""
        val result = service.parseSemesterJson(arrayJson)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("根节点必须为 JSON 对象"))

        val malformedJson = "{ unclosed json "
        val malformedResult = service.parseSemesterJson(malformedJson)
        assertTrue(malformedResult.isFailure)
        assertTrue(malformedResult.exceptionOrNull()!!.message!!.contains("无法解析 JSON 数据"))
    }

    @Test
    fun missingSemesterFails() = runTest {
        val jsonWithoutSemester = """
            {
                "formatVersion": 1,
                "arrangements": []
            }
        """.trimIndent()
        val result = service.parseSemesterJson(jsonWithoutSemester)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("缺少学期配置数据"))
    }

    @Test
    fun blankDisplayNameFails() = runTest {
        val json = buildValidJson(displayName = "   ")
        val result = service.parseSemesterJson(json)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("学期名称不能为空"))
    }

    @Test
    fun missingOrInvalidFirstWeekMondayFails() = runTest {
        // Tuesday instead of Monday
        val tuesdayJson = buildValidJson(firstWeekMonday = "2026-09-08")
        val resultTuesday = service.parseSemesterJson(tuesdayJson)
        assertTrue(resultTuesday.isFailure)
        assertTrue(resultTuesday.exceptionOrNull()!!.message!!.contains("必须为周一"))

        // Malformed date
        val malformedDateJson = buildValidJson(firstWeekMonday = "2026/09/07")
        val resultMalformed = service.parseSemesterJson(malformedDateJson)
        assertTrue(resultMalformed.isFailure)
        assertTrue(resultMalformed.exceptionOrNull()!!.message!!.contains("格式无效"))
    }

    @Test
    fun totalWeeksOutOfRangeFails() = runTest {
        val zeroWeeksJson = buildValidJson(totalWeeks = 0)
        val resultZero = service.parseSemesterJson(zeroWeeksJson)
        assertTrue(resultZero.isFailure)
        assertTrue(resultZero.exceptionOrNull()!!.message!!.contains("总周数超出范围"))

        val tooManyWeeksJson = buildValidJson(totalWeeks = 65)
        val resultTooMany = service.parseSemesterJson(tooManyWeeksJson)
        assertTrue(resultTooMany.isFailure)
        assertTrue(resultTooMany.exceptionOrNull()!!.message!!.contains("总周数超出范围"))
    }

    @Test
    fun totalSectionsOutOfRangeFails() = runTest {
        val zeroSectionsJson = buildValidJson(totalSections = 0)
        val resultZero = service.parseSemesterJson(zeroSectionsJson)
        assertTrue(resultZero.isFailure)
        assertTrue(resultZero.exceptionOrNull()!!.message!!.contains("每天总节数超出范围"))

        val tooManySectionsJson = buildValidJson(totalSections = 100)
        val resultTooMany = service.parseSemesterJson(tooManySectionsJson)
        assertTrue(resultTooMany.isFailure)
        assertTrue(resultTooMany.exceptionOrNull()!!.message!!.contains("每天总节数超出范围"))
    }

    @Test
    fun sectionTimesCountMismatchFails() = runTest {
        // totalSections is 8, but sectionTimes has only 2
        val shortTimesJson = """
            [
                {"section": 1, "start": "08:00", "end": "08:45"},
                {"section": 2, "start": "08:50", "end": "09:35"}
            ]
        """.trimIndent()
        val json = buildValidJson(totalSections = 8, sectionTimesJson = shortTimesJson)
        val result = service.parseSemesterJson(json)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("与总节数 (8) 不一致"))
    }

    @Test
    fun sectionTimesOverlapFails() = runTest {
        // section 2 starts at 08:30 before section 1 ends at 08:45
        val overlappingTimesJson = """
            [
                {"section": 1, "start": "08:00", "end": "08:45"},
                {"section": 2, "start": "08:30", "end": "09:15"}
            ]
        """.trimIndent()
        val json = buildValidJson(totalSections = 2, sectionTimesJson = overlappingTimesJson)
        val result = service.parseSemesterJson(json)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("冲突重叠"))
    }

    @Test
    fun missingArrangementsFails() = runTest {
        val jsonWithoutArrangements = """
            {
                "formatVersion": 1,
                "semester": {
                    "displayName": "测试学期",
                    "firstWeekMonday": "2026-09-07",
                    "totalWeeks": 20,
                    "totalSections": 8,
                    "sectionTimes": []
                }
            }
        """.trimIndent()
        val result = service.parseSemesterJson(jsonWithoutArrangements)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("缺少课程安排数据"))
    }

    @Test
    fun arrangementBlankNameFails() = runTest {
        val blankNameCourse = """
            [
                {
                    "name": "   ",
                    "dayOfWeek": 1,
                    "startSection": 1,
                    "endSection": 2,
                    "weeks": [1, 2]
                }
            ]
        """.trimIndent()
        val json = buildValidJson(arrangementsJson = blankNameCourse)
        val result = service.parseSemesterJson(json)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("名称不能为空"))
    }

    @Test
    fun arrangementDayOutOfRangeFails() = runTest {
        val invalidDayCourse = """
            [
                {
                    "name": "测试课",
                    "dayOfWeek": 8,
                    "startSection": 1,
                    "endSection": 2,
                    "weeks": [1, 2]
                }
            ]
        """.trimIndent()
        val json = buildValidJson(arrangementsJson = invalidDayCourse)
        val result = service.parseSemesterJson(json)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("星期超出有效范围"))
    }

    @Test
    fun arrangementSectionOutOfRangeFails() = runTest {
        // startSection 0
        val zeroStartCourse = """
            [
                {
                    "name": "测试课",
                    "dayOfWeek": 1,
                    "startSection": 0,
                    "endSection": 2,
                    "weeks": [1, 2]
                }
            ]
        """.trimIndent()
        val resultZero = service.parseSemesterJson(buildValidJson(arrangementsJson = zeroStartCourse))
        assertTrue(resultZero.isFailure)
        assertTrue(resultZero.exceptionOrNull()!!.message!!.contains("节次超出有效范围"))

        // endSection > totalSections (10 > 8)
        val beyondMaxCourse = """
            [
                {
                    "name": "测试课",
                    "dayOfWeek": 1,
                    "startSection": 7,
                    "endSection": 10,
                    "weeks": [1, 2]
                }
            ]
        """.trimIndent()
        val resultBeyond = service.parseSemesterJson(buildValidJson(arrangementsJson = beyondMaxCourse))
        assertTrue(resultBeyond.isFailure)
        assertTrue(resultBeyond.exceptionOrNull()!!.message!!.contains("节次超出有效范围"))
    }

    @Test
    fun arrangementWeeksOutOfRangeFails() = runTest {
        // empty weeks
        val emptyWeeksCourse = """
            [
                {
                    "name": "测试课",
                    "dayOfWeek": 1,
                    "startSection": 1,
                    "endSection": 2,
                    "weeks": []
                }
            ]
        """.trimIndent()
        val resultEmpty = service.parseSemesterJson(buildValidJson(arrangementsJson = emptyWeeksCourse))
        assertTrue(resultEmpty.isFailure)
        assertTrue(resultEmpty.exceptionOrNull()!!.message!!.contains("周次不能为空"))

        // week out of range (25 when totalWeeks is 20)
        val outOfRangeCourse = """
            [
                {
                    "name": "测试课",
                    "dayOfWeek": 1,
                    "startSection": 1,
                    "endSection": 2,
                    "weeks": [1, 25]
                }
            ]
        """.trimIndent()
        val resultOut = service.parseSemesterJson(buildValidJson(arrangementsJson = outOfRangeCourse))
        assertTrue(resultOut.isFailure)
        assertTrue(resultOut.exceptionOrNull()!!.message!!.contains("周次超出学期范围"))
    }
}
