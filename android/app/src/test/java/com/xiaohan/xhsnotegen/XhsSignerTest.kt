package com.xiaohan.xhsnotegen

import com.xiaohan.xhsnotegen.ui.publish.XhsSigner
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden values produced by the upstream Python implementation
 * (ReaJason/xhs 0.2.13, xhs/help.py `sign(uri, data, ctime, a1, b1)`).
 * If these break, requests stop verifying on the XHS side.
 */
class XhsSignerTest {

    @Test
    fun `GET without body matches upstream`() {
        val sig = XhsSigner.sign(
            uri = "/api/media/v1/upload/web/permit?biz_name=spectrum&scene=image&file_count=2&version=1&source=web",
            data = null,
            a1 = "18c3a1b2c3d4e5f60718293a4b5c6d7e8f9",
            b1 = "",
            timestampMs = 1760000000000,
        )
        assertEquals("O2sKOlMLsY1GOlOvOYdk0gsl0j9psi1WOlTiZ2ZBsgM3", sig["x-s"])
        assertEquals("1760000000000", sig["x-t"])
        assertEquals(
            "2UQAPsHC+aIjqArjwjHjNsQhPsHCH0rjNsQhPaHCH0P1PjhIHjIj2eHjwjQgynEDJ74AHjIj2ePjwjQhyoPTqBPT49pjHjIj2ecjwjHUN0P1PaHVHdWMH0ijP/Y0P9rlG0Q0P9cF8/pf+0Z7P/WUw/+Y+BHMGA8D+9Lh80DjNsQh+jHCH0r7+0ZIPeZIPeZIPeZjNsQh+UHCHDuUqFTOJrMPqMDlzFRV/78Onnz3PB4AJemxwgmAy/bg/9lLypiUnDQA8FFAHjIj2eWjwjHjNsQhwaHCN/r9weZA+eqM+/HVHdWlPsHCPgF=",
            sig["x-s-common"],
        )
    }

    @Test
    fun `POST with Chinese body and null field matches upstream`() {
        // Exercises: non-ASCII (ensure_ascii=False), null serialization, and an
        // mrc() result outside the 32-bit range (-3302212388 upstream).
        val data = mapOf(
            "common" to mapOf("title" to "周五的烤肉", "desc" to "好吃\n#烤肉", "ats" to emptyList<String>()),
            "image_info" to mapOf("images" to listOf(mapOf("file_id" to "abc", "width" to 1080, "height" to 1440))),
            "video_info" to null,
        )
        val sig = XhsSigner.sign("/web_api/sns/v2/note", data, a1 = "a1value", b1 = "b1v", timestampMs = 1760000001234)
        assertEquals("1gU6slA+Oldk1BZB0gd6ZBcC16TLsg1CO25Gs6dBOjF3", sig["x-s"])
        assertEquals(
            "2UQAPsHC+aIjqArjwjHjNsQhPsHCH0rjNsQhPaHCH0P1PjhIHjIj2eHjwjQgynEDJ74AHjIj2ePjwjQhyoPTqBPT49pjHjIj2ecjwjHUN0P1PaHVHdWMH0ijG/b9GnlM8aHVHdW9H0ijP/q9PeZIPeZIP/HA+sHVHdW7H0ijPn4p+d+VcaTOJBz3PLQyc0md8e8ycf+eP/8L/o+dPL+OP0poqA8DcDRxz0PjNsQhwsHCHfHl4jHVHdWEH0iTPAPIP0HlP0PhwsIj2erIH0ilKc==",
            sig["x-s-common"],
        )
    }
}
