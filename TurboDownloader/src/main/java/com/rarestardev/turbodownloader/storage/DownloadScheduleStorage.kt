package com.rarestardev.turbodownloader.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

internal class DownloadScheduleStorage(
    context: Context
) : ScheduleStore by PreferenceScheduleStore(
    SharedSchedulePrefs(
        context.applicationContext.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
    )
) {
    companion object {
        const val PREF_NAME = "turbo_download_scheduler"
    }
}

internal class SharedSchedulePrefs(
    private val preferences: SharedPreferences
) : SchedulePrefs {
    override fun getLong(key: String, defaultValue: Long): Long {
        return preferences.getLong(key, defaultValue)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return preferences.getBoolean(key, defaultValue)
    }

    override fun getString(key: String): String? {
        return preferences.getString(key, null)
    }

    override fun edit(transform: SchedulePrefEditor.() -> Unit) {
        preferences.edit {
            val editor = this
            object : SchedulePrefEditor {
                override fun putLong(key: String, value: Long) {
                    editor.putLong(key, value)
                }

                override fun putBoolean(key: String, value: Boolean) {
                    editor.putBoolean(key, value)
                }

                override fun putString(key: String, value: String?) {
                    editor.putString(key, value)
                }

                override fun remove(key: String) {
                    editor.remove(key)
                }
            }.transform()
        }
    }
}
