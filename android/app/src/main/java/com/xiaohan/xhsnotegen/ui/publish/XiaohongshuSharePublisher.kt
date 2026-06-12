package com.xiaohan.xhsnotegen.ui.publish

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.xiaohan.xhsnotegen.domain.NoteDraft
import java.io.File

object XiaohongshuSharePublisher {

    fun publish(context: Context, draft: NoteDraft) {
        val variant = draft.variants.getOrNull(draft.selectedVariantIndex)
            ?: run {
                Toast.makeText(context, "No variant selected", Toast.LENGTH_SHORT).show()
                return
            }

        val shareText = buildString {
            appendLine(variant.title)
            appendLine()
            appendLine(variant.body)
            appendLine()
            append(variant.hashtags.joinToString(" "))
        }

        // Copy text to clipboard as fallback
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("XHS Note", shareText))
        Toast.makeText(context, "Note text copied to clipboard", Toast.LENGTH_SHORT).show()

        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, variant.title)

            val urisToShare = if (draft.selectedPublishPhotoUris.isNotEmpty()) {
                draft.selectedPublishPhotoUris
            } else {
                draft.photoUris
            }

            val imageUris = urisToShare.map { uriStr ->
                val uri = Uri.parse(uriStr)
                if (uri.scheme == "file") {
                    FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", File(uri.path!!)
                    )
                } else uri
            }
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(imageUris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val xhsPackage = "com.xingin.xhs"
        val pm = context.packageManager
        val resolvedInfo = pm.queryIntentActivities(
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg"), 0
        )
        val xhsInstalled = resolvedInfo.any { it.activityInfo.packageName == xhsPackage }

        if (xhsInstalled) {
            shareIntent.setPackage(xhsPackage)
            try {
                context.startActivity(shareIntent)
            } catch (e: Exception) {
                shareIntent.setPackage(null)
                context.startActivity(Intent.createChooser(shareIntent, "Share to Xiaohongshu"))
            }
        } else {
            shareIntent.setPackage(null)
            context.startActivity(Intent.createChooser(shareIntent, "Share (Xiaohongshu not found)"))
            Toast.makeText(
                context,
                "Xiaohongshu not found. Text copied to clipboard. You can paste it manually.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
