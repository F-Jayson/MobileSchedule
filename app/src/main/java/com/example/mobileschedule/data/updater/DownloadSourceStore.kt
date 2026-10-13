package com.example.mobileschedule.data.updater

import android.content.Context
import com.example.mobileschedule.data.model.DownloadMirror

/**
 * Persists the user's preferred update download mirror source using SharedPreferences.
 */
class DownloadSourceStore(
    context: Context,
    preferencesName: String = "app_update_preferences",
) {
    private val prefs = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun getSelectedMirror(): DownloadMirror {
        val key = prefs.getString(KEY_MIRROR, DownloadMirror.DEFAULT.key)
        return DownloadMirror.fromKey(key)
    }

    fun getCustomPrefix(): String {
        return prefs.getString(KEY_CUSTOM_PREFIX, "") ?: ""
    }

    fun getBlogOrigin(): String {
        return prefs.getString(KEY_BLOG_ORIGIN, DownloadMirror.DEFAULT_BLOG_ORIGIN) ?: DownloadMirror.DEFAULT_BLOG_ORIGIN
    }

    fun saveSelectedMirror(
        mirror: DownloadMirror,
        customPrefix: String = getCustomPrefix(),
        blogOrigin: String = getBlogOrigin(),
    ): Boolean {
        return prefs.edit()
            .putString(KEY_MIRROR, mirror.key)
            .putString(KEY_CUSTOM_PREFIX, customPrefix)
            .putString(KEY_BLOG_ORIGIN, blogOrigin)
            .commit()
    }

    companion object {
        private const val KEY_MIRROR = "update_download_mirror"
        private const val KEY_CUSTOM_PREFIX = "update_custom_prefix"
        private const val KEY_BLOG_ORIGIN = "update_blog_origin"
    }
}
