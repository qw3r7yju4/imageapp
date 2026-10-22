package com.example.imageapp

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.imageapp.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs: SharedPreferences = application.getSharedPreferences("imageapp_prefs", Context.MODE_PRIVATE)

    private val _volumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val volumes: StateFlow<List<StorageVolumeInfo>> = _volumes

    private val _images = MutableStateFlow<List<ImageItem>>(emptyList())
    val images: StateFlow<List<ImageItem>> = _images

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _selectedStorageId = MutableStateFlow<String?>(null)
    val selectedStorageId: StateFlow<String?> = _selectedStorageId

    // Default to Dark Mode = true as requested
    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("key_dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        prefs.edit().putBoolean("key_dark_mode", enabled).apply()
    }

    fun scan(context: Context) {
        viewModelScope.launch {
            _loading.value = true
            val discoveredVolumes = withContext(Dispatchers.IO) {
                StorageScanner.getStorageVolumes(context)
            }
            _volumes.value = discoveredVolumes
            val foundImages = withContext(Dispatchers.IO) {
                StorageScanner.scanAllImages(context, discoveredVolumes)
            }
            _images.value = foundImages
            _loading.value = false
        }
    }

    suspend fun deleteImage(context: Context, image: ImageItem): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val deleted = if (image.file.exists()) {
                    image.file.delete()
                } else false

                if (deleted) {
                    _images.value = _images.value.filter { it.path != image.path }
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(image.path),
                        null,
                        null
                    )
                }
                deleted
            } catch (e: Exception) {
                false
            }
        }
    }

    fun selectStorage(id: String?) {
        _selectedStorageId.value = id
    }
}

