package com.xiaohan.xhsnotegen

import com.xiaohan.xhsnotegen.ui.create.moved
import com.xiaohan.xhsnotegen.ui.publish.XhsApiClient
import com.xiaohan.xhsnotegen.ui.publish.XhsApiClient.Topic
import org.junit.Assert.*
import org.junit.Test

class TopicsAndPhotosTest {

    @Test
    fun `linked topics use the xhs format, unknown tags stay plain`() {
        val desc = XhsApiClient.descWithTopics(
            "正文",
            listOf(Topic("南京美食", id = "5c1a"), Topic("没这个话题")),
        )
        assertEquals("正文\n#南京美食[话题]# #没这个话题", desc)
        assertEquals("正文", XhsApiClient.descWithTopics("正文", emptyList()))
    }

    @Test
    fun `topic search prefers exact match and never picks unrelated topics`() {
        val dtos = listOf(
            mapOf("id" to "1", "name" to "上海美食攻略", "link" to "l1"),
            mapOf("id" to "2", "name" to "上海美食", "link" to "l2"),
        )
        assertEquals("2", XhsApiClient.pickTopic("上海美食", dtos)?.id)
        assertEquals("1", XhsApiClient.pickTopic("美食攻略", dtos)?.id) // close match
        assertNull(XhsApiClient.pickTopic("北京烤鸭", dtos))             // unrelated → plain tag
    }

    @Test
    fun `photos move within bounds`() {
        val l = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), l.moved("b", -1))
        assertEquals(listOf("a", "c", "b"), l.moved("b", +1))
        assertEquals(l, l.moved("a", -1))
        assertEquals(l, l.moved("x", +1))
    }
}
