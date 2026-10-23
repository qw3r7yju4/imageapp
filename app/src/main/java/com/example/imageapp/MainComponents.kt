package com.example.imageapp

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.imageapp.model.*
import java.io.File


val TopBarBlue = Color(0xFF1E6FBA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightBlueTopBar(
    appMode: AppMode,
    mediaCategory: MediaCategory,
    currentDirectory: File,
    selectedCount: Int,
    hasClipboardItems: Boolean,
    showHiddenFiles: Boolean,
    onClearSelection: () -> Unit,
    onBackToHome: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onMoveTo: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {},
    onRename: () -> Unit = {},
    onEditExif: () -> Unit = {},
    onShare: () -> Unit,
    onSelectTransport: (TransferTransport) -> Unit,
    onToggleHiddenFiles: () -> Unit,
    onSwitchMode: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = when {
                    selectedCount > 0 -> "$selectedCount selected"
                    appMode == AppMode.INITIAL_PROMPT -> "File Suite"
                    appMode == AppMode.ALL_FILES -> currentDirectory.name.ifEmpty { "All Files" }
                    else -> "Media - ${mediaCategory.name.lowercase().replaceFirstChar { it.uppercase() }}"
                },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = TopBarBlue,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White,
            navigationIconContentColor = Color.White
        ),
        navigationIcon = {
            if (selectedCount > 0) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                }
            } else if (appMode != AppMode.INITIAL_PROMPT) {
                IconButton(onClick = onBackToHome) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
                }
            }
        },
        actions = {
            IconButton(onClick = onCopy, enabled = selectedCount > 0) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
            }
            IconButton(onClick = onCut, enabled = selectedCount > 0) {
                Icon(Icons.Default.ContentCut, contentDescription = "Cut")
            }
            IconButton(onClick = onPaste, enabled = hasClipboardItems) {
                Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
            }
            IconButton(onClick = onMoveTo, enabled = selectedCount > 0) {
                Icon(Icons.Default.DriveFileMove, contentDescription = "Move to...")
            }
            IconButton(onClick = onDelete, enabled = selectedCount > 0) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
            IconButton(onClick = onEdit, enabled = selectedCount == 1) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (selectedCount == 1) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                            onClick = { showMenu = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit EXIF Metadata") },
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            onClick = { showMenu = false; onEditExif() }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    LeftBreakoutSharingItem(
                        onSelectTransport = { transport ->
                            showMenu = false
                            onSelectTransport(transport)
                        },
                        onCloseParent = { showMenu = false }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text(if (showHiddenFiles) "Hide hidden files" else "Show hidden files") },
                        leadingIcon = { Icon(if (showHiddenFiles) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) },
                        onClick = { showMenu = false; onToggleHiddenFiles() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (appMode == AppMode.ALL_FILES) "Switch to Media" else "Switch to All Files") },
                        leadingIcon = { Icon(Icons.Default.FolderCopy, null) },
                        onClick = { showMenu = false; onSwitchMode() }
                    )
                    DropdownMenuItem(
                        text = { Text("Refresh") },
                        leadingIcon = { Icon(Icons.Default.Refresh, null) },
                        onClick = { showMenu = false; onRefresh() }
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Default.Settings, null) },
                        onClick = { showMenu = false; onOpenSettings() }
                    )
                }
            }
        }
    )
}


@Composable
fun MultiSelectBottomBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onZipAndSave: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "$selectedCount selected",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "of $totalCount items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSelectAll) { Text("Select All") }
                TextButton(onClick = onClearSelection) { Text("Clear") }
                Button(onClick = onZipAndSave) {
                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(".zip & Save")
                }
            }
        }
    }
}

