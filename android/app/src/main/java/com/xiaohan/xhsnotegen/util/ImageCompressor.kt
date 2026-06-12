package com.xiaohan.xhsnotegen.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
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

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

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

            val scaled = if (bitmap.width > MAX_WIDTH || bitmap.height > MAX_HEIGHT) {
                val ratio = minOf(
                    MAX_WIDTH.toFloat() / bitmap.width,
                    MAX_HEIGHT.toFloat() / bitmap.height,
                )
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt(),
                    (bitmap.height * ratio).toInt(),
                    true,
                )
            } else bitmap

            val outputStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            val bytes = outputStream.toByteArray()
            outputStream.close()

            if (scaled !== bitmap) scaled.recycle()
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
