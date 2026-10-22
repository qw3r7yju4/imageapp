package com.example.imageapp

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ImageViewModel : ViewModel() {
    private val _images = MutableStateFlow<List<ImageItem>>(emptyList())
    val images: StateFlow<List<ImageItem>> = _images

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun scanImages(context: Context) {
        viewModelScope.launch {
            _loading.value = true
            _images.value = StorageScanner.scanAllImages(context)
            _loading.value = false
        }
    }
}
