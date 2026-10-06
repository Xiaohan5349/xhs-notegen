package com.xiaohan.xhsnotegen.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.xiaohan.xhsnotegen.i18n.tr

/** Everything needed for one generation request. */
data class AiConfig(
    val provider: AiProvider,
    val model: String,
    val apiKey: String,
    val baseUrl: String,
    /** Whether photos are sent. False for text-only models. */
    val vision: Boolean,
) {
    /** Short name for the UI, e.g. "Gemini 3.8 Flash" or "Custom · qwen-vl-max". */
    val label: String
        get() = provider.findModel(model)?.label ?: "${provider.displayName} · $model"

    /** Human-readable reason this config can't be used yet, or null if it's ready. */
    fun problem(): String? = when {
        provider == AiProvider.CUSTOM && baseUrl.isBlank() -> tr("Set the API address for your custom model in Settings.", "请在设置中填写自定义模型的 API 地址。")
        model.isBlank() -> tr("Choose a model in Settings.", "请在设置中选择模型。")
        apiKey.isBlank() && provider != AiProvider.CUSTOM -> tr("Add your ${provider.displayName} API key in Settings first.", "请先在设置中添加 ${provider.displayName} API 密钥。")
        else -> null
    }
}

/**
 * Provider, model, key and endpoint choices. Keys are stored per provider, so
 * switching back and forth doesn't lose them.
 */
object AiSettings {

    private const val PREFS = "ai_config"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_CUSTOM_URL = "custom_base_url"
    private const val KEY_CUSTOM_VISION = "custom_vision"
    private fun keyFor(p: AiProvider) = "key_${p.id}"
    private fun modelFor(p: AiProvider) = "model_${p.id}"

    // Before multi-provider support the app only knew Gemini and kept its settings here.
    private const val LEGACY_PREFS = "gemini_config"

    private fun prefs(c: Context): SharedPreferences = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun provider(c: Context): AiProvider = AiProvider.fromId(prefs(c).getString(KEY_PROVIDER, null))

    fun setProvider(c: Context, p: AiProvider) = prefs(c).edit { putString(KEY_PROVIDER, p.id) }

    fun apiKey(c: Context, p: AiProvider): String {
        prefs(c).getString(keyFor(p), null)?.let { return it }
        if (p == AiProvider.GEMINI) {
            return c.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).getString("api_key", null).orEmpty()
        }
        return ""
    }

    fun setApiKey(c: Context, p: AiProvider, key: String) = prefs(c).edit { putString(keyFor(p), key.trim()) }

    /** The chosen model id, falling back to the provider's recommended one. */
    fun model(c: Context, p: AiProvider): String =
        prefs(c).getString(modelFor(p), null)?.takeIf { it.isNotBlank() }
            ?.let { if (p == AiProvider.GEMINI && it.startsWith("gemini-2.5")) null else it }
            ?: p.defaultModel

    fun setModel(c: Context, p: AiProvider, model: String) =
        prefs(c).edit { putString(modelFor(p), sanitizeModel(model)) }

    fun customBaseUrl(c: Context): String = prefs(c).getString(KEY_CUSTOM_URL, null).orEmpty()
    fun setCustomBaseUrl(c: Context, url: String) = prefs(c).edit { putString(KEY_CUSTOM_URL, url.trim().trimEnd('/')) }

    fun customVision(c: Context): Boolean = prefs(c).getBoolean(KEY_CUSTOM_VISION, true)
    fun setCustomVision(c: Context, vision: Boolean) = prefs(c).edit { putBoolean(KEY_CUSTOM_VISION, vision) }

    fun current(c: Context): AiConfig {
        val p = provider(c)
        val model = model(c, p)
        return AiConfig(
            provider = p,
            model = model,
            apiKey = apiKey(c, p),
            baseUrl = if (p == AiProvider.CUSTOM) customBaseUrl(c) else p.defaultBaseUrl,
            // Unknown ids typed by the user are assumed to see images; the API says so if not.
            vision = if (p == AiProvider.CUSTOM) customVision(c) else p.findModel(model)?.vision ?: true,
        )
    }

    /** Accepts "model" or "models/model"; strips whitespace. */
    fun sanitizeModel(raw: String): String = raw.trim().removePrefix("models/")
}
