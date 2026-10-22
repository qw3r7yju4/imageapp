package com.example.imageapp

import android.content.Context
import android.os.Environment
import android.provider.Settings
import android.content.Intent
import android.os.Build

object PermissionHelper {
    
    fun hasManageStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Permissions handled differently on older versions
        }
    }

    fun getManageStorageIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        } else {
            null
        }
    }
}
