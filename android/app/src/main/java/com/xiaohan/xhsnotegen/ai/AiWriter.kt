package com.xiaohan.xhsnotegen.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xiaohan.xhsnotegen.data.json.JsonCodec
import com.xiaohan.xhsnotegen.data.json.VariantsResponse
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.domain.NoteVariant
import com.xiaohan.xhsnotegen.i18n.tr
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

/** [detail] is the technical side (address, HTTP code, server reply), shown in debug builds and the connection test. */
class AiException(
    message: String,
    val retryable: Boolean = false,
    val badRequest: Boolean = false,
    val detail: String? = null,
) : Exception(message)

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

        // Custom servers often run reasoning models, whose thinking counts against the output limit —
        // give them room first, and step down if the server says that limit is too high.
        val budgets = if (config.provider == AiProvider.CUSTOM) listOf(MAX_OUTPUT_TOKENS_REASONING, 4096) else listOf<Int?>(null)
        var lastBadRequest: AiException? = null
        for (budget in budgets) {
            var tokenLimitHit = false
            for (mode in modes) {
                try {
                    val request = buildRequest(config, systemPrompt, userPrompt, imagesBase64, styles, mode, budget)
                    return@withContext retrying { parseVariants(execute(config, request), styles) }
                        .map { it.copy(model = config.label) }
                } catch (e: AiException) {
                    // A 400 at a stricter mode is usually "I don't support that response format".
                    if (!e.badRequest) throw e
                    lastBadRequest = e
                    if (isTokenLimitError(e.message)) { tokenLimitHit = true; break }
                }
            }
            if (!tokenLimitHit) break
        }
        throw lastBadRequest ?: AiException(tr("The model rejected the request", "模型拒绝了这个请求"))
    }

    internal fun isTokenLimitError(message: String?): Boolean {
        val m = message?.lowercase().orEmpty()
        return "max_tokens" in m || "max_completion_tokens" in m || "max tokens" in m || "maximum context" in m
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
                if (attempt >= 1) throw AiException(tr("Network error: ${e.message ?: "connection failed"}", "网络错误：${e.message ?: "连接失败"}"))
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
        maxTokens: Int? = null,
    ): HttpCall = when (config.provider.protocol) {
        AiProtocol.GEMINI -> geminiRequest(config, systemPrompt, userPrompt, imagesBase64, styles, mode)
        AiProtocol.OPENAI_CHAT -> openAiRequest(config, systemPrompt, userPrompt, imagesBase64, styles, mode, maxTokens)
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
        maxTokens: Int? = null,
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
                addProperty("max_tokens", maxTokens ?: MAX_OUTPUT_TOKENS)
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
            if (!resp.isSuccessful) throw httpError(c, resp.code, raw, call.url)
            return extractText(c.provider.protocol, raw)
        }
    }

    /** Pulls the model's answer text out of a successful response body. */
    internal fun extractText(protocol: AiProtocol, raw: String): String {
        val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            ?: throw AiException(tr("The model returned an unreadable response", "模型返回的内容无法读取"), retryable = true)

        val (text, stop) = when (protocol) {
            AiProtocol.GEMINI -> {
                json.obj("promptFeedback")?.str("blockReason")?.let { throw AiException(tr("The request was blocked ($it)", "请求被拦截（$it）")) }
                val cand = json.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject
                    ?: throw AiException(tr("The model returned no answer", "模型没有返回回答"), retryable = true)
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
                    ?: throw AiException(tr("The model returned no answer", "模型没有返回回答"), retryable = true)
                val msg = choice.obj("message")
                if (!msg?.str("refusal").isNullOrBlank()) throw AiException(tr("The model declined: ${msg?.str("refusal")}", "模型拒绝回答：${msg?.str("refusal")}"))
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
            Stop.TRUNCATED -> throw if (text.isBlank()) AiException(
                tr(
                    "The model used up its whole output limit thinking, before writing anything. Try a model that thinks less, or one with a larger limit.",
                    "模型把输出额度全用在“思考”上，还没开始写就用完了。请换一个思考较少的模型，或额度更大的模型。",
                ),
            ) else AiException(tr("The answer was cut off. Try fewer photos or shorter notes.", "回答被截断了。试试少放几张照片或写短一点。"), retryable = true)
            Stop.BLOCKED -> throw AiException(tr("The model blocked this content. Try different photos or wording.", "模型拦截了这些内容。换几张照片或换个说法试试。"))
            Stop.OK -> Unit
        }
        if (text.isBlank()) throw AiException(tr("The model returned an empty answer", "模型返回了空回答"), retryable = true)
        return text
    }

    private enum class Stop { OK, TRUNCATED, BLOCKED }

    internal fun httpError(c: AiConfig, code: Int, raw: String, url: String? = null): AiException {
        val detail = "${url ?: "?"}\nHTTP $code\n${raw.trim().take(800)}"
        return mapHttpError(c, code, raw).let { AiException(it.message.orEmpty(), it.retryable, it.badRequest, detail) }
    }

    private fun mapHttpError(c: AiConfig, code: Int, raw: String): AiException {
        // Providers put the reason in error.message and a machine-readable
        // error.code / error.type (OpenAI) or error.status (Gemini).
        val err = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()?.let { json ->
            json.get("error")?.let { if (it.isJsonObject) it.asJsonObject else null }
        }
        val message = (err?.str("message")
            ?: runCatching { JsonParser.parseString(raw).asJsonObject.let { it.str("message") ?: it.str("error") } }.getOrNull())
            ?.takeIf { it.isNotBlank() } ?: raw.take(200)
        val errCode = listOfNotNull(err?.str("code"), err?.str("type"), err?.str("status")).joinToString(" ").lowercase()
        // A custom address is best named by its host ("api.stepfun.com"), not "Custom".
        val name = if (c.provider == AiProvider.CUSTOM) runCatching { java.net.URI(c.baseUrl).host }.getOrNull() ?: c.provider.displayName
                   else c.provider.displayName
        val keyProblem = message.contains("api key", ignoreCase = true) || message.contains("api_key", ignoreCase = true) ||
            message.contains("authentication", ignoreCase = true)
        val noCredit = "insufficient_quota" in errCode || message.contains("insufficient", ignoreCase = true) ||
            message.contains("exceeded your current quota", ignoreCase = true)
        // "Request too large … tokens per min": waiting won't help, the request itself is over the limit.
        val tooLarge = message.contains("too large", ignoreCase = true) ||
            (message.contains("tokens per min", ignoreCase = true) && message.contains("requested", ignoreCase = true))
        return when {
            code == 401 || (code in listOf(400, 403) && keyProblem) ->
                AiException(tr("$name rejected the API key. Check it in Settings.", "$name 拒绝了 API 密钥，请在设置中检查。"))
            code == 402 || (code == 429 && noCredit) -> AiException(
                tr("Your $name API account has no credit left ($message). ", "你的 $name API 账户额度已用完（$message）。") +
                    if (c.provider == AiProvider.OPENAI) tr(
                        "Note: a ChatGPT Plus/Pro subscription doesn't include API credit — add it at platform.openai.com → Billing.",
                        "注意：ChatGPT Plus/Pro 订阅不包含 API 额度，请在 platform.openai.com → Billing 充值。",
                    )
                    else tr("Add credit in your $name account.", "请在 $name 账户中充值。")
            )
            code == 429 && tooLarge -> AiException(
                tr(
                    "This note is too big for your $name account's rate limit ($message). Try fewer photos, or a model with higher limits.",
                    "这篇笔记超出了你的 $name 账户频率限制（$message）。试试少放几张照片，或换一个限制更高的模型。",
                )
            )
            code == 429 -> AiException(tr("$name rate limit: $message", "$name 频率限制：$message"), retryable = true)
            code == 403 -> AiException(tr("This key can't use \"${c.model}\": $message", "这个密钥无法使用“${c.model}”：$message"))
            code == 404 -> AiException(tr("Model \"${c.model}\" wasn't found at $name. Check the model in Settings.", "$name 上找不到模型“${c.model}”，请在设置中检查模型。"))
            code >= 500 -> AiException(tr("$name is having trouble ($code). Retrying may help.", "$name 出了点问题（$code），重试也许能解决。"), retryable = true)
            (code == 400 || code == 422) && c.vision && mentionsImages(message) -> AiException(
                tr(
                    "$name says this model can't take photos ($message). In Settings → AI writing, turn off \"Model can see photos\" — your notes will be written from your text.",
                    "$name 表示这个模型不能接收图片（$message）。请在 设置 → AI 写作 里关闭“模型能看图片”，笔记会只根据你的文字生成。",
                ), badRequest = true,
            )
            code == 400 || code == 422 -> AiException(tr("$name couldn't handle the request: $message", "$name 无法处理这个请求：$message"), badRequest = true)
            else -> AiException(tr("$name error $code: $message", "$name 错误 $code：$message"))
        }
    }

    private fun mentionsImages(message: String): Boolean = listOf("image", "vision", "multimodal", "multi-modal", "图片", "图像")
        .any { message.contains(it, ignoreCase = true) }

    // ---------------------------------------------------------------------
    // Connection test & model list
    // ---------------------------------------------------------------------

    /** A raw HTTP answer; never throws for HTTP errors (network errors still throw IOException). */
    data class RawResult(val url: String, val code: Int, val body: String, val millis: Long)

    private fun timed(request: Request): RawResult {
        val start = System.currentTimeMillis()
        client.newCall(request).execute().use { resp ->
            return RawResult(request.url.toString(), resp.code, resp.body?.string().orEmpty(), System.currentTimeMillis() - start)
        }
    }

    internal fun post(call: HttpCall): RawResult = timed(
        Request.Builder().url(call.url)
            .apply { call.headers.forEach { (k, v) -> addHeader(k, v) } }
            .post(call.body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    )

    private fun get(url: String, headers: Map<String, String>): RawResult = timed(
        Request.Builder().url(url).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.get().build()
    )

    /**
     * The smallest request that proves the key, address and model work: one short
     * question, optionally with a tiny picture to see whether the model takes photos.
     */
    internal fun pingCall(c: AiConfig, imageBase64: String? = null): HttpCall {
        val question = if (imageBase64 == null) "Reply with just the word OK." else "What is the main color of this image? Answer in one word."
        val images = listOfNotNull(imageBase64)
        return when (c.provider.protocol) {
            AiProtocol.GEMINI -> {
                val parts = JsonArray().apply {
                    add(obj("text" to question))
                    images.forEach { add(obj("inlineData" to obj("mimeType" to "image/jpeg", "data" to it))) }
                }
                val generation = JsonObject().apply {
                    addProperty("maxOutputTokens", 512)
                    if (c.model.startsWith("gemini-3")) add("thinkingConfig", obj("thinkingLevel" to "low"))
                }
                HttpCall(
                    url = "${c.baseUrl.trimEnd('/')}/models/${c.model}:generateContent",
                    headers = mapOf("x-goog-api-key" to c.apiKey),
                    body = JsonObject().apply {
                        add("contents", arr(obj("role" to "user", "parts" to parts)))
                        add("generationConfig", generation)
                    },
                )
            }
            AiProtocol.OPENAI_CHAT -> {
                val content = JsonArray().apply {
                    add(obj("type" to "text", "text" to question))
                    images.forEach { add(obj("type" to "image_url", "image_url" to obj("url" to "data:image/jpeg;base64,$it"))) }
                }
                val body = JsonObject().apply {
                    addProperty("model", c.model)
                    add("messages", arr(obj("role" to "user", "content" to if (images.isEmpty()) JsonCodec.gson.toJsonTree(question) else content)))
                    // Reasoning models spend tokens thinking first; leave room for an answer.
                    addProperty(if (c.provider == AiProvider.OPENAI) "max_completion_tokens" else "max_tokens", 1024)
                }
                val base = c.baseUrl.trimEnd('/')
                HttpCall(
                    url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions",
                    headers = if (c.apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${c.apiKey}"),
                    body = body,
                )
            }
            AiProtocol.ANTHROPIC -> {
                val content = JsonArray().apply {
                    images.forEach { add(obj("type" to "image", "source" to obj("type" to "base64", "media_type" to "image/jpeg", "data" to it))) }
                    add(obj("type" to "text", "text" to question))
                }
                HttpCall(
                    url = "${c.baseUrl.trimEnd('/')}/messages",
                    headers = mapOf("x-api-key" to c.apiKey, "anthropic-version" to "2023-06-01"),
                    body = JsonObject().apply {
                        addProperty("model", c.model)
                        addProperty("max_tokens", 256)
                        add("messages", arr(obj("role" to "user", "content" to content)))
                    },
                )
            }
        }
    }

    /** Address of the models list for this provider, plus the headers it needs. */
    private fun modelsCall(c: AiConfig): Pair<String, Map<String, String>> {
        val base = c.baseUrl.trimEnd('/').removeSuffix("/chat/completions").removeSuffix("/messages")
        return when (c.provider.protocol) {
            AiProtocol.GEMINI -> "$base/models?pageSize=200" to mapOf("x-goog-api-key" to c.apiKey)
            AiProtocol.OPENAI_CHAT -> "$base/models" to
                if (c.apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${c.apiKey}")
            AiProtocol.ANTHROPIC -> "$base/models?limit=200" to mapOf("x-api-key" to c.apiKey, "anthropic-version" to "2023-06-01")
        }
    }

    /** What the server says it has — used to pick a model for a custom address. */
    suspend fun listModels(c: AiConfig): List<String> = withContext(Dispatchers.IO) {
        val (url, headers) = modelsCall(c)
        val raw = try { get(url, headers) } catch (e: IOException) {
            throw AiException(tr("Couldn't reach ${c.baseUrl}: ${e.message ?: "connection failed"}", "连接不上 ${c.baseUrl}：${e.message ?: "连接失败"}"),
                detail = "GET $url\n${e.javaClass.simpleName}: ${e.message}")
        }
        if (raw.code !in 200..299) throw httpError(c, raw.code, raw.body, "GET $url")
        parseModelIds(c.provider.protocol, raw.body)
    }

    internal fun parseModelIds(protocol: AiProtocol, body: String): List<String> {
        val json = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return emptyList()
        return when (protocol) {
            AiProtocol.GEMINI -> json.getAsJsonArray("models").orEmpty()
                .mapNotNull { it.asJsonObject }
                .filter { m -> m.getAsJsonArray("supportedGenerationMethods")?.any { it.asString == "generateContent" } != false }
                .mapNotNull { it.str("name")?.removePrefix("models/") }
            else -> (json.getAsJsonArray("data") ?: json.getAsJsonArray("models")).orEmpty()
                .mapNotNull { e -> if (e.isJsonObject) e.asJsonObject.str("id") ?: e.asJsonObject.str("name") else e.takeIf { it.isJsonPrimitive }?.asString }
        }.distinct().sorted()
    }

    private fun JsonArray?.orEmpty(): List<com.google.gson.JsonElement> = this?.toList().orEmpty()

    /** Parses the model's JSON answer; tolerates code fences and stray prose around it. */
    fun parseVariants(text: String, styles: List<NoteStyle>): List<NoteVariant> {
        val trimmed = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        val candidate = if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
        val dtos = runCatching {
            JsonCodec.gson.fromJson(candidate, VariantsResponse::class.java)?.variants
        }.getOrNull().orEmpty().filterNotNull()
        if (dtos.isEmpty()) throw AiException(tr("Couldn't read the model's answer", "无法读取模型的回答"), retryable = true)

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
        }.ifEmpty { throw AiException(tr("The answer had no usable notes", "回答里没有可用的笔记"), retryable = true) }
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
