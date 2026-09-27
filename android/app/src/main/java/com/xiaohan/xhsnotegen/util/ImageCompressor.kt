package com.xiaohan.xhsnotegen.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

object ImageCompressor {
    private const val MAX_WIDTH = 1024
    private const val MAX_HEIGHT = 1024
    private const val JPEG_QUALITY = 85

    data class CompressedImage(
        val base64: String,
        val success: Boolean,
        val error: String? = null,
    )

    fun compress(context: Context, uri: Uri): CompressedImage {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return CompressedImage("", false, "Cannot open image: $uri")

            // Read EXIF orientation FIRST — phone cameras store rotation as
            // metadata and BitmapFactory ignores it, so a portrait photo would
            // otherwise be compressed (and later published) sideways.
            val orientation = try {
                ExifInterface(inputStream)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL)
            } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
            inputStream.close()

            val boundsStream = context.contentResolver.openInputStream(uri)
                ?: return CompressedImage("", false, "Cannot reopen image: $uri")
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(boundsStream, null, options)
            boundsStream.close()

            val stream = context.contentResolver.openInputStream(uri)
                ?: return CompressedImage("", false, "Cannot reopen image: $uri")

            val sampleSize = calculateInSampleSize(
                options.outWidth, options.outHeight, MAX_WIDTH, MAX_HEIGHT
            )
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeStream(stream, null, decodeOptions)
            stream.close()

            if (bitmap == null) {
                return CompressedImage("", false, "Failed to decode: $uri")
            }

            // Apply EXIF orientation (rotate/flip) before scaling.
            val matrix = Matrix().apply {
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
            val oriented = if (!matrix.isIdentity) {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else bitmap

            val scaled = if (oriented.width > MAX_WIDTH || oriented.height > MAX_HEIGHT) {
                val ratio = minOf(
                    MAX_WIDTH.toFloat() / oriented.width,
                    MAX_HEIGHT.toFloat() / oriented.height,
                )
                Bitmap.createScaledBitmap(
                    oriented,
                    (oriented.width * ratio).toInt(),
                    (oriented.height * ratio).toInt(),
                    true,
                )
            } else oriented

            val outputStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            val bytes = outputStream.toByteArray()
            outputStream.close()

            if (scaled !== oriented) scaled.recycle()
            if (oriented !== bitmap) oriented.recycle()
            bitmap.recycle()

            CompressedImage(Base64.encodeToString(bytes, Base64.NO_WRAP), true)
        } catch (e: Exception) {
            CompressedImage("", false, "Compression error: ${e.message}")
        }
    }

    private fun calculateInSampleSize(
        rawWidth: Int, rawHeight: Int, reqWidth: Int, reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                   (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
