package com.example.mobileschedule.ui.today

import java.time.LocalDate

object LunarCalendarHelper {
    private val LUNAR_MONTH_NAMES = arrayOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月"
    )
    private val LUNAR_DAY_NAMES = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    fun getLunarDateString(date: LocalDate): String {
        return try {
            val calClass = Class.forName("android.icu.util.ChineseCalendar")
            val cal = calClass.getDeclaredConstructor().newInstance()
            val setMethod = calClass.getMethod("set", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val getMethod = calClass.getMethod("get", Int::class.javaPrimitiveType)

            val fieldYear = 1
            val fieldMonth = 2
            val fieldDate = 5
            val fieldIsLeap = 22

            setMethod.invoke(cal, fieldYear, date.year)
            setMethod.invoke(cal, fieldMonth, date.monthValue - 1)
            setMethod.invoke(cal, fieldDate, date.dayOfMonth)

            val month = getMethod.invoke(cal, fieldMonth) as Int
            val isLeap = (getMethod.invoke(cal, fieldIsLeap) as? Int ?: 0) == 1
            val day = getMethod.invoke(cal, fieldDate) as Int

            val monthStr = (if (isLeap) "闰" else "") + (LUNAR_MONTH_NAMES.getOrNull(month) ?: "${month + 1}月")
            val dayStr = LUNAR_DAY_NAMES.getOrNull(day - 1) ?: "${day}日"
            "农历$monthStr$dayStr"
        } catch (_: Throwable) {
            ""
        }
    }

    fun dayOfWeekChinese(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "星期一"
        2 -> "星期二"
        3 -> "星期三"
        4 -> "星期四"
        5 -> "星期五"
        6 -> "星期六"
        7 -> "星期日"
        else -> ""
    }
}
