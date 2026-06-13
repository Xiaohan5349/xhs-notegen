package com.xiaohan.xhsnotegen.util

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ExifReader {

    data class ExifData(
        val captureDate: String?,   // yyyy-MM-dd format
        val location: String?,      // human-readable city/area
    )

    fun read(context: Context, uri: Uri): ExifData {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val exif = inputStream?.let { ExifInterface(it) }
            inputStream?.close()

            if (exif == null) return ExifData(null, null)

            val date = readDate(exif)
            val location = readLocation(context, exif)
            ExifData(date, location)
        } catch (e: IOException) {
            ExifData(null, null)
        }
    }

    fun aggregate(context: Context, uris: List<Uri>): ExifData {
        val allData = uris.map { read(context, it) }

        val dates = allData.mapNotNull { it.captureDate }.sorted()
        val earliest = dates.firstOrNull()

        val locations = allData.mapNotNull { it.location }
            .groupBy { it }
            .maxByOrNull { it.value.size }
            ?.key

        return ExifData(earliest, locations)
    }

    private fun readDate(exif: ExifInterface): String? {
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null

        return try {
            val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date: Date = parser.parse(raw) ?: return null
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            formatter.format(date)
        } catch (e: Exception) {
            null
        }
    }

    private fun readLocation(context: Context, exif: ExifInterface): String? {
        val latLong = exif.latLong ?: return null
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latLong[0], latLong[1], 1)
            addresses?.firstOrNull()?.let { addr ->
                addr.locality ?: addr.subAdminArea ?: addr.adminArea
            }
        } catch (e: IOException) {
            null
        }
    }
}