@Composable
fun ModeSelectionScreen(
    onSelectMedia: () -> Unit,
    onSelectAllFiles: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to File Suite",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Please choose how you would like to explore:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectMedia() },
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.PermMedia,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Do you want to see just media?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Opens to Pictures with full video & audio playback support.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectAllFiles() },
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Do you want to look at all files?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Full storage browser starting at the highest folder of internal storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaBrowserView(
    category: MediaCategory,
    items: List<FileItem>,
    volumes: List<StorageVolumeInfo>,
    selectedStorageId: String?,
    selectedPaths: Set<String>,
    loading: Boolean,
    onSelectCategory: (MediaCategory) -> Unit,
    onSelectStorage: (String?) -> Unit,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onScanAgain: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(
            selectedTabIndex = when (category) {
                MediaCategory.PICTURES -> 0
                MediaCategory.VIDEOS -> 1
                MediaCategory.AUDIO -> 2
                MediaCategory.ALL -> 3
            }
        ) {
            Tab(selected = category == MediaCategory.PICTURES, onClick = { onSelectCategory(MediaCategory.PICTURES) }, text = { Text("Pictures") })
            Tab(selected = category == MediaCategory.VIDEOS, onClick = { onSelectCategory(MediaCategory.VIDEOS) }, text = { Text("Videos") })
            Tab(selected = category == MediaCategory.AUDIO, onClick = { onSelectCategory(MediaCategory.AUDIO) }, text = { Text("Audio") })
            Tab(selected = category == MediaCategory.ALL, onClick = { onSelectCategory(MediaCategory.ALL) }, text = { Text("All Media") })
        }

        if (volumes.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(selected = selectedStorageId == null, onClick = { onSelectStorage(null) }, label = { Text("All (${items.size})") })
                }
                items(volumes) { vol ->
                    val count = items.count { it.storageId == vol.id }
                    FilterChip(selected = selectedStorageId == vol.id, onClick = { onSelectStorage(vol.id) }, label = { Text("${vol.name} ($count)") })
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
            when {
                loading -> {
                    CircularProgressIndicator()
                }
                items.isEmpty() -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text("No media files found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onScanAgain) { Text("Scan Again") }
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
                        items(items, key = { it.path }) { item ->
                            MediaGridTile(
                                item = item,
                                isSelected = selectedPaths.contains(item.path),
                                onClick = { onItemClick(item) },
                                onLongClick = { onItemLongClick(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGridTile(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        when (item.mediaType) {
            MediaType.IMAGE, MediaType.VIDEO -> {
                AsyncImage(
                    model = item.file,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (item.mediaType == MediaType.VIDEO) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            }
            MediaType.AUDIO -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
            else -> {
                Icon(Icons.Default.InsertDriveFile, null, modifier = Modifier.align(Alignment.Center).size(36.dp))
            }
        }

        if (isSelected) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.padding(6.dp).size(24.dp).align(Alignment.TopEnd)
            ) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.padding(4.dp))
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllFilesBrowserView(
    currentDirectory: File,
    items: List<FileItem>,
    volumes: List<StorageVolumeInfo>,
    selectedPaths: Set<String>,
    loading: Boolean,
    onFolderClick: (FileItem) -> Unit,
    onFileClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onNavigateUp: () -> Unit,
    onFolderCreated: (File) -> Unit,
    onRefresh: () -> Unit
) {
    var showCreateFolder by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateUp,
                    enabled = currentDirectory.parentFile != null && currentDirectory.parentFile?.canRead() == true
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up folder")
                }
                Text(
                    text = currentDirectory.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showCreateFolder = true }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (volumes.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(volumes) { vol ->
                    FilterChip(
                        selected = currentDirectory.absolutePath.startsWith(vol.rootFile.absolutePath),
                        onClick = { onFolderCreated(vol.rootFile) },
                        label = { Text(vol.name) }
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
            when {
                loading -> CircularProgressIndicator()
                items.isEmpty() -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text("This folder is empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onRefresh) { Text("Refresh") }
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items, key = { it.path }) { item ->
                            val isSelected = selectedPaths.contains(item.path)
                            ListItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { if (item.isDirectory) onFolderClick(item) else onFileClick(item) },
                                        onLongClick = { onItemLongClick(item) }
                                    ),
                                headlineContent = {
                                    Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal)
                                },
                                supportingContent = {
                                    Text(if (item.isDirectory) "Folder" else StorageScanner.formatFileSize(item.sizeBytes), style = MaterialTheme.typography.bodySmall)
                                },
                                leadingContent = {
                                    FileItemIcon(item)
                                },
                                trailingContent = {
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolder) {
        CreateFolderDialog(
            parentDirectory = currentDirectory,
            onDismiss = { showCreateFolder = false },
            onFolderCreated = { newDir ->
                showCreateFolder = false
                onFolderCreated(newDir)
            }
        )
    }
}

@Composable
fun FileItemIcon(item: FileItem) {
    if (item.isDirectory) {
        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
    } else {
        when (item.mediaType) {
            MediaType.IMAGE -> AsyncImage(model = item.file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)))
            MediaType.VIDEO -> Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(36.dp))
            MediaType.AUDIO -> Icon(Icons.Default.Audiotrack, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(36.dp))
            else -> Icon(Icons.Default.InsertDriveFile, null, modifier = Modifier.size(36.dp))
        }
    }
}


