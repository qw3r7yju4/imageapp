package com.example.imageapp

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.imageapp.model.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs: SharedPreferences = application.getSharedPreferences("imageapp_prefs", Context.MODE_PRIVATE)

    private val _appMode = MutableStateFlow(AppMode.INITIAL_PROMPT)
    val appMode: StateFlow<AppMode> = _appMode

    private val _mediaCategory = MutableStateFlow(MediaCategory.PICTURES)
    val mediaCategory: StateFlow<MediaCategory> = _mediaCategory

    private val _volumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val volumes: StateFlow<List<StorageVolumeInfo>> = _volumes

    private val _mediaItems = MutableStateFlow<List<FileItem>>(emptyList())
    val mediaItems: StateFlow<List<FileItem>> = _mediaItems

    private val _images = MutableStateFlow<List<ImageItem>>(emptyList())
    val images: StateFlow<List<ImageItem>> = _images

    private val _currentDirectory = MutableStateFlow<File>(Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0"))
    val currentDirectory: StateFlow<File> = _currentDirectory

    private val _directoryFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val directoryFiles: StateFlow<List<FileItem>> = _directoryFiles

    private val _showHiddenFiles = MutableStateFlow(prefs.getBoolean("key_show_hidden", false))
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _selectedStorageId = MutableStateFlow<String?>(null)
    val selectedStorageId: StateFlow<String?> = _selectedStorageId

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("key_dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val _selectedItemPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedItemPaths: StateFlow<Set<String>> = _selectedItemPaths

    private val _clipboard = MutableStateFlow(ClipboardState())
    val clipboard: StateFlow<ClipboardState> = _clipboard

    private val _isZipping = MutableStateFlow(false)
    val isZipping: StateFlow<Boolean> = _isZipping
    private val _zipProgress = MutableStateFlow(0f)
    val zipProgress: StateFlow<Float> = _zipProgress

    fun setAppMode(mode: AppMode) {
        _appMode.value = mode
        clearSelection()
        if (mode == AppMode.ALL_FILES) {
            _currentDirectory.value = Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
        }
    }

    fun setMediaCategory(category: MediaCategory, context: Context) {
        _mediaCategory.value = category
        clearSelection()
        scan(context)
    }

    fun toggleShowHiddenFiles(context: Context) {
        val next = !_showHiddenFiles.value
        _showHiddenFiles.value = next
        prefs.edit().putBoolean("key_show_hidden", next).apply()
        refresh(context)
    }

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

            if (_appMode.value == AppMode.ALL_FILES) {
                loadDirectoryFiles(_currentDirectory.value)
            } else {
                val foundMedia = withContext(Dispatchers.IO) {
                    StorageScanner.scanMedia(
                        context,
                        discoveredVolumes,
                        _mediaCategory.value,
                        _showHiddenFiles.value
                    )
                }
                _mediaItems.value = foundMedia
                _images.value = foundMedia.filter { it.mediaType == MediaType.IMAGE }.map { ImageItem.fromFileItem(it) }
            }
            _loading.value = false
        }
    }

    fun loadDirectoryFiles(directory: File) {
        viewModelScope.launch {
            _loading.value = true
            _currentDirectory.value = directory
            val vol = _volumes.value.find { directory.canonicalPath.startsWith(it.rootFile.canonicalPath) }
            val files = withContext(Dispatchers.IO) {
                StorageScanner.listFilesInDirectory(directory, vol, _showHiddenFiles.value)
            }
            _directoryFiles.value = files
            _loading.value = false
        }
    }

    fun navigateTo(directory: File) {
        clearSelection()
        loadDirectoryFiles(directory)
    }

    fun navigateUp(): Boolean {
        val parent = _currentDirectory.value.parentFile
        return if (parent != null && parent.exists() && parent.canRead()) {
            clearSelection()
            loadDirectoryFiles(parent)
            true
        } else false
    }

    fun refresh(context: Context) {
        if (_appMode.value == AppMode.ALL_FILES) {
            loadDirectoryFiles(_currentDirectory.value)
        } else {
            scan(context)
        }
    }

    fun toggleSelection(path: String) {
        val current = _selectedItemPaths.value.toMutableSet()
        if (current.contains(path)) current.remove(path) else current.add(path)
        _selectedItemPaths.value = current
    }

    fun selectAll(items: List<FileItem>) {
        _selectedItemPaths.value = items.map { it.path }.toSet()
    }

    fun clearSelection() {
        _selectedItemPaths.value = emptySet()
    }

    fun copySelected(items: List<FileItem>) {
        _clipboard.value = ClipboardState(operation = ClipboardOperation.COPY, items = items)
        clearSelection()
    }

    fun cutSelected(items: List<FileItem>) {
        _clipboard.value = ClipboardState(operation = ClipboardOperation.CUT, items = items)
        clearSelection()
    }

    suspend fun paste(destinationDir: File, context: Context): Boolean = withContext(Dispatchers.IO) {
        val clip = _clipboard.value
        if (clip.items.isEmpty() || clip.operation == ClipboardOperation.NONE) return@withContext false

        var anySuccess = false
        for (item in clip.items) {
            try {
                val target = File(destinationDir, item.name)
                if (clip.operation == ClipboardOperation.COPY) {
                    if (item.file.isDirectory) item.file.copyRecursively(target, overwrite = true)
                    else item.file.copyTo(target, overwrite = true)
                } else if (clip.operation == ClipboardOperation.CUT) {
                    item.file.renameTo(target)
                }
                anySuccess = true
                MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (clip.operation == ClipboardOperation.CUT) {
            _clipboard.value = ClipboardState()
        }
        withContext(Dispatchers.Main) {
            refresh(context)
        }
        anySuccess
    }

    suspend fun moveTo(items: List<FileItem>, destinationDir: File, context: Context): Boolean = withContext(Dispatchers.IO) {
        var anySuccess = false
        for (item in items) {
            try {
                val target = File(destinationDir, item.name)
                val moved = item.file.renameTo(target)
                if (moved) {
                    anySuccess = true
                    MediaScannerConnection.scanFile(context, arrayOf(item.path, target.absolutePath), null, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        withContext(Dispatchers.Main) {
            clearSelection()
            refresh(context)
        }
        anySuccess
    }

    suspend fun deleteSelected(items: List<FileItem>, context: Context): Boolean = withContext(Dispatchers.IO) {
        var anySuccess = false
        val scannedPaths = mutableListOf<String>()
        for (item in items) {
            try {
                val deleted = if (item.file.isDirectory) item.file.deleteRecursively() else item.file.delete()
                if (deleted) {
                    anySuccess = true
                    scannedPaths.add(item.path)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (scannedPaths.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, scannedPaths.toTypedArray(), null, null)
        }
        withContext(Dispatchers.Main) {
            clearSelection()
            refresh(context)
        }
        anySuccess
    }

    suspend fun deleteImage(context: Context, image: ImageItem): Boolean {
        return deleteSelected(listOf(image.toFileItem()), context)
    }

    fun zipSelected(
        items: List<FileItem>,
        destinationFolder: File,
        zipFileName: String,
        context: Context,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _isZipping.value = true
            _zipProgress.value = 0f
            val destZip = File(destinationFolder, zipFileName)
            val success = withContext(Dispatchers.IO) {
                ZipManager.zipFiles(
                    sourceFiles = items.map { it.file },
                    destZipFile = destZip,
                    onProgress = { _, pct ->
                        _zipProgress.value = pct
                    }
                )
            }
            if (success) {
                MediaScannerConnection.scanFile(context, arrayOf(destZip.absolutePath), null, null)
            }
            _isZipping.value = false
            clearSelection()
            refresh(context)
            onComplete(success)
        }
    }

    fun selectStorage(id: String?) {
        _selectedStorageId.value = id
    }
}


