package com.xiaohan.xhsnotegen.ui.publish

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object XiaohongshuSharePublisher {

    sealed class PublishResult {
        data class Success(val shareLink: String) : PublishResult()
        data object NeedsLogin : PublishResult()
        data class Error(val message: String) : PublishResult()
        data object Handoff : PublishResult()
    }

    /**
     * Try direct API publish via backend (now with x-s signing), fall back to handoff.
     */
    suspend fun publish(context: Context, draft: NoteDraft): PublishResult {
        val variant = draft.variants.getOrNull(draft.selectedVariantIndex)
            ?: return PublishResult.Error("No variant selected")

        // Compress images
        val urisToShare = if (draft.selectedPublishPhotoUris.isNotEmpty()) {
            draft.selectedPublishPhotoUris
        } else {
            draft.photoUris
        }

        val imagesBase64 = withContext(Dispatchers.IO) {
            urisToShare.map { uriStr ->
                val uri = Uri.parse(uriStr)
                val compressed = ImageCompressor.compress(context, uri)
                if (compressed.success) compressed.base64 else ""
            }.filter { it.isNotBlank() }
        }

        if (imagesBase64.isEmpty()) {
            return PublishResult.Error("No valid images to publish")
        }

        val shareText = buildString {
            appendLine(variant.title)
            appendLine()
            appendLine(variant.body)
            appendLine()
            append(variant.hashtags.joinToString(" "))
        }

        // Copy text to clipboard always
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("note", shareText))

        // Try direct API publish from Android (same IP as cookies)
        val cookies = XhsAuthStore.getCookies(context)
        if (!cookies.isNullOrBlank()) {
            try {
                val result = XhsApiClient.publish(
                    cookies = cookies,
                    title = variant.title,
                    body = variant.body,
                    hashtags = variant.hashtags,
                    imagesBase64 = imagesBase64,
                )
                if (result.success) {
                    deleteLocalImages(context, draft)
                    return PublishResult.Success(result.shareLink)
                }
                // Show the error from the API
                return PublishResult.Error(result.error.ifBlank { "Publish failed" })
            } catch (e: Exception) {
                return PublishResult.Error("Publish error: ${e.message}")
            }
        } else {
            return PublishResult.NeedsLogin
        }
    }

    /**
     * Manual handoff: save images to public Pictures folder and open XHS.
     */
    private suspend fun handoffToXhs(
        context: Context,
        draft: NoteDraft,
        imagesBase64: List<String>,
    ): PublishResult {
        val savedCount = withContext(Dispatchers.IO) {
            val galleryDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "XHSNoteGen"
            )
            galleryDir.mkdirs()
            galleryDir.listFiles()?.forEach { it.delete() }

            var count = 0
            imagesBase64.forEachIndexed { i, b64 ->
                try {
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME,
                            "XHS_${System.currentTimeMillis()}_$i.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_PICTURES}/XHSNoteGen")
                    }
                    context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                    )?.let { mediaUri ->
                        context.contentResolver.openOutputStream(mediaUri)?.use { out ->
                            out.write(bytes)
                            count++
                        }
                    }
                } catch (_: Exception) { }
            }
            count
        }

        if (savedCount == 0) {
            return PublishResult.Error("Failed to save images for handoff")
        }

        // Open XHS app
        val pm = context.packageManager
        val xhsPkg = findXhsPackage(pm)

        if (xhsPkg != null) {
            for (strategy in listOf(
                { pm.getLaunchIntentForPackage(xhsPkg) },
                {
                    Intent(Intent.ACTION_MAIN).apply {
                        setPackage(xhsPkg)
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }.takeIf { pm.resolveActivity(it, 0) != null }
                },
                {
                    Intent(Intent.ACTION_VIEW, Uri.parse("xhsdiscover://")).apply {
                        setPackage(xhsPkg)
                    }.takeIf { pm.resolveActivity(it, 0) != null }
                }
            )) {
                try {
                    val intent = strategy()
                    if (intent != null) {
                        context.startActivity(intent)
                        Toast.makeText(context,
                            "已打开小红书\n文字已复制，照片在 Pictures/XHSNoteGen",
                            Toast.LENGTH_LONG).show()
                        return PublishResult.Handoff
                    }
                } catch (_: Exception) { }
            }
        }

        // Ultimate fallback: share chooser
        try {
            context.startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "打开小红书粘贴内容\n照片在 Pictures/XHSNoteGen")
                }, "分享到小红书"))
        } catch (_: Exception) { }

        Toast.makeText(context,
            "文字已复制到剪贴板，照片在 Pictures/XHSNoteGen\n请打开小红书粘贴并上传",
            Toast.LENGTH_LONG).show()
        return PublishResult.Handoff
    }

    /** Delete local image copies after successful publish. XHS has them now. */
    private fun deleteLocalImages(context: Context, draft: NoteDraft) {
        val allUris = draft.photoUris + draft.selectedPublishPhotoUris
        for (uriStr in allUris) {
            try {
                val uri = Uri.parse(uriStr)
                if (uri.scheme == "file") {
                    File(uri.path ?: continue).delete()
                }
            } catch (_: Exception) { }
        }
        // Also clean the Pictures/XHSNoteGen handoff directory
        try {
            val galleryDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "XHSNoteGen"
            )
            galleryDir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) { }
    }

    private fun findXhsPackage(pm: android.content.pm.PackageManager): String? {
        val candidates = listOf(
            "com.xingin.xhs", "com.xingin.xhs.lite",
            "com.xingin.xhs.intl", "com.xingin.xhs.global",
        )
        for (pkg in candidates) {
            try { pm.getPackageInfo(pkg, 0); return pkg } catch (_: Exception) { }
        }
        val allApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            android.content.pm.PackageManager.MATCH_ALL
        )
        for (ri in allApps) {
            val pkg = ri.activityInfo.packageName
            val label = ri.loadLabel(pm).toString()
            if (pkg.contains("xingin") || pkg.contains("xhs") ||
                label.contains("小红书") || label.contains("红书")
            ) {
                return pkg
            }
        }
        return null
    }
}
