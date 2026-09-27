package com.xiaohan.xhsnotegen.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xiaohan.xhsnotegen.data.json.JsonCodec
import com.xiaohan.xhsnotegen.data.json.VariantsResponse
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.domain.NoteVariant
import com.xiaohan.xhsnotegen.util.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class AiException(message: String, val retryable: Boolean = false, val badRequest: Boolean = false) : Exception(message)

/**
 * Sends one "write these styles" request to whichever provider is configured
 * and turns the answer into [NoteVariant]s.
 *
 * Structured output is requested in the strictest form each provider supports,
 * stepping down if the server rejects it:
 *   SCHEMA (JSON schema enforced) → JSON (JSON mode) → PLAIN (prompt only).
 * That keeps "Custom" OpenAI-compatible servers working even when they don't
 * implement response_format. The prompt always carries a JSON example too.
 */
object AiWriter {

    enum class OutputMode { SCHEMA, JSON, PLAIN }

    /** Same pool as the rest of the app; longer read timeout because reasoning models answer in one go. */
    private val client: OkHttpClient by lazy {
        HttpClientFactory.shared.newBuilder().readTimeout(180, TimeUnit.SECONDS).build()
    }

    private const val MAX_OUTPUT_TOKENS = 8192
    /** Claude/OpenAI count thinking/reasoning tokens against the same limit. */
    private const val MAX_OUTPUT_TOKENS_REASONING = 16000

    suspend fun generateVariants(
        config: AiConfig,
        systemPrompt: String,
        userPrompt: String,
        imagesBase64: List<String>,
        styles: List<NoteStyle>,
    ): List<NoteVariant> = withContext(Dispatchers.IO) {
        config.problem()?.let { throw AiException(it) }

        val modes = when (config.provider) {
            AiProvider.GEMINI, AiProvider.OPENAI -> listOf(OutputMode.SCHEMA, OutputMode.JSON, OutputMode.PLAIN)
            AiProvider.CLAUDE -> listOf(OutputMode.SCHEMA, OutputMode.PLAIN) // no separate JSON mode
            // DeepSeek documents json_object but not json_schema; custom servers vary.
            AiProvider.DEEPSEEK, AiProvider.CUSTOM -> listOf(OutputMode.JSON, OutputMode.PLAIN)
        }

        var lastBadRequest: AiException? = null
        for (mode in modes) {
            try {
                val request = buildRequest(config, systemPrompt, userPrompt, imagesBase64, styles, mode)
                return@withContext retrying { parseVariants(execute(config, request), styles) }
            } catch (e: AiException) {
                // A 400 at a stricter mode is usually "I don't support that response format".
                if (!e.badRequest) throw e
                lastBadRequest = e
            }
        }
        throw lastBadRequest ?: AiException("The model rejected the request")
    }

