package com.xiaohan.xhsnotegen.ui.generate

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xiaohan.xhsnotegen.data.json.JsonCodec
import com.xiaohan.xhsnotegen.data.json.VariantDto
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.domain.NoteVariant
import com.xiaohan.xhsnotegen.util.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Direct Gemini REST API client. No backend needed.
 */
object GeminiClient {

    const val DEFAULT_MODEL = "gemini-2.5-flash"

    private const val PREFS_NAME = "gemini_config"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"

    private val client = HttpClientFactory.shared

    class GeminiException(message: String, val retryable: Boolean = false) : Exception(message)

    // ---- Settings ----

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getApiKey(context: Context): String? =
        prefs(context).getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun saveApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    /** User-configurable so a newer model can be tried without a rebuild. */
    fun getModel(context: Context): String =
        prefs(context).getString(KEY_MODEL, null)?.let(::sanitizeModel) ?: DEFAULT_MODEL

    fun saveModel(context: Context, model: String) {
        prefs(context).edit().putString(KEY_MODEL, sanitizeModel(model) ?: "").apply()
    }

    /** Accepts "gemini-x" or "models/gemini-x"; rejects anything that isn't a model id. */
    private fun sanitizeModel(raw: String): String? =
        raw.trim().removePrefix("models/").takeIf { it.matches(Regex("[A-Za-z0-9._-]+")) }

    // ---- Generation ----

    /**
     * Generates one variant per style in a single request: the photos are
     * uploaded once instead of once per style, and the model can deliberately
     * make the variants differ from each other. Result follows [styles] order.
     */
    suspend fun generateVariants(
        context: Context,
        systemPrompt: String,
        userPrompt: String,
        imagesBase64: List<String>,
        styles: List<NoteStyle>,
    ): List<NoteVariant> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
            ?: throw GeminiException("Add your Gemini API key in Settings first.")
        val model = getModel(context)

        val parts = mutableListOf<Map<String, Any>>(mapOf("text" to userPrompt))
        imagesBase64.forEach { img ->
            parts += mapOf("inline_data" to mapOf("mime_type" to "image/jpeg", "data" to img))
        }
        val request = mapOf(
            "system_instruction" to mapOf("parts" to listOf(mapOf("text" to systemPrompt))),
            "contents" to listOf(mapOf("role" to "user", "parts" to parts)),
            "generation_config" to mapOf(
                "temperature" to 1.0,
                // 2.5-series models spend "thinking" tokens from this same budget;
                // 2048 could truncate the JSON mid-note on longer inputs.
                "max_output_tokens" to 8192,
                "response_mime_type" to "application/json",
                "response_schema" to responseSchema(styles),
            ),
        )
        val body = JsonCodec.toJson(request)

        retrying { parseVariants(call(model, apiKey, body), styles) }
    }

    /** One retry for transient failures (rate limits, 5xx, network, truncated JSON). */
    private suspend fun <T> retrying(block: () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: GeminiException) {
                if (!e.retryable || attempt >= 1) throw e
            } catch (e: IOException) {
                if (attempt >= 1) throw GeminiException("Network error: ${e.message ?: "connection failed"}")
            }
            attempt++
            delay(3_000)
        }
    }

    private fun call(model: String, apiKey: String, body: String): String {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw httpError(resp.code, raw, model)

            val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
                ?: throw GeminiException("Gemini returned an unreadable response", retryable = true)

            json.obj("promptFeedback")?.str("blockReason")?.let {
                throw GeminiException("Gemini refused the request ($it)")
            }
            val candidate = json.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject
                ?: throw GeminiException("Gemini returned no content", retryable = true)

            // Skip thought-summary parts; the answer is the non-thought text.
            val text = candidate.obj("content")?.getAsJsonArray("parts")
                ?.mapNotNull { it.asJsonObject }
                ?.filter { it.get("thought")?.asBoolean != true }
                ?.mapNotNull { it.str("text") }
                ?.joinToString("")
                .orEmpty()

            when (candidate.str("finishReason")) {
                "MAX_TOKENS" -> throw GeminiException("The answer was cut off. Try fewer photos or shorter notes.", retryable = true)
                "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST" ->
                    throw GeminiException("Gemini blocked this content. Try different photos or wording.")
            }
            if (text.isBlank()) throw GeminiException("Gemini returned an empty answer", retryable = true)
            return text
        }
    }

    private fun httpError(code: Int, raw: String, model: String): GeminiException {
        val message = runCatching {
            JsonParser.parseString(raw).asJsonObject.obj("error")?.str("message")
        }.getOrNull() ?: raw.take(200)
        return when {
            code == 400 && message.contains("API key", ignoreCase = true) ->
                GeminiException("Gemini rejected the API key. Check it in Settings.")
            code == 403 -> GeminiException("This API key can't use Gemini: $message")
            code == 404 -> GeminiException("Model \"$model\" wasn't found. Check the model name in Settings.")
            code == 429 -> GeminiException("Gemini rate limit or quota reached. Wait a minute and retry.", retryable = true)
            code >= 500 -> GeminiException("Gemini is having trouble ($code). Retrying may help.", retryable = true)
            else -> GeminiException("Gemini error $code: $message")
        }
    }

    internal fun parseVariants(text: String, styles: List<NoteStyle>): List<NoteVariant> {
        val cleaned = text.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val dtos = runCatching {
            JsonCodec.gson.fromJson(cleaned, VariantsResponse::class.java)?.variants
        }.getOrNull().orEmpty().filterNotNull()
        if (dtos.isEmpty()) throw GeminiException("Couldn't read Gemini's answer", retryable = true)

        // Match by the style key the model echoed back; fall back to position.
        val unmatched = dtos.toMutableList()
        val byStyle = styles.associateWith { style ->
            unmatched.firstOrNull { NoteStyle.fromLabelOrNull(it.style ?: it.styleLabel) == style }
                ?.also { unmatched.remove(it) }
        }
        return styles.mapNotNull { style ->
            val dto = byStyle[style] ?: unmatched.removeFirstOrNull()
            dto?.toDomain(fallbackStyle = style)?.copy(styleLabel = style.key)
                ?.takeIf { it.body.isNotBlank() }
        }.ifEmpty { throw GeminiException("Gemini's answer had no usable notes", retryable = true) }
    }

    private class VariantsResponse(val variants: List<VariantDto?>? = null)

    private fun responseSchema(styles: List<NoteStyle>): Map<String, Any> {
        val string = mapOf("type" to "STRING")
        val stringArray = mapOf("type" to "ARRAY", "items" to string)
        val fields = listOf("style", "title", "body", "hashtags", "warnings")
        return mapOf(
            "type" to "OBJECT",
            "properties" to mapOf(
                "variants" to mapOf(
                    "type" to "ARRAY",
                    "items" to mapOf(
                        "type" to "OBJECT",
                        "properties" to mapOf(
                            "style" to mapOf("type" to "STRING", "format" to "enum", "enum" to styles.map { it.key }),
                            "title" to string,
                            "body" to string,
                            "hashtags" to stringArray,
                            "warnings" to stringArray,
                        ),
                        "required" to fields,
                        "propertyOrdering" to fields,
                    ),
                ),
            ),
            "required" to listOf("variants"),
        )
    }

    private fun JsonObject.obj(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.str(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString
}
