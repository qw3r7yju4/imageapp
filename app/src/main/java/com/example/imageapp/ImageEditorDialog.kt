package com.example.imageapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.media.MediaScannerConnection
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.imageapp.model.ImageItem
import com.example.imageapp.model.StorageVolumeInfo
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

enum class ImageEditorTab(val label: String) {
    CROP_ROTATE("Crop & Rotate"),
    FILTERS("Filters"),
    ADJUST("Adjust"),
    DRAW("Draw"),
    TEXT("Text")
}

enum class ColorPreset(val label: String) {
    ORIGINAL("Original"),
    GRAYSCALE("B&W"),
    SEPIA("Sepia"),
    WARM("Warm"),
    COOL("Cool"),
    VIVID("Vivid"),
    VINTAGE("Vintage"),
    INVERT("Invert")
}

data class DrawStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

object ImageEditHelper {
    fun loadBitmap(path: String, maxDim: Int = 2048): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(path, decodeOptions)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun applyColorMatrix(
        src: Bitmap,
        brightness: Float,
        contrast: Float,
        saturation: Float,
        preset: ColorPreset
    ): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(result)
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)

        val cm = ColorMatrix()
        cm.setSaturation(saturation)

        val scale = contrast
        val translate = brightness + (1f - contrast) * 128f
        val cbMat = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(cbMat)

        when (preset) {
            ColorPreset.ORIGINAL -> {}
            ColorPreset.GRAYSCALE -> {
                val gray = ColorMatrix()
                gray.setSaturation(0f)
                cm.postConcat(gray)
            }
            ColorPreset.SEPIA -> {
                val sepia = ColorMatrix(
                    floatArrayOf(
                        0.393f, 0.769f, 0.189f, 0f, 0f,
                        0.349f, 0.686f, 0.168f, 0f, 0f,
                        0.272f, 0.534f, 0.131f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(sepia)
            }
            ColorPreset.WARM -> {
                val warm = ColorMatrix(
                    floatArrayOf(
                        1.2f, 0f, 0f, 0f, 20f,
                        0f, 1.0f, 0f, 0f, 10f,
                        0f, 0f, 0.8f, 0f, -15f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(warm)
            }
            ColorPreset.COOL -> {
                val cool = ColorMatrix(
                    floatArrayOf(
                        0.8f, 0f, 0f, 0f, -10f,
                        0f, 1.0f, 0f, 0f, 5f,
                        0f, 0f, 1.25f, 0f, 25f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(cool)
            }
            ColorPreset.VIVID -> {
                val vivid = ColorMatrix()
                vivid.setSaturation(1.5f)
                cm.postConcat(vivid)
            }
            ColorPreset.VINTAGE -> {
                val vintage = ColorMatrix(
                    floatArrayOf(
                        0.9f, 0.1f, 0.1f, 0f, 30f,
                        0.1f, 0.8f, 0.1f, 0f, 20f,
                        0.1f, 0.1f, 0.6f, 0f, 10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(vintage)
            }
            ColorPreset.INVERT -> {
                val invert = ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(invert)
            }
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }


    fun transformBitmap(
        src: Bitmap,
        rotationDegrees: Float,
        straightenAngle: Float,
        flipH: Boolean,
        flipV: Boolean,
        cropRectFrac: List<Float>
    ): Bitmap {
        val totalRotation = rotationDegrees + straightenAngle
        val matrix = Matrix()
        val scaleX = if (flipH) -1f else 1f
        val scaleY = if (flipV) -1f else 1f
        matrix.postScale(scaleX, scaleY, src.width / 2f, src.height / 2f)
        matrix.postRotate(totalRotation, src.width / 2f, src.height / 2f)

        val transformed = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)

        val cropLeft = (cropRectFrac[0] * transformed.width).roundToInt().coerceIn(0, transformed.width - 1)
        val cropTop = (cropRectFrac[1] * transformed.height).roundToInt().coerceIn(0, transformed.height - 1)
        val cropRight = (cropRectFrac[2] * transformed.width).roundToInt().coerceIn(cropLeft + 1, transformed.width)
        val cropBottom = (cropRectFrac[3] * transformed.height).roundToInt().coerceIn(cropTop + 1, transformed.height)
        val cropWidth = (cropRight - cropLeft).coerceAtLeast(1)
        val cropHeight = (cropBottom - cropTop).coerceAtLeast(1)

        return Bitmap.createBitmap(transformed, cropLeft, cropTop, cropWidth, cropHeight)
    }

    fun renderDrawingsAndText(
        baseBitmap: Bitmap,
        strokes: List<DrawStroke>,
        overlayText: String,
        textColor: Color,
        textSizeSp: Float,
        textPosFraction: Offset
    ): Bitmap {
        if (strokes.isEmpty() && overlayText.isBlank()) return baseBitmap
        val output = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = AndroidCanvas(output)

        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }

        for (stroke in strokes) {
            paint.color = stroke.color.toArgb()
            paint.strokeWidth = stroke.strokeWidth
            val points = stroke.points
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                canvas.drawLine(
                    p1.x * output.width,
                    p1.y * output.height,
                    p2.x * output.width,
                    p2.y * output.height,
                    paint
                )
            }
        }

        if (overlayText.isNotBlank()) {
            val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = textColor.toArgb()
                textSize = textSizeSp * (output.width / 400f).coerceAtLeast(1f)
                isFakeBoldText = true
                setShadowLayer(6f, 2f, 2f, android.graphics.Color.BLACK)
            }
            val x = textPosFraction.x * output.width
            val y = textPosFraction.y * output.height
            canvas.drawText(overlayText, x, y, textPaint)
        }

        return output
    }

    fun saveProcessedImage(
        context: Context,
        originalImagePath: String,
        targetFile: File,
        baseBitmap: Bitmap,
        strokes: List<DrawStroke>,
        overlayText: String,
        textColor: Color,
        textSizeSp: Float,
        textPosFrac: Offset
    ): Boolean {
        val finalBmp = renderDrawingsAndText(baseBitmap, strokes, overlayText, textColor, textSizeSp, textPosFrac)
        return try {
            targetFile.parentFile?.mkdirs()
            FileOutputStream(targetFile).use { fos ->
                finalBmp.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }
            ExifHelper.copyExif(originalImagePath, targetFile.absolutePath)
            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}



@Composable
fun EditorCanvas(
    bitmap: Bitmap,
    activeTab: ImageEditorTab,
    strokeColor: Color,
    strokeWidth: Float,
    strokes: List<DrawStroke>,
    currentPoints: List<Offset>,
    onPointsChange: (List<Offset>) -> Unit,
    onStrokeCompleted: (DrawStroke) -> Unit,
    overlayText: String,
    textColor: Color,
    textSizeSp: Float,
    textPosFrac: Offset,
    onTextPosChange: (Offset) -> Unit
) {
    val bmpImage = remember(bitmap) { bitmap.asImageBitmap() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(activeTab, strokeColor, strokeWidth) {
                if (activeTab == ImageEditorTab.DRAW) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onPointsChange(listOf(Offset(offset.x / size.width, offset.y / size.height)))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val pt = Offset(change.position.x / size.width, change.position.y / size.height)
                            onPointsChange(currentPoints + pt)
                        },
                        onDragEnd = {
                            if (currentPoints.isNotEmpty()) {
                                onStrokeCompleted(DrawStroke(currentPoints, strokeColor, strokeWidth))
                                onPointsChange(emptyList())
                            }
                        }
                    )
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val imgWidth = bmpImage.width.toFloat()
            val imgHeight = bmpImage.height.toFloat()
            val scale = minOf(size.width / imgWidth, size.height / imgHeight)
            val destW = imgWidth * scale
            val destH = imgHeight * scale
            val left = (size.width - destW) / 2f
            val top = (size.height - destH) / 2f

            drawImage(
                image = bmpImage,
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = androidx.compose.ui.unit.IntSize(destW.roundToInt(), destH.roundToInt())
            )

            // Crop frame & rule-of-thirds grid
            if (activeTab == ImageEditorTab.CROP_ROTATE) {
                val gridColor = Color.White.copy(alpha = 0.45f)
                drawLine(gridColor, Offset(left + destW / 3f, top), Offset(left + destW / 3f, top + destH), strokeWidth = 1.5f)
                drawLine(gridColor, Offset(left + 2f * destW / 3f, top), Offset(left + 2f * destW / 3f, top + destH), strokeWidth = 1.5f)
                drawLine(gridColor, Offset(left, top + destH / 3f), Offset(left + destW, top + destH / 3f), strokeWidth = 1.5f)
                drawLine(gridColor, Offset(left, top + 2f * destH / 3f), Offset(left + destW, top + 2f * destH / 3f), strokeWidth = 1.5f)

                drawRect(
                    color = Color.White,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(destW, destH),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                )

                val cornerLen = 28f
                // Top-Left
                drawLine(Color.White, Offset(left, top), Offset(left + cornerLen, top), strokeWidth = 6f)
                drawLine(Color.White, Offset(left, top), Offset(left, top + cornerLen), strokeWidth = 6f)
                // Top-Right
                drawLine(Color.White, Offset(left + destW, top), Offset(left + destW - cornerLen, top), strokeWidth = 6f)
                drawLine(Color.White, Offset(left + destW, top), Offset(left + destW, top + cornerLen), strokeWidth = 6f)
                // Bottom-Left
                drawLine(Color.White, Offset(left, top + destH), Offset(left + cornerLen, top + destH), strokeWidth = 6f)
                drawLine(Color.White, Offset(left, top + destH), Offset(left, top + destH - cornerLen), strokeWidth = 6f)
                // Bottom-Right
                drawLine(Color.White, Offset(left + destW, top + destH), Offset(left + destW - cornerLen, top + destH), strokeWidth = 6f)
                drawLine(Color.White, Offset(left + destW, top + destH), Offset(left + destW, top + destH - cornerLen), strokeWidth = 6f)
            }

            for (stroke in strokes) {
                val pts = stroke.points
                for (i in 0 until pts.size - 1) {
                    drawLine(
                        color = stroke.color,
                        start = Offset(pts[i].x * size.width, pts[i].y * size.height),
                        end = Offset(pts[i + 1].x * size.width, pts[i + 1].y * size.height),
                        strokeWidth = stroke.strokeWidth
                    )
                }
            }

            if (currentPoints.size > 1) {
                for (i in 0 until currentPoints.size - 1) {
                    drawLine(
                        color = strokeColor,
                        start = Offset(currentPoints[i].x * size.width, currentPoints[i].y * size.height),
                        end = Offset(currentPoints[i + 1].x * size.width, currentPoints[i + 1].y * size.height),
                        strokeWidth = strokeWidth
                    )
                }
            }
        }

        if (overlayText.isNotBlank()) {
            Text(
                text = overlayText,
                color = textColor,
                fontSize = textSizeSp.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .offset {
                        IntOffset((textPosFrac.x * 300).roundToInt(), (textPosFrac.y * 500).roundToInt())
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onTextPosChange(
                                Offset(
                                    (textPosFrac.x + dragAmount.x / 300f).coerceIn(0f, 1f),
                                    (textPosFrac.y + dragAmount.y / 500f).coerceIn(0f, 1f)
                                )
                            )
                        }
                    }
            )
        }
    }
}


@Composable
fun CropRotateControls(
    rotation: Float,
    onRotate: (Float) -> Unit,
    straighten: Float,
    onStraightenChange: (Float) -> Unit,
    flipH: Boolean,
    onToggleFlipH: () -> Unit,
    flipV: Boolean,
    onToggleFlipV: () -> Unit,
    selectedRatio: String,
    onSelectRatio: (String, Float, Float, Float, Float) -> Unit,
    onResetCrop: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onRotate(-90f) }) {
                Icon(Icons.Default.RotateLeft, contentDescription = "Rotate Left 90°")
            }
            IconButton(onClick = { onRotate(90f) }) {
                Icon(Icons.Default.RotateRight, contentDescription = "Rotate Right 90°")
            }
            FilterChip(selected = flipH, onClick = onToggleFlipH, label = { Text("Flip H") })
            FilterChip(selected = flipV, onClick = onToggleFlipV, label = { Text("Flip V") })
            TextButton(onClick = onResetCrop) { Text("Reset") }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val angleStr = if (straighten > 0) "+${straighten.roundToInt()}°" else "${straighten.roundToInt()}°"
            Text("Straighten: $angleStr", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(100.dp))
            Slider(value = straighten, onValueChange = onStraightenChange, valueRange = -45f..45f, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(4.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            item {
                FilterChip(selected = selectedRatio == "Full", onClick = { onSelectRatio("Full", 0f, 0f, 1f, 1f) }, label = { Text("Full") })
            }
            item {
                FilterChip(selected = selectedRatio == "1:1", onClick = { onSelectRatio("1:1", 0.125f, 0.125f, 0.875f, 0.875f) }, label = { Text("1:1") })
            }
            item {
                FilterChip(selected = selectedRatio == "4:3", onClick = { onSelectRatio("4:3", 0.05f, 0.15f, 0.95f, 0.85f) }, label = { Text("4:3") })
            }
            item {
                FilterChip(selected = selectedRatio == "16:9", onClick = { onSelectRatio("16:9", 0f, 0.22f, 1f, 0.78f) }, label = { Text("16:9") })
            }
            item {
                FilterChip(selected = selectedRatio == "9:16", onClick = { onSelectRatio("9:16", 0.22f, 0f, 0.78f, 1f) }, label = { Text("9:16") })
            }
        }
    }
}


