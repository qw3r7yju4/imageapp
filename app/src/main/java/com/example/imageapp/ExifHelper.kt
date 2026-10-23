package com.example.imageapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
        val cameraMake: String? = null,
        val cameraModel: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val copyright: String? = null,
        val aperture: String?,
        val iso: String?,
        val exposureTime: String?,
        val focalLength: String?,
        val flash: String?,
        val gps: String?,
        val latitude: Double? = null,
        val longitude: Double? = null
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

            val artist = exif.getAttribute(ExifInterface.TAG_ARTIST)?.trim()
            val description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)?.trim()
            val copyright = exif.getAttribute(ExifInterface.TAG_COPYRIGHT)?.trim()

            ExifData(
                fileName = file.name,
                filePath = file.absolutePath,
                formattedSize = formattedSize,
                dimensions = dimensions,
                dateTaken = dateStr,
                camera = camera,
                cameraMake = make.ifEmpty { null },
                cameraModel = model.ifEmpty { null },
                artist = artist,
                description = description,
                copyright = copyright,
                aperture = aperture,
                iso = iso,
                exposureTime = exposureTime,
                focalLength = focalLength,
                flash = flash,
                gps = gps,
                latitude = latLong?.get(0),
                longitude = latLong?.get(1)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun openLocationInMaps(context: Context, latitude: Double, longitude: Double, label: String = "Photo Location") {
        try {
            val encodedLabel = Uri.encode(label)
            val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)")
            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
            } else {
                val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open map: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveExifData(
        filePath: String,
        dateTaken: String?,
        cameraMake: String?,
        cameraModel: String?,
        artist: String?,
        description: String?,
        latitude: Double?,
        longitude: Double?
    ): Boolean {
        return try {
            val exif = ExifInterface(filePath)
            if (!dateTaken.isNullOrBlank()) {
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTaken)
                exif.setAttribute(ExifInterface.TAG_DATETIME, dateTaken)
            }
            if (!cameraMake.isNullOrBlank()) {
                exif.setAttribute(ExifInterface.TAG_MAKE, cameraMake)
            }
            if (!cameraModel.isNullOrBlank()) {
                exif.setAttribute(ExifInterface.TAG_MODEL, cameraModel)
            }
            if (artist != null) {
                exif.setAttribute(ExifInterface.TAG_ARTIST, artist)
            }
            if (description != null) {
                exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, description)
            }
            if (latitude != null && longitude != null) {
                exif.setLatLong(latitude, longitude)
            }
            exif.saveAttributes()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun removeGpsLocation(filePath: String): Boolean {
        return try {
            val exif = ExifInterface(filePath)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null)
            exif.saveAttributes()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun copyExif(sourcePath: String, destPath: String) {
        try {
            val src = ExifInterface(sourcePath)
            val dest = ExifInterface(destPath)
            val tags = listOf(
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_ARTIST,
                ExifInterface.TAG_IMAGE_DESCRIPTION,
                ExifInterface.TAG_COPYRIGHT,
                ExifInterface.TAG_FLASH,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_WHITE_BALANCE
            )
            for (tag in tags) {
                val value = src.getAttribute(tag)
                if (value != null) {
                    dest.setAttribute(tag, value)
                }
            }
            val latLong = src.latLong
            if (latLong != null) {
                dest.setLatLong(latLong[0], latLong[1])
            }
            dest.saveAttributes()
        } catch (_: Exception) {}
    }
}

