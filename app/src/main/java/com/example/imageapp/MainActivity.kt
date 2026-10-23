package com.example.imageapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.imageapp.model.*
import java.io.File
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

    var hasPermission by remember { mutableStateOf(PermissionHelper.hasManageStoragePermission()) }
    val appMode by viewModel.appMode.collectAsStateWithLifecycle()
    val mediaCategory by viewModel.mediaCategory.collectAsStateWithLifecycle()
    val volumes by viewModel.volumes.collectAsStateWithLifecycle()
    val mediaItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    val currentDirectory by viewModel.currentDirectory.collectAsStateWithLifecycle()
    val directoryFiles by viewModel.directoryFiles.collectAsStateWithLifecycle()
    val showHiddenFiles by viewModel.showHiddenFiles.collectAsStateWithLifecycle()
    val selectedStorageId by viewModel.selectedStorageId.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val selectedItemPaths by viewModel.selectedItemPaths.collectAsStateWithLifecycle()
    val clipboard by viewModel.clipboard.collectAsStateWithLifecycle()
    val isZipping by viewModel.isZipping.collectAsStateWithLifecycle()
    val zipProgress by viewModel.zipProgress.collectAsStateWithLifecycle()
    val transferState by P2PTransferManager.transferState.collectAsStateWithLifecycle()

    var showMainMenu by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showZipDestinationDialog by remember { mutableStateOf(false) }
    var showMoveToDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var selectedImageForViewer by remember { mutableStateOf<ImageItem?>(null) }
    var selectedVideoForPlayer by remember { mutableStateOf<FileItem?>(null) }
    var selectedAudioForPlayer by remember { mutableStateOf<FileItem?>(null) }
    var exifTargetImage by remember { mutableStateOf<ImageItem?>(null) }
    var contextMenuFile by remember { mutableStateOf<FileItem?>(null) }

    // Resolve currently selected FileItem objects
    val currentVisibleItems = remember(appMode, mediaItems, directoryFiles, selectedStorageId) {
        if (appMode == AppMode.ALL_FILES) directoryFiles
        else {
            if (selectedStorageId == null) mediaItems
            else mediaItems.filter { it.storageId == selectedStorageId }
        }
    }

    val selectedFileItems = remember(selectedItemPaths, currentVisibleItems) {
        currentVisibleItems.filter { selectedItemPaths.contains(it.path) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = PermissionHelper.hasManageStoragePermission()
        hasPermission = granted
        if (granted) viewModel.scan(context)
    }

    val sharingPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Callback after sharing permissions requested
    }

    fun requestSharingPermissionsIfNeeded() {
        if (!PermissionHelper.hasSharingPermissions(context)) {
            sharingPermissionLauncher.launch(PermissionHelper.getRequiredSharingPermissions())
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = PermissionHelper.hasManageStoragePermission()
                if (granted != hasPermission) hasPermission = granted
                if (granted && volumes.isEmpty()) viewModel.scan(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && volumes.isEmpty()) viewModel.scan(context)
    }

    BackHandler(enabled = appMode != AppMode.INITIAL_PROMPT || selectedItemPaths.isNotEmpty()) {
        if (selectedItemPaths.isNotEmpty()) {
            viewModel.clearSelection()
        } else if (appMode == AppMode.ALL_FILES && viewModel.navigateUp()) {
            // Navigated up one folder
        } else {
            viewModel.setAppMode(AppMode.INITIAL_PROMPT)
        }
    }


    Scaffold(
        topBar = {
            LightBlueTopBar(
                appMode = appMode,
                mediaCategory = mediaCategory,
                currentDirectory = currentDirectory,
                selectedCount = selectedItemPaths.size,
                hasClipboardItems = clipboard.items.isNotEmpty(),
                showHiddenFiles = showHiddenFiles,
                onClearSelection = { viewModel.clearSelection() },
                onBackToHome = { viewModel.setAppMode(AppMode.INITIAL_PROMPT) },
                onCopy = { viewModel.copySelected(selectedFileItems) },
                onCut = { viewModel.cutSelected(selectedFileItems) },
                onPaste = {
                    if (appMode == AppMode.ALL_FILES) {
                        coroutineScope.launch {
                            val ok = viewModel.paste(currentDirectory, context)
                            Toast.makeText(context, if (ok) "Pasted successfully" else "Failed to paste", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        showMoveToDialog = true
                    }
                },
                onMoveTo = { showMoveToDialog = true },
                onDelete = { showDeleteConfirmDialog = true },
                onShare = {
                    requestSharingPermissionsIfNeeded()
                    showShareDialog = true
                },
                onSelectTransport = { transport ->
                    requestSharingPermissionsIfNeeded()
                    val toShare = if (selectedFileItems.isNotEmpty()) selectedFileItems else currentVisibleItems
                    if (toShare.isNotEmpty()) {
                        P2PTransferManager.startSendSession(context, toShare, transport)
                    } else {
                        P2PTransferManager.startReceiveSession(context, transport)
                    }
                },
                onToggleHiddenFiles = { viewModel.toggleShowHiddenFiles(context) },
                onSwitchMode = {
                    viewModel.setAppMode(if (appMode == AppMode.ALL_FILES) AppMode.MEDIA else AppMode.ALL_FILES)
                    viewModel.refresh(context)
                },
                onRefresh = { viewModel.refresh(context) },
                onOpenSettings = { showSettingsDialog = true }
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selectedItemPaths.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                MultiSelectBottomBar(
                    selectedCount = selectedItemPaths.size,
                    totalCount = currentVisibleItems.size,
                    onSelectAll = { viewModel.selectAll(currentVisibleItems) },
                    onClearSelection = { viewModel.clearSelection() },
                    onZipAndSave = { showZipDestinationDialog = true }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                !hasPermission -> {
                    PermissionRequestScreen(
                        onGrantClick = {
                            val intent = PermissionHelper.getManageStorageIntent(context)
                            permissionLauncher.launch(intent)
                        }
                    )
                }
                appMode == AppMode.INITIAL_PROMPT -> {
                    ModeSelectionScreen(
                        onSelectMedia = {
                            viewModel.setAppMode(AppMode.MEDIA)
                            viewModel.scan(context)
                        },
                        onSelectAllFiles = {
                            viewModel.setAppMode(AppMode.ALL_FILES)
                            viewModel.loadDirectoryFiles(Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0"))
                        }
                    )
                }
                appMode == AppMode.MEDIA -> {
                    MediaBrowserView(
                        category = mediaCategory,
                        items = currentVisibleItems,
                        volumes = volumes,
                        selectedStorageId = selectedStorageId,
                        selectedPaths = selectedItemPaths,
                        loading = loading,
                        onSelectCategory = { cat -> viewModel.setMediaCategory(cat, context) },
                        onSelectStorage = { id -> viewModel.selectStorage(id) },
                        onItemClick = { item ->
                            if (selectedItemPaths.isNotEmpty()) {
                                viewModel.toggleSelection(item.path)
                            } else {
                                when (item.mediaType) {
                                    MediaType.IMAGE -> selectedImageForViewer = ImageItem.fromFileItem(item)
                                    MediaType.VIDEO -> selectedVideoForPlayer = item
                                    MediaType.AUDIO -> selectedAudioForPlayer = item
                                    else -> openFileWithIntent(context, item.file)
                                }
                            }
                        },
                        onItemLongClick = { item -> viewModel.toggleSelection(item.path) },
                        onScanAgain = { viewModel.scan(context) }
                    )
                }
                appMode == AppMode.ALL_FILES -> {
                    AllFilesBrowserView(
                        currentDirectory = currentDirectory,
                        items = directoryFiles,
                        volumes = volumes,
                        selectedPaths = selectedItemPaths,
                        loading = loading,
                        onFolderClick = { folder ->
                            if (selectedItemPaths.isNotEmpty()) {
                                viewModel.toggleSelection(folder.path)
                            } else {
                                viewModel.navigateTo(folder.file)
                            }
                        },
                        onFileClick = { item ->
                            if (selectedItemPaths.isNotEmpty()) {
                                viewModel.toggleSelection(item.path)
                            } else {
                                when (item.mediaType) {
                                    MediaType.IMAGE -> selectedImageForViewer = ImageItem.fromFileItem(item)
                                    MediaType.VIDEO -> selectedVideoForPlayer = item
                                    MediaType.AUDIO -> selectedAudioForPlayer = item
                                    else -> openFileWithIntent(context, item.file)
                                }
                            }
                        },
                        onItemLongClick = { item -> viewModel.toggleSelection(item.path) },
                        onNavigateUp = { viewModel.navigateUp() },
                        onFolderCreated = { newDir -> viewModel.navigateTo(newDir) },
                        onRefresh = { viewModel.refresh(context) }
                    )
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Items") },
            text = { Text("Are you sure you want to permanently delete ${selectedFileItems.size} selected item(s)?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        coroutineScope.launch {
                            val ok = viewModel.deleteSelected(selectedFileItems, context)
                            Toast.makeText(context, if (ok) "Deleted successfully" else "Failed to delete", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showZipDestinationDialog) {
        StorageFolderBrowserDialog(
            volumes = volumes,
            initialDirectory = currentDirectory,
            title = "Zip & Save Items",
            confirmButtonText = "Place .zip Here",
            showZipNameField = true,
            onDismiss = { showZipDestinationDialog = false },
            onConfirm = { folder, zipName ->
                showZipDestinationDialog = false
                val name = zipName ?: "archive.zip"
                viewModel.zipSelected(selectedFileItems, folder, name, context) { success ->
                    Toast.makeText(context, if (success) "Created $name" else "Zip failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showMoveToDialog) {
        StorageFolderBrowserDialog(
            volumes = volumes,
            initialDirectory = currentDirectory,
            title = "Move Items To...",
            confirmButtonText = "Move Here",
            showZipNameField = false,
            onDismiss = { showMoveToDialog = false },
            onConfirm = { folder, _ ->
                showMoveToDialog = false
                coroutineScope.launch {
                    val ok = if (selectedFileItems.isNotEmpty()) viewModel.moveTo(selectedFileItems, folder, context)
                             else viewModel.paste(folder, context)
                    Toast.makeText(context, if (ok) "Moved successfully" else "Failed to move", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showShareDialog) {
        val toShare = if (selectedFileItems.isNotEmpty()) selectedFileItems else currentVisibleItems
        ShareOptionsDialog(
            itemsToShare = toShare,
            onDismiss = { showShareDialog = false },
            onStartP2PShare = { transport ->
                P2PTransferManager.startSendSession(context, toShare, transport)
            },
            onSystemShare = {
                shareMultipleFiles(context, toShare)
            }
        )
    }

    if (transferState.status != TransferStatus.IDLE) {
        ActiveTransferDialog(
            onDismiss = { P2PTransferManager.resetState() }
        )
    }

    if (selectedVideoForPlayer != null) {
        VideoPlayerDialog(
            fileItem = selectedVideoForPlayer!!,
            onDismiss = { selectedVideoForPlayer = null },
            onShare = { item -> shareMultipleFiles(context, listOf(item)) }
        )
    }

    if (selectedAudioForPlayer != null) {
        AudioPlayerDialog(
            fileItem = selectedAudioForPlayer!!,
            onDismiss = { selectedAudioForPlayer = null },
            onShare = { item -> shareMultipleFiles(context, listOf(item)) }
        )
    }

    if (isZipping) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Creating .zip archive...", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { zipProgress }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    // Full screen photo viewer
    if (selectedImageForViewer != null) {
        FullScreenPhotoViewer(
            image = selectedImageForViewer!!,
            onClose = { selectedImageForViewer = null },
            onReadExif = { img -> exifTargetImage = img },
            onDelete = { img ->
                coroutineScope.launch {
                    viewModel.deleteImage(context, img)
                    selectedImageForViewer = null
                }
            },
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
                                LeftBreakoutSharingItem(
                                    onSelectTransport = { transport ->
                                        showOverflowMenu = false
                                        P2PTransferManager.startSendSession(context, listOf(image.toFileItem()), transport)
                                    },
                                    onCloseParent = { showOverflowMenu = false }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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

fun shareMultipleFiles(context: Context, items: List<FileItem>) {
    try {
        if (items.isEmpty()) return
        if (items.size == 1) {
            shareImageFile(context, ImageItem.fromFileItem(items.first()))
            return
        }
        val uris = ArrayList(items.map {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", it.file)
        })
        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${items.size} files"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share files: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun openFileWithIntent(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val ext = file.extension.lowercase()
        val mime = when (ext) {
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "*/*"
        }
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(viewIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}



