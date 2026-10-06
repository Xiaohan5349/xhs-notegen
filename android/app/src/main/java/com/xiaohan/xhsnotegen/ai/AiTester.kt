package com.xiaohan.xhsnotegen.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.gson.JsonParser
import com.xiaohan.xhsnotegen.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/** What the "Test connection" button found. */
data class AiTestResult(
    val ok: Boolean,
    /** One line for the person: what worked or what to fix. */
    val message: String,
    /** The model's own reply, when there was one. */
    val reply: String? = null,
    /** null = not tried (text-only setup); true/false = whether the model accepted a picture. */
    val photosOk: Boolean? = null,
    val millis: Long = 0,
    /** Address, HTTP code and the server's answer, for finding out why it failed. */
    val detail: String = "",
)

/** Sends a tiny real request with the settings on screen, before any note depends on them. */
object AiTester {

    suspend fun test(config: AiConfig): AiTestResult = withContext(Dispatchers.IO) {
        config.problem()?.let { return@withContext AiTestResult(false, it) }

        // 1. Text: proves address, key and model.
        val text = try {
            AiWriter.post(AiWriter.pingCall(config))
        } catch (e: IOException) {
            return@withContext networkFailure(config, e)
        }
        if (text.code !in 200..299) return@withContext httpFailure(config, text)
        val reply = runCatching { AiWriter.extractText(config.provider.protocol, text.body).trim() }.getOrNull()
        val summary = describe(text.body)

        // 2. Photos: only worth asking when photos would be sent.
        var photosOk: Boolean? = null
        var photoDetail = ""
        var total = text.millis
        if (config.vision) {
            try {
                val pic = AiWriter.post(AiWriter.pingCall(config, tinyJpegBase64()))
                total += pic.millis
                photosOk = pic.code in 200..299
                if (!photosOk) photoDetail = "\n\nPhoto test → ${pic.url}\nHTTP ${pic.code}\n${pic.body.trim().take(500)}"
            } catch (e: IOException) {
                photosOk = false
                photoDetail = "\n\nPhoto test → ${e.javaClass.simpleName}: ${e.message}"
            }
        }

        val message = when (photosOk) {
            false -> tr(
                "Connected — but this model didn't accept a photo. Turn off \"Model can see photos\" and notes will be written from your text.",
                "已连接，但这个模型不接受图片。请关闭“模型能看图片”，笔记会只根据你的文字生成。",
            )
            else -> tr("Connected — ${config.label} answered in ${text.millis / 100 / 10.0}s.", "连接成功：${config.label} 用了 ${text.millis / 100 / 10.0} 秒回答。")
        }
        AiTestResult(
            ok = true, message = message,
            reply = reply?.takeIf { it.isNotBlank() },
            photosOk = photosOk, millis = total,
            detail = "POST ${text.url}\nHTTP ${text.code}\n$summary$photoDetail",
        )
    }

    private fun networkFailure(c: AiConfig, e: IOException): AiTestResult {
        val what = when (e) {
            is java.net.UnknownHostException -> tr("Can't find ${hostOf(c.baseUrl)}. Check the address and your internet connection.", "找不到 ${hostOf(c.baseUrl)}，请检查地址和网络。")
            is java.net.SocketTimeoutException -> tr("The server didn't answer in time.", "服务器没有及时回应。")
            is javax.net.ssl.SSLException -> tr("Secure connection failed — check the address starts with https://.", "安全连接失败，请确认地址以 https:// 开头。")
            else -> tr("Couldn't connect: ${e.message ?: "unknown error"}", "连接失败：${e.message ?: "未知错误"}")
        }
        return AiTestResult(false, what, detail = "${e.javaClass.name}: ${e.message}\n(address: ${c.baseUrl})")
    }

    private fun httpFailure(c: AiConfig, r: AiWriter.RawResult): AiTestResult {
        val e = AiWriter.httpError(c, r.code, r.body, "POST ${r.url}")
        var msg = e.message.orEmpty()
        // Two hints that catch most wrong setups for custom addresses.
        if (c.provider == AiProvider.CUSTOM) {
            if (r.code == 401 || r.code == 403) msg += "\n" + tr(
                "Keys made on a China console and on a global console usually only work on that console's own address.",
                "国内控制台和海外控制台的密钥通常只能用于各自的地址，请确认两者一致。",
            )
            if (r.code == 404) msg += "\n" + tr(
                "The address should stop at the version part, e.g. https://api.example.com/v1 — not include /chat/completions.",
                "地址应写到版本号为止，例如 https://api.example.com/v1，不要带 /chat/completions。",
            )
        }
        return AiTestResult(false, msg, millis = r.millis, detail = e.detail.orEmpty())
    }

    /** First few characters of the answer, or the model's finish reason when it said nothing yet. */
    private fun describe(body: String): String = body.trim().take(400)

    private fun hostOf(url: String): String = runCatching { java.net.URI(url).host }.getOrNull() ?: url

    /** A 16×16 solid picture, small enough to cost nothing. */
    private fun tinyJpegBase64(): String {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(220, 40, 40)) }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
