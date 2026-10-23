package com.example.imageapp

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import com.example.imageapp.model.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StorageScanner {
    
    private val imageExtensions = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "dng", "avif")
    val videoExtensions = listOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v", "ts")
    val audioExtensions = listOf("mp3", "wav", "flac", "aac", "m4a", "ogg", "opus", "wma")

    fun getStorageVolumes(context: Context): List<StorageVolumeInfo> {
        val volumes = mutableListOf<StorageVolumeInfo>()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return volumes

        // 1. Primary Internal Shared Storage
        try {
            val primaryRoot = Environment.getExternalStorageDirectory()
            if (primaryRoot != null && primaryRoot.exists()) {
                volumes.add(
                    StorageVolumeInfo(
                        id = primaryRoot.canonicalPath,
                        name = "Internal Storage",
                        rootFile = primaryRoot,
                        type = StorageType.INTERNAL_SHARED
                    )
                )
            }
        } catch (_: Exception) {
        }

        // 2. App-Private Internal Storage
        try {
            val appInternal = context.filesDir
            if (appInternal != null && appInternal.exists()) {
                volumes.add(
                    StorageVolumeInfo(
                        id = appInternal.canonicalPath,
                        name = "App Private",
                        rootFile = appInternal,
                        type = StorageType.INTERNAL_APP_PRIVATE
                    )
                )
            }
        } catch (_: Exception) {
        }

        // 3. Attached Storage (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val primaryPath = try { Environment.getExternalStorageDirectory()?.canonicalPath } catch (_: Exception) { null }
                storageManager.storageVolumes.forEach { volume ->
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val root = volume.directory
                            if (root != null && (primaryPath == null || root.canonicalPath != primaryPath)) {
                                volumes.add(
                                    StorageVolumeInfo(
                                        id = root.canonicalPath,
                                        name = volume.getDescription(context) ?: "External Storage",
                                        rootFile = root,
                                        type = StorageType.EXTERNAL_ATTACHED
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }
        
        return volumes
    }

    suspend fun scanMedia(
        context: Context,
        volumes: List<StorageVolumeInfo>,
        category: MediaCategory,
        showHiddenFiles: Boolean = false
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val mediaItems = mutableListOf<FileItem>()
        val visited = mutableSetOf<String>()

        for (volume in volumes) {
            scanDirectoryForMedia(volume.rootFile, volume, category, showHiddenFiles, mediaItems, visited)
        }
        mediaItems.sortedByDescending { it.lastModified }
    }

    suspend fun scanAllImages(context: Context, volumes: List<StorageVolumeInfo>): List<ImageItem> = withContext(Dispatchers.IO) {
        val media = scanMedia(context, volumes, MediaCategory.PICTURES, false)
        media.map { ImageItem.fromFileItem(it) }
    }

    private fun scanDirectoryForMedia(
        directory: File,
        volume: StorageVolumeInfo,
        category: MediaCategory,
        showHiddenFiles: Boolean,
        results: MutableList<FileItem>,
        visited: MutableSet<String>
    ) {
        if (!directory.exists() || !directory.isDirectory) return

        val canonical = try { directory.canonicalPath } catch (e: Exception) { directory.path }
        if (visited.contains(canonical)) return
        visited.add(canonical)

        if (!showHiddenFiles && (directory.name.startsWith(".") || directory.name == "Android" || directory.name == ".thumbnails")) {
            return
        }

        val files = try { directory.listFiles() } catch (e: Exception) { null } ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanDirectoryForMedia(file, volume, category, showHiddenFiles, results, visited)
            } else {
                if (!showHiddenFiles && file.name.startsWith(".")) continue
                val ext = file.extension.lowercase()
                val mediaType = when {
                    imageExtensions.contains(ext) -> MediaType.IMAGE
                    videoExtensions.contains(ext) -> MediaType.VIDEO
                    audioExtensions.contains(ext) -> MediaType.AUDIO
                    else -> MediaType.OTHER
                }

                val matches = when (category) {
                    MediaCategory.PICTURES -> mediaType == MediaType.IMAGE
                    MediaCategory.VIDEOS -> mediaType == MediaType.VIDEO
                    MediaCategory.AUDIO -> mediaType == MediaType.AUDIO
                    MediaCategory.ALL -> mediaType != MediaType.OTHER
                }

                if (matches) {
                    results.add(
                        FileItem(
                            file = file,
                            name = file.name,
                            path = file.absolutePath,
                            isDirectory = false,
                            sizeBytes = file.length(),
                            lastModified = file.lastModified(),
                            storageId = volume.id,
                            storageName = volume.name,
                            storageType = volume.type,
                            mediaType = mediaType,
                            mimeType = getMimeType(file, mediaType)
                        )
                    )
                }
            }
        }
    }

    suspend fun listFilesInDirectory(
        directory: File,
        volumeInfo: StorageVolumeInfo?,
        showHiddenFiles: Boolean
    ): List<FileItem> = withContext(Dispatchers.IO) {
        if (!directory.exists() || !directory.isDirectory) return@withContext emptyList()

        val files = try { directory.listFiles() } catch (e: Exception) { null } ?: return@withContext emptyList()
        val defaultStorageId = volumeInfo?.id ?: directory.absolutePath
        val defaultStorageName = volumeInfo?.name ?: "Storage"
        val defaultStorageType = volumeInfo?.type ?: StorageType.INTERNAL_SHARED

        val items = files
            .filter { showHiddenFiles || !it.name.startsWith(".") }
            .map { file ->
                val ext = file.extension.lowercase()
                val mediaType = when {
                    file.isDirectory -> MediaType.OTHER
                    imageExtensions.contains(ext) -> MediaType.IMAGE
                    videoExtensions.contains(ext) -> MediaType.VIDEO
                    audioExtensions.contains(ext) -> MediaType.AUDIO
                    else -> MediaType.OTHER
                }
                FileItem(
                    file = file,
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isDirectory) 0L else file.length(),
                    lastModified = file.lastModified(),
                    storageId = defaultStorageId,
                    storageName = defaultStorageName,
                    storageType = defaultStorageType,
                    mediaType = mediaType,
                    mimeType = getMimeType(file, mediaType)
                )
            }

        items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(java.util.Locale.getDefault()) }))
    }

    fun getMimeType(file: File, mediaType: MediaType): String {
        val ext = file.extension.lowercase()
        return when (mediaType) {
            MediaType.IMAGE -> when (ext) {
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                "heic", "heif" -> "image/heic"
                else -> "image/jpeg"
            }
            MediaType.VIDEO -> when (ext) {
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "3gp" -> "video/3gpp"
                else -> "video/*"
            }
            MediaType.AUDIO -> when (ext) {
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "flac" -> "audio/flac"
                "ogg", "opus" -> "audio/ogg"
                "aac" -> "audio/aac"
                "m4a" -> "audio/mp4"
                else -> "audio/*"
            }
            MediaType.OTHER -> when (ext) {
                "zip" -> "application/zip"
                "pdf" -> "application/pdf"
                "txt" -> "text/plain"
                "apk" -> "application/vnd.android.package-archive"
                else -> "*/*"
            }
        }
    }
    fun formatDuration(durationMs: Long): String {
        val totalSec = durationMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return if (min >= 60) {
            val hours = min / 60
            val remMin = min % 60
            String.format(java.util.Locale.US, "%d:%02d:%02d", hours, remMin, sec)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", min, sec)
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }

}