@Composable
fun FiltersControls(
    selectedPreset: ColorPreset,
    onSelectPreset: (ColorPreset) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val row1 = listOf(ColorPreset.ORIGINAL, ColorPreset.GRAYSCALE, ColorPreset.SEPIA, ColorPreset.WARM)
        val row2 = listOf(ColorPreset.COOL, ColorPreset.VIVID, ColorPreset.VINTAGE, ColorPreset.INVERT)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            row1.forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(preset.label, fontSize = 12.sp, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            row2.forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(preset.label, fontSize = 12.sp, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun AdjustControls(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    contrast: Float,
    onContrastChange: (Float) -> Unit,
    saturation: Float,
    onSaturationChange: (Float) -> Unit,
    onResetAdjustments: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Adjustments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = onResetAdjustments) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset")
            }
        }

        // Brightness
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Brightness", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                val bStr = if (brightness > 0) "+${brightness.roundToInt()}" else "${brightness.roundToInt()}"
                Text(bStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = brightness,
                onValueChange = onBrightnessChange,
                valueRange = -100f..100f,
                modifier = Modifier.fillMaxWidth().height(36.dp)
            )
        }

        // Contrast
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Contrast", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                val cStr = String.format(java.util.Locale.US, "%.1fx", contrast)
                Text(cStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = contrast,
                onValueChange = onContrastChange,
                valueRange = 0.5f..2.0f,
                modifier = Modifier.fillMaxWidth().height(36.dp)
            )
        }

        // Saturation
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Saturation", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                val sStr = String.format(java.util.Locale.US, "%.1fx", saturation)
                Text(sStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = saturation,
                onValueChange = onSaturationChange,
                valueRange = 0.0f..2.0f,
                modifier = Modifier.fillMaxWidth().height(36.dp)
            )
        }
    }
}


