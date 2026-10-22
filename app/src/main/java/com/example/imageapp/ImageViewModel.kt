package com.example.imageapp

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.imageapp.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ImageViewModel : ViewModel() {
    private val _volumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val volumes: StateFlow<List<StorageVolumeInfo>> = _volumes

    private val _images = MutableStateFlow<List<ImageItem>>(emptyList())
    val images: StateFlow<List<ImageItem>> = _images

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _selectedStorageId = MutableStateFlow<String?>(null)
    val selectedStorageId: StateFlow<String?> = _selectedStorageId

    fun scan(context: Context) {
        viewModelScope.launch {
            _loading.value = true
            val discoveredVolumes = StorageScanner.getStorageVolumes(context)
            _volumes.value = discoveredVolumes
            _images.value = StorageScanner.scanAllImages(context, discoveredVolumes)
            _loading.value = false
        }
    }

    fun selectStorage(id: String?) {
        _selectedStorageId.value = id
    }
}
