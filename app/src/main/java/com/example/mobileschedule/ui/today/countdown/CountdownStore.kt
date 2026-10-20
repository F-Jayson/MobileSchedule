package com.example.mobileschedule.ui.today.countdown

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class CountdownStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences("countdown_preferences", Context.MODE_PRIVATE)

    private val _customCountdowns = MutableStateFlow<List<CustomCountdown>>(emptyList())
    val customCountdowns: StateFlow<List<CustomCountdown>> = _customCountdowns.asStateFlow()

    private val _pinnedId = MutableStateFlow<String?>(null)
    val pinnedId: StateFlow<String?> = _pinnedId.asStateFlow()

    init {
        _customCountdowns.value = loadCustomCountdowns()
        _pinnedId.value = prefs.getString(KEY_PINNED_ID, null)
    }

    fun addCustomCountdown(title: String, targetDate: LocalDate, note: String? = null): CustomCountdown {
        val item = CustomCountdown(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            targetDate = targetDate,
            note = note?.trim()?.takeIf { it.isNotBlank() },
        )
        val updated = _customCountdowns.value + item
        saveCustomCountdowns(updated)
        return item
    }

    fun deleteCustomCountdown(id: String) {
        val updated = _customCountdowns.value.filterNot { it.id == id }
        saveCustomCountdowns(updated)
        if (_pinnedId.value == id) {
            setPinnedId(null)
        }
    }

    fun setPinnedId(id: String?) {
        val normalized = id?.takeIf { it.isNotBlank() }
        prefs.edit().putString(KEY_PINNED_ID, normalized).apply()
        _pinnedId.value = normalized
    }

    private fun loadCustomCountdowns(): List<CustomCountdown> {
        val rawJson = prefs.getString(KEY_CUSTOM_LIST, null) ?: return emptyList()
        return try {
            val array = JSONArray(rawJson)
            val list = mutableListOf<CustomCountdown>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val epochDay = obj.getLong("epochDay")
                val note = obj.optString("note").takeIf { it.isNotBlank() }
                list.add(
                    CustomCountdown(
                        id = id,
                        title = title,
                        targetDate = LocalDate.ofEpochDay(epochDay),
                        note = note,
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCustomCountdowns(list: List<CustomCountdown>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("epochDay", item.targetDate.toEpochDay())
                if (item.note != null) put("note", item.note)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_LIST, array.toString()).apply()
        _customCountdowns.value = list
    }

    companion object {
        private const val KEY_CUSTOM_LIST = "custom_countdowns_json"
        private const val KEY_PINNED_ID = "pinned_countdown_id"
    }
}
