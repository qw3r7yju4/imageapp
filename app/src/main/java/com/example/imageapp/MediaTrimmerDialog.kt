package com.example.imageapp

import android.content.Context
import android.media.*
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.imageapp.model.FileItem
import com.example.imageapp.model.MediaType
import com.example.imageapp.model.StorageVolumeInfo
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MediaTrimHelper {

    suspend fun trimMediaLossless(
        srcFile: File,
        destFile: File,
        startMs: Long,
        endMs: Long,
        muteAudio: Boolean = false,
        audioOnly: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(srcFile.absolutePath)
            val trackCount = extractor.trackCount

            val outputFormat = if (audioOnly) MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            destFile.parentFile?.mkdirs()
            muxer = MediaMuxer(destFile.absolutePath, outputFormat)

            val indexMap = HashMap<Int, Int>()
            var bufferSize = 256 * 1024

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                val isAudio = mime.startsWith("audio/")
                val isVideo = mime.startsWith("video/")

                if (audioOnly && !isAudio) continue
                if (muteAudio && isAudio) continue

                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    if (size > bufferSize) bufferSize = size
                }

                extractor.selectTrack(i)
                val dstIndex = muxer.addTrack(format)
                indexMap[i] = dstIndex
            }

            if (indexMap.isEmpty()) return@withContext false

            muxer.start()

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break

                val sampleTime = extractor.sampleTime
                if (sampleTime > endUs) {
                    extractor.advance()
                    break
                }

                if (sampleTime >= startUs) {
                    val dstIndex = indexMap[trackIndex]
                    if (dstIndex != null) {
                        bufferInfo.offset = 0
                        bufferInfo.size = extractor.readSampleData(buffer, 0)
                        bufferInfo.presentationTimeUs = (sampleTime - startUs).coerceAtLeast(0L)
                        val flags = extractor.sampleFlags
                        bufferInfo.flags = if ((flags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                        } else 0

                        if (bufferInfo.size > 0) {
                            muxer.writeSampleData(dstIndex, buffer, bufferInfo)
                        }
                    }
                }
                extractor.advance()
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    fun executeTrimAction(
        context: Context,
        fileItem: FileItem,
        destinationFolder: File,
        startMs: Long,
        endMs: Long,
        muteAudio: Boolean,
        extractAudioOnly: Boolean,
        onComplete: (File?, Boolean) -> Unit
    ) {
        val baseName = fileItem.file.nameWithoutExtension
        val ext = if (extractAudioOnly) "m4a" else fileItem.extension
        val targetName = "trimmed_${System.currentTimeMillis()}_$baseName.$ext"
        val targetFile = File(destinationFolder, targetName)

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val ok = trimMediaLossless(
                srcFile = fileItem.file,
                destFile = targetFile,
                startMs = startMs,
                endMs = endMs,
                muteAudio = muteAudio && !extractAudioOnly,
                audioOnly = extractAudioOnly
            )
            withContext(Dispatchers.Main) {
                if (ok) {
                    MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)
                    onComplete(targetFile, true)
                } else {
                    onComplete(null, false)
                }
            }
        }
    }
}


@Composable
fun TrimmerControls(
    trimRange: ClosedFloatingPointRange<Float>,
    onTrimRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    totalDurationMs: Long,
    isVideo: Boolean,
    muteAudio: Boolean,
    onMuteAudioChange: (Boolean) -> Unit,
    onExtractAudio: () -> Unit,
    onSelectFolder: () -> Unit,
    isTrimming: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Start: ${StorageScanner.formatDuration(trimRange.start.toLong())}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Clip: ${StorageScanner.formatDuration((trimRange.endInclusive - trimRange.start).toLong())}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("End: ${StorageScanner.formatDuration(trimRange.endInclusive.toLong())}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }

                RangeSlider(
                    value = trimRange,
                    onValueChange = onTrimRangeChange,
                    valueRange = 0f..totalDurationMs.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isVideo) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = muteAudio, onCheckedChange = onMuteAudioChange)
                    Text("Mute", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }

                OutlinedButton(
                    onClick = onExtractAudio,
                    enabled = !isTrimming
                ) {
                    Icon(Icons.Default.Audiotrack, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Extract Audio")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            TextButton(onClick = onSelectFolder, enabled = !isTrimming) {
                Icon(Icons.Default.Folder, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Custom Folder...")
            }
        }
    }
}


