package com.xiaohan.xhsnotegen.ui.publish

import com.xiaohan.xhsnotegen.i18n.tr
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
            // Tags only become real (clickable) XHS topics with an id from topic search.
            val topics = hashtags.map { resolveTopic(cookies, a1, it) ?: Topic(name = it) }
            createNote(cookies, a1, buildNoteBody(title, body, topics, imageMetas))
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
            checkResponse(tr("Upload permit", "上传许可"), resp.code, raw)
        }

        @Suppress("UNCHECKED_CAST")
        val permits = ((permitData["data"] as? Map<String, Any?>)?.get("uploadTempPermits") as? List<Map<String, Any?>>)
            ?: throw ApiFailure(tr("Upload permit: unexpected response", "上传许可：返回内容异常"))

        // Flatten: each permit entry can carry several file ids.
        val slots = permits.flatMap { entry ->
            val token = entry["token"] as? String ?: ""
            val addr = entry["uploadAddr"] as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            (entry["fileIds"] as? List<String>).orEmpty().map { Triple(it, addr, token) }
        }
        if (slots.size < images.size) {
            throw ApiFailure(tr("Only got ${slots.size} upload slots for ${images.size} photos", "${images.size} 张照片只拿到 ${slots.size} 个上传位"))
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
                if (resp.code !in listOf(200, 204)) throw ApiFailure(tr("Photo ${i + 1} upload failed (HTTP ${resp.code})", "第 ${i + 1} 张照片上传失败（HTTP ${resp.code}）"))
            }
            fileId
        }
    }

    /** A tag to publish. [id] is empty when XHS has no matching topic (then it stays plain text). */
    data class Topic(val name: String, val id: String = "", val link: String = "")

    /**
     * Looks up the XHS topic for [keyword] (same call the web editor makes when
     * you type "#"). Accepts an exact name match, else a close one (one name
     * contains the other) — never an unrelated suggestion.
     * Any failure just returns null — the tag is then posted as plain text.
     */
    private fun resolveTopic(cookies: String, a1: String, keyword: String): Topic? = runCatching {
        val uri = "/web_api/sns/v1/search/topic"
        val data = mapOf(
            "keyword" to keyword,
            "suggest_topic_request" to mapOf("title" to "", "desc" to ""),
            "page" to mapOf("page_size" to 20, "page" to 1),
        )
        val headers = getSignedHeaders(uri, data, a1)
        val json = client.newCall(Request.Builder()
            .url("https://edith.xiaohongshu.com$uri")
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .addHeader("Cookie", cookies)
            .post(gson.toJson(data).toRequestBody("application/json".toMediaType()))
            .build()
        ).execute().use { resp -> checkResponse(tr("Topic search", "话题搜索"), resp.code, resp.body?.string().orEmpty()) }

        @Suppress("UNCHECKED_CAST")
        val dtos = ((json["data"] as? Map<String, Any?>)?.get("topic_info_dtos") as? List<Map<String, Any?>>).orEmpty()
        pickTopic(keyword, dtos)
    }.getOrNull()

    internal fun pickTopic(keyword: String, dtos: List<Map<String, Any?>>): Topic? {
        val candidates = dtos.mapNotNull { d ->
            val id = d["id"] as? String ?: return@mapNotNull null
            val name = d["name"] as? String ?: return@mapNotNull null
            Topic(name = name, id = id, link = d["link"] as? String ?: "")
        }
        val k = keyword.trim()
        return candidates.firstOrNull { it.name.equals(k, ignoreCase = true) }
            ?: candidates.firstOrNull { it.name.contains(k, ignoreCase = true) || k.contains(it.name, ignoreCase = true) }
    }

    /**
     * Note body as XHS expects it: linked topics are written "#name[话题]#"
     * (the web editor's own format); unlinked tags stay plain "#name".
     */
    internal fun descWithTopics(body: String, topics: List<Topic>): String {
        if (topics.isEmpty()) return body
        val tags = topics.joinToString(" ") { if (it.id.isNotEmpty()) "#${it.name}[话题]#" else "#${it.name}" }
        return "$body\n$tags"
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
            checkResponse(tr("Create note", "发布笔记"), resp.code, raw)
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
        if (authExpired) throw ApiFailure(tr("$step: XHS login has expired", "$step：小红书登录已过期"), authExpired = true)
        if (httpCode != 200 || json == null) throw ApiFailure(tr("$step failed (HTTP $httpCode) ${raw.take(120)}", "$step 失败（HTTP $httpCode）${raw.take(120)}"))
        if (success == false || (code != null && code != 0)) {
            throw ApiFailure(tr("$step failed: ${msg.ifBlank { "code $code" }}", "$step 失败：${msg.ifBlank { "错误码 $code" }}"))
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
        title: String, body: String, topics: List<Topic>,
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
                "desc" to descWithTopics(body, topics),
                "ats" to emptyList<String>(),
                // Only real topics go in hash_tag; an entry without an id isn't a topic.
                "hash_tag" to topics.filter { it.id.isNotEmpty() }.map {
                    mapOf("id" to it.id, "name" to it.name, "type" to "topic", "link" to it.link)
                },
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
