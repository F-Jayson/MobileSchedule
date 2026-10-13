package com.example.mobileschedule.data.preferences

import android.content.Context

class AppPreferencesStore(context: Context, preferencesName: String = "app_preferences") {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun isQuickImportEnabled(): Boolean = preferences.getBoolean("quick_import", false)
    fun isAutoUpdateEnabled(): Boolean = preferences.getBoolean("auto_check_update", true)
    fun saveQuickImport(enabled: Boolean): Boolean = preferences.edit().putBoolean("quick_import", enabled).commit()
    fun saveAutoUpdate(enabled: Boolean): Boolean = preferences.edit().putBoolean("auto_check_update", enabled).commit()
}
