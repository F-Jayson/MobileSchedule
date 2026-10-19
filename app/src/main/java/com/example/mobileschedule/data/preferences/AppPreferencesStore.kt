package com.example.mobileschedule.data.preferences

import android.content.Context
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val title: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色模式"),
    DARK("深色模式"),
}

class AppPreferencesStore(context: Context, preferencesName: String = "app_preferences") {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    companion object {
        private val _themeModeFlow = MutableStateFlow(AppThemeMode.SYSTEM)
        private val _showWeekendFlow = MutableStateFlow(true)
        private val _firstDayOfWeekFlow = MutableStateFlow(DayOfWeek.MONDAY)
        private val _reminderEnabledFlow = MutableStateFlow(false)
        private val _reminderAdvanceMinutesFlow = MutableStateFlow(15)
        private val _showNonCurrentWeekCoursesFlow = MutableStateFlow(false)
        @Volatile private var isInitialized = false
    }

    init {
        if (!isInitialized) {
            _themeModeFlow.value = getThemeMode()
            _showWeekendFlow.value = isShowWeekend()
            _firstDayOfWeekFlow.value = getFirstDayOfWeek()
            _reminderEnabledFlow.value = isReminderEnabled()
            _reminderAdvanceMinutesFlow.value = getReminderAdvanceMinutes()
            _showNonCurrentWeekCoursesFlow.value = isShowNonCurrentWeekCourses()
            isInitialized = true
        }
    }

    val themeModeFlow: StateFlow<AppThemeMode> = _themeModeFlow.asStateFlow()
    val showWeekendFlow: StateFlow<Boolean> = _showWeekendFlow.asStateFlow()
    val firstDayOfWeekFlow: StateFlow<DayOfWeek> = _firstDayOfWeekFlow.asStateFlow()
    val reminderEnabledFlow: StateFlow<Boolean> = _reminderEnabledFlow.asStateFlow()
    val reminderAdvanceMinutesFlow: StateFlow<Int> = _reminderAdvanceMinutesFlow.asStateFlow()
    val showNonCurrentWeekCourses: Flow<Boolean> = _showNonCurrentWeekCoursesFlow.asStateFlow()
    val showNonCurrentWeekCoursesFlow: StateFlow<Boolean> = _showNonCurrentWeekCoursesFlow.asStateFlow()

    fun isQuickImportEnabled(): Boolean = preferences.getBoolean("quick_import", false)
    fun isAutoUpdateEnabled(): Boolean = preferences.getBoolean("auto_check_update", true)
    fun saveQuickImport(enabled: Boolean): Boolean = preferences.edit().putBoolean("quick_import", enabled).commit()
    fun saveAutoUpdate(enabled: Boolean): Boolean = preferences.edit().putBoolean("auto_check_update", enabled).commit()

    // 1. 课表高级视图设置
    fun isShowWeekend(): Boolean = preferences.getBoolean("show_weekend", true)
    fun saveShowWeekend(show: Boolean): Boolean {
        val ok = preferences.edit().putBoolean("show_weekend", show).commit()
        if (ok) {
            _showWeekendFlow.value = show
        }
        return ok
    }

    fun getFirstDayOfWeek(): DayOfWeek {
        val name = preferences.getString("first_day_of_week", DayOfWeek.MONDAY.name) ?: DayOfWeek.MONDAY.name
        return runCatching { DayOfWeek.valueOf(name) }.getOrDefault(DayOfWeek.MONDAY)
    }
    fun saveFirstDayOfWeek(day: DayOfWeek): Boolean {
        val ok = preferences.edit().putString("first_day_of_week", day.name).commit()
        if (ok) {
            _firstDayOfWeekFlow.value = day
        }
        return ok
    }

    // 2. 独立外观主题选择
    fun getThemeMode(): AppThemeMode {
        val name = preferences.getString("app_theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        return runCatching { AppThemeMode.valueOf(name) }.getOrDefault(AppThemeMode.SYSTEM)
    }
    fun saveThemeMode(mode: AppThemeMode): Boolean {
        val ok = preferences.edit().putString("app_theme_mode", mode.name).commit()
        if (ok) {
            _themeModeFlow.value = mode
        }
        return ok
    }

    // 3. 课前提醒设置
    fun isReminderEnabled(): Boolean = preferences.getBoolean("reminder_enabled", false)
    fun saveReminderEnabled(enabled: Boolean): Boolean {
        val ok = preferences.edit().putBoolean("reminder_enabled", enabled).commit()
        if (ok) {
            _reminderEnabledFlow.value = enabled
        }
        return ok
    }

    fun getReminderAdvanceMinutes(): Int = preferences.getInt("reminder_advance_minutes", 15)
    fun saveReminderAdvanceMinutes(minutes: Int): Boolean {
        val ok = preferences.edit().putInt("reminder_advance_minutes", minutes).commit()
        if (ok) {
            _reminderAdvanceMinutesFlow.value = minutes
        }
        return ok
    }

    // 4. 非本周课程差异化展示设置
    fun isShowNonCurrentWeekCourses(): Boolean = preferences.getBoolean("show_non_current_week_courses", false)
    fun saveShowNonCurrentWeekCourses(show: Boolean): Boolean {
        val ok = preferences.edit().putBoolean("show_non_current_week_courses", show).commit()
        if (ok) {
            _showNonCurrentWeekCoursesFlow.value = show
        }
        return ok
    }
}
