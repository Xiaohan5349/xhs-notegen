package com.xiaohan.xhsnotegen

import com.xiaohan.xhsnotegen.ai.AiConfig
import com.xiaohan.xhsnotegen.ai.AiException
import com.xiaohan.xhsnotegen.ai.AiProtocol
import com.xiaohan.xhsnotegen.ai.AiProvider
import com.xiaohan.xhsnotegen.ai.AiWriter
import com.xiaohan.xhsnotegen.domain.NoteStyle
import org.junit.Assert.*
import org.junit.Test

/** The connection test, the model list and the custom-endpoint (StepFun-style) behavior. */
class AiConnectionTest {

    private val step = AiConfig(
        AiProvider.CUSTOM, "step-5-preview", "sk-test", "https://api.stepfun.com/step_plan/v1", vision = true,
    )

    @Test
    fun `models list is read from an OpenAI-style answer, sorted`() {
        val body = """{"object":"list","data":[{"id":"step-5-preview","object":"model"},{"id":"step-3.7-flash"},{"id":"step-3.7-flash"}]}"""
        assertEquals(listOf("step-3.7-flash", "step-5-preview"), AiWriter.parseModelIds(AiProtocol.OPENAI_CHAT, body))
        assertEquals(emptyList<String>(), AiWriter.parseModelIds(AiProtocol.OPENAI_CHAT, "not json"))
    }

    @Test
    fun `gemini list keeps only models that can write text`() {
        val body = """{"models":[
            {"name":"models/gemini-3.8-flash","supportedGenerationMethods":["generateContent"]},
            {"name":"models/text-embedding-9","supportedGenerationMethods":["embedContent"]}]}"""
        assertEquals(listOf("gemini-3.8-flash"), AiWriter.parseModelIds(AiProtocol.GEMINI, body))
    }

    @Test
    fun `the test request is small, plain, and can carry a picture`() {
        val text = AiWriter.pingCall(step)
        assertEquals("https://api.stepfun.com/step_plan/v1/chat/completions", text.url)
        assertEquals("Bearer sk-test", text.headers["Authorization"])
        assertEquals(1024, text.body["max_tokens"].asInt)
        assertFalse(text.body.has("response_format"))
        assertTrue(text.body.getAsJsonArray("messages")[0].asJsonObject["content"].isJsonPrimitive)

        val pic = AiWriter.pingCall(step, "AAAA")
        val parts = pic.body.getAsJsonArray("messages")[0].asJsonObject.getAsJsonArray("content")
        assertEquals("image_url", parts[1].asJsonObject["type"].asString)
        // A pasted address that already includes /chat/completions isn't doubled.
        assertEquals(
            "https://x.ai/v1/chat/completions",
            AiWriter.pingCall(step.copy(baseUrl = "https://x.ai/v1/chat/completions")).url,
        )
    }

    @Test
    fun `custom notes get a large output budget for reasoning models`() {
        val call = AiWriter.buildRequest(step, "S", "U", emptyList(), NoteStyle.entries, AiWriter.OutputMode.JSON, 16000)
        assertEquals(16000, call.body["max_tokens"].asInt)
        assertTrue(AiWriter.isTokenLimitError("max_tokens is too large: 16000. This model supports at most 8192"))
        assertFalse(AiWriter.isTokenLimitError("response_format is not supported"))
    }

    @Test
    fun `a model that refuses photos gets an actionable message`() {
        val e = AiWriter.httpError(step, 400, """{"error":{"message":"image input is not supported by this model"}}""", "https://x/y")
        assertTrue(e.message, e.message!!.contains("Model can see photos"))
        assertTrue(e.badRequest)
        // With photos already off, the same reply is an ordinary bad request.
        val plain = AiWriter.httpError(step.copy(vision = false), 400, """{"error":{"message":"image input is not supported"}}""")
        assertFalse(plain.message!!.contains("Model can see photos"))
    }

    @Test
    fun `errors carry the address, code and server reply for debugging`() {
        val e = AiWriter.httpError(step, 400, """{"error":{"message":"you have no active step plan subscription"}}""", "https://api.stepfun.com/step_plan/v1/chat/completions")
        assertTrue(e.message!!.contains("no active step plan subscription"))
        assertTrue(e.detail!!.contains("HTTP 400"))
        assertTrue(e.detail!!.contains("api.stepfun.com/step_plan/v1"))
    }

    @Test
    fun `a reasoning model that spends its whole budget thinking is explained`() {
        val body = """{"choices":[{"finish_reason":"length","message":{"content":"","reasoning_content":"hmm…"}}]}"""
        val err = try { AiWriter.extractText(AiProtocol.OPENAI_CHAT, body); null } catch (e: AiException) { e }
        assertNotNull(err)
        assertTrue(err!!.message!!.contains("thinking"))
        assertFalse(err.retryable)
    }
}
