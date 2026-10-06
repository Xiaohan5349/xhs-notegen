package com.xiaohan.xhsnotegen.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.IOException

object ImageCompressor {

    /** Longest-edge limit and JPEG quality for one use of an image. */
    data class Profile(val maxDim: Int, val quality: Int)

    /** Enough for the model to read the food; keeps 20-photo requests small. */
    val FOR_AI = Profile(maxDim = 1024, quality = 85)

    /** What followers see — XHS displays up to ~1080 wide, so leave headroom for zoom. */
    val FOR_PUBLISH = Profile(maxDim = 2160, quality = 92)

    class Compressed(val bytes: ByteArray, val width: Int, val height: Int) {
        fun toBase64(): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Decodes, applies EXIF orientation, scales to [profile] and re-encodes as JPEG. */
    @Throws(IOException::class)
    fun compress(context: Context, uri: Uri, profile: Profile): Compressed {
        val resolver = context.contentResolver
        fun open() = resolver.openInputStream(uri) ?: throw IOException("Cannot open image")

        // Phone cameras store rotation as metadata and BitmapFactory ignores it,
        // so a portrait photo would otherwise come out sideways.
        val orientation = try {
            open().use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                )
            }
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unsupported image format")

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, profile.maxDim)
        }
        val decoded = open().use { BitmapFactory.decodeStream(it, null, decodeOptions) }
            ?: throw IOException("Failed to decode image")

        // Scale and orient in a single pass so only one extra bitmap is ever alive.
        val longest = maxOf(decoded.width, decoded.height)
        val scale = if (longest > profile.maxDim) profile.maxDim.toFloat() / longest else 1f
        val matrix = Matrix().apply {
            postScale(scale, scale)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            }
        }
        val output = if (matrix.isIdentity) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        try {
            val bytes = ByteArrayOutputStream().use { out ->
                output.compress(Bitmap.CompressFormat.JPEG, profile.quality, out)
                out.toByteArray()
            }
            return Compressed(bytes, output.width, output.height)
        } finally {
            if (output !== decoded) output.recycle()
            decoded.recycle()
        }
    }

    /** Largest power-of-two subsample that still leaves the longest edge >= [maxDim]. */
    internal fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val longest = maxOf(rawWidth, rawHeight)
        while (longest / (inSampleSize * 2) >= maxDim) inSampleSize *= 2
        return inSampleSize
    }
}
