package com.xiaohan.xhsnotegen.ai

/** How requests are shaped on the wire. */
enum class AiProtocol { GEMINI, OPENAI_CHAT, ANTHROPIC }

/**
 * A built-in model choice.
 * @param vision whether the model accepts images — text-only models get the
 *   note details without the photos.
 */
data class AiModel(
    val id: String,
    val label: String,
    val note: String,
    val vision: Boolean = true,
)

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
    val displayName: String,
    val protocol: AiProtocol,
    val defaultBaseUrl: String,
    val keyHint: String,
    val models: List<AiModel>,
) {
    GEMINI(
        id = "gemini",
        displayName = "Gemini",
        protocol = AiProtocol.GEMINI,
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        keyHint = "aistudio.google.com/apikey",
        models = listOf(
            AiModel("gemini-3.8-flash", "Gemini 3.8 Flash", "Latest Flash — fast, great value"),
            AiModel("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Cheapest"),
            AiModel("gemini-3.1-pro-preview", "Gemini 3.1 Pro (preview)", "Strongest, slower"),
        ),
    ),
    OPENAI(
        id = "openai",
        displayName = "ChatGPT",
        protocol = AiProtocol.OPENAI_CHAT,
        defaultBaseUrl = "https://api.openai.com/v1",
        keyHint = "platform.openai.com/api-keys",
        models = listOf(
            AiModel("gpt-6-luna", "GPT-6 Luna", "Fast and cheapest ($0.10 / $0.50 per 1M)"),
            AiModel("gpt-6-sol", "GPT-6 Sol", "Balanced ($2 / $10 per 1M)"),
            AiModel("gpt-6-astra", "GPT-6 Astra", "Most capable, pricey ($10 / $50 per 1M)"),
        ),
    ),
    CLAUDE(
        id = "claude",
        displayName = "Claude",
        protocol = AiProtocol.ANTHROPIC,
        defaultBaseUrl = "https://api.anthropic.com/v1",
        keyHint = "platform.claude.com → API keys",
        models = listOf(
            AiModel("claude-sonnet-5", "Claude Sonnet 5", "Best balance of speed and quality"),
            AiModel("claude-haiku-4-5", "Claude Haiku 4.5", "Fastest, cheapest"),
            AiModel("claude-opus-5-5", "Claude Opus 5.5", "Most capable writer, pricier"),
        ),
    ),
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek",
        protocol = AiProtocol.OPENAI_CHAT,
        defaultBaseUrl = "https://api.deepseek.com",
        keyHint = "platform.deepseek.com/api_keys",
        models = listOf(
            AiModel("deepseek-flash", "DeepSeek V4.1 Flash", "Sees photos, very cheap"),
            AiModel("deepseek-v4-pro", "DeepSeek V4 Pro", "Text only — photos aren't sent", vision = false),
        ),
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom",
        protocol = AiProtocol.OPENAI_CHAT,
        defaultBaseUrl = "",
        keyHint = "Any OpenAI-compatible API (Qwen, Kimi, Doubao, OpenRouter, a local server…)",
        models = emptyList(),
    );

    val defaultModel: String get() = models.firstOrNull()?.id.orEmpty()

    fun findModel(modelId: String): AiModel? = models.firstOrNull { it.id == modelId }

    companion object {
        fun fromId(id: String?): AiProvider = entries.firstOrNull { it.id == id } ?: GEMINI
    }
}
