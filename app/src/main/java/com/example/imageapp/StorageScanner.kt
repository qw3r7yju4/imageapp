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

    suspend fun scanAllImages(context: Context, volumes: List<StorageVolumeInfo>): List<ImageItem> = withContext(Dispatchers.IO) {
        val images = mutableListOf<ImageItem>()
        val visited = mutableSetOf<String>()

        for (volume in volumes) {
            scanDirectory(volume.rootFile, volume, images, visited)
        }
        
        images.sortedByDescending { it.lastModified }
    }

    private fun scanDirectory(directory: File, volume: StorageVolumeInfo, images: MutableList<ImageItem>, visited: MutableSet<String>) {
        if (!directory.exists() || !directory.isDirectory) return
        
        val canonical = try { directory.canonicalPath } catch (e: Exception) { directory.path }
        if (visited.contains(canonical)) return
        visited.add(canonical)

        // Skip noisy system folders
        if (directory.name.startsWith(".") || directory.name == "Android" || directory.name == ".thumbnails") return

        val files = try { directory.listFiles() } catch (e: Exception) { null } ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanDirectory(file, volume, images, visited)
            } else {
                val extension = file.extension.lowercase()
                if (imageExtensions.contains(extension)) {
                    images.add(
                        ImageItem(
                            file = file,
                            name = file.name,
                            path = file.absolutePath,
                            folderName = directory.name,
                            sizeBytes = file.length(),
                            lastModified = file.lastModified(),
                            storageId = volume.id,
                            storageName = volume.name,
                            storageType = volume.type
                        )
                    )
                }
            }
        }
    }
}

