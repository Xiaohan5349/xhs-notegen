package com.xiaohan.xhsnotegen.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xiaohan.xhsnotegen.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Account balance, for the providers that let an API key ask for it.
 *
 * - DeepSeek: GET /user/balance (api-docs.deepseek.com/api/get-user-balance)
 * - OpenRouter: GET /api/v1/key — the key's own spending cap. The account-wide
 *   /credits needs a management key, so it isn't used (openrouter.ai/docs, Oct 2026).
 *
 * OpenAI, Anthropic and Google have no balance endpoint for API keys.
 */
object AiBalance {

    private enum class Kind { DEEPSEEK, OPENROUTER }

    private fun kindOf(c: AiConfig): Kind? = when {
        c.provider == AiProvider.DEEPSEEK -> Kind.DEEPSEEK
        c.provider == AiProvider.CUSTOM && hostOf(c.baseUrl).endsWith("openrouter.ai") -> Kind.OPENROUTER
        else -> null
    }

    fun supports(c: AiConfig): Boolean = kindOf(c) != null

    /** One line for the person, e.g. "Balance ¥110.00 (¥10.00 granted)". */
    suspend fun fetch(c: AiConfig): String = withContext(Dispatchers.IO) {
        val kind = kindOf(c) ?: throw AiException(tr("This provider doesn't report a balance.", "这个服务商不提供余额查询。"))
        if (c.apiKey.isBlank()) throw AiException(tr("Add the API key first.", "请先填写 API 密钥。"))
        val base = c.baseUrl.trimEnd('/').removeSuffix("/chat/completions")
        val url = when (kind) {
            Kind.DEEPSEEK -> "$base/user/balance"
            Kind.OPENROUTER -> "$base/key"
        }
        val raw = try { AiWriter.get(url, mapOf("Authorization" to "Bearer ${c.apiKey}")) } catch (e: IOException) {
            throw AiException(tr("Couldn't connect: ${e.message ?: "unknown error"}", "连接失败：${e.message ?: "未知错误"}"),
                detail = "GET $url\n${e.javaClass.simpleName}: ${e.message}")
        }
        if (raw.code !in 200..299) throw AiWriter.httpError(c, raw.code, raw.body, "GET $url")
        when (kind) {
            Kind.DEEPSEEK -> describeDeepSeek(raw.body)
            Kind.OPENROUTER -> describeOpenRouter(raw.body)
        } ?: throw AiException(tr("Couldn't read the balance from the answer.", "无法从返回结果读取余额。"), detail = "GET $url\n${raw.body.take(500)}")
    }

    internal fun describeDeepSeek(body: String): String? {
        val json = parse(body) ?: return null
        val infos = json.getAsJsonArray("balance_infos")?.mapNotNull { it as? JsonObject } ?: return null
        val parts = infos.map { info ->
            val sign = symbol(info.text("currency"))
            val total = info.text("total_balance")
            val granted = info.text("granted_balance")?.takeIf { it.toDoubleOrNull()?.let { g -> g > 0 } == true }
            if (granted == null) "$sign$total" else tr("$sign$total ($sign$granted granted)", "$sign$total（含赠送 $sign$granted）")
        }
        if (parts.isEmpty()) return null
        val line = tr("Balance ", "余额 ") + parts.joinToString(" · ")
        return if (json.get("is_available")?.asBoolean == false) line + tr(" — too low to make calls", "，不足以调用") else line
    }

    internal fun describeOpenRouter(body: String): String? {
        val data = parse(body)?.getAsJsonObject("data") ?: return null
        val usage = data.number("usage")
        val remaining = data.number("limit_remaining")
        val limit = data.number("limit")
        return when {
            remaining != null -> tr("This key has ${usd(remaining)} left", "这把密钥剩余 ${usd(remaining)}") +
                (limit?.let { tr(" of ${usd(it)}", "（上限 ${usd(it)}）") } ?: "")
            usage != null -> tr(
                "This key has no limit; it has used ${usd(usage)}. Account credits: openrouter.ai/settings/credits",
                "这把密钥不限额，已用 ${usd(usage)}。账户余额请到 openrouter.ai/settings/credits 查看",
            )
            else -> null
        }
    }

    private fun parse(body: String): JsonObject? = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
    private fun JsonObject.text(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.number(name: String): Double? = get(name)?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()
    private fun symbol(currency: String?) = when (currency) { "CNY" -> "¥"; "USD" -> "$"; null -> ""; else -> "$currency " }
    private fun usd(v: Double) = "$" + String.format(java.util.Locale.US, "%.2f", v)
    private fun hostOf(url: String): String = runCatching { java.net.URI(url.trim()).host }.getOrNull().orEmpty()
}
