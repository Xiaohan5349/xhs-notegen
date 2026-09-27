package com.xiaohan.xhsnotegen.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * App-private photo copies in filesDir/images/. Photo Picker grants are
 * temporary, so every draft photo is copied here and referenced by file:// URI.
 * Only file:// URIs are ever deleted — content:// URIs belong to other apps.
 */
object ImageCleanup {

    fun imagesDir(context: Context): File =
        File(context.filesDir, "images").also { it.mkdirs() }

    /** Copies [source] into app storage. Returns null if it can't be read. */
    fun copyToLocal(context: Context, source: Uri): Uri? {
        val dest = File(imagesDir(context), "img_${UUID.randomUUID()}.jpg")
        return try {
            val copied = context.contentResolver.openInputStream(source)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            if (copied == null || copied == 0L) { dest.delete(); null } else Uri.fromFile(dest)
        } catch (e: Exception) {
            dest.delete()
            null
        }
    }

    /** True if [uriStr] is a local copy whose file still exists. */
    fun localFileExists(uriStr: String): Boolean {
        val uri = Uri.parse(uriStr)
        return uri.scheme == "file" && uri.path?.let { File(it).exists() } == true
    }

    fun deleteLocalFiles(uris: List<String>) {
        for (uriStr in uris) {
            try {
                val uri = Uri.parse(uriStr)
                if (uri.scheme == "file") {
                    File(uri.path ?: continue).delete()
                }
            } catch (_: Exception) { }
        }
    }
}
