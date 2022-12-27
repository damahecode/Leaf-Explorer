package com.leaf.explorer.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.leaf.explorer.app.FileExplorer
import com.leaf.explorer.config.AppConfig.SAF_SDCARD_URI

object PreferenceUtil {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(FileExplorer.getContext())
    fun getPreferences(context: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun editBooleanPreferences(context: Context, keyword: String, value: Boolean) = getPreferences(context).edit {
        putBoolean(keyword, value)
    }

    fun setStringPreferences(keyword: String, value: String) = sharedPreferences.edit {
        putString(keyword, value)
    }

    fun getSafUriString(keyword: String) = sharedPreferences.getString(keyword, "")
}