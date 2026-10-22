package com.example.imageapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.imageapp.model.ImageItem
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ImageViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

            MaterialTheme(
                colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()
            ) {
                MainScreen(viewModel)
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: ImageViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(PermissionHelper.hasManageStoragePermission())
    }
    val volumes by viewModel.volumes.collectAsStateWithLifecycle()
    val allImages by viewModel.images.collectAsStateWithLifecycle()
    val selectedStorageId by viewModel.selectedStorageId.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    var showMainMenu by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedImageForViewer by remember { mutableStateOf<ImageItem?>(null) }
    var exifTargetImage by remember { mutableStateOf<ImageItem?>(null) }
    var deleteTargetImage by remember { mutableStateOf<ImageItem?>(null) }
    var contextMenuImage by remember { mutableStateOf<ImageItem?>(null) }

    val filteredImages = remember(allImages, selectedStorageId) {
        if (selectedStorageId == null) allImages
        else allImages.filter { it.storageId == selectedStorageId }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = PermissionHelper.hasManageStoragePermission()
        hasPermission = granted
        if (granted) {
            viewModel.scan(context)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = PermissionHelper.hasManageStoragePermission()
                if (granted != hasPermission) {
                    hasPermission = granted
                }
                if (granted && volumes.isEmpty()) {
                    viewModel.scan(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && volumes.isEmpty()) {
            viewModel.scan(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ImageApp",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (hasPermission) {
                        IconButton(onClick = { viewModel.scan(context) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showMainMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMainMenu,
                            onDismissRequest = { showMainMenu = false }
                        ) {
                            if (hasPermission) {
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    onClick = {
                                        showMainMenu = false
                                        viewModel.scan(context)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showMainMenu = false
                                    showSettingsDialog = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!hasPermission) {
                PermissionRequestScreen(
                    onGrantClick = {
                        val intent = PermissionHelper.getManageStorageIntent(context)
                        permissionLauncher.launch(intent)
                    }
                )
            } else {
                if (volumes.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedStorageId == null,
                                onClick = { viewModel.selectStorage(null) },
                                label = { Text("All (${allImages.size})") }
                            )
                        }
                        items(volumes) { volume ->
                            val count = allImages.count { it.storageId == volume.id }
                            FilterChip(
                                selected = selectedStorageId == volume.id,
                                onClick = { viewModel.selectStorage(volume.id) },
                                label = { Text("${volume.name} ($count)") }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        loading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Scanning storage for images...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        filteredImages.isEmpty() -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Text(
                                    text = "No images found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No compatible image files were detected in this storage location.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.scan(context) }) {
                                    Text("Scan Again")
                                }
                            }
                        }
                        else -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredImages, key = { it.path }) { image ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(4.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    selectedImageForViewer = image
                                                },
                                                onLongClick = {
                                                    contextMenuImage = image
                                                }
                                            )
                                    ) {
                                        AsyncImage(
                                            model = image.file,
                                            contentDescription = image.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Long press context menu for selected grid photo
    if (contextMenuImage != null) {
        val target = contextMenuImage!!
        AlertDialog(
            onDismissRequest = { contextMenuImage = null },
            title = {
                Text(
                    text = target.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            contextMenuImage = null
                            exifTargetImage = target
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text("Read EXIF Data", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    TextButton(
                        onClick = {
                            val imgToShare = target
                            contextMenuImage = null
                            shareImageFile(context, imgToShare)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text("Share", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    TextButton(
                        onClick = {
                            contextMenuImage = null
                            deleteTargetImage = target
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Delete",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { contextMenuImage = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full screen photo viewer
    if (selectedImageForViewer != null) {
        FullScreenPhotoViewer(
            image = selectedImageForViewer!!,
            onClose = { selectedImageForViewer = null },
            onReadExif = { img -> exifTargetImage = img },
            onDelete = { img -> deleteTargetImage = img },
            onOpenSettings = { showSettingsDialog = true }
        )
    }

    // EXIF dialog
    if (exifTargetImage != null) {
        ExifDetailsDialog(
            context = context,
            image = exifTargetImage!!,
            onDismiss = { exifTargetImage = null }
        )
    }

    // Delete confirmation dialog
    if (deleteTargetImage != null) {
        val toDelete = deleteTargetImage!!
        AlertDialog(
            onDismissRequest = { deleteTargetImage = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Image") },
            text = { Text("Are you sure you want to permanently delete \"${toDelete.name}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTargetImage = null
                        if (selectedImageForViewer?.path == toDelete.path) {
                            selectedImageForViewer = null
                        }
                        coroutineScope.launch {
                            val success = viewModel.deleteImage(context, toDelete)
                            if (success) {
                                Toast.makeText(context, "Image deleted", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Could not delete image", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetImage = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Settings dialog (Settings option with Dark Mode default toggle)
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }
}



@Composable
fun PermissionRequestScreen(
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Storage Access Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "ImageApp needs All Files Access to discover and display images across internal storage and connected USB/SD drives.\n\nPlease tap below to enable storage access in system settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onGrantClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant File Access")
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPhotoViewer(
    image: ImageItem,
    onClose: () -> Unit,
    onReadExif: (ImageItem) -> Unit,
    onDelete: (ImageItem) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var showOverflowMenu by remember { mutableStateOf(false) }

    BackHandler {
        onClose()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = image.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.65f),
                        navigationIconContentColor = Color.White,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { shareImageFile(context, image) }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share"
                            )
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options"
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Read EXIF Data") },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onReadExif(image)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onDelete(image)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onOpenSettings()
                                    }
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = image.file,
                    contentDescription = image.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}


@Composable
fun SettingsDialog(
    viewModel: ImageViewModel,
    onDismiss: () -> Unit
) {
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dark Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Enabled by default",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { checked ->
                            viewModel.setDarkMode(checked)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun ExifDetailsDialog(
    context: Context,
    image: ImageItem,
    onDismiss: () -> Unit
) {
    val exifData = remember(image) {
        ExifHelper.getExifData(context, image.path)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "EXIF Data",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExifItemRow(label = "File Name", value = exifData?.fileName ?: image.name)
                ExifItemRow(label = "File Size", value = exifData?.formattedSize ?: "${image.sizeBytes / 1024} KB")
                ExifItemRow(label = "Dimensions", value = exifData?.dimensions ?: "Unknown")
                ExifItemRow(label = "Date Taken", value = exifData?.dateTaken ?: "Unknown")
                if (!exifData?.camera.isNullOrBlank()) {
                    ExifItemRow(label = "Camera", value = exifData!!.camera!!)
                }
                if (!exifData?.aperture.isNullOrBlank()) {
                    ExifItemRow(label = "Aperture", value = exifData!!.aperture!!)
                }
                if (!exifData?.exposureTime.isNullOrBlank()) {
                    ExifItemRow(label = "Exposure Time", value = exifData!!.exposureTime!!)
                }
                if (!exifData?.iso.isNullOrBlank()) {
                    ExifItemRow(label = "ISO", value = exifData!!.iso!!)
                }
                if (!exifData?.focalLength.isNullOrBlank()) {
                    ExifItemRow(label = "Focal Length", value = exifData!!.focalLength!!)
                }
                if (!exifData?.flash.isNullOrBlank()) {
                    ExifItemRow(label = "Flash", value = exifData!!.flash!!)
                }
                if (!exifData?.gps.isNullOrBlank()) {
                    ExifItemRow(label = "GPS Coordinates", value = exifData!!.gps!!)
                }
                ExifItemRow(label = "Storage", value = image.storageName)
                ExifItemRow(label = "Full Path", value = image.path)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ExifItemRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

fun shareImageFile(context: Context, image: ImageItem) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            image.file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share image: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}


