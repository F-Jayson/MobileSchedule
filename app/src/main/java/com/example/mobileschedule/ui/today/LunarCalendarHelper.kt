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
            val tzClass = Class.forName("android.icu.util.TimeZone")
            val getTimeZoneMethod = tzClass.getMethod("getTimeZone", String::class.java)
            val tz = getTimeZoneMethod.invoke(null, "GMT+8")

            val cal = try {
                val tzConstructor = calClass.getConstructor(tzClass)
                tzConstructor.newInstance(tz)
            } catch (_: Throwable) {
                val defaultInstance = calClass.getDeclaredConstructor().newInstance()
                try {
                    calClass.getMethod("setTimeZone", tzClass).invoke(defaultInstance, tz)
                } catch (_: Throwable) {}
                defaultInstance
            }

            val setTimeInMillisMethod = calClass.getMethod("setTimeInMillis", Long::class.javaPrimitiveType)
            val getMethod = calClass.getMethod("get", Int::class.javaPrimitiveType)

            val millis = date.atTime(12, 0).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
            setTimeInMillisMethod.invoke(cal, millis)

            val fieldMonth = 2
            val fieldDate = 5
            val isLeapField = try {
                calClass.getField("IS_LEAP_MONTH").getInt(null)
            } catch (_: Throwable) {
                22
            }

            val month = getMethod.invoke(cal, fieldMonth) as Int
            val isLeap = (getMethod.invoke(cal, isLeapField) as? Int ?: 0) == 1
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
