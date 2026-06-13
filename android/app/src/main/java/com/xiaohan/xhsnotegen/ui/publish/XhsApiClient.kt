package com.xiaohan.xhsnotegen.ui.publish

import android.util.Base64
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Direct XHS Creator API client running on Android.
 * API calls originate from the phone → same IP as cookies → no 406.
 * x-s signing uses the Python backend's real xhs library.
 */
object XhsApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    data class PublishResult(
        val success: Boolean,
        val noteId: String = "",
        val shareLink: String = "",
        val error: String = "",
    )

    /**
     * Publish a note to XHS. Calls are made from the device (same IP as cookies).
     */
    suspend fun publish(
        cookies: String,
        title: String,
        body: String,
        hashtags: List<String>,
        imagesBase64: List<String>,
    ): PublishResult = withContext(Dispatchers.IO) {
        try {
            val a1 = extractA1(cookies)

            // Step 1: Get upload permits
            val permitUri = "/api/media/v1/upload/web/permit"
            val permitParams = mapOf(
                "biz_name" to "spectrum", "scene" to "image",
                "file_count" to imagesBase64.size.toString(),
                "version" to "1", "source" to "web",
            )
            val permitQuery = permitParams.entries.joinToString("&") { "${it.key}=${it.value}" }
            val permitHeaders = getSignedHeaders("$permitUri?$permitQuery", null, a1)

            val permitResp = client.newCall(Request.Builder()
                .url("https://creator.xiaohongshu.com$permitUri?$permitQuery")
                .apply { permitHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .addHeader("Cookie", cookies)
                .get().build()
            ).execute()

            val permitBody = permitResp.body?.string() ?: ""

            if (permitResp.code != 200) {
                return@withContext PublishResult(false, error = "Permit ${permitResp.code}: $permitBody")
            }
            val permitData = gson.fromJson(permitBody, Map::class.java)
            val code = (permitData["code"] as? Double)?.toInt() ?: -1
            if (code != 0) {
                return@withContext PublishResult(false, error = "Permit code=$code")
            }

            @Suppress("UNCHECKED_CAST")
            val permitsRaw = (permitData["data"] as Map<String, Any>)["uploadTempPermits"] as List<Map<String, Any>>
            // Flatten: each permit entry can have multiple file_ids
            val fileEntries = mutableListOf<Pair<String, String>>() // fileId -> uploadAddr
            val fileTokens = mutableMapOf<String, String>() // fileId -> token
            for (entry in permitsRaw) {
                val token = entry["token"] as String
                val addr = entry["uploadAddr"] as String
                @Suppress("UNCHECKED_CAST")
                val fids = entry["fileIds"] as List<String>
                for (fid in fids) {
                    fileEntries.add(fid to addr)
                    fileTokens[fid] = token
                }
            }

            if (fileEntries.size < imagesBase64.size) {
                return@withContext PublishResult(false,
                    error = "Only got ${fileEntries.size} permits for ${imagesBase64.size} images")
            }

            // Step 2: Upload images
            val imageMetas = mutableListOf<Map<String, Any>>()
            for ((i, imgB64) in imagesBase64.withIndex()) {
                val (fileId, addr) = fileEntries[i]
                val token = fileTokens[fileId]!!
                val uploadUrl = "https://$addr/$fileId"

                val imgBytes = Base64.decode(imgB64, Base64.NO_WRAP)
                val uploadResp = client.newCall(Request.Builder()
                    .url(uploadUrl)
                    .put(imgBytes.toRequestBody("image/jpeg".toMediaType()))
                    .addHeader("x-cos-security-token", token)
                    .addHeader("Origin", "https://creator.xiaohongshu.com")
                    .build()
                ).execute()

                if (uploadResp.code !in listOf(200, 204)) {
                    return@withContext PublishResult(false,
                        error = "Upload ${i+1} HTTP ${uploadResp.code}")
                }

                imageMetas.add(mapOf(
                    "file_id" to fileId, "width" to 1024, "height" to 768,
                    "metadata" to mapOf("source" to -1),
                    "stickers" to mapOf("version" to 2, "floating" to emptyList<String>()),
                    "extra_info_json" to """{"mimeType":"image/jpeg","image_metadata":{"bg_color":"","origin_size":${imgBytes.size}}}""",
                ))
            }

            // Step 3: Create note
            val noteBody = buildNoteBody(title, body, hashtags, imageMetas)
            val noteUri = "/web_api/sns/v2/note"
            val noteHeaders = getSignedHeaders(noteUri, noteBody, a1)

            val noteResp = client.newCall(Request.Builder()
                .url("https://edith.xiaohongshu.com$noteUri")
                .apply { noteHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .addHeader("Cookie", cookies)
                .addHeader("Content-Type", "application/json")
                .post(gson.toJson(noteBody).toRequestBody("application/json".toMediaType()))
                .build()
            ).execute()

            val noteRespBody = noteResp.body?.string() ?: ""

            if (noteResp.code != 200) {
                return@withContext PublishResult(false, error = "Note ${noteResp.code}: $noteRespBody")
            }

            @Suppress("UNCHECKED_CAST")
            val noteData = gson.fromJson(noteRespBody, Map::class.java)
            val success = noteData["success"] as? Boolean ?: false
            if (success) {
                @Suppress("UNCHECKED_CAST")
                val data = noteData["data"] as Map<String, Any>
                val noteId = data["id"] as? String ?: ""
                val link = (noteData["share_link"] as? String)
                    ?: "https://www.xiaohongshu.com/discovery/item/$noteId"
                return@withContext PublishResult(true, noteId, link)
            } else {
                return@withContext PublishResult(false, error = "Note failed: ${noteData["msg"]}")
            }
        } catch (e: Exception) {
            return@withContext PublishResult(false, error = e.message ?: "Unknown error")
        }
    }

    /** Generate x-s headers locally — no backend needed. */
    private fun getSignedHeaders(uri: String, data: Map<String, Any?>?, a1: String): Map<String, String> {
        val sig = XhsSigner.sign(uri, data, a1 = a1)
        return mapOf(
            "x-s" to sig["x-s"]!!,
            "x-t" to sig["x-t"]!!,
            "x-s-common" to sig["x-s-common"]!!,
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

    @Suppress("UNCHECKED_CAST")
    private fun buildNoteBody(
        title: String, body: String, hashtags: List<String>,
        images: List<Map<String, Any>>,
    ): Map<String, Any?> {
        fun jsonStr(vararg pairs: Pair<String, Any?>): String {
            val map = mutableMapOf<String, Any?>()
            for ((k, v) in pairs) map[k] = v
            return gson.toJson(map)
        }

        return mapOf(
            "common" to mapOf(
                "type" to "normal", "note_id" to "",
                "source" to jsonStr(
                    "type" to "web", "ids" to "",
                    "extraInfo" to jsonStr("subType" to "official", "systemId" to "web"),
                ),
                "title" to title,
                "desc" to "$body\n${hashtags.joinToString(" ") { "#$it" }}",
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
