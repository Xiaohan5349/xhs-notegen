package com.xiaohan.xhsnotegen.ui.publish

import com.xiaohan.xhsnotegen.util.HttpClientFactory
import com.xiaohan.xhsnotegen.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Direct XHS Creator API client running on Android.
 * API calls originate from the phone → same IP as cookies → no 406.
 * x-s signing is done on-device by [XhsSigner].
 */
object XhsApiClient {

    private val client = HttpClientFactory.shared

    // Same instance the signature is computed with — the body must be byte-identical.
    private val gson = XhsSigner.gson

    data class PublishResult(
        val success: Boolean,
        val noteId: String = "",
        val shareLink: String = "",
        val error: String = "",
        /** The server said the session is gone — the user must log in again. */
        val authExpired: Boolean = false,
    )

    private class ApiFailure(message: String, val authExpired: Boolean = false) : Exception(message)

    /**
     * Publish a note to XHS. Calls are made from the device (same IP as cookies).
     */
    suspend fun publish(
        cookies: String,
        title: String,
        body: String,
        hashtags: List<String>,
        images: List<ImageCompressor.Compressed>,
    ): PublishResult = withContext(Dispatchers.IO) {
        try {
            val a1 = extractA1(cookies)
            val fileIds = uploadImages(cookies, a1, images)
            val imageMetas = images.mapIndexed { i, img ->
                mapOf(
                    "file_id" to fileIds[i], "width" to img.width, "height" to img.height,
                    "metadata" to mapOf("source" to -1),
                    "stickers" to mapOf("version" to 2, "floating" to emptyList<String>()),
                    "extra_info_json" to """{"mimeType":"image/jpeg","image_metadata":{"bg_color":"","origin_size":${img.bytes.size}}}""",
                )
            }
            createNote(cookies, a1, buildNoteBody(title, body, hashtags, imageMetas))
        } catch (e: ApiFailure) {
            PublishResult(false, error = e.message.orEmpty(), authExpired = e.authExpired)
        } catch (e: Exception) {
            PublishResult(false, error = e.message ?: e.javaClass.simpleName)
        }
    }

    /** Step 1 + 2: get upload permits, PUT each image. Returns file ids in image order. */
    private fun uploadImages(cookies: String, a1: String, images: List<ImageCompressor.Compressed>): List<String> {
        val permitUri = "/api/media/v1/upload/web/permit"
        val permitQuery = listOf(
            "biz_name" to "spectrum", "scene" to "image",
            "file_count" to images.size.toString(),
            "version" to "1", "source" to "web",
        ).joinToString("&") { "${it.first}=${it.second}" }
        val permitHeaders = getSignedHeaders("$permitUri?$permitQuery", null, a1)

        val permitData = client.newCall(Request.Builder()
            .url("https://creator.xiaohongshu.com$permitUri?$permitQuery")
            .apply { permitHeaders.forEach { (k, v) -> addHeader(k, v) } }
            .addHeader("Cookie", cookies)
            .get().build()
        ).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            checkResponse("Upload permit", resp.code, raw)
        }

        @Suppress("UNCHECKED_CAST")
        val permits = ((permitData["data"] as? Map<String, Any?>)?.get("uploadTempPermits") as? List<Map<String, Any?>>)
            ?: throw ApiFailure("Upload permit: unexpected response")

        // Flatten: each permit entry can carry several file ids.
        val slots = permits.flatMap { entry ->
            val token = entry["token"] as? String ?: ""
            val addr = entry["uploadAddr"] as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            (entry["fileIds"] as? List<String>).orEmpty().map { Triple(it, addr, token) }
        }
        if (slots.size < images.size) {
            throw ApiFailure("Only got ${slots.size} upload slots for ${images.size} photos")
        }

