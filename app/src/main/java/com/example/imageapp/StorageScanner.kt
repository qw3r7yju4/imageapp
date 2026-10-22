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
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

        // 1. Primary Internal Shared Storage
        val primaryRoot = Environment.getExternalStorageDirectory()
        volumes.add(StorageVolumeInfo(
            primaryRoot.canonicalPath, "Internal Storage", primaryRoot, StorageType.INTERNAL_SHARED
        ))

        // 2. App-Private Internal Storage
        val appInternal = context.filesDir
        volumes.add(StorageVolumeInfo(
            appInternal.canonicalPath, "App Private", appInternal, StorageType.INTERNAL_APP_PRIVATE
        ))

        // 3. Attached Storage (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            storageManager.storageVolumes.forEach { volume ->
                val root = volume.directory
                if (root != null && root.path != primaryRoot.path) {
                    volumes.add(StorageVolumeInfo(
                        root.canonicalPath, 
                        volume.getDescription(context) ?: "External Storage", 
                        root, 
                        StorageType.EXTERNAL_ATTACHED
                    ))
                }
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

        val files = directory.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanDirectory(file, volume, images, visited)
            } else {
                val extension = file.extension.lowercase()
                if (imageExtensions.contains(extension)) {
                    images.add(ImageItem(
                        file = file,
                        name = file.name,
                        path = file.absolutePath,
                        folderName = directory.name,
                        sizeBytes = file.length(),
                        lastModified = file.lastModified(),
                        storageId = volume.id,
                        storageName = volume.name,
                        storageType = volume.type
                    ))
                }
            }
        }
    }
}
