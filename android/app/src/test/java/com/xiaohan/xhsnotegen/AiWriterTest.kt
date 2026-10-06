package com.xiaohan.xhsnotegen

import com.xiaohan.xhsnotegen.ai.AiConfig
import com.xiaohan.xhsnotegen.ai.AiException
import com.xiaohan.xhsnotegen.ai.AiProtocol
import com.xiaohan.xhsnotegen.ai.AiProvider
import com.xiaohan.xhsnotegen.ai.AiWriter
import com.xiaohan.xhsnotegen.ai.AiWriter.OutputMode
import com.xiaohan.xhsnotegen.domain.NoteStyle
import org.junit.Assert.*
import org.junit.Test

/**
 * Request shapes follow each provider's current docs (Sept 2026); these tests
 * pin them so a refactor can't silently break a provider we can't call in CI.
 */
class AiWriterTest {

    private val styles = listOf(NoteStyle.CASUAL_STORY, NoteStyle.CLEAN)
    private fun cfg(p: AiProvider, model: String = p.defaultModel, url: String = p.defaultBaseUrl) =
        AiConfig(p, model, "KEY", url, vision = true)

    private fun build(c: AiConfig, mode: OutputMode = OutputMode.SCHEMA) =
        AiWriter.buildRequest(c, "SYS", "USER", listOf("IMG"), styles, mode)

    @Test
    fun `gemini 3 uses json schema, thinking level and no temperature`() {
        val call = build(cfg(AiProvider.GEMINI))
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", call.url)
        assertEquals("KEY", call.headers["x-goog-api-key"])
        val gen = call.body.getAsJsonObject("generationConfig")
        assertEquals("application/json", gen["responseMimeType"].asString)
        assertTrue(gen.has("responseJsonSchema"))
        assertEquals("low", gen.getAsJsonObject("thinkingConfig")["thinkingLevel"].asString)
        assertFalse(gen.has("temperature"))
        val parts = call.body.getAsJsonArray("contents")[0].asJsonObject.getAsJsonArray("parts")
        assertEquals("IMG", parts[1].asJsonObject.getAsJsonObject("inlineData")["data"].asString)
    }

    @Test
    fun `openai uses strict json schema, reasoning effort and data-url images`() {
        val call = build(cfg(AiProvider.OPENAI))
        assertEquals("https://api.openai.com/v1/chat/completions", call.url)
        assertEquals("Bearer KEY", call.headers["Authorization"])
        val b = call.body
        assertEquals("gpt-6-luna", b["model"].asString)
        assertEquals("low", b["reasoning_effort"].asString)
        assertTrue(b.has("max_completion_tokens"))
        val rf = b.getAsJsonObject("response_format")
        assertEquals("json_schema", rf["type"].asString)
        assertTrue(rf.getAsJsonObject("json_schema")["strict"].asBoolean)
        val user = b.getAsJsonArray("messages")[1].asJsonObject.getAsJsonArray("content")
        assertEquals("data:image/jpeg;base64,IMG", user[1].asJsonObject.getAsJsonObject("image_url")["url"].asString)
    }

    @Test
    fun `deepseek and custom use json mode and max_tokens, no openai-only params`() {
        val call = build(cfg(AiProvider.DEEPSEEK), OutputMode.JSON)
        assertEquals("https://api.deepseek.com/chat/completions", call.url)
        assertEquals("json_object", call.body.getAsJsonObject("response_format")["type"].asString)
        assertTrue(call.body.has("max_tokens"))
        assertFalse(call.body.has("reasoning_effort"))

        val custom = build(cfg(AiProvider.CUSTOM, "qwen-vl", "https://x.ai/v1/"), OutputMode.PLAIN)
        assertEquals("https://x.ai/v1/chat/completions", custom.url)
        assertFalse(custom.body.has("response_format"))
    }

    @Test
    fun `claude puts images first and uses output_config`() {
        val call = build(cfg(AiProvider.CLAUDE))
        assertEquals("https://api.anthropic.com/v1/messages", call.url)
        assertEquals("2023-06-01", call.headers["anthropic-version"])
        assertEquals("SYS", call.body["system"].asString)
        val content = call.body.getAsJsonArray("messages")[0].asJsonObject.getAsJsonArray("content")
        assertEquals("image", content[0].asJsonObject["type"].asString)
        assertEquals("text", content[1].asJsonObject["type"].asString)
        val oc = call.body.getAsJsonObject("output_config")
        assertEquals("json_schema", oc.getAsJsonObject("format")["type"].asString)
        assertEquals("low", oc["effort"].asString)
        // Haiku doesn't support effort.
        val haiku = build(cfg(AiProvider.CLAUDE, "claude-haiku-4-5"), OutputMode.PLAIN)
        assertFalse(haiku.body.has("output_config"))
    }