@Composable
fun TrimmerPreview(
    fileItem: FileItem,
    isVideo: Boolean,
    startMs: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center
    ) {
        if (isVideo) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val controller = MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoURI(Uri.fromFile(fileItem.file))
                        setOnPreparedListener { seekTo(startMs) }
                    }
                }
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(8.dp))
                Text(fileItem.name, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaTrimmerDialog(
    fileItem: FileItem,
    volumes: List<StorageVolumeInfo>,
    onDismiss: () -> Unit,
    onMediaSaved: (File) -> Unit
) {
    val context = LocalContext.current
    val isVideo = fileItem.mediaType == MediaType.VIDEO

    var totalDurationMs by remember { mutableLongStateOf(1000L) }
    var trimRange by remember { mutableStateOf(0f..1000f) }
    var muteAudio by remember { mutableStateOf(false) }
    var isTrimming by remember { mutableStateOf(false) }
    var showFolderBrowser by remember { mutableStateOf(false) }

    LaunchedEffect(fileItem.path) {
        withContext(Dispatchers.IO) {
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(fileItem.path)
                val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val dur = durStr?.toLongOrNull() ?: 1000L
                totalDurationMs = dur.coerceAtLeast(1000L)
                trimRange = 0f..totalDurationMs.toFloat()
                mmr.release()
            } catch (_: Exception) {}
        }
    }

    fun startTrim(destinationFolder: File, extractAudioOnly: Boolean = false) {
        isTrimming = true
        MediaTrimHelper.executeTrimAction(
            context = context,
            fileItem = fileItem,
            destinationFolder = destinationFolder,
            startMs = trimRange.start.toLong(),
            endMs = trimRange.endInclusive.toLong(),
            muteAudio = muteAudio,
            extractAudioOnly = extractAudioOnly
        ) { savedFile, success ->
            isTrimming = false
            if (success && savedFile != null) {
                Toast.makeText(context, "Saved: ${savedFile.name}", Toast.LENGTH_SHORT).show()
                onMediaSaved(savedFile)
                onDismiss()
            } else {
                Toast.makeText(context, "Trimming failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = { Text(if (isVideo) "Trim Video" else "Trim Audio", color = Color.White) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.85f),
                        navigationIconContentColor = Color.White,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        Button(
                            onClick = {
                                val parent = fileItem.file.parentFile ?: context.filesDir
                                startTrim(parent)
                            },
                            enabled = !isTrimming
                        ) {
                            Icon(Icons.Default.ContentCut, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Trim")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                TrimmerPreview(
                    fileItem = fileItem,
                    isVideo = isVideo,
                    startMs = trimRange.start.toInt(),
                    modifier = Modifier.weight(1f)
                )

                Spacer(Modifier.height(16.dp))

                TrimmerControls(
                    trimRange = trimRange,
                    onTrimRangeChange = { trimRange = it },
                    totalDurationMs = totalDurationMs,
                    isVideo = isVideo,
                    muteAudio = muteAudio,
                    onMuteAudioChange = { muteAudio = it },
                    onExtractAudio = {
                        val parent = fileItem.file.parentFile ?: context.filesDir
                        startTrim(parent, extractAudioOnly = true)
                    },
                    onSelectFolder = { showFolderBrowser = true },
                    isTrimming = isTrimming
                )

                if (isTrimming) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (showFolderBrowser) {
        StorageFolderBrowserDialog(
            volumes = volumes,
            initialDirectory = fileItem.file.parentFile,
            title = "Save Trimmed File To...",
            confirmButtonText = "Save Here",
            showZipNameField = false,
            onDismiss = { showFolderBrowser = false },
            onConfirm = { folder, _ ->
                showFolderBrowser = false
                startTrim(folder)
            }
        )
    }
}

