package com.example.imageapp

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.imageapp.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageViewModel : ViewModel() {
    private val _volumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val volumes: StateFlow<List<StorageVolumeInfo>> = _volumes

    private val _images = MutableStateFlow<List<ImageItem>>(emptyList())
    val images: StateFlow<List<ImageItem>> = _images

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _selectedStorageId = MutableStateFlow<String?>(null)
    val selectedStorageId: StateFlow<String?> = _selectedStorageId

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
    }

    fun scan(context: Context) {
        viewModelScope.launch {
            _loading.value = true
            val discoveredVolumes = StorageScanner.getStorageVolumes(context)
            _volumes.value = discoveredVolumes
            _images.value = StorageScanner.scanAllImages(context, discoveredVolumes)
            _loading.value = false
        }
    }

    fun deleteImage(context: Context, image: ImageItem): Boolean {
        return try {
            if (image.file.exists() && image.file.delete()) {
                // Update local state
                _images.value = _images.value.filter { it.path != image.path }
                
                // Notify system media scanner
                android.media.MediaScannerConnection.scanFile(
                    context, 
                    arrayOf(image.path), 
                    null, 
                    null
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun selectStorage(id: String?) {
        _selectedStorageId.value = id
    }
}
