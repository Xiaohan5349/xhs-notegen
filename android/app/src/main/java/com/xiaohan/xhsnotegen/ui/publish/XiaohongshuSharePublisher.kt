package com.xiaohan.xhsnotegen.ui.publish

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteVariant
import com.xiaohan.xhsnotegen.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Posts a draft to XHS: direct Creator API first, manual handoff
 * (clipboard + gallery + open the XHS app) as the fallback.
 *
 * Uses only the application context — publishing runs in a ViewModel and
 * must not hold on to an Activity.
 */
object XiaohongshuSharePublisher {

    const val TITLE_LIMIT = 20
    private const val GALLERY_FOLDER = "XHSNoteGen"
    private val GALLERY_RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/$GALLERY_FOLDER/"

    sealed class PublishResult {
        data class Success(val shareLink: String) : PublishResult()
        /** No saved login, or XHS said the session expired. */
        data class NeedsLogin(val expired: Boolean) : PublishResult()
        data class Error(val message: String) : PublishResult()
        /** Handed off to the XHS app. [reason] explains why direct publish didn't happen. */
        data class Handoff(val reason: String?, val openedXhs: Boolean) : PublishResult()
    }

    /** Try direct API publish, fall back to handoff. */
    suspend fun publish(context: Context, draft: NoteDraft): PublishResult {
        val variant = validate(draft) ?: return PublishResult.Error(validationError(draft))

        val cookies = XhsAuthStore.getCookies(context)
            ?: return PublishResult.NeedsLogin(expired = false)

        val images = when (val loaded = loadImages(context, draft)) {
            is Loaded.Ok -> loaded.images
            is Loaded.Failed -> return PublishResult.Error(loaded.message)
        }

        val result = try {
            XhsApiClient.publish(
                cookies = cookies,
                title = variant.title,
                body = variant.body,
                hashtags = variant.hashtags,
                images = images,
            )
        } catch (e: Exception) {
            XhsApiClient.PublishResult(false, error = e.message ?: "Unexpected error")
        }

        if (result.success) {
            // Local photos are intentionally kept: this is a diary, and the draft
            // still shows (and may regenerate from) them. They are removed when
            // the draft itself is deleted.
            clearHandoffGallery(context)
            return PublishResult.Success(result.shareLink)
        }
        if (result.authExpired) {
            XhsAuthStore.clear(context)
            return PublishResult.NeedsLogin(expired = true)
        }
        // Signature drift, risk control, network... the note can still be posted by hand.
        return handoff(context, draft, images, reason = result.error)
    }

    /** Manual path, also offered directly when the user isn't logged in. */
    suspend fun handoff(context: Context, draft: NoteDraft): PublishResult {
        validate(draft) ?: return PublishResult.Error(validationError(draft))
        return when (val loaded = loadImages(context, draft)) {
            is Loaded.Ok -> handoff(context, draft, loaded.images, reason = null)
            is Loaded.Failed -> PublishResult.Error(loaded.message)
        }
    }

    private fun validate(draft: NoteDraft): NoteVariant? {
        val v = draft.selectedVariant ?: return null
        if (v.title.isBlank() || v.body.isBlank()) return null
        if (v.title.length > TITLE_LIMIT) return null
        if (draft.publishPhotoUris.isEmpty()) return null
        return v
    }

    private fun validationError(draft: NoteDraft): String {
        val v = draft.selectedVariant
        return when {
            v == null -> "No note selected"
            v.title.isBlank() -> "Add a title first"
            v.body.isBlank() -> "The note body is empty"
            // XHS rejects titles over 20 characters; fail early with an actionable message.
            v.title.length > TITLE_LIMIT -> "标题超长（${v.title.length} 字，上限 $TITLE_LIMIT 字），请缩短后再发布"
            else -> "Select at least one photo"
        }
    }

    private sealed class Loaded {
        class Ok(val images: List<ImageCompressor.Compressed>) : Loaded()
        class Failed(val message: String) : Loaded()
    }

    /** Every selected photo must load — silently posting fewer photos is worse than an error. */
    private suspend fun loadImages(context: Context, draft: NoteDraft): Loaded = withContext(Dispatchers.IO) {
        val uris = draft.publishPhotoUris
        val images = uris.mapIndexed { i, uriStr ->
            try {
                ImageCompressor.compress(context, Uri.parse(uriStr), ImageCompressor.FOR_PUBLISH)
            } catch (e: Exception) {
                return@withContext Loaded.Failed("Photo ${i + 1} can't be read anymore. Deselect it and try again.")
            }
        }
        Loaded.Ok(images)
    }

    private fun shareText(variant: NoteVariant) = buildString {
        appendLine(variant.title)
        appendLine()
        appendLine(variant.body)
        if (variant.hashtags.isNotEmpty()) {
            appendLine()
            append(variant.hashtags.joinToString(" ") { "#$it" })
        }
    }.trim()

    private suspend fun handoff(
        context: Context,
        draft: NoteDraft,
        images: List<ImageCompressor.Compressed>,
        reason: String?,
    ): PublishResult {
        val variant = draft.selectedVariant ?: return PublishResult.Error("No note selected")

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("note", shareText(variant)))

        val savedCount = withContext(Dispatchers.IO) {
            clearHandoffGallery(context)
            val stamp = System.currentTimeMillis()
            images.withIndex().count { (i, img) ->
                try {
                    val values = ContentValues().apply {
                        // Zero-padded index keeps gallery order = publish order.
                        put(MediaStore.Images.Media.DISPLAY_NAME, "XHS_${stamp}_%02d.jpg".format(i + 1))
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, GALLERY_RELATIVE_PATH)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: return@count false
                    context.contentResolver.openOutputStream(uri)?.use { it.write(img.bytes) } != null
                } catch (_: Exception) {
                    false
                }
            }
        }
        if (savedCount == 0) return PublishResult.Error("Couldn't save photos to the gallery")

        return PublishResult.Handoff(reason = reason, openedXhs = openXhs(context))
    }

    /** Removes photos this app saved to Pictures/XHSNoteGen by an earlier handoff. */
    private fun clearHandoffGallery(context: Context) {
        try {
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                arrayOf(GALLERY_RELATIVE_PATH),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                    // Only our own files are deletable without a user prompt;
                    // anything else (e.g. after a reinstall) is left alone.
                    try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }
    }

    private fun openXhs(context: Context): Boolean {
        val pm = context.packageManager
        val pkg = findXhsPackage(pm) ?: return false
        val candidates = listOfNotNull(
            pm.getLaunchIntentForPackage(pkg),
            Intent(Intent.ACTION_VIEW, Uri.parse("xhsdiscover://")).setPackage(pkg)
                .takeIf { pm.resolveActivity(it, 0) != null },
        )
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) { }
        }
        return false
    }

    /** Visible thanks to the <queries> block in the manifest (Android 11+ package visibility). */
    private fun findXhsPackage(pm: PackageManager): String? =
        listOf("com.xingin.xhs", "com.xingin.xhs.lite", "com.xingin.xhs.intl", "com.xingin.xhs.global")
            .firstOrNull { pkg ->
                try { pm.getPackageInfo(pkg, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
            }
}
