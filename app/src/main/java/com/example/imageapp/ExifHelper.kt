package com.example.imageapp

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExifHelper {

    data class ExifData(
        val fileName: String,
        val filePath: String,
        val formattedSize: String,
        val dimensions: String,
        val dateTaken: String?,
        val camera: String?,
        val aperture: String?,
        val iso: String?,
        val exposureTime: String?,
        val focalLength: String?,
        val flash: String?,
        val gps: String?
    )

    fun getExifData(context: Context, filePath: String): ExifData? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null

            val exif = ExifInterface(filePath)

            val width = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
            val length = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)
            val dimensions = if (!width.isNullOrBlank() && !length.isNullOrBlank()) {
                "${width} x ${length}"
            } else {
                "Unknown"
            }

            var dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            if (dateStr.isNullOrBlank() && file.lastModified() > 0) {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                dateStr = sdf.format(Date(file.lastModified()))
            }

            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim() ?: ""
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim() ?: ""
            val camera = "$make $model".trim().ifEmpty { null }

            val fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
            val aperture = if (!fNumber.isNullOrBlank()) "f/$fNumber" else null

            @Suppress("DEPRECATION")
            val iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)

            val expTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
            val exposureTime = if (!expTime.isNullOrBlank()) {
                val expDouble = expTime.toDoubleOrNull()
                if (expDouble != null && expDouble > 0 && expDouble < 1.0) {
                    "1/${(1.0 / expDouble).toInt()}s"
                } else {
                    "${expTime}s"
                }
            } else null

            val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
            val focalLength = if (!focal.isNullOrBlank()) "${focal}mm" else null

            val flashAttr = exif.getAttribute(ExifInterface.TAG_FLASH)
            val flash = if (flashAttr != null) {
                val flashInt = flashAttr.toIntOrNull() ?: 0
                if ((flashInt and 1) != 0) "Fired" else "Did not fire"
            } else null

            val latLong = exif.latLong
            val gps = if (latLong != null) {
                String.format(Locale.US, "%.5f, %.5f", latLong[0], latLong[1])
            } else null

            val sizeBytes = file.length()
            val formattedSize = if (sizeBytes >= 1024 * 1024) {
                String.format(Locale.US, "%.2f MB", sizeBytes.toDouble() / (1024 * 1024))
            } else {
                String.format(Locale.US, "%.1f KB", sizeBytes.toDouble() / 1024)
            }

            ExifData(
                fileName = file.name,
                filePath = file.absolutePath,
                formattedSize = formattedSize,
                dimensions = dimensions,
                dateTaken = dateStr,
                camera = camera,
                aperture = aperture,
                iso = iso,
                exposureTime = exposureTime,
                focalLength = focalLength,
                flash = flash,
                gps = gps
            )
        } catch (e: Exception) {
            null
        }
    }
}

