package com.example.imageapp

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.imageapp.model.StorageVolumeInfo
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ZipManager {
    suspend fun zipFiles(
        sourceFiles: List<File>,
        destZipFile: File,
        onProgress: (fileName: String, percent: Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            destZipFile.parentFile?.mkdirs()
            val totalCount = sourceFiles.size.coerceAtLeast(1)
            var processed = 0

            FileOutputStream(destZipFile).use { fos ->
                BufferedOutputStream(fos).use { bos ->
                    ZipOutputStream(bos).use { zos ->
                        for (file in sourceFiles) {
                            if (!file.exists()) continue
                            addEntry(file, file.name, zos)
                            processed++
                            onProgress(file.name, processed.toFloat() / totalCount)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun addEntry(file: File, entryPath: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val dirEntry = if (entryPath.endsWith("/")) entryPath else "$entryPath/"
            zos.putNextEntry(ZipEntry(dirEntry))
            zos.closeEntry()
            file.listFiles()?.forEach { child ->
                addEntry(child, "$dirEntry${child.name}", zos)
            }
        } else {
            zos.putNextEntry(ZipEntry(entryPath))
            FileInputStream(file).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (bis.read(buffer).also { read = it } != -1) {
                        zos.write(buffer, 0, read)
                    }
                }
            }
            zos.closeEntry()
        }
    }
}


@Composable
fun StorageFolderBrowserDialog(
    volumes: List<StorageVolumeInfo>,
    initialDirectory: File?,
    title: String = "Select Storage Location",
    confirmButtonText: String = "Select Folder",
    showZipNameField: Boolean = false,
    defaultZipName: String = "archive_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.zip",
    onDismiss: () -> Unit,
    onConfirm: (selectedFolder: File, zipName: String?) -> Unit
) {
    val defaultDir = remember(volumes, initialDirectory) {
        initialDirectory ?: volumes.firstOrNull()?.rootFile ?: Environment.getExternalStorageDirectory()
    }
    var currentDir by remember { mutableStateOf(defaultDir) }
    var zipFileName by remember { mutableStateOf(defaultZipName) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    val subFolders = remember(currentDir) {
        try {
            currentDir.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase(Locale.getDefault()) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(text = "Browse anywhere in storage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (volumes.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        items(volumes) { vol ->
                            FilterChip(
                                selected = currentDir.absolutePath.startsWith(vol.rootFile.absolutePath),
                                onClick = { currentDir = vol.rootFile },
                                label = { Text(vol.name) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val parent = currentDir.parentFile
                                if (parent != null && parent.exists() && parent.canRead()) currentDir = parent
                            },
                            enabled = currentDir.parentFile != null && currentDir.parentFile?.canRead() == true,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up a folder")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = currentDir.absolutePath, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                FolderListAndBottomSection(
                    subFolders = subFolders,
                    showZipNameField = showZipNameField,
                    zipFileName = zipFileName,
                    confirmButtonText = confirmButtonText,
                    onFolderClick = { currentDir = it },
                    onZipNameChange = { zipFileName = it },
                    onDismiss = onDismiss,
                    onConfirm = {
                        val finalZip = if (showZipNameField) {
                            val trimmed = zipFileName.trim()
                            if (trimmed.endsWith(".zip", ignoreCase = true)) trimmed else "$trimmed.zip"
                        } else null
                        onConfirm(currentDir, finalZip)
                    }
                )
            }
        }
    }
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            parentDirectory = currentDir,
            onDismiss = { showCreateFolderDialog = false },
            onFolderCreated = { newDir ->
                showCreateFolderDialog = false
                currentDir = newDir
            }
        )
    }
}

@Composable
fun ColumnScope.FolderListAndBottomSection(
    subFolders: List<File>,
    showZipNameField: Boolean,
    zipFileName: String,
    confirmButtonText: String,
    onFolderClick: (File) -> Unit,
    onZipNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Text(
        text = "Subfolders (${subFolders.size}):",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        if (subFolders.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No subfolders in this directory",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(subFolders) { folder ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = folder.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFolderClick(folder) }
                )
            }
        }
    }

    if (showZipNameField) {
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = zipFileName,
            onValueChange = onZipNameChange,
            label = { Text("Zip Archive Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(modifier = Modifier.height(14.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onDismiss) {
            Text("Cancel")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onConfirm) {
            Text(confirmButtonText)
        }
    }
}

@Composable
fun CreateFolderDialog(
    parentDirectory: File,
    onDismiss: () -> Unit,
    onFolderCreated: (File) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder") },
        text = {
            Column {
                Text(
                    text = "Create inside: ${parentDirectory.name.ifEmpty { parentDirectory.path }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = folderName,
                    onValueChange = {
                        folderName = it
                        errorText = null
                    },
                    label = { Text("Folder Name") },
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val name = folderName.trim()
                    if (name.isEmpty()) {
                        errorText = "Folder name cannot be empty"
                        return@Button
                    }
                    val target = File(parentDirectory, name)
                    if (target.exists()) {
                        errorText = "Folder or file already exists"
                        return@Button
                    }
                    if (target.mkdirs() || target.exists()) {
                        onFolderCreated(target)
                    } else {
                        errorText = "Failed to create folder"
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

