package com.xiaohan.xhsnotegen.ai

import com.xiaohan.xhsnotegen.i18n.tr

/** How requests are shaped on the wire. */
enum class AiProtocol { GEMINI, OPENAI_CHAT, ANTHROPIC }

/**
 * A built-in model choice.
 * @param vision whether the model accepts images — text-only models get the
 *   note details without the photos.
 * @param noteZh Chinese [note]; picked at display time by the app language.
 */
data class AiModel(
    val id: String,
    val label: String,
    private val noteEn: String,
    val vision: Boolean = true,
    private val noteZh: String? = null,
) {
    val note: String get() = if (noteZh != null) tr(noteEn, noteZh) else noteEn
}

/**
 * Supported providers and their built-in models.
 *
 * Model ids verified against each provider's docs in September 2026:
 * ai.google.dev/gemini-api/docs/models, developers.openai.com/api/docs/models,
 * platform.claude.com/docs/en/about-claude/models/overview,
 * api-docs.deepseek.com (Models & Pricing, Vision guide).
 * Any other id can be typed in Settings, so new releases don't need an app update.
 */
enum class AiProvider(
    val id: String,
    private val brand: String,
    val protocol: AiProtocol,
    val defaultBaseUrl: String,
    private val hint: String,
    val models: List<AiModel>,
) {
    GEMINI(
        id = "gemini",
        brand = "Gemini",
        protocol = AiProtocol.GEMINI,
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        hint = "aistudio.google.com/apikey",
        models = listOf(
            AiModel("gemini-3.8-flash", "Gemini 3.8 Flash", "Latest Flash — fast, great value", noteZh = "最新 Flash，速度快、性价比高"),
            AiModel("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Cheapest", noteZh = "最便宜"),
            AiModel("gemini-3.1-pro-preview", "Gemini 3.1 Pro (preview)", "Strongest, slower", noteZh = "最强，较慢"),
        ),
    ),
    OPENAI(
        id = "openai",
        brand = "ChatGPT",
        protocol = AiProtocol.OPENAI_CHAT,
        defaultBaseUrl = "https://api.openai.com/v1",
        hint = "platform.openai.com/api-keys",
        models = listOf(
            AiModel("gpt-6-luna", "GPT-6 Luna", "Fast and cheapest ($0.10 / $0.50 per 1M)", noteZh = "最快最便宜（每百万 $0.10 / $0.50）"),
            AiModel("gpt-6-sol", "GPT-6 Sol", "Balanced ($2 / $10 per 1M)", noteZh = "均衡（每百万 $2 / $10）"),
            AiModel("gpt-6-astra", "GPT-6 Astra", "Most capable, pricey ($10 / $50 per 1M)", noteZh = "能力最强，较贵（每百万 $10 / $50）"),
        ),
    ),
    CLAUDE(
        id = "claude",
        brand = "Claude",
        protocol = AiProtocol.ANTHROPIC,
        defaultBaseUrl = "https://api.anthropic.com/v1",
        hint = "platform.claude.com → API keys",
        models = listOf(
            AiModel("claude-sonnet-5", "Claude Sonnet 5", "Best balance of speed and quality", noteZh = "速度与质量最均衡"),
            AiModel("claude-haiku-4-5", "Claude Haiku 4.5", "Fastest, cheapest", noteZh = "最快、最便宜"),
            AiModel("claude-opus-5-5", "Claude Opus 5.5", "Most capable writer, pricier", noteZh = "文笔最强，较贵"),
        ),
    ),
    DEEPSEEK(
        id = "deepseek",
        brand = "DeepSeek",
        protocol = AiProtocol.OPENAI_CHAT,
        defaultBaseUrl = "https://api.deepseek.com",
        hint = "platform.deepseek.com/api_keys",
        models = listOf(
            AiModel("deepseek-flash", "DeepSeek V4.1 Flash", "Sees photos, very cheap", noteZh = "能看照片，非常便宜"),
            AiModel("deepseek-v4-pro", "DeepSeek V4 Pro", "Text only — photos aren't sent", vision = false, noteZh = "仅文字，不发送照片"),
        ),
    ),
    CUSTOM(
        id = "custom",
        brand = "Custom",
        protocol = AiProtocol.OPENAI_CHAT,
        defaultBaseUrl = "",
        hint = "Any OpenAI-compatible API (Qwen, Kimi, Doubao, OpenRouter, a local server…)",
        models = emptyList(),
    );

    /** Brand names stay as they are; only "Custom" is translated. */
    val displayName: String
        get() = if (this == CUSTOM) tr(brand, "自定义") else brand

    val keyHint: String
        get() = if (this == CUSTOM) tr(hint, "任何兼容 OpenAI 的 API（通义千问、Kimi、豆包、OpenRouter、本地服务器等）") else hint

    val defaultModel: String get() = models.firstOrNull()?.id.orEmpty()

    fun findModel(modelId: String): AiModel? = models.firstOrNull { it.id == modelId }

    companion object {
        fun fromId(id: String?): AiProvider = entries.firstOrNull { it.id == id } ?: GEMINI
    }
}
