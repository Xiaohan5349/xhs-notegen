package com.xiaohan.xhsnotegen.util

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface

/**
 * Reads GPS from the ORIGINAL photo. The photo picker hands apps a copy with the
 * location zeroed out (verified: date kept, GPS = 0,0). Android only releases
 * real coordinates to apps holding photo access + ACCESS_MEDIA_LOCATION.
 */
object PhotoLocation {

    private const val TAG = "PhotoLocation"

    /** Permissions to request; photo access is what makes ACCESS_MEDIA_LOCATION useful. */
    fun permissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.ACCESS_MEDIA_LOCATION)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
    }

    fun isGranted(context: Context): Boolean {
        fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        val photos = when {
            Build.VERSION.SDK_INT >= 34 -> has(Manifest.permission.READ_MEDIA_IMAGES) || has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> has(Manifest.permission.READ_MEDIA_IMAGES)
            else -> has(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return photos && has(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }

    /** Coordinates of the photo behind [picked], or null if unavailable. */
    fun read(context: Context, picked: Uri): DoubleArray? {
        val candidates = listOfNotNull(originalMediaUri(picked), picked)
        for (uri in candidates) {
            val result = runCatching {
                // A file descriptor, not a stream: ExifInterface can seek, and
                // MediaProvider serves the original bytes for requireOriginal.
                context.contentResolver.openFileDescriptor(uri, "r")?.use {
                    ExifInterface(it.fileDescriptor).latLong
                }
            }
            val latLong = result.getOrNull()
            // Logs outcome only — never the coordinates.
            Log.d(TAG, "read ${if (uri == candidates.first()) "original" else "picker copy"}: " +
                (result.exceptionOrNull()?.javaClass?.simpleName ?: if (latLong == null) "no GPS" else if (PlaceResolver.isNullIsland(latLong[0], latLong[1])) "GPS zeroed" else "GPS ok"))
            if (latLong != null && !PlaceResolver.isNullIsland(latLong[0], latLong[1])) return latLong
        }
        return null
    }

    /**
     * Picker URIs end in the MediaStore id of on-device photos
     * (…/photopicker/media/1000000033). Wrapped with setRequireOriginal, that
     * MediaStore URI returns unredacted EXIF when permission is granted.
     */
    private fun originalMediaUri(picked: Uri): Uri? {
        if (picked.authority != MediaStore.AUTHORITY) return null
        val id = picked.lastPathSegment?.toLongOrNull() ?: return null
        return runCatching {
            MediaStore.setRequireOriginal(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id))
        }.getOrNull()
    }
}
