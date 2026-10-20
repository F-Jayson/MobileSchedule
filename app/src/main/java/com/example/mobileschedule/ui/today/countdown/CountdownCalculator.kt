package com.example.mobileschedule.ui.today.countdown

import com.example.mobileschedule.data.model.SemesterConfig
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object CountdownCalculator {

    fun resolvePostgraduateExamDate(year: Int): LocalDate {
        val saturdays = (1..31).map { LocalDate.of(year, 12, it) }
            .filter { it.dayOfWeek == DayOfWeek.SATURDAY }
        return if (saturdays.size >= 2) saturdays[saturdays.size - 2] else saturdays.last()
    }

    fun resolveJuneCetSaturday(year: Int): LocalDate {
        val saturdays = (1..30).map { LocalDate.of(year, 6, it) }
            .filter { it.dayOfWeek == DayOfWeek.SATURDAY }
        return saturdays.getOrElse(2) { saturdays.last() }
    }

    fun resolveDecemberCetSaturday(year: Int): LocalDate {
        val saturdays = (1..31).map { LocalDate.of(year, 12, it) }
            .filter { it.dayOfWeek == DayOfWeek.SATURDAY }
        return saturdays.getOrElse(1) { saturdays.first() }
    }

    fun resolvePostgraduateExam(today: LocalDate): CountdownTarget {
        val currentYearDate = resolvePostgraduateExamDate(today.year)
        val targetDate = if (today.isAfter(currentYearDate)) {
            resolvePostgraduateExamDate(today.year + 1)
        } else {
            currentYearDate
        }
        val days = ChronoUnit.DAYS.between(today, targetDate)
        return CountdownTarget(
            id = "preset_kaoyan",
            title = "${targetDate.year + 1}年全国硕士研究生招生考试",
            shortTitle = "考研初试",
            targetDate = targetDate,
            category = CountdownCategory.EXAM,
            motto = "星光不问赶路人，时光不负有心人",
            daysRemaining = days,
        )
    }

    fun resolveCetExam(today: LocalDate): CountdownTarget {
        val juneDate = resolveJuneCetSaturday(today.year)
        val decDate = resolveDecemberCetSaturday(today.year)

        val (targetDate, isJune) = when {
            !today.isAfter(juneDate) -> juneDate to true
            !today.isAfter(decDate) -> decDate to false
            else -> resolveJuneCetSaturday(today.year + 1) to true
        }

        val halfText = if (isJune) "上半年" else "下半年"
        val days = ChronoUnit.DAYS.between(today, targetDate)
        return CountdownTarget(
            id = "preset_cet",
            title = "${targetDate.year}年${halfText}大学英语四六级考试",
            shortTitle = "四六级考试",
            targetDate = targetDate,
            category = CountdownCategory.EXAM,
            motto = "词汇语法了然于心，自信下笔稳操胜券",
            daysRemaining = days,
        )
    }

    fun resolveVacation(today: LocalDate, config: SemesterConfig?, semesterName: String? = null): CountdownTarget? {
        if (config == null) return null
        val vacationStart = config.firstWeekMonday.plusWeeks(config.totalWeeks.toLong())
        val days = ChronoUnit.DAYS.between(today, vacationStart)
        // If vacation started more than 30 days ago, no need to show
        if (days < -30) return null

        val isSummer = config.firstWeekMonday.monthValue in 2..5
        val vacationName = if (isSummer) "暑假放假" else "寒假放假"
        val prefix = semesterName?.let { "$it · " } ?: ""
        val motto = if (isSummer) "过好充实每一天，拥抱惬意盛夏" else "学期渐入尾声，迎接温暖寒冬与新年"

        return CountdownTarget(
            id = "preset_vacation",
            title = "$prefix$vacationName",
            shortTitle = vacationName,
            targetDate = vacationStart,
            category = CountdownCategory.HOLIDAY,
            motto = motto,
            daysRemaining = days,
        )
    }

    fun resolveFinalExams(today: LocalDate, config: SemesterConfig?, semesterName: String? = null): CountdownTarget? {
        if (config == null || config.totalWeeks <= 1) return null
        val finalsWeekStart = config.firstWeekMonday.plusWeeks((config.totalWeeks - 1).toLong())
        val days = ChronoUnit.DAYS.between(today, finalsWeekStart)
        // If finals week ended more than 14 days ago, omit
        if (days < -14) return null

        val prefix = semesterName?.let { "$it · " } ?: ""
        return CountdownTarget(
            id = "preset_finals",
            title = "${prefix}期末考试周",
            shortTitle = "期末考试周",
            targetDate = finalsWeekStart,
            category = CountdownCategory.EXAM,
            motto = "查缺补漏沉着应考，全力以赴不留遗憾",
            daysRemaining = days,
        )
    }

    fun buildAllCountdowns(
        today: LocalDate,
        config: SemesterConfig?,
        semesterName: String? = null,
        customList: List<CustomCountdown> = emptyList(),
        pinnedId: String? = null,
    ): List<CountdownTarget> {
        val targets = mutableListOf<CountdownTarget>()

        // 1. Presets
        targets.add(resolvePostgraduateExam(today))
        targets.add(resolveCetExam(today))
        resolveVacation(today, config, semesterName)?.let { targets.add(it) }
        resolveFinalExams(today, config, semesterName)?.let { targets.add(it) }

        // 2. Customs
        for (custom in customList) {
            val days = ChronoUnit.DAYS.between(today, custom.targetDate)
            targets.add(
                CountdownTarget(
                    id = custom.id,
                    title = custom.title,
                    shortTitle = custom.title,
                    targetDate = custom.targetDate,
                    category = CountdownCategory.CUSTOM,
                    motto = custom.note ?: "持之以恒，笃行不怠",
                    daysRemaining = days,
                    isCustom = true,
                )
            )
        }

        // 3. Mark pinned status
        val marked = targets.map { target ->
            target.copy(isPinned = target.id == pinnedId)
        }

        // 4. Sort:
        // Pinned item first;
        // Upcoming items (days >= 0) sorted by daysRemaining ascending;
        // Past items (days < 0) sorted by daysRemaining descending (most recent past first).
        return marked.sortedWith { a, b ->
            when {
                a.isPinned && !b.isPinned -> -1
                !a.isPinned && b.isPinned -> 1
                a.daysRemaining >= 0 && b.daysRemaining < 0 -> -1
                a.daysRemaining < 0 && b.daysRemaining >= 0 -> 1
                a.daysRemaining >= 0 && b.daysRemaining >= 0 -> a.daysRemaining.compareTo(b.daysRemaining)
                else -> b.daysRemaining.compareTo(a.daysRemaining)
            }
        }
    }
}