    /** One retry for transient failures (rate limits, 5xx, network, truncated JSON). */
    private suspend fun <T> retrying(block: () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: AiException) {
                if (!e.retryable || attempt >= 1) throw e
            } catch (e: IOException) {
                if (attempt >= 1) throw AiException("Network error: ${e.message ?: "connection failed"}")
            }
            attempt++
            delay(3_000)
        }
    }

    // ---------------------------------------------------------------------
    // Requests
    // ---------------------------------------------------------------------

    data class HttpCall(val url: String, val headers: Map<String, String>, val body: JsonObject)

    internal fun buildRequest(
        config: AiConfig,
        systemPrompt: String,
        userPrompt: String,
        imagesBase64: List<String>,
        styles: List<NoteStyle>,
        mode: OutputMode,
    ): HttpCall = when (config.provider.protocol) {
        AiProtocol.GEMINI -> geminiRequest(config, systemPrompt, userPrompt, imagesBase64, styles, mode)
        AiProtocol.OPENAI_CHAT -> openAiRequest(config, systemPrompt, userPrompt, imagesBase64, styles, mode)
        AiProtocol.ANTHROPIC -> anthropicRequest(config, systemPrompt, userPrompt, imagesBase64, styles, mode)
    }

    private fun geminiRequest(
        c: AiConfig, system: String, user: String, images: List<String>, styles: List<NoteStyle>, mode: OutputMode,
    ): HttpCall {
        val parts = JsonArray().apply {
            add(obj("text" to user))
            images.forEach { add(obj("inlineData" to obj("mimeType" to "image/jpeg", "data" to it))) }
        }
        val generation = JsonObject().apply {
            addProperty("maxOutputTokens", MAX_OUTPUT_TOKENS)
            if (mode != OutputMode.PLAIN) addProperty("responseMimeType", "application/json")
            if (mode == OutputMode.SCHEMA) add("responseJsonSchema", variantsSchema(styles))
            // Gemini 3 deprecates temperature/top_p and uses thinking levels;
            // a quick diary note doesn't need deep thinking.
            if (c.model.startsWith("gemini-3")) add("thinkingConfig", obj("thinkingLevel" to "low"))
        }
        val body = JsonObject().apply {
            add("systemInstruction", obj("parts" to arr(obj("text" to system))))
            add("contents", arr(obj("role" to "user", "parts" to parts)))
            add("generationConfig", generation)
        }
        return HttpCall(
            url = "${c.baseUrl.trimEnd('/')}/models/${c.model}:generateContent",
            headers = mapOf("x-goog-api-key" to c.apiKey),
            body = body,
        )
    }

    private fun openAiRequest(
        c: AiConfig, system: String, user: String, images: List<String>, styles: List<NoteStyle>, mode: OutputMode,
    ): HttpCall {
        val userContent = JsonArray().apply {
            add(obj("type" to "text", "text" to user))
            images.forEach {
                add(obj("type" to "image_url", "image_url" to obj("url" to "data:image/jpeg;base64,$it")))
            }
        }
        val official = c.provider == AiProvider.OPENAI
        val body = JsonObject().apply {
            addProperty("model", c.model)
            add("messages", arr(
                obj("role" to "system", "content" to system),
                obj("role" to "user", "content" to userContent),
            ))
            if (official) {
                // GPT-6 models reason; "low" keeps a short note fast and cheap.
                addProperty("max_completion_tokens", MAX_OUTPUT_TOKENS_REASONING)
                addProperty("reasoning_effort", "low")
            } else {
                addProperty("max_tokens", MAX_OUTPUT_TOKENS)
            }
            when (mode) {
                OutputMode.SCHEMA -> add("response_format", obj(
                    "type" to "json_schema",
                    "json_schema" to obj("name" to "food_notes", "strict" to true, "schema" to variantsSchema(styles)),
                ))
                OutputMode.JSON -> add("response_format", obj("type" to "json_object"))
                OutputMode.PLAIN -> Unit
            }
        }
        val base = c.baseUrl.trimEnd('/')
        return HttpCall(
            url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions",
            headers = if (c.apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${c.apiKey}"),
            body = body,
        )
    }

    private fun anthropicRequest(
        c: AiConfig, system: String, user: String, images: List<String>, styles: List<NoteStyle>, mode: OutputMode,
    ): HttpCall {
        // Claude reads best with images before the text that refers to them.
        val content = JsonArray().apply {
            images.forEach {
                add(obj("type" to "image", "source" to obj("type" to "base64", "media_type" to "image/jpeg", "data" to it)))
            }
            add(obj("type" to "text", "text" to user))
        }
        val outputConfig = JsonObject().apply {
            if (mode == OutputMode.SCHEMA) add("format", obj("type" to "json_schema", "schema" to variantsSchema(styles)))
            // Effort is supported on Opus / Sonnet 5+ / Fable, not on Haiku.
            if (supportsClaudeEffort(c.model)) addProperty("effort", "low")
        }
        val body = JsonObject().apply {
            addProperty("model", c.model)
            addProperty("max_tokens", MAX_OUTPUT_TOKENS_REASONING)
            addProperty("system", system)
            add("messages", arr(obj("role" to "user", "content" to content)))
            if (outputConfig.size() > 0) add("output_config", outputConfig)
        }
        return HttpCall(
            url = "${c.baseUrl.trimEnd('/')}/messages",
            headers = mapOf("x-api-key" to c.apiKey, "anthropic-version" to "2023-06-01"),
            body = body,
        )
    }

    private fun supportsClaudeEffort(model: String): Boolean =
        listOf("opus", "fable", "mythos", "sonnet-5", "sonnet-4-6").any { model.contains(it) }

    /**
     * JSON Schema for the answer. Written to the strictest common subset:
     * every object lists all properties as required and forbids extras
     * (OpenAI strict mode and Claude both require this).
     */
    internal fun variantsSchema(styles: List<NoteStyle>): JsonObject {
        val string = obj("type" to "string")
        val stringArray = obj("type" to "array", "items" to obj("type" to "string"))
        val fields = listOf("style", "title", "body", "hashtags", "warnings")
        val variant = obj(
            "type" to "object",
            "properties" to obj(
                "style" to obj("type" to "string", "enum" to styles.map { it.key }),
                "title" to string,
                "body" to string,
                "hashtags" to stringArray,
                "warnings" to stringArray,
            ),
            "required" to fields,
            "additionalProperties" to false,
        )
        return obj(
            "type" to "object",
            "properties" to obj("variants" to obj("type" to "array", "items" to variant)),
            "required" to listOf("variants"),
            "additionalProperties" to false,
        )
    }

    // ---------------------------------------------------------------------
    // Responses
    // ---------------------------------------------------------------------

    private fun execute(c: AiConfig, call: HttpCall): String {
        val request = Request.Builder()
            .url(call.url)
            .apply { call.headers.forEach { (k, v) -> addHeader(k, v) } }
            .post(call.body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw httpError(c, resp.code, raw)
            return extractText(c.provider.protocol, raw)
        }
    }

    /** Pulls the model's answer text out of a successful response body. */
    internal fun extractText(protocol: AiProtocol, raw: String): String {
        val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            ?: throw AiException("The model returned an unreadable response", retryable = true)

        val (text, stop) = when (protocol) {
            AiProtocol.GEMINI -> {
                json.obj("promptFeedback")?.str("blockReason")?.let { throw AiException("The request was blocked ($it)") }
                val cand = json.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject
                    ?: throw AiException("The model returned no answer", retryable = true)
                val t = cand.obj("content")?.getAsJsonArray("parts")
                    ?.mapNotNull { it.asJsonObject }
                    ?.filter { it.get("thought")?.asBoolean != true } // skip thought summaries
                    ?.mapNotNull { it.str("text") }?.joinToString("").orEmpty()
                t to when (cand.str("finishReason")) {
                    "MAX_TOKENS" -> Stop.TRUNCATED
                    "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "RECITATION" -> Stop.BLOCKED
                    else -> Stop.OK
                }
            }
            AiProtocol.OPENAI_CHAT -> {
                val choice = json.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                    ?: throw AiException("The model returned no answer", retryable = true)
                val msg = choice.obj("message")
                if (!msg?.str("refusal").isNullOrBlank()) throw AiException("The model declined: ${msg?.str("refusal")}")
                msg?.str("content").orEmpty() to when (choice.str("finish_reason")) {
                    "length" -> Stop.TRUNCATED
                    "content_filter" -> Stop.BLOCKED
                    else -> Stop.OK
                }
            }
            AiProtocol.ANTHROPIC -> {
                val t = json.getAsJsonArray("content")
                    ?.mapNotNull { it.asJsonObject }
                    ?.filter { it.str("type") == "text" }
                    ?.mapNotNull { it.str("text") }?.joinToString("").orEmpty()
                t to when (json.str("stop_reason")) {
                    "max_tokens" -> Stop.TRUNCATED
                    "refusal" -> Stop.BLOCKED
                    else -> Stop.OK
                }
            }
        }
        when (stop) {
            Stop.TRUNCATED -> throw AiException("The answer was cut off. Try fewer photos or shorter notes.", retryable = true)
            Stop.BLOCKED -> throw AiException("The model blocked this content. Try different photos or wording.")
            Stop.OK -> Unit
        }
        if (text.isBlank()) throw AiException("The model returned an empty answer", retryable = true)
        return text
    }

    private enum class Stop { OK, TRUNCATED, BLOCKED }

    internal fun httpError(c: AiConfig, code: Int, raw: String): AiException {
        val message = runCatching {
            val json = JsonParser.parseString(raw).asJsonObject
            val err = json.get("error")
            when {
                err == null -> json.str("message")
                err.isJsonObject -> err.asJsonObject.str("message")
                else -> err.asString
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: raw.take(200)
        val name = c.provider.displayName
        val keyProblem = message.contains("api key", ignoreCase = true) || message.contains("api_key", ignoreCase = true) ||
            message.contains("authentication", ignoreCase = true)
        return when {
            code == 401 || (code in listOf(400, 403) && keyProblem) ->
                AiException("$name rejected the API key. Check it in Settings.")
            code == 402 -> AiException("Your $name account is out of credit.")
            code == 403 -> AiException("This key can't use \"${c.model}\": $message")
            code == 404 -> AiException("Model \"${c.model}\" wasn't found at $name. Check the model in Settings.")
            code == 429 -> AiException("$name rate limit or quota reached. Wait a minute and retry.", retryable = true)
            code >= 500 -> AiException("$name is having trouble ($code). Retrying may help.", retryable = true)
            code == 400 || code == 422 -> AiException("$name couldn't handle the request: $message", badRequest = true)
            else -> AiException("$name error $code: $message")
        }
    }

    /** Parses the model's JSON answer; tolerates code fences and stray prose around it. */
    fun parseVariants(text: String, styles: List<NoteStyle>): List<NoteVariant> {
        val trimmed = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        val candidate = if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
        val dtos = runCatching {
            JsonCodec.gson.fromJson(candidate, VariantsResponse::class.java)?.variants
        }.getOrNull().orEmpty().filterNotNull()
        if (dtos.isEmpty()) throw AiException("Couldn't read the model's answer", retryable = true)

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
        }.ifEmpty { throw AiException("The answer had no usable notes", retryable = true) }
    }

    // ---------------------------------------------------------------------
    // Tiny JSON builders
    // ---------------------------------------------------------------------

    private fun obj(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject().apply {
        pairs.forEach { (k, v) -> add(k, JsonCodec.gson.toJsonTree(v)) }
    }

    private fun arr(vararg items: JsonObject): JsonArray = JsonArray().apply { items.forEach { add(it) } }

    private fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.str(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString
}
