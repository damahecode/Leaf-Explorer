package com.leaf.explorer.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Environment
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat.*
import com.leaf.explorer.R

object Permissions {
    fun checkRunningConditions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        } else {
            for (permission in getAll()) {
                if (checkSelfPermission(context, permission.id) != PERMISSION_GRANTED && permission.isRequired) {
                    return false
                }
            }
        }
        return true
    }

    fun getAll(): List<Permission> {
        val permissions: MutableList<Permission> = ArrayList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            permissions.add(
                Permission(
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                    R.string.storage_permission,
                    R.string.storage_permission_summary
                )
            )
        }
        if (Build.VERSION.SDK_INT in 23..29) {
            permissions.add(
                Permission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    R.string.storage_permission,
                    R.string.storage_permission_summary
                )
            )
        }
        return permissions
    }

    data class Permission(
        val id: String,
        @StringRes val title: Int,
        @StringRes val description: Int,
        val isRequired: Boolean = true
    )
}