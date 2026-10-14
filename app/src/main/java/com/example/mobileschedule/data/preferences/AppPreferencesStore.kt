package com.example.mobileschedule.data.preferences

import android.content.Context
import java.time.DayOfWeek
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
        @Volatile private var isInitialized = false
    }

    init {
        if (!isInitialized) {
            _themeModeFlow.value = getThemeMode()
            _showWeekendFlow.value = isShowWeekend()
            _firstDayOfWeekFlow.value = getFirstDayOfWeek()
            isInitialized = true
        }
    }

    val themeModeFlow: StateFlow<AppThemeMode> = _themeModeFlow.asStateFlow()
    val showWeekendFlow: StateFlow<Boolean> = _showWeekendFlow.asStateFlow()
    val firstDayOfWeekFlow: StateFlow<DayOfWeek> = _firstDayOfWeekFlow.asStateFlow()

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
}