    @Test
    fun `schema forbids extra properties and lists every field as required`() {
        val s = AiWriter.variantsSchema(styles)
        assertFalse(s["additionalProperties"].asBoolean)
        val item = s.getAsJsonObject("properties").getAsJsonObject("variants").getAsJsonObject("items")
        assertFalse(item["additionalProperties"].asBoolean)
        assertEquals(5, item.getAsJsonArray("required").size())
    }

    @Test
    fun `answer text is extracted from each protocol`() {
        assertEquals("A", AiWriter.extractText(AiProtocol.GEMINI,
            """{"candidates":[{"content":{"parts":[{"text":"x","thought":true},{"text":"A"}]},"finishReason":"STOP"}]}"""))
        assertEquals("B", AiWriter.extractText(AiProtocol.OPENAI_CHAT,
            """{"choices":[{"message":{"content":"B"},"finish_reason":"stop"}]}"""))
        assertEquals("C", AiWriter.extractText(AiProtocol.ANTHROPIC,
            """{"content":[{"type":"thinking","thinking":"..."},{"type":"text","text":"C"}],"stop_reason":"end_turn"}"""))
    }

    @Test
    fun `truncation and refusals become clear errors`() {
        val cut = runCatching {
            AiWriter.extractText(AiProtocol.ANTHROPIC, """{"content":[{"type":"text","text":"{"}],"stop_reason":"max_tokens"}""")
        }.exceptionOrNull() as AiException
        assertTrue(cut.retryable)
        val refused = runCatching {
            AiWriter.extractText(AiProtocol.OPENAI_CHAT, """{"choices":[{"message":{"content":null,"refusal":"no"}}]}""")
        }.exceptionOrNull()
        assertTrue(refused is AiException && !refused.retryable)
    }

    @Test
    fun `http errors map to actionable messages`() {
        val c = cfg(AiProvider.DEEPSEEK)
        assertTrue(AiWriter.httpError(c, 401, """{"error":{"message":"bad"}}""").message!!.contains("API key"))
        assertTrue(AiWriter.httpError(c, 402, "{}").message!!.contains("credit"))
        assertTrue(AiWriter.httpError(c, 429, "{}").retryable)
        assertTrue(AiWriter.httpError(c, 400, """{"error":{"message":"response_format not supported"}}""").badRequest)
        // Gemini reports bad keys as 400 — must not be treated as a format problem.
        assertFalse(AiWriter.httpError(cfg(AiProvider.GEMINI), 400, """{"error":{"message":"API key not valid"}}""").badRequest)
    }

    @Test
    fun `prose around the JSON is tolerated`() {
        val v = AiWriter.parseVariants("Here you go:\n{\"variants\":[{\"style\":\"clean\",\"title\":\"t\",\"body\":\"b\"}]}\nEnjoy!",
            listOf(NoteStyle.CLEAN))
        assertEquals("b", v.single().body)
    }

    @Test
    fun `openai quota errors are told apart`() {
        val c = cfg(AiProvider.OPENAI)
        val noCredit = AiWriter.httpError(c, 429,
            """{"error":{"message":"You exceeded your current quota, please check your plan and billing details.","type":"insufficient_quota","code":"insufficient_quota"}}""")
        assertTrue(noCredit.message!!.contains("no credit"))
        assertTrue(noCredit.message!!.contains("ChatGPT Plus"))
        assertFalse(noCredit.retryable)

        val tooBig = AiWriter.httpError(c, 429,
            """{"error":{"message":"Request too large for gpt-6-luna on tokens per min (TPM): Limit 30000, Requested 41000.","type":"tokens","code":"rate_limit_exceeded"}}""")
        assertTrue(tooBig.message!!.contains("too big"))
        assertFalse(tooBig.retryable)

        val busy = AiWriter.httpError(c, 429, """{"error":{"message":"Rate limit reached for requests","code":"rate_limit_exceeded"}}""")
        assertTrue(busy.retryable)
        assertTrue(busy.message!!.contains("Rate limit reached"))
    }
}
