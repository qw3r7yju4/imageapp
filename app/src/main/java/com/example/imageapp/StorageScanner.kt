package com.example.imageapp

import android.content.Context
import android.os.Environment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImageItem(
    val file: File,
    val name: String,
    val path: String
)

object StorageScanner {
    
    private val imageExtensions = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "dng", "avif")

    suspend fun scanAllImages(context: Context): List<ImageItem> = withContext(Dispatchers.IO) {
        val images = mutableListOf<ImageItem>()
        
        // Scan internal and external storages
        val rootPaths = mutableListOf<File>()
        rootPaths.add(Environment.getExternalStorageDirectory())
        
        // Add other potential volumes (like SD cards if accessible)
        context.getExternalFilesDirs(null).forEach { file ->
            // Try to find the root of the volume
            var parent = file
            while (parent.parentFile != null && parent.parentFile!!.path.contains("/storage/")) {
                parent = parent.parentFile!!
            }
            if (!rootPaths.contains(parent)) {
                rootPaths.add(parent)
            }
        }

        for (root in rootPaths) {
            scanDirectory(root, images)
        }
        
        images
    }

    private fun scanDirectory(directory: File, images: MutableList<ImageItem>) {
        val files = directory.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".")) { // Skip hidden folders
                    scanDirectory(file, images)
                }
            } else {
                val extension = file.extension.lowercase()
                if (imageExtensions.contains(extension)) {
                    images.add(ImageItem(file, file.name, file.absolutePath))
                }
            }
        }
    }
}
