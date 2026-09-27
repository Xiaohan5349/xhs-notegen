package com.xiaohan.xhsnotegen.util

import android.net.Uri
import java.io.File

/**
 * Deletes local image files previously copied into filesDir/images/.
 * Only file:// URIs are touched — content:// URIs belong to other apps
 * and must not be deleted.
 */
object ImageCleanup {

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
