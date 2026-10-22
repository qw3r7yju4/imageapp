package com.example.imageapp.model

import java.io.File

enum class StorageType {
    INTERNAL_SHARED,
    INTERNAL_APP_PRIVATE,
    EXTERNAL_ATTACHED
}

data class StorageVolumeInfo(
    val id: String, // Canonical Path
    val name: String,
    val rootFile: File,
    val type: StorageType,
    val totalBytes: Long = 0,
    val freeBytes: Long = 0
)

data class ImageItem(
    val file: File,
    val name: String,
    val path: String,
    val folderName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val storageId: String,
    val storageName: String,
    val storageType: StorageType
) {
    val id: String get() = path
}

