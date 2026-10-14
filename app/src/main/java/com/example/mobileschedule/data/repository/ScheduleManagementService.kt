package com.example.mobileschedule.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.mobileschedule.data.local.AppDatabase
import com.example.mobileschedule.data.local.entity.*
import com.example.mobileschedule.data.model.*
import com.example.mobileschedule.data.rules.ScheduleRules
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class ScheduleManagementService private constructor(
    private val database: AppDatabase?,
    private val context: Context?,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) {
    @Inject
    constructor(
        database: AppDatabase,
        @ApplicationContext context: Context,
    ) : this(database, context, Unit)

    constructor(database: AppDatabase) : this(database, null, Unit)

    constructor() : this(null, null, Unit)

    suspend fun deleteSemester(semesterId: Long): Unit {
        val db = checkNotNull(database) { "Database is required for deleteSemester" }
        db.withTransaction {
            val semesters = db.semesterDao()
            requireNotNull(semesters.getSemester(semesterId)) { "Semester no longer exists" }
            val wasActive = semesters.getActiveSemesterId() == semesterId
            db.courseDao().deleteSemesterCourses(semesterId)
            db.importBatchDao().deleteSemesterBatches(semesterId)
            semesters.deleteSourceBindings(semesterId)
            // The calendar and section times belong to the semester and cascade with its deletion.
            semesters.deleteSemester(semesterId)
            if (wasActive) semesters.setActiveSemester(semesters.getLatestSemesterId())
        }
    }

    suspend fun clearSemester(semesterId: Long): Int {
        val db = checkNotNull(database) { "Database is required for clearSemester" }
        return db.withTransaction {
            requireNotNull(db.semesterDao().getSemester(semesterId)) { "Semester no longer exists" }
            val removed = db.courseDao().deleteSemesterCourses(semesterId)
            db.importBatchDao().deleteSemesterBatches(semesterId)
            db.semesterDao().deleteSourceBindings(semesterId)
            // Every pending preview of this semester must be prepared again after a clear operation.
            db.semesterDao().advanceConfigRevision(semesterId)
            removed
        }
    }

    suspend fun exportSemester(semesterId: Long): String {
        val db = checkNotNull(database) { "Database is required for exportSemester" }
        return db.withTransaction {
            val semester = requireNotNull(db.semesterDao().getSemester(semesterId)).toModel()
            val courses = db.courseDao().getSemesterCourses(semesterId).map { it.toArrangement() }
            val snapshot = buildJsonObject {
                put("formatVersion", 1)
                putJsonObject("semester") {
                    put("displayName", semester.displayName)
                    semester.config?.let { config ->
                        put("firstWeekMonday", config.firstWeekMonday.toString())
                        put("totalWeeks", config.totalWeeks)
                        put("totalSections", config.totalSections)
                        putJsonArray("sectionTimes") {
                            config.sectionTimes.forEach { time ->
                                addJsonObject {
                                    put("section", time.section)
                                    put("start", time.start.toString())
                                    put("end", time.end.toString())
                                }
                            }
                        }
                    }
                }
                putJsonArray("arrangements") {
                    courses.forEach { course ->
                        addJsonObject {
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
                        }
                    }
                }
            }
            Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), snapshot)
        }
    }

    /**
     * Reads a schedule JSON backup from an Android [Uri] and validates its schema and data integrity.
     */
    suspend fun importSemesterFromJson(uri: Uri): Result<SemesterImportPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val ctx = checkNotNull(context) { "读取文件需要系统 Context 支持" }
            val jsonString = ctx.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw IllegalArgumentException("无法打开指定的备份文件")
            parseSemesterJsonInternal(jsonString)
        }
    }

    /**
     * Parses and validates a schedule JSON backup string without requiring an Android [Uri].
     */
    suspend fun importSemesterFromJson(jsonString: String): Result<SemesterImportPreview> = withContext(Dispatchers.IO) {
        runCatching {
            parseSemesterJsonInternal(jsonString)
        }
    }

    /**
     * Alias for [importSemesterFromJson] taking a raw JSON string.
     */
    suspend fun parseSemesterJson(jsonString: String): Result<SemesterImportPreview> =
        importSemesterFromJson(jsonString)

    private suspend fun parseSemesterJsonInternal(jsonString: String): SemesterImportPreview {
        val rootElement = try {
            Json.parseToJsonElement(jsonString)
        } catch (e: Exception) {
            throw IllegalArgumentException("无法解析 JSON 数据：${e.localizedMessage ?: "格式错误"}")
        }

        val rootObject = rootElement as? JsonObject
            ?: throw IllegalArgumentException("备份文件格式错误：根节点必须为 JSON 对象")

        // 1. Version validation
        val formatVersionElement = rootObject["formatVersion"]
            ?: throw IllegalArgumentException("缺少备份格式版本字段 (formatVersion)")
        val formatVersion = formatVersionElement.jsonPrimitive.intOrNull
            ?: throw IllegalArgumentException("备份格式版本字段必须为有效整数")
        if (formatVersion != 1) {
            throw IllegalArgumentException("不支持的备份格式版本: $formatVersion (当前仅支持版本: 1)")
        }

        // 2. Semester metadata validation
        val semesterObject = rootObject["semester"] as? JsonObject
            ?: throw IllegalArgumentException("缺少学期配置数据 (semester)")

        val rawDisplayName = semesterObject["displayName"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: throw IllegalArgumentException("学期名称不能为空")
        if (rawDisplayName.isEmpty()) {
            throw IllegalArgumentException("学期名称不能为空")
        }
        val displayName: String = rawDisplayName

        val mondayStr = semesterObject["firstWeekMonday"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("缺少第1周起始周一日期 (firstWeekMonday)")
        val firstWeekMonday = try {
            LocalDate.parse(mondayStr)
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("第1周起始日期格式无效: $mondayStr (需为 YYYY-MM-DD)")
        }
        if (firstWeekMonday.dayOfWeek != DayOfWeek.MONDAY) {
            throw IllegalArgumentException("第1周起始日期必须为周一 (当前为: ${firstWeekMonday.dayOfWeek})")
        }

        val totalWeeks = semesterObject["totalWeeks"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("缺少或无效的总周数 (totalWeeks)")
        if (totalWeeks !in 1..60) {
            throw IllegalArgumentException("总周数超出范围 (必须在 1 到 60 周之间，当前为: $totalWeeks)")
        }

        val totalSections = semesterObject["totalSections"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("缺少或无效的每天总节数 (totalSections)")
        if (totalSections !in 1..99) {
            throw IllegalArgumentException("每天总节数超出范围 (必须在 1 到 99 节之间，当前为: $totalSections)")
        }

        val sectionTimes = mutableListOf<SectionTime>()
        val sectionTimesArray = semesterObject["sectionTimes"] as? JsonArray
        if (sectionTimesArray != null && sectionTimesArray.isNotEmpty()) {
            for ((index, item) in sectionTimesArray.withIndex()) {
                val itemObj = item as? JsonObject
                    ?: throw IllegalArgumentException("节次时间项必须为 JSON 对象")
                val section = itemObj["section"]?.jsonPrimitive?.intOrNull
                    ?: throw IllegalArgumentException("第 ${index + 1} 个节次时间缺少节次编号 (section)")
                val startStr = itemObj["start"]?.jsonPrimitive?.contentOrNull
                    ?: throw IllegalArgumentException("第 $section 节缺少开始时间 (start)")
                val endStr = itemObj["end"]?.jsonPrimitive?.contentOrNull
                    ?: throw IllegalArgumentException("第 $section 节缺少结束时间 (end)")

                val start = try {
                    LocalTime.parse(startStr)
                } catch (_: DateTimeParseException) {
                    throw IllegalArgumentException("第 $section 节开始时间格式无效: $startStr (需为 HH:mm)")
                }
                val end = try {
                    LocalTime.parse(endStr)
                } catch (_: DateTimeParseException) {
                    throw IllegalArgumentException("第 $section 节结束时间格式无效: $endStr (需为 HH:mm)")
                }
                sectionTimes.add(SectionTime(section, start, end))
            }

            // Validate section times using domain rules
            val configDraft = SemesterConfigDraft(displayName, firstWeekMonday, totalWeeks, totalSections, sectionTimes)
            val issues = ScheduleRules.validateConfig(configDraft)
            if (issues.isNotEmpty()) {
                val issue = issues.first()
                throw IllegalArgumentException(when (issue.code) {
                    RuleIssueCode.TIMES_INCOMPLETE -> "节次时间数量 (${sectionTimes.size}) 与总节数 ($totalSections) 不一致"
                    RuleIssueCode.SECTION_ORDER_INVALID -> "第 ${issue.section} 节序号顺序错误"
                    RuleIssueCode.TIME_NOT_MINUTE_PRECISE -> "第 ${issue.section} 节时间必须精确到分钟"
                    RuleIssueCode.TIME_RANGE_INVALID -> "第 ${issue.section} 节开始时间必须早于结束时间"
                    RuleIssueCode.TIME_OVERLAP -> "第 ${issue.section} 节时间与上一节时间冲突重叠"
                    else -> "学期节次配置无效: ${issue.code.name}"
                })
            }
        }

        // 3. Arrangements validation
        val arrangementsArray = rootObject["arrangements"] as? JsonArray
            ?: throw IllegalArgumentException("缺少课程安排数据 (arrangements)")

        val arrangements = mutableListOf<ArrangementBackupItem>()
        val dummyConfig = SemesterConfig(firstWeekMonday, totalWeeks, totalSections, sectionTimes, 0L)

        for ((idx, item) in arrangementsArray.withIndex()) {
            val itemObj = item as? JsonObject
                ?: throw IllegalArgumentException("第 ${idx + 1} 条课程安排必须为 JSON 对象")

            val rawName = itemObj["name"]?.jsonPrimitive?.contentOrNull?.trim()
                ?: throw IllegalArgumentException("第 ${idx + 1} 条课程安排名称不能为空")
            if (rawName.isEmpty()) {
                throw IllegalArgumentException("第 ${idx + 1} 条课程安排名称不能为空")
            }
            val name: String = rawName

            val dayOfWeek = itemObj["dayOfWeek"]?.jsonPrimitive?.intOrNull
                ?: throw IllegalArgumentException("课程“$name”缺少星期设置 (dayOfWeek)")
            if (dayOfWeek !in 1..7) {
                throw IllegalArgumentException("课程“$name”的星期超出有效范围 (必须在 1 到 7 之间，当前为: $dayOfWeek)")
            }

            val startSection = itemObj["startSection"]?.jsonPrimitive?.intOrNull
                ?: throw IllegalArgumentException("课程“$name”缺少开始节次 (startSection)")
            val endSection = itemObj["endSection"]?.jsonPrimitive?.intOrNull
                ?: throw IllegalArgumentException("课程“$name”缺少结束节次 (endSection)")
            if (startSection < 1 || endSection < startSection || endSection > totalSections) {
                throw IllegalArgumentException("课程“$name”的节次超出有效范围 (第 $startSection - $endSection 节，总节数: $totalSections)")
            }

            val weeksArray = itemObj["weeks"] as? JsonArray
                ?: throw IllegalArgumentException("课程“$name”缺少周次设置 (weeks)")
            if (weeksArray.isEmpty()) {
                throw IllegalArgumentException("课程“$name”的周次不能为空")
            }
            val weeksSet = weeksArray.mapNotNull { it.jsonPrimitive.intOrNull }.toSortedSet()
            if (weeksSet.isEmpty()) {
                throw IllegalArgumentException("课程“$name”包含无效周次")
            }
            if (weeksSet.any { it !in 1..totalWeeks }) {
                throw IllegalArgumentException("课程“$name”的周次超出学期范围 (必须在 1 到 $totalWeeks 周之间)")
            }

            val teacher = itemObj["teacher"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            val location = itemObj["location"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

            val originObj = itemObj["origin"] as? JsonObject
            val originType = originObj?.get("type")?.jsonPrimitive?.contentOrNull
            val origin = when (originType) {
                "SCHOOL_IMPORT" -> {
                    val schoolId = originObj["schoolId"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val sourceId = originObj["sourceId"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val sourceTermId = originObj["sourceTermId"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val sourceEntryId = originObj["sourceEntryId"]?.jsonPrimitive?.contentOrNull
                    if (schoolId.isNotBlank() && sourceId.isNotBlank() && sourceTermId.isNotBlank()) {
                        CourseOriginDraft.SchoolImport(schoolId, sourceId, sourceTermId, sourceEntryId)
                    } else {
                        CourseOriginDraft.Manual
                    }
                }
                "LEGACY" -> CourseOriginDraft.Legacy
                else -> CourseOriginDraft.Manual
            }

            // Verify with domain arrangement rules
            val draft = ArrangementDraft(name, teacher, location, dayOfWeek, startSection, endSection, weeksSet)
            val issues = ScheduleRules.validateArrangement(dummyConfig, draft)
            if (issues.isNotEmpty()) {
                val issue = issues.first()
                throw IllegalArgumentException("课程“$name”安排无效: ${issue.code.name}")
            }

            arrangements.add(
                ArrangementBackupItem(
                    name = name,
                    teacher = teacher,
                    location = location,
                    dayOfWeek = dayOfWeek,
                    startSection = startSection,
                    endSection = endSection,
                    weeks = weeksSet,
                    origin = origin,
                )
            )
        }

        // 4. Check for existing semester with matching name if database is present
        val existing = database?.semesterDao()?.getSemesterByDisplayName(displayName)
        val existingSemesterId = existing?.semester?.id
        val existingSemesterName = existing?.semester?.displayName

        return SemesterImportPreview(
            displayName = displayName,
            firstWeekMonday = firstWeekMonday,
            totalWeeks = totalWeeks,
            totalSections = totalSections,
            sectionTimes = sectionTimes,
            arrangementCount = arrangements.size,
            existingSemesterId = existingSemesterId,
            existingSemesterName = existingSemesterName,
            arrangements = arrangements,
        )
    }

    /**
     * Commits the imported semester backup into the database inside an atomic Room transaction.
     * If [overwriteExisting] is true and a semester with the same display name exists,
     * the existing semester's calendar, section times, and arrangements are replaced.
     * Otherwise, a new semester is created (with a distinct name if necessary).
     */
    suspend fun commitImport(
        preview: SemesterImportPreview,
        overwriteExisting: Boolean = false,
    ): Result<Long> = withContext(Dispatchers.IO) {
        val db = checkNotNull(database) { "Database is required for commitImport" }
        runCatching {
            db.withTransaction {
                val targetSemesterId: Long
                if (overwriteExisting && preview.existingSemesterId != null) {
                    val existingId = preview.existingSemesterId
                    requireNotNull(db.semesterDao().getSemester(existingId)) { "待覆盖的学期已不存在" }

                    // Clear previous data
                    db.courseDao().deleteSemesterCourses(existingId)
                    db.importBatchDao().deleteSemesterBatches(existingId)
                    db.semesterDao().deleteSourceBindings(existingId)
                    db.semesterDao().deleteSectionTimes(existingId)

                    db.semesterDao().updateDisplayName(existingId, preview.displayName.trim())
                    db.semesterDao().updateConfig(
                        semesterId = existingId,
                        firstWeekMonday = preview.firstWeekMonday.toEpochDay(),
                        totalWeeks = preview.totalWeeks,
                        totalSections = preview.totalSections,
                        revision = 1L,
                    )
                    targetSemesterId = existingId
                } else {
                    val finalName = if (preview.existingSemesterId != null) {
                        generateUniqueDisplayName(db, preview.displayName.trim())
                    } else {
                        preview.displayName.trim()
                    }

                    targetSemesterId = db.semesterDao().insertSemester(SemesterEntity(displayName = finalName))
                    db.semesterDao().insertConfig(
                        SemesterConfigEntity(
                            semesterId = targetSemesterId,
                            firstWeekMonday = preview.firstWeekMonday.toEpochDay(),
                            totalWeeks = preview.totalWeeks,
                            totalSections = preview.totalSections,
                            revision = 1L,
                        )
                    )
                }

                if (preview.sectionTimes.isNotEmpty()) {
                    db.semesterDao().insertSectionTimes(preview.sectionTimes.map {
                        SectionTimeEntity(
                            semesterId = targetSemesterId,
                            section = it.section,
                            startMinute = it.start.toSecondOfDay() / 60,
                            endMinute = it.end.toSecondOfDay() / 60,
                        )
                    })
                }

                insertCoursesAndWeeks(db, targetSemesterId, preview.arrangements)

                if (db.semesterDao().getActiveSemesterId() == null) {
                    db.semesterDao().setActiveSemester(targetSemesterId)
                }
                db.semesterDao().advanceConfigRevision(targetSemesterId)

                targetSemesterId
            }
        }
    }

    private suspend fun generateUniqueDisplayName(db: AppDatabase, baseName: String): String {
        val existingNames = db.semesterDao().getAllSemesterNames().toSet()
        if (baseName !in existingNames) return baseName
        var candidate = "$baseName (导入)"
        if (candidate !in existingNames) return candidate
        var index = 2
        while (true) {
            candidate = "$baseName (导入 $index)"
            if (candidate !in existingNames) return candidate
            index++
        }
    }

    private suspend fun insertCoursesAndWeeks(
        db: AppDatabase,
        semesterId: Long,
        arrangements: List<ArrangementBackupItem>,
    ) {
        if (arrangements.isEmpty()) return

        val schoolImports = arrangements.filter { it.origin is CourseOriginDraft.SchoolImport }
        val importsByScope = schoolImports.groupBy { it.origin as CourseOriginDraft.SchoolImport }

        val scopeBatchMap = mutableMapOf<CourseOriginDraft.SchoolImport, String>()
        for ((origin, items) in importsByScope) {
            val existingBinding = db.semesterDao().getSourceBinding(origin.schoolId, origin.sourceId, origin.sourceTermId)
            val canBind = existingBinding == null || existingBinding.semesterId == semesterId
            if (canBind) {
                if (existingBinding == null) {
                    db.semesterDao().insertSourceBinding(
                        SourceBindingEntity(
                            schoolId = origin.schoolId,
                            sourceId = origin.sourceId,
                            sourceTermId = origin.sourceTermId,
                            semesterId = semesterId,
                            sourceTermLabel = null,
                        )
                    )
                }
                val batchId = UUID.randomUUID().toString()
                db.importBatchDao().insertBatch(
                    ImportBatchEntity(
                        id = batchId,
                        semesterId = semesterId,
                        schoolId = origin.schoolId,
                        sourceId = origin.sourceId,
                        sourceTermId = origin.sourceTermId,
                        committedAt = System.currentTimeMillis(),
                        savedCount = items.size,
                        removedCount = 0,
                    )
                )
                scopeBatchMap[origin] = batchId
            }
        }

        val courseEntities = mutableListOf<CourseEntity>()
        val weeksList = mutableListOf<List<Int>>()

        for (item in arrangements) {
            val (originType, batchId, sourceEntryId) = when (val origin = item.origin) {
                is CourseOriginDraft.SchoolImport -> {
                    val batch = scopeBatchMap[origin]
                    if (batch != null) {
                        Triple(CourseOriginType.SCHOOL_IMPORT, batch, origin.sourceEntryId)
                    } else {
                        Triple(CourseOriginType.MANUAL, null, null)
                    }
                }
                CourseOriginDraft.Legacy -> Triple(CourseOriginType.LEGACY, null, null)
                CourseOriginDraft.Manual -> Triple(CourseOriginType.MANUAL, null, null)
            }

            courseEntities.add(
                CourseEntity(
                    name = item.name,
                    teacher = item.teacher,
                    location = item.location,
                    dayOfWeek = item.dayOfWeek,
                    startSection = item.startSection,
                    endSection = item.endSection,
                    semesterId = semesterId,
                    originType = originType,
                    importBatchId = batchId,
                    sourceEntryId = sourceEntryId,
                )
            )
            weeksList.add(item.weeks.toList())
        }

        val insertedIds = db.courseDao().insertCourses(courseEntities)
        check(insertedIds.size == courseEntities.size) { "课程安排写入数量不匹配" }

        val weekEntities = mutableListOf<CourseWeekEntity>()
        for (i in insertedIds.indices) {
            val id = insertedIds[i]
            for (w in weeksList[i]) {
                weekEntities.add(CourseWeekEntity(id, w))
            }
        }
        db.courseDao().insertWeeks(weekEntities)
    }
}
