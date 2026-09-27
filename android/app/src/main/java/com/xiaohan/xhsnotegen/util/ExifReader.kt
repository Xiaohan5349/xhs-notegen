package com.xiaohan.xhsnotegen.util

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExifReader {

    data class ExifData(
        val captureDate: String?,   // yyyy-MM-dd HH:mm format
        val location: String?,      // human-readable city/area
    )

    fun read(context: Context, uri: Uri): ExifData {
        return try {
            val exif = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
                ?: return ExifData(null, null)
            ExifData(readDate(exif), readLocation(context, exif))
        } catch (e: Exception) {
            // Malformed EXIF (bad GPS values, truncated files) must never crash photo picking.
            ExifData(null, null)
        }
    }

    fun aggregate(context: Context, uris: List<Uri>): ExifData {
        val allData = uris.map { read(context, it) }

        val earliest = allData.mapNotNull { it.captureDate }.minOrNull()

        val location = allData.mapNotNull { it.location }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }
            ?.key

        return ExifData(earliest, location)
    }

    private fun readDate(exif: ExifInterface): String? {
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null

        return try {
            // EXIF timestamps are in LOCAL time — parse as local, not UTC,
            // otherwise the exported date can be a day off around midnight.
            val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            val date: Date = parser.parse(raw) ?: return null
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(date)
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION") // the async overload is API 33+; this runs on Dispatchers.IO
    private fun readLocation(context: Context, exif: ExifInterface): String? {
        val latLong = exif.latLong ?: return null
        return try {
            val addresses = Geocoder(context, Locale.getDefault())
                .getFromLocation(latLong[0], latLong[1], 1)
            addresses?.firstOrNull()?.let { addr ->
                addr.locality ?: addr.subAdminArea ?: addr.adminArea
            }
        } catch (e: Exception) {
            null
        }
    }
}
