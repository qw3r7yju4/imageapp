package com.example.imageapp

import android.content.Context
import android.media.MediaScannerConnection
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.imageapp.model.ImageItem
import java.io.File

@Composable
fun RenameFileDialog(
    file: File,
    onDismiss: () -> Unit,
    onRenamed: (File) -> Unit
) {
    val context = LocalContext.current
    val extension = file.extension
    var nameWithoutExt by remember { mutableStateOf(file.nameWithoutExtension) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename File", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Original: ${file.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = nameWithoutExt,
                    onValueChange = {
                        nameWithoutExt = it
                        errorText = null
                    },
                    label = { Text("New Name") },
                    suffix = { if (extension.isNotEmpty()) Text(".$extension") },
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
                    val trimmed = nameWithoutExt.trim()
                    if (trimmed.isEmpty()) {
                        errorText = "Name cannot be empty"
                        return@Button
                    }
                    val targetName = if (extension.isNotEmpty()) "$trimmed.$extension" else trimmed
                    val targetFile = File(file.parentFile, targetName)
                    if (targetFile.exists() && targetFile.absolutePath != file.absolutePath) {
                        errorText = "A file with this name already exists"
                        return@Button
                    }
                    val ok = file.renameTo(targetFile)
                    if (ok) {
                        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath, targetFile.absolutePath), null, null)
                        Toast.makeText(context, "Renamed to $targetName", Toast.LENGTH_SHORT).show()
                        onRenamed(targetFile)
                        onDismiss()
                    } else {
                        errorText = "Failed to rename file"
                    }
                }
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GpsEditSection(
    context: Context,
    imageName: String,
    imagePath: String,
    latStr: String,
    onLatChange: (String) -> Unit,
    lonStr: String,
    onLonChange: (String) -> Unit
) {
    val parsedLat = latStr.toDoubleOrNull()
    val parsedLon = lonStr.toDoubleOrNull()
    val hasValidGps = parsedLat != null && parsedLon != null

    Text("GPS Coordinates", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = latStr,
            onValueChange = onLatChange,
            label = { Text("Latitude") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = lonStr,
            onValueChange = onLonChange,
            label = { Text("Longitude") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }

    if (hasValidGps) {
        OutlinedButton(
            onClick = { ExifHelper.openLocationInMaps(context, parsedLat!!, parsedLon!!, imageName) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("📍 Go to Maps Location")
        }
    }

    if (latStr.isNotEmpty() || lonStr.isNotEmpty()) {
        TextButton(
            onClick = {
                onLatChange("")
                onLonChange("")
                ExifHelper.removeGpsLocation(imagePath)
                Toast.makeText(context, "GPS location cleared", Toast.LENGTH_SHORT).show()
            }
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Clear GPS Location")
        }
    }
}


@Composable
fun ExifEditorDialog(
    image: ImageItem,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val initialExif = remember(image.path) { ExifHelper.getExifData(context, image.path) }

    var dateTaken by remember { mutableStateOf(initialExif?.dateTaken ?: "") }
    var cameraMake by remember { mutableStateOf(initialExif?.cameraMake ?: "") }
    var cameraModel by remember { mutableStateOf(initialExif?.cameraModel ?: "") }
    var artist by remember { mutableStateOf(initialExif?.artist ?: "") }
    var description by remember { mutableStateOf(initialExif?.description ?: "") }
    var latStr by remember { mutableStateOf(initialExif?.latitude?.toString() ?: "") }
    var lonStr by remember { mutableStateOf(initialExif?.longitude?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit EXIF Metadata", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = dateTaken,
                    onValueChange = { dateTaken = it },
                    label = { Text("Date Taken (yyyy-MM-dd HH:mm:ss)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cameraMake,
                        onValueChange = { cameraMake = it },
                        label = { Text("Camera Make") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cameraModel,
                        onValueChange = { cameraModel = it },
                        label = { Text("Model") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist / Author") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Image Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                GpsEditSection(
                    context = context,
                    imageName = image.name,
                    imagePath = image.path,
                    latStr = latStr,
                    onLatChange = { latStr = it },
                    lonStr = lonStr,
                    onLonChange = { lonStr = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latStr.toDoubleOrNull()
                    val lon = lonStr.toDoubleOrNull()
                    val ok = ExifHelper.saveExifData(
                        filePath = image.path,
                        dateTaken = dateTaken.ifEmpty { null },
                        cameraMake = cameraMake.ifEmpty { null },
                        cameraModel = cameraModel.ifEmpty { null },
                        artist = artist.ifEmpty { null },
                        description = description.ifEmpty { null },
                        latitude = lat,
                        longitude = lon
                    )
                    if (ok) {
                        MediaScannerConnection.scanFile(context, arrayOf(image.path), null, null)
                        Toast.makeText(context, "EXIF updated", Toast.LENGTH_SHORT).show()
                        onSaved()
                        onDismiss()
                    } else {
                        Toast.makeText(context, "Failed to update EXIF", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("Save EXIF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

