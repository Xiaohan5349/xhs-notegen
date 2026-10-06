package com.xiaohan.xhsnotegen

import com.xiaohan.xhsnotegen.ui.publish.cleanWebViewUserAgent
import org.junit.Assert.assertEquals
import org.junit.Test

class LoginUserAgentTest {
    @Test
    fun `webview markers are removed, real chrome version kept`() {
        val webView = "Mozilla/5.0 (Linux; Android 15; Pixel 7 Build/AP3A; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/124.0.6367.219 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 15; Pixel 7 Build/AP3A) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.6367.219 Mobile Safari/537.36",
            cleanWebViewUserAgent(webView),
        )
    }
}
