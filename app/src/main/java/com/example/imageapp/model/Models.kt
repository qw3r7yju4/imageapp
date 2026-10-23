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

enum class MediaType {
    IMAGE,
    VIDEO,
    AUDIO,
    OTHER
}

data class FileItem(
    val file: File,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val storageId: String,
    val storageName: String,
    val storageType: StorageType,
    val mediaType: MediaType = MediaType.OTHER,
    val durationMs: Long? = null,
    val mimeType: String = "*/*"
) {
    val id: String get() = path
    val extension: String get() = file.extension.lowercase()
}

// Backwards compatibility alias & converter for existing image components
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

    fun toFileItem(): FileItem = FileItem(
        file = file,
        name = name,
        path = path,
        isDirectory = false,
        sizeBytes = sizeBytes,
        lastModified = lastModified,
        storageId = storageId,
        storageName = storageName,
        storageType = storageType,
        mediaType = MediaType.IMAGE,
        mimeType = "image/*"
    )

    companion object {
        fun fromFileItem(item: FileItem): ImageItem = ImageItem(
            file = item.file,
            name = item.name,
            path = item.path,
            folderName = item.file.parentFile?.name ?: "",
            sizeBytes = item.sizeBytes,
            lastModified = item.lastModified,
            storageId = item.storageId,
            storageName = item.storageName,
            storageType = item.storageType
        )
    }
}

enum class AppMode {
    INITIAL_PROMPT,
    MEDIA,
    ALL_FILES
}

enum class MediaCategory {
    PICTURES,
    VIDEOS,
    AUDIO,
    ALL
}

enum class ClipboardOperation {
    NONE,
    COPY,
    CUT
}

data class ClipboardState(
    val operation: ClipboardOperation = ClipboardOperation.NONE,
    val items: List<FileItem> = emptyList()
)

enum class TransferTransport {
    BLUETOOTH_ONLY,
    BLUETOOTH_AND_WIFI,
    NFC
}

enum class TransferStatus {
    IDLE,
    WAITING_FOR_NFC,
    DISCOVERING,
    CONNECTING,
    TRANSFERRING,
    UPGRADING_TO_WIFI,
    COMPLETED,
    ERROR
}

data class TransferState(
    val status: TransferStatus = TransferStatus.IDLE,
    val transport: TransferTransport = TransferTransport.BLUETOOTH_ONLY,
    val isWifiActive: Boolean = false,
    val isReceiver: Boolean = false,
    val currentFileName: String = "",
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val transferredBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val statusMessage: String = "",
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) (transferredBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}


