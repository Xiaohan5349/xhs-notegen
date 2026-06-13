package com.xiaohan.xhsnotegen.ui.generate

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Direct Gemini REST API client. No backend needed.
 */
object GeminiClient {

    private const val GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    private const val PREFS_NAME = "gemini_config"
    private const val KEY_API_KEY = "api_key"

    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // ---- API key management ----

    fun getApiKey(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, null)
    }

    fun saveApiKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    // ---- Gemini API call ----

    data class GeminiRequest(
        @SerializedName("system_instruction") val systemInstruction: SystemInstruction?,
        val contents: List<Content>,
        @SerializedName("generation_config") val generationConfig: GenerationConfig,
    )

    data class SystemInstruction(
        val parts: List<Part>,
    )

    data class Part(
        val text: String? = null,
        @SerializedName("inline_data") val inlineData: InlineData? = null,
    )

    data class InlineData(
        @SerializedName("mime_type") val mimeType: String,
        val data: String,
    )

    data class Content(
        val role: String,
        val parts: List<Part>,
    )

    data class GenerationConfig(
        val temperature: Double,
        @SerializedName("max_output_tokens") val maxOutputTokens: Int,
        @SerializedName("response_mime_type") val responseMimeType: String,
    )

    // Response types
    data class GeminiResponse(
        val candidates: List<Candidate>?,
    )

    data class Candidate(
        val content: ContentResp?,
    )

    data class ContentResp(
        val parts: List<PartResp>?,
    )

    data class PartResp(
        val text: String?,
    )

    data class NoteVariantJson(
        val styleLabel: String,
        val title: String,
        val body: String,
        val hashtags: List<String>,
        val warnings: List<String> = emptyList(),
    )

    data class VariantsResponse(
        val variants: List<NoteVariantJson>,
    )

    /**
     * Call Gemini API to generate food note variants.
     */
    suspend fun generateFoodNote(
        context: Context,
        systemPrompt: String,
        userPrompt: String,
        imagesBase64: List<String>,
    ): List<NoteVariantJson> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
            ?: throw IllegalStateException("Gemini API key not set. Please configure it in settings.")

        // Build parts: text prompt + images
        val parts = mutableListOf<Part>()
        parts.add(Part(text = userPrompt))
        for (img in imagesBase64) {
            parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = img)))
        }

        val request = GeminiRequest(
            systemInstruction = SystemInstruction(parts = listOf(Part(text = systemPrompt))),
            contents = listOf(Content(role = "user", parts = parts)),
            generationConfig = GenerationConfig(
                temperature = 0.8,
                maxOutputTokens = 2048,
                responseMimeType = "application/json",
            ),
        )

        val resp = client.newCall(
            Request.Builder()
                .url(GEMINI_URL)
                .addHeader("x-goog-api-key", apiKey)
                .post(gson.toJson(request).toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()

        val body = resp.body?.string()
            ?: throw RuntimeException("Gemini returned empty response (${resp.code})")

        if (resp.code != 200) {
            throw RuntimeException("Gemini API error ${resp.code}: ${body.take(300)}")
        }

        val geminiResp = gson.fromJson(body, GeminiResponse::class.java)
        val text = geminiResp.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            ?: throw RuntimeException("Gemini returned no content")

        // Parse the JSON response from Gemini
        val variantsResp = gson.fromJson(text, VariantsResponse::class.java)
        variantsResp.variants
    }
}