@Composable
fun DrawControls(
    selectedColor: Color,
    onSelectColor: (Color) -> Unit,
    strokeWidth: Float,
    onStrokeWidthChange: (Float) -> Unit,
    hasStrokes: Boolean,
    onUndoStroke: () -> Unit,
    onClearStrokes: () -> Unit
) {
    val colors = listOf(Color.Red, Color(0xFFFF9800), Color.Yellow, Color.Green, Color.Blue, Color(0xFF9C27B0), Color.White, Color.Black)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(colors) { col ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(col)
                            .border(
                                width = if (selectedColor == col) 3.dp else 1.dp,
                                color = if (selectedColor == col) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { onSelectColor(col) }
                    )
                }
            }

            if (hasStrokes) {
                IconButton(onClick = onUndoStroke) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
            }
            TextButton(onClick = onClearStrokes) {
                Text("Clear")
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Brush Size: ${strokeWidth.roundToInt()}px", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(110.dp))
            Slider(value = strokeWidth, onValueChange = onStrokeWidthChange, valueRange = 4f..48f, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun TextControls(
    overlayText: String,
    onTextChange: (String) -> Unit,
    textColor: Color,
    onSelectTextColor: (Color) -> Unit,
    textSizeSp: Float,
    onTextSizeChange: (Float) -> Unit,
    onClearText: () -> Unit
) {
    val colors = listOf(Color.White, Color.Black, Color.Red, Color(0xFFFF9800), Color.Yellow, Color.Green, Color.Blue, Color(0xFF9C27B0))

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = overlayText,
            onValueChange = onTextChange,
            label = { Text("Overlay Text (drag on photo to position)") },
            trailingIcon = {
                if (overlayText.isNotBlank()) {
                    IconButton(onClick = onClearText) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(colors) { col ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(col)
                            .border(
                                width = if (textColor == col) 3.dp else 1.dp,
                                color = if (textColor == col) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { onSelectTextColor(col) }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(160.dp)) {
                Text("${textSizeSp.roundToInt()}sp", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp))
                Slider(value = textSizeSp, onValueChange = onTextSizeChange, valueRange = 14f..64f, modifier = Modifier.weight(1f))
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    hasStrokes: Boolean,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onSaveCopy: () -> Unit,
    onOverwrite: () -> Unit,
    onSaveToFolder: () -> Unit
) {
    var showSaveMenu by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = Modifier.statusBarsPadding(),
        title = { Text("Edit Photo", color = Color.White) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black.copy(alpha = 0.85f),
            navigationIconContentColor = Color.White,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White
        ),
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
            }
        },
        actions = {
            if (hasStrokes) {
                IconButton(onClick = onUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
            }
            IconButton(onClick = onReset) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset")
            }
            Box {
                Button(onClick = { showSaveMenu = true }) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }
                DropdownMenu(expanded = showSaveMenu, onDismissRequest = { showSaveMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Save Copy") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = { showSaveMenu = false; onSaveCopy() }
                    )
                    DropdownMenuItem(
                        text = { Text("Overwrite Original") },
                        leadingIcon = { Icon(Icons.Default.SaveAs, null) },
                        onClick = { showSaveMenu = false; onOverwrite() }
                    )
                    DropdownMenuItem(
                        text = { Text("Save to Folder...") },
                        leadingIcon = { Icon(Icons.Default.Folder, null) },
                        onClick = { showSaveMenu = false; onSaveToFolder() }
                    )
                }
            }
        }
    )
}



@Composable
fun EditorTabsHeader(
    activeTab: ImageEditorTab,
    onTabSelected: (ImageEditorTab) -> Unit
) {
    TabRow(
        selectedTabIndex = activeTab.ordinal,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        ImageEditorTab.entries.forEach { tab ->
            val isSelected = activeTab == tab
            Tab(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = when (tab) {
                            ImageEditorTab.CROP_ROTATE -> "Crop"
                            ImageEditorTab.FILTERS -> "Filters"
                            ImageEditorTab.ADJUST -> "Adjust"
                            ImageEditorTab.DRAW -> "Draw"
                            ImageEditorTab.TEXT -> "Text"
                        },
                        fontSize = 11.sp,
                        maxLines = 1,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                icon = {
                    Icon(
                        imageVector = when (tab) {
                            ImageEditorTab.CROP_ROTATE -> Icons.Default.CropRotate
                            ImageEditorTab.FILTERS -> Icons.Default.ColorLens
                            ImageEditorTab.ADJUST -> Icons.Default.Tune
                            ImageEditorTab.DRAW -> Icons.Default.Brush
                            ImageEditorTab.TEXT -> Icons.Default.TextFields
                        },
                        contentDescription = tab.label,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}


@Composable
fun EditorBottomSection(
    state: EditorParamState
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EditorTabsHeader(
                activeTab = state.activeTab,
                onTabSelected = { state.activeTab = it }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                when (state.activeTab) {
                    ImageEditorTab.CROP_ROTATE -> {
                        CropRotateControls(
                            rotation = state.rotationDeg,
                            onRotate = { state.rotationDeg = (state.rotationDeg + it) % 360f },
                            straighten = state.straightenAngle,
                            onStraightenChange = { state.straightenAngle = it },
                            flipH = state.flipH,
                            onToggleFlipH = { state.flipH = !state.flipH },
                            flipV = state.flipV,
                            onToggleFlipV = { state.flipV = !state.flipV },
                            selectedRatio = state.selectedCropRatio,
                            onSelectRatio = { label, l, t, r, b ->
                                state.selectedCropRatio = label
                                state.cropLeft = l
                                state.cropTop = t
                                state.cropRight = r
                                state.cropBottom = b
                            },
                            onResetCrop = {
                                state.rotationDeg = 0f
                                state.straightenAngle = 0f
                                state.flipH = false
                                state.flipV = false
                                state.cropLeft = 0f
                                state.cropTop = 0f
                                state.cropRight = 1f
                                state.cropBottom = 1f
                                state.selectedCropRatio = "Full"
                            }
                        )
                    }
                    ImageEditorTab.FILTERS -> {
                        FiltersControls(
                            selectedPreset = state.colorPreset,
                            onSelectPreset = { state.colorPreset = it }
                        )
                    }
                    ImageEditorTab.ADJUST -> {
                        AdjustControls(
                            brightness = state.brightness,
                            onBrightnessChange = { state.brightness = it },
                            contrast = state.contrast,
                            onContrastChange = { state.contrast = it },
                            saturation = state.saturation,
                            onSaturationChange = { state.saturation = it },
                            onResetAdjustments = {
                                state.brightness = 0f
                                state.contrast = 1f
                                state.saturation = 1f
                            }
                        )
                    }
                    ImageEditorTab.DRAW -> {
                        DrawControls(
                            selectedColor = state.strokeColor,
                            onSelectColor = { state.strokeColor = it },
                            strokeWidth = state.strokeWidth,
                            onStrokeWidthChange = { state.strokeWidth = it },
                            hasStrokes = state.strokes.isNotEmpty(),
                            onUndoStroke = { state.strokes.removeLastOrNull() },
                            onClearStrokes = { state.strokes.clear() }
                        )
                    }
                    ImageEditorTab.TEXT -> {
                        TextControls(
                            overlayText = state.overlayText,
                            onTextChange = { state.overlayText = it },
                            textColor = state.textColor,
                            onSelectTextColor = { state.textColor = it },
                            textSizeSp = state.textSizeSp,
                            onTextSizeChange = { state.textSizeSp = it },
                            onClearText = { state.overlayText = "" }
                        )
                    }
                }
            }
        }
    }
}


class EditorParamState {
    var activeTab by mutableStateOf(ImageEditorTab.CROP_ROTATE)
    var rotationDeg by mutableFloatStateOf(0f)
    var straightenAngle by mutableFloatStateOf(0f)
    var flipH by mutableStateOf(false)
    var flipV by mutableStateOf(false)
    var cropLeft by mutableFloatStateOf(0f)
    var cropTop by mutableFloatStateOf(0f)
    var cropRight by mutableFloatStateOf(1f)
    var cropBottom by mutableFloatStateOf(1f)
    var selectedCropRatio by mutableStateOf("Full")

    var brightness by mutableFloatStateOf(0f)
    var contrast by mutableFloatStateOf(1f)
    var saturation by mutableFloatStateOf(1f)
    var colorPreset by mutableStateOf(ColorPreset.ORIGINAL)

    val strokes = mutableStateListOf<DrawStroke>()
    var currentPoints by mutableStateOf<List<Offset>>(emptyList())
    var strokeColor by mutableStateOf(Color.Red)
    var strokeWidth by mutableFloatStateOf(12f)

    var overlayText by mutableStateOf("")
    var textColor by mutableStateOf(Color.White)
    var textSizeSp by mutableFloatStateOf(24f)
    var textPosFrac by mutableStateOf(Offset(0.2f, 0.5f))

    fun reset() {
        rotationDeg = 0f; straightenAngle = 0f; flipH = false; flipV = false
        cropLeft = 0f; cropTop = 0f; cropRight = 1f; cropBottom = 1f
        selectedCropRatio = "Full"
        brightness = 0f; contrast = 1f; saturation = 1f
        colorPreset = ColorPreset.ORIGINAL
        strokes.clear(); overlayText = ""
    }
}



@Composable
fun ImageEditorDialog(
    image: ImageItem,
    volumes: List<StorageVolumeInfo>,
    onDismiss: () -> Unit,
    onImageSaved: (File) -> Unit
) {
    val context = LocalContext.current
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val state = remember { EditorParamState() }
    var showCustomFolderBrowser by remember { mutableStateOf(false) }

    LaunchedEffect(image.path) {
        isLoading = true
        originalBitmap = ImageEditHelper.loadBitmap(image.path)
        isLoading = false
    }

    val processedBitmap = remember(
        originalBitmap,
        state.rotationDeg,
        state.straightenAngle,
        state.flipH,
        state.flipV,
        state.cropLeft,
        state.cropTop,
        state.cropRight,
        state.cropBottom,
        state.brightness,
        state.contrast,
        state.saturation,
        state.colorPreset
    ) {
        val base = originalBitmap ?: return@remember null
        val transformed = ImageEditHelper.transformBitmap(
            base,
            state.rotationDeg,
            state.straightenAngle,
            state.flipH,
            state.flipV,
            listOf(state.cropLeft, state.cropTop, state.cropRight, state.cropBottom)
        )
        ImageEditHelper.applyColorMatrix(transformed, state.brightness, state.contrast, state.saturation, state.colorPreset)
    }

    fun handleSave(targetFile: File) {
        val bmp = processedBitmap ?: return
        val success = ImageEditHelper.saveProcessedImage(context, image.path, targetFile, bmp, state.strokes, state.overlayText, state.textColor, state.textSizeSp, state.textPosFrac)
        if (success) {
            Toast.makeText(context, "Saved: ${targetFile.name}", Toast.LENGTH_SHORT).show()
            onImageSaved(targetFile)
            onDismiss()
        } else {
            Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .navigationBarsPadding()
        ) {
            EditorTopBar(
                hasStrokes = state.strokes.isNotEmpty(),
                onCancel = onDismiss,
                onUndo = { state.strokes.removeLastOrNull() },
                onReset = { state.reset() },
                onSaveCopy = {
                    val parent = image.file.parentFile ?: context.filesDir
                    handleSave(File(parent, "edited_${System.currentTimeMillis()}_${image.file.name}"))
                },
                onOverwrite = { handleSave(image.file) },
                onSaveToFolder = { showCustomFolderBrowser = true }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White)
                } else if (processedBitmap != null) {
                    EditorCanvas(
                        bitmap = processedBitmap,
                        activeTab = state.activeTab,
                        strokeColor = state.strokeColor,
                        strokeWidth = state.strokeWidth,
                        strokes = state.strokes,
                        currentPoints = state.currentPoints,
                        onPointsChange = { state.currentPoints = it },
                        onStrokeCompleted = { state.strokes.add(it) },
                        overlayText = state.overlayText,
                        textColor = state.textColor,
                        textSizeSp = state.textSizeSp,
                        textPosFrac = state.textPosFrac,
                        onTextPosChange = { state.textPosFrac = it }
                    )
                }
            }

            EditorBottomSection(state = state)
        }
    }

    if (showCustomFolderBrowser) {
        StorageFolderBrowserDialog(
            volumes = volumes,
            initialDirectory = image.file.parentFile,
            title = "Save Edited Image To...",
            confirmButtonText = "Save Here",
            showZipNameField = false,
            onDismiss = { showCustomFolderBrowser = false },
            onConfirm = { folder, _ ->
                showCustomFolderBrowser = false
                handleSave(File(folder, "edited_${System.currentTimeMillis()}_${image.file.name}"))
            }
        )
    }
}