        return images.mapIndexed { i, img ->
            val (fileId, addr, token) = slots[i]
            client.newCall(Request.Builder()
                .url("https://$addr/$fileId")
                .put(img.bytes.toRequestBody("image/jpeg".toMediaType()))
                .addHeader("x-cos-security-token", token)
                .addHeader("Origin", "https://creator.xiaohongshu.com")
                .build()
            ).execute().use { resp ->
                if (resp.code !in listOf(200, 204)) throw ApiFailure("Photo ${i + 1} upload failed (HTTP ${resp.code})")
            }
            fileId
        }
    }

    /** Step 3: create the note. */
    private fun createNote(cookies: String, a1: String, noteBody: Map<String, Any?>): PublishResult {
        val noteUri = "/web_api/sns/v2/note"
        val noteHeaders = getSignedHeaders(noteUri, noteBody, a1)

        val noteData = client.newCall(Request.Builder()
            .url("https://edith.xiaohongshu.com$noteUri")
            .apply { noteHeaders.forEach { (k, v) -> addHeader(k, v) } }
            .addHeader("Cookie", cookies)
            .post(gson.toJson(noteBody).toRequestBody("application/json".toMediaType()))
            .build()
        ).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            checkResponse("Create note", resp.code, raw)
        }

        @Suppress("UNCHECKED_CAST")
        val data = noteData["data"] as? Map<String, Any?>
        val noteId = data?.get("id") as? String ?: ""
        val link = (data?.get("share_link") as? String)
            ?: (noteData["share_link"] as? String)
            ?: "https://www.xiaohongshu.com/discovery/item/$noteId"
        return PublishResult(true, noteId, link)
    }

    /**
     * Parses an XHS JSON envelope and throws [ApiFailure] on failure,
     * flagging login problems so the UI can ask for a fresh login.
     */
    private fun checkResponse(step: String, httpCode: Int, raw: String): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val json = runCatching { gson.fromJson(raw, Map::class.java) as Map<String, Any?> }.getOrNull()
        val code = (json?.get("code") as? Number)?.toInt()
        val msg = json?.get("msg") as? String ?: ""
        val success = json?.get("success") as? Boolean

        val authExpired = httpCode == 401 || httpCode == 403 ||
            code == -100 || code == -101 || msg.contains("登录")
        if (authExpired) throw ApiFailure("$step: XHS login has expired", authExpired = true)
        if (httpCode != 200 || json == null) throw ApiFailure("$step failed (HTTP $httpCode) ${raw.take(120)}")
        if (success == false || (code != null && code != 0)) {
            throw ApiFailure("$step failed: ${msg.ifBlank { "code $code" }}")
        }
        return json
    }

    /** Generate x-s headers locally — no backend needed. */
    private fun getSignedHeaders(uri: String, data: Map<String, Any?>?, a1: String): Map<String, String> {
        val sig = XhsSigner.sign(uri, data, a1 = a1)
        return mapOf(
            "x-s" to sig.getValue("x-s"),
            "x-t" to sig.getValue("x-t"),
            "x-s-common" to sig.getValue("x-s-common"),
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/149.0.0.0 Safari/537.36",
            "Origin" to "https://creator.xiaohongshu.com",
            "Referer" to "https://creator.xiaohongshu.com/",
            "Accept" to "application/json, text/plain, */*",
            "Accept-Language" to "zh-CN,zh;q=0.8",
        )
    }

    private fun extractA1(cookies: String): String {
        return cookies.split(";")
            .map { it.trim().split("=", limit = 2) }
            .firstOrNull { it[0] == "a1" }
            ?.getOrNull(1) ?: ""
    }

    private fun buildNoteBody(
        title: String, body: String, hashtags: List<String>,
        images: List<Map<String, Any>>,
    ): Map<String, Any?> {
        fun jsonStr(vararg pairs: Pair<String, Any?>): String = gson.toJson(mapOf(*pairs))

        return mapOf(
            "common" to mapOf(
                "type" to "normal", "note_id" to "",
                "source" to jsonStr(
                    "type" to "web", "ids" to "",
                    "extraInfo" to jsonStr("subType" to "official", "systemId" to "web"),
                ),
                "title" to title,
                "desc" to if (hashtags.isEmpty()) body else "$body\n${hashtags.joinToString(" ") { "#$it" }}",
                "ats" to emptyList<String>(),
                "hash_tag" to hashtags.map { mapOf("id" to "", "name" to it) },
                "business_binds" to jsonStr(
                    "version" to 1, "noteId" to 0, "bizType" to 0,
                    "noteOrderBind" to emptyMap<String, Any>(),
                    "notePostTiming" to emptyMap<String, Any>(),
                    "noteCollectionBind" to mapOf("id" to ""),
                    "noteSketchCollectionBind" to mapOf("id" to ""),
                    "coProduceBind" to mapOf("enable" to false),
                    "noteCopyBind" to mapOf("copyable" to true),
                    "interactionPermissionBind" to mapOf("commentPermission" to 0),
                    "optionRelationList" to emptyList<Any>(),
                ),
                "privacy_info" to mapOf("op_type" to 1, "type" to 0, "user_ids" to emptyList<String>()),
                "goods_info" to emptyMap<String, Any>(),
                "biz_relations" to emptyList<Any>(),
                "capa_trace_info" to mapOf(
                    "contextJson" to jsonStr(
                        "recommend_title" to mapOf("recommend_title_id" to "", "is_use" to 3, "used_index" to -1),
                        "recommendTitle" to emptyList<Any>(),
                        "recommend_topics" to mapOf("used" to emptyList<Any>()),
                    ),
                ),
            ),
            "image_info" to mapOf("images" to images),
            "video_info" to null,
        )
    }
}
