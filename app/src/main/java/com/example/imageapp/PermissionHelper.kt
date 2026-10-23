package com.example.imageapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionHelper {
    
    fun hasManageStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun getRequiredSharingPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.toTypedArray()
    }

    fun hasSharingPermissions(context: Context): Boolean {
        return getRequiredSharingPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getManageStorageIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val appSpecificIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                if (appSpecificIntent.resolveActivity(context.packageManager) != null) {
                    return appSpecificIntent
                }
            } catch (_: Exception) {
            }

            try {
                val generalIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                if (generalIntent.resolveActivity(context.packageManager) != null) {
                    return generalIntent
                }
            } catch (_: Exception) {
            }
        }

        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
}

